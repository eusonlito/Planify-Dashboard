#!/bin/bash
# Instala el dashboard en el XORO CPF 10PF y lo deja en modo quiosco. La primera vez, por USB; después
# también por Wi-Fi: adb connect <ip-del-marco>:5555 y ANDROID_SERIAL=<ip-del-marco>:5555 scripts/install.sh
# Idempotente: se puede repetir para actualizar la app. Ver docs/SETUP.md.
#
# Configuración de cada instalación: scripts/config.local (copia de scripts/config.example; git lo ignora).
# Las variables de entorno con el mismo nombre tienen prioridad.
#
# Uso: scripts/install.sh [--build] [--no-backup]
#   --build      compila la release antes de instalar (si no, usa la última compilada)
#   --no-backup  no hace la copia de la eMMC (solo si ya existe una verificada)
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_ADB_FROM="${ADB_FROM-}" ENV_FRAME_TIMEZONE="${FRAME_TIMEZONE-}" ENV_FRAME_NTP="${FRAME_NTP-}"
# shellcheck source=/dev/null
[ -f "$HERE/scripts/config.local" ] && . "$HERE/scripts/config.local"
ADB_FROM="${ENV_ADB_FROM:-${ADB_FROM:-}}"
FRAME_TIMEZONE="${ENV_FRAME_TIMEZONE:-${FRAME_TIMEZONE:-$(timedatectl show -p Timezone --value 2>/dev/null || echo UTC)}}"
FRAME_NTP="${ENV_FRAME_NTP:-${FRAME_NTP:-pool.ntp.org}}"
PKG=com.lito.planify.dashboard
APK="$HERE/android/app/build/outputs/apk/release/app-release.apk"
BUILD=0
BACKUP=1
for arg in "$@"; do
    case "$arg" in
        --build) BUILD=1 ;;
        --no-backup) BACKUP=0 ;;
        *) echo "Opción desconocida: $arg"; exit 1 ;;
    esac
done

step() { echo; echo "==> $*"; }
sh() { adb shell "$@" | tr -d '\r'; }

step "Dispositivo"
adb get-state >/dev/null
HARDWARE="$(sh getprop ro.hardware)"
RELEASE="$(sh getprop ro.build.version.release)"
echo "$(sh getprop ro.serialno) · $HARDWARE · Android $RELEASE"
if [ "$HARDWARE" != "sun50iw10p1" ] && [ "${FORCE:-0}" != "1" ]; then
    echo "No es el XORO CPF 10PF (A133). Revisa docs/FRAME.md o usa FORCE=1."
    exit 1
fi

if [ "$BACKUP" = "1" ]; then
    step "Copia de seguridad de la eMMC"
    "$HERE/scripts/frame/backup.sh"
fi

if [ "$BUILD" = "1" ] || [ ! -f "$APK" ]; then
    step "Compilando la release"
    (cd "$HERE/android" && ./gradlew -q :app:assembleRelease)
fi

step "Instalando $PKG"
adb install -r "$APK"

step "Device Owner"
if sh dumpsys device_policy | grep -q "admin=ComponentInfo{$PKG/"; then
    echo "Ya es Device Owner"
else
    ACCOUNTS="$(sh dumpsys account | grep -c 'Account {' || true)"
    if [ "$ACCOUNTS" != "0" ]; then
        echo "Hay $ACCOUNTS cuenta(s) en el dispositivo: Android no permite Device Owner con cuentas."
        exit 1
    fi
    sh dpm set-device-owner "$PKG/.admin.AdminReceiver"
fi
sh cmd package set-home-activity "$PKG/.MainActivity" >/dev/null
# Botón flotante para volver de Ajustes de Android (el marco no tiene barra de navegación)
sh appops set "$PKG" SYSTEM_ALERT_WINDOW allow
# Botón "Apagar o reiniciar": un servicio de accesibilidad abre el menú de apagado de Android
sh settings put secure enabled_accessibility_services "$PKG/$PKG.admin.PowerMenuService"
sh settings put secure accessibility_enabled 1

