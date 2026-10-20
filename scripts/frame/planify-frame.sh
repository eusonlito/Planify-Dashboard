#!/system/bin/sh
# Se ejecuta como root en cada arranque (planify-frame.rc). Instalado por scripts/install.sh.
# Cortafuegos de salida por UID: solo el dashboard sale a Internet; el resto del sistema solo a la red local,
# DNS y NTP. La única conexión entrante admitida es ADB (5555) desde el PC de desarrollo (ADB_FROM, mejor con IP
# reservada en el router); ADB pide igualmente la clave autorizada. Por USB, ADB funciona siempre.

# ADB_FROM viene de scripts/config.local: install.sh lo deja en planify-frame.conf. Vacío, no se abre el 5555.
ADB_FROM=
[ -f /system/etc/planify-frame.conf ] && . /system/etc/planify-frame.conf

# Sin suspensión del kernel: el marco siempre está enchufado y en esta placa suspender sale mal. El acelerómetro
# (mir3da) hace fallar la suspensión, el táctil (gslX680) se queda sin responder hasta recargar su módulo y el
# servicio de suspensión de Android (build eng con ubsan) aborta, con lo que system_server se reinicia al despertar.
# Con este wakelock del kernel la pantalla se sigue apagando, pero el sistema no suspende.
echo planify-frame > /sys/power/wake_lock

DASH_UID=$(stat -c %u /data/data/com.lito.planify.dashboard 2>/dev/null)

for T in iptables ip6tables; do
    for C in FRAME_OUT FRAME_IN; do $T -N $C 2>/dev/null; $T -F $C; done
    $T -A FRAME_OUT -o lo -j ACCEPT
    $T -A FRAME_OUT -m state --state ESTABLISHED,RELATED -j ACCEPT
    for P in udp tcp; do $T -A FRAME_OUT -p $P --dport 53 -j ACCEPT; done
    $T -A FRAME_OUT -p tcp --dport 853 -j ACCEPT
    $T -A FRAME_OUT -p udp --dport 123 -j ACCEPT
    [ -n "$DASH_UID" ] && $T -A FRAME_OUT -m owner --uid-owner "$DASH_UID" -j ACCEPT
    $T -A FRAME_IN -i lo -j ACCEPT
    $T -A FRAME_IN -m state --state ESTABLISHED,RELATED -j ACCEPT
done
for NET in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 224.0.0.0/4 255.255.255.255; do
    iptables -A FRAME_OUT -d $NET -j ACCEPT
done
iptables -A FRAME_IN -p udp --sport 67 --dport 68 -j ACCEPT
[ -n "$ADB_FROM" ] && iptables -A FRAME_IN -p tcp -s "$ADB_FROM" --dport 5555 -j ACCEPT
for NET in fe80::/10 ff00::/8; do ip6tables -A FRAME_OUT -d $NET -j ACCEPT; done
ip6tables -A FRAME_OUT -p ipv6-icmp -j ACCEPT
ip6tables -A FRAME_IN -p ipv6-icmp -j ACCEPT
for T in iptables ip6tables; do
    $T -A FRAME_OUT -j REJECT
    $T -A FRAME_IN -j DROP
    $T -C OUTPUT -j FRAME_OUT 2>/dev/null || $T -I OUTPUT -j FRAME_OUT
    $T -C INPUT -j FRAME_IN 2>/dev/null || $T -I INPUT -j FRAME_IN
done

# Servicio del botón "Apagar o reiniciar": tras reiniciar, Android no lo enlaza aunque siga en
# enabled_accessibility_services (install.sh solo lo dejaba activo hasta el siguiente reinicio). Volver a escribir el
# mismo valor no basta: hay que vaciar la lista y ponerla otra vez.
A11Y=$(settings get secure enabled_accessibility_services)
case "$A11Y" in
    *PowerMenuService*)
        settings put secure enabled_accessibility_services ""
        settings put secure enabled_accessibility_services "$A11Y"
        ;;
esac

# Por si una actualización o un reset vuelve a activar Uhale
pm disable-user --user 0 com.zeasn.frame >/dev/null 2>&1
