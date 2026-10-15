#!/bin/bash
# Genera el arranque personalizado a partir de boot.html:
#   out/bootlogo.bmp.gz    logo del bootloader (partición FAT "bootloader"): BMP de 24 bits,
#                          1200×1920 en vertical, porque así escanea el panel. Comprimido para git.
#   out/bootlogo.png       el mismo logo en horizontal, para verlo.
#   out/bootanimation.zip  animación de Android (/system/media): recuadro de 640×520 centrado
#                          sobre el color de fondo, en bucle hasta que arranca el dashboard.
# Requiere google-chrome (o CHROME=...), ImageMagick y zip.
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CHROME="${CHROME:-google-chrome}"
OUT="$HERE/out"
FRAMES=36
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

shot() { # shot <hash> <ancho> <alto> <png>
    "$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --allow-file-access-from-files --virtual-time-budget=2000 \
        --window-size="$2,$3" --screenshot="$4" "file://$HERE/boot.html#$1" >/dev/null 2>&1
}

rm -rf "$OUT" && mkdir -p "$OUT"

shot logo 1920 1200 "$OUT/bootlogo.png"
# La imagen se ve en horizontal tras girar el BMP 90° a la izquierda, así que se gira a la derecha.
convert "$OUT/bootlogo.png" -rotate 90 -alpha off -type TrueColor BMP3:"$OUT/bootlogo.bmp"
gzip -9n "$OUT/bootlogo.bmp"

mkdir -p "$TMP/part0"
for i in $(seq 0 $((FRAMES - 1))); do
    shot "frame=$i" 640 520 "$TMP/part0/$(printf %03d "$i").png"
done
printf '640 520 30\np 0 0 part0 #F7EEE2\n' > "$TMP/desc.txt"
# Android exige el zip sin comprimir.
(cd "$TMP" && zip -q -0 -r "$OUT/bootanimation.zip" desc.txt part0)

ls -l "$OUT"
