"""Prepara el APK y su suma de comprobación en local para una GitHub Release."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parent
APK = "Planify-Dashboard.apk"
PACKAGE = "com.lito.planify.dashboard"
# Certificado público de las instalaciones existentes.
CERTIFICATE = "356642b6d5e12cef2db3679f9cf41a9383fc9ae320fcd5f3ec770d11a68e3bd1"


def prepare():
    subprocess.run(["./gradlew", ":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleRelease"], cwd=ROOT, check=True)
    output = ROOT / "app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text())
    entry, = metadata["elements"]
    version = entry["versionName"]
    if not re.fullmatch(r"(0|[1-9][0-9]{0,8})(\.(0|[1-9][0-9]{0,8})){2}", version):
        raise SystemExit("versionName debe tener el formato mayor.menor.parche")
    apk = output / entry["outputFile"]
    sdk = os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
    if not sdk:
        sdk = next(line[8:] for line in (ROOT / "local.properties").read_text().splitlines() if line.startswith("sdk.dir="))
    tools = max((d for d in (Path(sdk) / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", d.name)),
                key=lambda d: tuple(map(int, d.name.split("."))))
    signers = subprocess.check_output([tools / "apksigner", "verify", "--print-certs", apk], text=True)
    badging = subprocess.check_output([tools / "aapt", "dump", "badging", apk], text=True)
    if re.findall(r"Signer #\d+ certificate SHA-256 digest: (\w+)", signers) != [CERTIFICATE]:
        raise SystemExit("La firma es distinta de la versión instalada")
    if (f"package: name='{PACKAGE}' versionCode='{entry['versionCode']}' versionName='{version}'" not in badging
            or "application-debuggable" in badging):
        raise SystemExit("El paquete, la versión o el tipo de compilación son incorrectos")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    # Cada preparación deja juntos los dos archivos exactos que se han verificado.
    dest = ROOT / "build/updates" / f"v{version}" / digest
    dest.parent.mkdir(parents=True, exist_ok=True)
    checksum = f"{digest}  {APK}\n"
    if not dest.exists():
        with TemporaryDirectory(dir=dest.parent) as tmp:
            staged = Path(tmp) / "assets"
            staged.mkdir()
            shutil.copyfile(apk, staged / APK)
            (staged / f"{APK}.sha256").write_text(checksum)
            staged.rename(dest)
    if hashlib.sha256((dest / APK).read_bytes()).hexdigest() != digest or (dest / f"{APK}.sha256").read_text() != checksum:
        raise SystemExit("Los archivos preparados no coinciden")
    print(f"Preparado en local para v{version}: {dest}")


if __name__ == "__main__":
    prepare()
