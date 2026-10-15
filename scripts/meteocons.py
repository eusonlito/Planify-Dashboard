#!/usr/bin/env python3
"""
Genera los iconos del tiempo de la app a partir de Meteocons (Bas Milius, MIT).

Descarga los paquetes de la versión fijada con `npm pack` y, del estilo "line":
- los iconos del tiempo, animados (Lottie) → res/raw/meteocons_*.json, minificados;
- los de la cabecera (probabilidad de lluvia, humedad y viento), estáticos (SVG) → res/drawable/meteocons_*.xml.
Los colores pensados para fondo oscuro (nubes, niebla y viento casi blancos) se cambian por tonos pizarra que se vean
sobre el fondo crema del dashboard. Uso: scripts/meteocons.py
"""
import json
import pathlib
import re
import xml.etree.ElementTree as ET
import subprocess
import sys
import tarfile
import tempfile

VERSION = "3.0.0-next.10"
STYLE = "line"
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "android/app/src/main/res/raw"
DRAWABLES = ROOT / "android/app/src/main/res/drawable"
LICENSE = ROOT / "third_party/meteocons/LICENSE"

ICONS = [
    # Códigos WMO (ver meteocon() en ui/WeatherIcons.kt)
    "clear-day", "clear-night", "mostly-clear-day", "mostly-clear-night",
    "partly-cloudy-day", "partly-cloudy-night", "overcast", "fog-day", "fog-night",
    "drizzle", "rain", "sleet", "snow",
    "partly-cloudy-day-rain", "partly-cloudy-night-rain", "partly-cloudy-day-snow", "partly-cloudy-night-snow",
    "thunderstorms-day", "thunderstorms-night", "thunderstorms-day-hail", "thunderstorms-night-hail",
]

# Cabecera, estáticos: probabilidad de lluvia (nube con lluvia), humedad (gota) y viento. La animación del viento es
# un trazo discontinuo que en ningún fotograma se ve entero.
STATIC_ICONS = ["rain", "raindrop", "wind"]

# Colores del estilo "line" casi blancos → pizarra. La nube principal más oscura que la secundaria (#94A3B8).
RECOLOR = {
    "#E6EFFC": "#7C8BA1",  # nubes
    "#94A3B8": "#B3BFCF",  # nube secundaria (cubierto)
    "#E2E8F0": "#94A3B8",  # niebla y viento
}


def hex_of(rgba):
    return "#%02X%02X%02X" % tuple(round(c * 255) for c in rgba[:3])


def rgba_of(hex_color, alpha):
    return [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)] + [alpha]


def recolor(node):
    if isinstance(node, dict):
        color = node.get("c", {}).get("k") if node.get("ty") in ("st", "fl") else None
        if isinstance(color, list) and len(color) >= 3 and not isinstance(color[0], dict):
            new = RECOLOR.get(hex_of(color))
            if new:
                node["c"]["k"] = rgba_of(new, color[3] if len(color) > 3 else 1)
        for value in node.values():
            recolor(value)
    elif isinstance(node, list):
        for value in node:
            recolor(value)


SVG = "{http://www.w3.org/2000/svg}"
TRANSLATE = re.compile(r"translate\(\s*(-?[\d.]+)[\s,]+(-?[\d.]+)\s*\)")


