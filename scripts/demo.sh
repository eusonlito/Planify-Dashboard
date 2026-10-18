#!/bin/bash
# Entorno de demo para probar el dashboard en el emulador y hacer las capturas del README sin datos reales.
#
# Uso: scripts/demo.sh <orden>
#   up       backend de demo en el puerto 3001: copia de Planify-Backend en .demo/ (git lo ignora) con datos
#            ficticios (scripts/demo/seed.py). Las fechas son relativas a hoy: si los datos son de otro día, los
#            vuelve a crear. Si ya está arrancado y al día, no hace nada.
#   app      además de up: arranca el emulador (si no lo está), le pone la hora del PC, compila e instala la
#            debug y entra con la cuenta de demo (sin teclear: escribe la sesión en las preferencias de la app).
#            Con --no-build, instala la última debug compilada.
#   down     para el backend.
#   reset    borra .demo/ y repite up.
#
# Variables: PLANIFY_BACKEND (por defecto ../backend), DEMO_AVD (Frame_API_29), DEMO_EMULATOR_PORT (5556),
# ANDROID_SDK (~/Android/Sdk).
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="$HERE/.demo"
BACKEND="${PLANIFY_BACKEND:-$HERE/../backend}"
AVD="${DEMO_AVD:-Frame_API_29}"
EMULATOR_PORT="${DEMO_EMULATOR_PORT:-5556}"
SDK="${ANDROID_SDK:-$HOME/Android/Sdk}"
PORT=3001
URL="http://127.0.0.1:$PORT"
EMAIL=demo@planify.example
PASSWORD=demo1234
PKG=com.lito.planify.dashboard
export ANDROID_SERIAL="emulator-$EMULATOR_PORT"

step() { echo "==> $*"; }
running() { curl -s -o /dev/null "$URL/" 2>/dev/null; }

stop() {
    pkill -f "[p]hp -S 0.0.0.0:$PORT" || true
}

up() {
    if running && [ "$(cat "$DEMO/seeded" 2>/dev/null)" = "$(date +%F)" ]; then
        step "Backend de demo en $URL (datos de hoy)"
        return
    fi
    stop
    if [ ! -f "$DEMO/backend/public/index.php" ]; then
        step "Copiando el backend de $BACKEND"
        [ -d "$BACKEND" ] || { echo "No está el backend en $BACKEND (PLANIFY_BACKEND)"; exit 1; }
        mkdir -p "$DEMO/backend"
        git -C "$BACKEND" archive HEAD | tar -x -C "$DEMO/backend"
        printf '{\n  "gemini_api_key": "",\n  "token_secret": "%s",\n  "db_log_enabled": false\n}\n' \
            "$(head -c 24 /dev/urandom | base64)" > "$DEMO/backend/config/private.local.json"
    fi
    step "Base de datos nueva"
    rm -f "$DEMO/backend/db/database.sqlite"
    mkdir -p "$DEMO/backend/db" "$DEMO/backend/logs"
    (cd "$DEMO/backend" && php src/Commands/Migrate.php >/dev/null)
    # En su propia sesión y sin heredar la salida, para que no quede colgado de este script
    (cd "$DEMO/backend" && exec setsid php -S 0.0.0.0:$PORT -t public public/index.php) > "$DEMO/php.log" 2>&1 < /dev/null &
    for _ in $(seq 50); do running && break; sleep 0.1; done
    step "Datos de demo"
    python3 -I "$HERE/scripts/demo/seed.py" "$URL"
    date +%F > "$DEMO/seeded"
}

emulator() {
    if ! adb devices | grep -q "^$ANDROID_SERIAL\s*device"; then
        step "Arrancando el emulador $AVD"
        setsid "$SDK/emulator/emulator" -avd "$AVD" -port "$EMULATOR_PORT" -no-snapshot-save > "$DEMO/emulator.log" 2>&1 < /dev/null &
        adb wait-for-device
    fi
    until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 1; done
    adb root >/dev/null
    adb wait-for-device
    # Al arrancar de un snapshot, el emulador conserva la hora en la que se guardó
    adb shell date "$(date +%m%d%H%M%Y.%S)" >/dev/null
}

app() {
    local build=1
    for arg in "$@"; do
        case "$arg" in
            --no-build) build=0 ;;
            *) echo "Opción desconocida: $arg"; exit 1 ;;
        esac
    done
    up
    emulator
    local apk="$HERE/android/app/build/outputs/apk/debug/app-debug.apk"
    if [ $build = 1 ]; then
        step "Compilando la debug"
        (cd "$HERE/android" && ./gradlew -q assembleDebug -Pplanify.debugBaseUrl="http://10.0.2.2:$PORT")
    fi
    step "Instalando"
    adb install -r "$apk" >/dev/null

    step "Sesión de demo"
    local token prefs dir
    token="$(curl -sf "$URL/user/auth" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"
    dir="/data/data/$PKG"
    prefs="$(adb shell cat "$dir/shared_prefs/dashboard.xml" 2>/dev/null | tr -d '\r' || true)"
    # Sesión y ubicación de demo; el resto de ajustes (horario, brillo…) se conserva. Sin la caché de otra cuenta.
    python3 -I - "$token" "$EMAIL" "$prefs" > "$DEMO/dashboard.xml" <<'EOF'
import json, sys, xml.etree.ElementTree as ET
text = sys.argv[3].strip()
root = ET.fromstring(text.encode()) if "<map" in text else ET.Element("map")
values = {
    "token": sys.argv[1],
    "email": sys.argv[2],
    "location": json.dumps({"detail": "Comunidad Autónoma de Madrid, España", "latitude": 40.4165,
                            "longitude": -3.70256, "name": "Madrid"}, ensure_ascii=False),
}
for name, value in values.items():
    node = root.find(f"*[@name='{name}']")
    if node is None:
        node = ET.SubElement(root, "string", name=name)
    node.text = value
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
print(ET.tostring(root, encoding="unicode"))
EOF
    local owner
    owner="$(adb shell stat -c %u:%g "$dir" | tr -d '\r')"
    adb shell mkdir -p "$dir/shared_prefs"
    adb push "$DEMO/dashboard.xml" "$dir/shared_prefs/dashboard.xml" >/dev/null 2>&1
    adb shell chown -R "$owner" "$dir/shared_prefs"
    adb shell chmod 660 "$dir/shared_prefs/dashboard.xml"
    adb shell rm -rf "$dir/files/cache"
    # Es el launcher: al pararla se vuelve a abrir sola, ya con la sesión nueva
    adb shell am force-stop $PKG
    adb shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
    step "Listo: $EMAIL en $ANDROID_SERIAL"
}

mkdir -p "$DEMO"
case "${1:-}" in
    up) up ;;
    app) shift; app "$@" ;;
    down) stop; step "Backend de demo parado" ;;
    reset) stop; rm -rf "$DEMO"; mkdir -p "$DEMO"; up ;;
    *) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