step "Desactivando apps de fábrica (reversible con pm enable)"
for p in com.zeasn.frame com.softwinner.dragonaging com.softwinner.dragonaging.res \
         com.softwinner.dragonfire com.softwinner.fireplayer com.allwinner.batterytest \
         com.android.music com.android.traceur com.android.captiveportallogin \
         com.android.bluetoothmidiservice com.android.mms.service; do
    if sh pm list packages | grep -qx "package:$p"; then
        sh pm disable-user --user 0 "$p" | sed "s/^/  /"
    fi
done
# La app de pruebas de fábrica es "persistent" con UID system: Android la arranca aunque esté
# desactivada, así que se desinstala para el usuario 0 (reversible: cmd package install-existing com.example).
if sh pm list packages | grep -qx "package:com.example"; then
    sh pm uninstall -k --user 0 com.example | sed "s/^/  /"
fi
# Logs persistentes de Allwinner en /data/media/awlog
sh setprop persist.debug.logcat.enable false
sh setprop persist.debug.kernel_log.enable false
sh setprop persist.debug.logpersistd clear

step "Ajustes del sistema"
for kv in "global auto_time 1" "global auto_time_zone 0" "global ntp_server $FRAME_NTP" \
          "global captive_portal_mode 0" "global power_sounds_enabled 0" "global stay_on_while_plugged_in 7" \
          "global wifi_scan_always_enabled 0" "global ble_scan_always_enabled 0" "global package_verifier_enable 0" \
          "global window_animation_scale 0" "global transition_animation_scale 0" "global animator_duration_scale 0" \
          "secure charging_sounds_enabled 0" "secure location_mode 0" "secure immersive_mode_confirmations confirmed" \
          "system lockscreen_sounds_enabled 0" "system accelerometer_rotation 0" "system screen_off_timeout 2147483647"; do
    sh settings put $kv
done
sh setprop persist.sys.timezone "$FRAME_TIMEZONE"
echo "Zona horaria $FRAME_TIMEZONE · NTP $FRAME_NTP"
sh svc bluetooth disable >/dev/null 2>&1 || true
sh locksettings set-disabled true >/dev/null 2>&1 || true

step "Script de arranque: cortafuegos y su"
if adb remount 2>&1 | grep -qi "remount succeeded"; then
    adb push "$HERE/scripts/frame/planify-frame.sh" /system/etc/planify-frame.sh >/dev/null
    adb push "$HERE/scripts/frame/planify-frame.rc" /system/etc/init/planify-frame.rc >/dev/null
    # Lo que cambia en cada instalación lo lee el script de arranque de aquí
    printf 'ADB_FROM=%s\n' "$ADB_FROM" | adb shell 'cat > /system/etc/planify-frame.conf'
    sh chmod 644 /system/etc/planify-frame.sh /system/etc/init/planify-frame.rc /system/etc/planify-frame.conf
    # su sigue disponible para adb (root) pero no para las apps
    sh chown root:shell /system/xbin/su
    sh chmod 4750 /system/xbin/su
    sh sh /system/etc/planify-frame.sh
    echo "Instalado. Reglas activas: $(sh iptables -S FRAME_OUT | wc -l)"
else
    echo "AVISO: adb remount no funciona en este build; el cortafuegos no se ha instalado."
fi

step "ADB por Wi-Fi (puerto 5555, solo desde ADB_FROM)"
if [ -z "$ADB_FROM" ]; then
    echo "ADB_FROM vacío en scripts/config.local: no se activa (el cortafuegos no abre el 5555)."
elif [ "$(sh getprop persist.adb.tcp.port)" = "5555" ]; then
    echo "Ya activo"
else
    sh setprop persist.adb.tcp.port 5555
    # Reiniciar adbd corta la conexión USB un momento
    adb shell setprop ctl.restart adbd >/dev/null 2>&1 || true
    sleep 3
    adb wait-for-device
    echo "Activado"
fi

step "Arranque personalizado (logo y animación)"
"$HERE/scripts/boot/install.sh"

step "Arrancando el dashboard"
sh am start -n "$PKG/.MainActivity" >/dev/null
echo "Listo. Conecta el Wi-Fi con \"Ir a Ajustes\" en el login (solo 2,4 GHz y WPA2: el chip no soporta WPA3)."
