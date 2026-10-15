#!/bin/bash
# Copia de la eMMC del marco antes de tocar nada: todo lo anterior a /data (GPT, boot0/U-Boot y particiones),
# las particiones de hardware boot0/boot1 y la GPT de respaldo. Comprueba el SHA-256 contra el dispositivo.
# Uso: scripts/frame/backup.sh [carpeta]   (por defecto dashboard/backups/<serie>)
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
SERIAL="$(adb shell getprop ro.serialno | tr -d '\r')"
OUT="${1:-$HERE/backups/$SERIAL}"

# UDISK (/data) empieza en este sector en el XORO CPF 10PF: ver docs/FRAME.md
UDISK_START="$(adb shell cat /sys/block/mmcblk0/mmcblk0p17/start | tr -d '\r')"
UDISK_SIZE="$(adb shell cat /sys/block/mmcblk0/mmcblk0p17/size | tr -d '\r')"
HEAD_MB=$(( UDISK_START * 512 / 1048576 ))
TAIL_SKIP=$(( UDISK_START + UDISK_SIZE ))

mkdir -p "$OUT"
cd "$OUT"

if [ -f SHA256SUMS ] && sha256sum --quiet -c SHA256SUMS; then
    echo "Copia ya existente y válida en $OUT"
    exit 0
fi

echo "==> eMMC hasta /data (${HEAD_MB} MiB)"
adb exec-out "dd if=/dev/block/mmcblk0 bs=1048576 count=$HEAD_MB 2>/dev/null" > mmcblk0-head.img
echo "==> GPT de respaldo"
adb exec-out "dd if=/dev/block/mmcblk0 bs=512 skip=$TAIL_SKIP 2>/dev/null" > mmcblk0-tail.img
echo "==> boot0 / boot1"
adb exec-out "cat /dev/block/mmcblk0boot0" > mmcblk0boot0.img
adb exec-out "cat /dev/block/mmcblk0boot1" > mmcblk0boot1.img

echo "==> Verificando contra el dispositivo"
check() {
    local file="$1" remote="$2" local_hash device_hash
    local_hash="$(sha256sum "$file" | cut -d' ' -f1)"
    device_hash="$(adb shell "$remote | sha256sum" | cut -d' ' -f1)"
    [ "$local_hash" = "$device_hash" ] || { echo "ERROR: $file no coincide ($local_hash != $device_hash)"; exit 1; }
    echo "  $file OK"
}
check mmcblk0-head.img "dd if=/dev/block/mmcblk0 bs=1048576 count=$HEAD_MB 2>/dev/null"
check mmcblk0-tail.img "dd if=/dev/block/mmcblk0 bs=512 skip=$TAIL_SKIP 2>/dev/null"
check mmcblk0boot0.img "cat /dev/block/mmcblk0boot0"
check mmcblk0boot1.img "cat /dev/block/mmcblk0boot1"

adb shell 'for p in $(ls /dev/block/by-name/); do echo "$p $(readlink /dev/block/by-name/$p) $(cat /sys/class/block/$(basename $(readlink /dev/block/by-name/$p))/start 2>/dev/null)"; done' > partitions.txt
sha256sum mmcblk0-head.img mmcblk0-tail.img mmcblk0boot0.img mmcblk0boot1.img > SHA256SUMS
echo "Copia completa en $OUT"
