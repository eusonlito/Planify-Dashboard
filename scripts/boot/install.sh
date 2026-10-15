#!/bin/bash
# Instala el arranque personalizado (generado con make.sh) en el marco (USB o ADB por Wi-Fi).
#   - Logo del bootloader: sustituye bootlogo.bmp en la partición FAT "bootloader" (mmcblk0p1).
#   - Animación de Android: /system/media/bootanimation.zip (necesita adb remount).
# Guarda los originales en backups/<serie>/ la primera vez. Restaurar: install.sh --restore
#
# Ojo: la FAT de esa partición declara 128 MB, pero la partición solo mide 32 MB y detrás va "env".
# Por eso nunca se monta en el marco: se edita una copia en el PC, se comprueba que ningún clúster
# cae fuera de la partición y se escribe la imagen entera, verificándola por hash.
# Requiere mtools y dosfstools (fsck.vfat).
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$HERE/../.." && pwd)"
PART=/dev/block/by-name/bootloader
RESTORE=0
[ "${1:-}" = "--restore" ] && RESTORE=1

sh() { adb shell "$@" | tr -d '\r'; }
export MTOOLS_SKIP_CHECK=1

SERIAL="$(adb shell getprop ro.serialno | tr -d '\r')"
SAVE="$ROOT/backups/$SERIAL"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"; adb shell rm -f /data/local/tmp/bootloader.img >/dev/null 2>&1 || true' EXIT
mkdir -p "$SAVE"

# --- Logo del bootloader ---
SECTORS=$(( $(sh blockdev --getsize64 "$PART") / 512 ))
adb exec-out "dd if=$PART bs=1M 2>/dev/null" > "$TMP/current.img"
[ "$(stat -c %s "$TMP/current.img")" -eq $((SECTORS * 512)) ] || { echo "Lectura incompleta de $PART"; exit 1; }
[ -f "$SAVE/bootloader-partition.img" ] || cp "$TMP/current.img" "$SAVE/bootloader-partition.img"

cp "$TMP/current.img" "$TMP/new.img"
if [ $RESTORE = 1 ]; then
    mcopy -n -i "$SAVE/bootloader-partition.img" ::/bootlogo.bmp "$TMP/bootlogo.bmp"
else
    gunzip -c "$HERE/out/bootlogo.bmp.gz" > "$TMP/bootlogo.bmp"
fi
mcopy -n -i "$TMP/current.img" ::/bootlogo.bmp "$TMP/installed.bmp"
if cmp -s "$TMP/installed.bmp" "$TMP/bootlogo.bmp"; then
    echo "El logo ya está instalado."
    SKIP_LOGO=1
else
    SKIP_LOGO=0
    mdel -i "$TMP/new.img" ::/bootlogo.bmp
    mcopy -i "$TMP/new.img" "$TMP/bootlogo.bmp" ::/bootlogo.bmp
fi

# Geometría FAT16: datos tras el sector de arranque, las FAT y el directorio raíz.
INFO="$(minfo -i "$TMP/new.img" ::)"
field() { echo "$INFO" | sed -n "s/^$1: \([0-9]*\).*/\1/p"; }
SPC=$(field "cluster size"); FATS=$(field "fats"); FATLEN=$(field "sectors per fat")
RESERVED=$(field "reserved (boot) sectors"); ROOTDIR=$(field "max available root directory slots")
DATA=$((RESERVED + FATS * FATLEN + ROOTDIR * 32 / 512))
LAST=$(mshowfat -i "$TMP/new.img" ::/bootlogo.bmp | grep -oE '[0-9]+>' | tr -d '>' | sort -n | tail -1)
END=$((DATA + (LAST - 2) * SPC + SPC))
echo "bootlogo.bmp: último clúster $LAST, termina en el sector $END de $SECTORS"
[ "$END" -le "$SECTORS" ] || { echo "El fichero cae fuera de la partición: no se escribe."; exit 1; }
cp "$TMP/new.img" "$TMP/check.img"; truncate -s $(( $(field "big size") * 512 )) "$TMP/check.img"
# De fábrica ya trae la etiqueta de volumen solo en el directorio raíz: es el único aviso admitido.
PROBLEMS="$(fsck.vfat -n "$TMP/check.img" 2>&1 | grep -vE '^fsck\.fat|^Volume label|Auto-copying volume label|^Leaving filesystem unchanged|: [0-9]+ files, [0-9]+/[0-9]+ clusters$|^$' || true)"
[ -z "$PROBLEMS" ] || { echo "fsck.vfat encuentra errores: no se escribe."; echo "$PROBLEMS"; exit 1; }

if [ $SKIP_LOGO = 0 ]; then
    adb push "$TMP/new.img" /data/local/tmp/bootloader.img >/dev/null
    sh "dd if=/data/local/tmp/bootloader.img of=$PART bs=1M conv=fsync 2>/dev/null"
    adb exec-out "dd if=$PART bs=1M 2>/dev/null" > "$TMP/written.img"
    cmp -s "$TMP/new.img" "$TMP/written.img" && echo "Logo escrito y verificado." \
        || { echo "ERROR: la partición no coincide; restaura $SAVE/bootloader-partition.img"; exit 1; }
fi

# --- Animación de Android ---
[ -f "$SAVE/bootanimation.zip" ] || adb pull /system/media/bootanimation.zip "$SAVE/bootanimation.zip" >/dev/null
if adb remount 2>&1 | grep -qi "remount succeeded"; then
    ANIM="$HERE/out/bootanimation.zip"
    [ $RESTORE = 1 ] && ANIM="$SAVE/bootanimation.zip"
    adb push "$ANIM" /system/media/bootanimation.zip >/dev/null
    sh chmod 644 /system/media/bootanimation.zip
    echo "Animación instalada."
else
    echo "AVISO: adb remount no funciona; la animación no se ha cambiado."
fi