def vector_drawable(svg_text):
    """
    SVG estático de Meteocons → VectorDrawable. Admite lo que usan los iconos de la cabecera: <path> con trazo o
    relleno (también evenodd), grupos, translate() y un recorte del lienzo entero (que no recorta nada).
    Cualquier otra cosa para el script, para no generar un icono distinto del original sin darse cuenta.
    """
    svg = ET.fromstring(svg_text)
    if svg.get("viewBox") != "0 0 128 128":
        raise ValueError("viewBox inesperado")
    for rect in svg.iter(f"{SVG}rect"):
        if (rect.get("width"), rect.get("height"), rect.get("x", "0"), rect.get("y", "0")) != ("128", "128", "0", "0"):
            raise ValueError("recorte que no es el lienzo entero")
    unsupported = {"circle", "line", "polyline", "polygon", "ellipse", "use", "mask", "linearGradient", "radialGradient", "text"}
    if any(el.tag.removeprefix(SVG) in unsupported for el in svg.iter()):
        raise ValueError("SVG con elementos no soportados")

    def color(value):
        return RECOLOR.get(value.upper(), value.upper())

    def path_xml(path, indent):
        attrs = [f'android:pathData="{path.get("d")}"']
        if path.get("fill") and path.get("fill") != "none":
            attrs.append(f'android:fillColor="{color(path.get("fill"))}"')
            if path.get("fill-rule") == "evenodd":
                attrs.append('android:fillType="evenOdd"')
        if path.get("stroke"):
            attrs.append(f'android:strokeColor="{color(path.get("stroke"))}"')
            attrs.append(f'android:strokeWidth="{path.get("stroke-width", "1")}"')
            if path.get("stroke-linecap"):
                attrs.append(f'android:strokeLineCap="{path.get("stroke-linecap")}"')
            if path.get("stroke-miterlimit"):
                attrs.append(f'android:strokeMiterLimit="{path.get("stroke-miterlimit")}"')
        pad = " " * indent
        xml = f"{pad}<path\n{pad}    " + f"\n{pad}    ".join(attrs) + " />"
        transform = path.get("transform")
        if transform:
            match = TRANSLATE.fullmatch(transform.strip())
            if not match:
                raise ValueError(f"transform no soportado: {transform}")
            xml = (f'{pad}<group android:translateX="{match.group(1)}" android:translateY="{match.group(2)}">\n'
                   + "\n".join("    " + line for line in xml.split("\n")) + f"\n{pad}</group>")
        return xml

    def walk(element):
        for child in element:
            tag = child.tag.removeprefix(SVG)
            if tag == "defs":
                continue
            if tag == "path":
                yield path_xml(child, 4)
            elif tag == "g":
                if child.get("transform"):
                    raise ValueError("grupo con transform")
                yield from walk(child)

    paths = list(walk(svg))
    if not paths:
        raise ValueError("SVG sin trazados")
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- Meteocons {VERSION} ({STYLE}), MIT: generado por scripts/meteocons.py -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="128dp"\n    android:height="128dp"\n'
            '    android:viewportWidth="128"\n    android:viewportHeight="128">\n'
            + "\n".join(paths) + "\n</vector>\n")


def download(package, tmp):
    folder = pathlib.Path(tmp) / package
    folder.mkdir()
    subprocess.run(["npm", "pack", f"@meteocons/{package}@{VERSION}", "--silent"], cwd=folder, check=True, stdout=subprocess.DEVNULL)
    with tarfile.open(next(folder.glob("*.tgz"))) as tar:
        tar.extractall(folder, filter="data")
    return folder / "package"


def main():
    with tempfile.TemporaryDirectory() as tmp:
        package = download("lottie", tmp)
        static = download("svg-static", tmp)
        for old in DRAWABLES.glob("meteocons_*.xml"):
            old.unlink()
        for name in STATIC_ICONS:
            xml = vector_drawable((static / STYLE / f"{name}.svg").read_text())
            (DRAWABLES / f"meteocons_{name.replace('-', '_')}.xml").write_text(xml)
        OUT.mkdir(parents=True, exist_ok=True)
        for old in OUT.glob("meteocons_*.json"):
            old.unlink()
        for name in ICONS:
            data = json.loads((package / STYLE / f"{name}.json").read_text())
            recolor(data)
            target = OUT / f"meteocons_{name.replace('-', '_')}.json"
            target.write_text(json.dumps(data, separators=(",", ":")))
        LICENSE.parent.mkdir(parents=True, exist_ok=True)
        LICENSE.write_text((package / "LICENSE").read_text())
    print(f"Meteocons {VERSION} ({STYLE}): {len(ICONS)} animados en {OUT.relative_to(ROOT)}, "
          f"{len(STATIC_ICONS)} estáticos en {DRAWABLES.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
