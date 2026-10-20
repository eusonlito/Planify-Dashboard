# Preparar el XORO CPF 10PF como dashboard de Planify

**Android 10 de fábrica, aislado y en modo quiosco, con el dashboard nativo como launcher y Device Owner.** En [ANDROID.md](ANDROID.md) se explica por qué no se actualiza Android.

Principios:
- **Reversible:** se desactivan apps en vez de borrarlas, y la imagen de fábrica se guarda entera antes de tocar nada.
- **Aislado:** el marco solo habla con el backend de Planify y con Open-Meteo (más DNS, NTP y la red local). Nada más sale a Internet.
- **Mínimo:** una sola app (el dashboard), sin Uhale ni apps de fábrica, y `su` fuera del alcance de las apps.

## Instalación

Antes de nada, la configuración de tu instalación (los dos ficheros están en `.gitignore`):

```sh
cp android/planify.properties.example android/planify.properties   # planify.baseUrl: URL de tu Planify-Backend
cp scripts/config.example scripts/config.local                      # ADB_FROM (IP de tu PC), zona horaria y NTP
```

```sh
# La primera vez, con el marco conectado por USB-C (ADB root viene activado de fábrica)
scripts/install.sh --build

# Después, por Wi-Fi (ver «Actualizar por Wi-Fi»)
adb connect <ip-del-marco>:5555
ANDROID_SERIAL=<ip-del-marco>:5555 scripts/install.sh --build --no-backup
```

`scripts/install.sh` es idempotente: para actualizar la app basta con repetirlo (`--no-backup` si ya hay copia). Hace lo siguiente:

1. **Comprueba el dispositivo:** `ro.hardware` tiene que ser `sun50iw10p1`. Con otro dispositivo se para, salvo `FORCE=1`.
2. **Copia de la eMMC** (`scripts/frame/backup.sh`):
   - copia todo lo anterior a `/data`: GPT, boot0/U-Boot y todas las particiones, unos 3 GiB;
   - copia también la GPT de respaldo y las particiones de hardware `boot0`/`boot1`;
   - compara el SHA-256 de cada pieza con el dispositivo y guarda todo en `backups/<serie>/`, que está en `.gitignore`.
   - Guarda además una copia fuera del repositorio.
3. **Compila e instala** la release (`com.lito.planify.dashboard`), que apunta a la `planify.baseUrl` de `android/planify.properties`. Sin ella, la release no compila.
4. **Device Owner:** `dpm set-device-owner com.lito.planify.dashboard/.admin.AdminReceiver`. Android solo lo permite si no hay cuentas en el dispositivo, y el script lo comprueba. A partir de ahí la propia app, en cada arranque:
   - activa el **lock task** sin botones de inicio ni recientes (solo se permite Ajustes de Android, para el Wi-Fi), pero con el **menú de apagado**: una pulsación larga del botón de encendido muestra Apagar / Reiniciar. Es la forma limpia de apagar; mantenerlo pulsado ~10 s corta la corriente de golpe. (`input keyevent --longpress KEYCODE_POWER` por ADB no sirve para probarlo: en este Android lanza una excepción en `GlobalActions`.)
   - desactiva el keyguard y la barra de estado;
   - mantiene la pantalla encendida con alimentación;
   - se registra como HOME persistente.
5. **Desactiva las apps de fábrica** con `pm disable-user`:
   - Uhale (`com.zeasn.frame`);
   - las herramientas de Allwinner (`dragonaging`, `dragonfire`, `fireplayer`, `batterytest`);
   - Música, Traceur, el portal cautivo, MIDI Bluetooth y MMS.
   - La app de pruebas de fábrica `com.example` (`TS8ProdutionTest`) se **desinstala para el usuario 0** (`pm uninstall -k --user 0`): está marcada como *persistent* y corre con UID `system`, así que Android la arranca aunque esté desactivada. Se recupera con `cmd package install-existing com.example`.
   - Apaga también los logs persistentes de Allwinner (`persist.debug.*`).
6. **Ajustes del sistema:**
   - hora automática por NTP (`FRAME_NTP`, por defecto `pool.ntp.org`) y la zona horaria `FRAME_TIMEZONE` (por defecto la de tu PC; venía con Amsterdam);
   - sin sonidos, sin animaciones y sin escaneos Wi-Fi/BLE en segundo plano;
   - localización y Bluetooth apagados;
   - sin pantalla de bloqueo, rotación fija y sin el aviso de "pantalla completa".
7. **Script de arranque**, si `adb remount` funciona: copia `scripts/frame/planify-frame.sh` a `/system/etc` y `planify-frame.rc` a `/system/etc/init`. En cada arranque aplica:
   - **cortafuegos por UID:** solo el dashboard sale a Internet; el resto de apps solo pueden usar la red local, DNS y NTP; la única conexión entrante admitida es ADB (5555) desde el PC de desarrollo (`ADB_FROM` de `scripts/config.local`, que `install.sh` deja en `/system/etc/planify-frame.conf`);
   - **sin suspensión del kernel** (wakelock `planify-frame` en `/sys/power/wake_lock`): ver [Horario de pantalla](#horario-de-pantalla);
   - vuelve a desactivar Uhale si alguien lo reactiva.
   - Además deja `su` como `root:shell 4750`: ADB sigue siendo root, pero las apps ya no pueden serlo.
8. **ADB por Wi-Fi**, solo si hay `ADB_FROM`: `persist.adb.tcp.port=5555` y reinicio de `adbd` (solo la primera vez). Ver [Actualizar por Wi-Fi](#actualizar-por-wi-fi).
9. **Arranque personalizado** (`scripts/boot/install.sh`): sustituye el logo de XORO y la animación de Uhale por los de Planify. Ver [Arranque personalizado](#arranque-personalizado).
10. **Arranca el dashboard.** Falta conectar el Wi-Fi con "Ir a Ajustes" en la pantalla de espera del login (**solo 2,4 GHz**; mejor una red de invitados).

Lo que no se toca: la densidad del sistema (la app se diseña en píxeles nativos), Ajustes de Android, el teclado, WebView y los proveedores del sistema.

## Primer uso

1. **Wi-Fi:** el chip (Realtek RTL8189FS, driver `8189fs`) **no soporta WPA3**. Con un router en modo mixto WPA2/WPA3, Android 10 guarda la red como WPA3 (SAE) y el driver rechaza la asociación al instante ("Association request to the driver failed" en `logcat`). Para evitarlo, añade la red a mano (Añadir red → Seguridad **WPA/WPA2-Personal**) o pon el router en WPA2. Si ya está guardada como SAE, se corrige en `/data/misc/wifi/WifiConfigStore.xml` con el framework parado (`stop`): `ConfigKey` `"red"SAE` → `"red"WPA_PSK`, `AllowedKeyMgmt` `num="2">0001` → `num="1">02` y `RequirePMF` → `false`; después, `start`. La contraseña ya está en `PreSharedKey`.

   Hasta que haya red y NTP, el reloj del marco marca la fecha de fábrica y el dashboard muestra "Sincronizando hora…" (no se fía de ninguna hora anterior a su compilación).
2. **Inicia sesión** con la cuenta de Planify. El dashboard solo lee: eventos, rutinas y tareas de los calendarios y listas marcados en la app (interruptor «Mostrar en el dashboard» de cada calendario y lista).
3. **Ajustes del dashboard:**
   - ubicación del tiempo (búsqueda en Open-Meteo);
   - horario de pantalla para lunes–viernes y sábado–domingo;
   - brillo: un deslizador y, a su derecha, «A» para el brillo automático del sistema (con él, el deslizador queda desactivado);
   - Conexión: la red Wi-Fi conectada, con su señal en dBm, una barra de calidad y la IP (se actualiza cada 5 s). El driver del marco no le pasa la señal a Android (RSSI -127) y, con la localización apagada, Android oculta el nombre de la red: los dos se leen de `/proc/net/rtl8189fs/wlan0/` (`rx_signal` y `ap_info`). Debajo, en otra caja, los botones (además de Cerrar sesión):
     - **Wi-Fi** y **Ajustes de Android** (Ajustes es la única app permitida dentro del modo quiosco). Como el marco no tiene barra de navegación, mientras Ajustes está abierto hay un botón flotante redondo con el icono de inicio, abajo a la derecha, que vuelve al dashboard. También vuelve solo tras 1 minuto sin tocar la pantalla (la superposición se entera de cada toque con `FLAG_WATCH_OUTSIDE_TOUCH`). `install.sh` le da el permiso con `appops set … SYSTEM_ALERT_WINDOW allow`.
     - **Apagar o reiniciar:** abre el menú de apagado nativo de Android. Ninguna app, ni siendo Device Owner, puede apagar el dispositivo, pero un servicio de accesibilidad (`PowerMenuService`, que no escucha eventos ni lee ventanas) sí puede abrir ese menú con `GLOBAL_ACTION_POWER_DIALOG`. `install.sh` lo activa con `settings put secure enabled_accessibility_services`. Tras reiniciar, Android no lo vuelve a enlazar aunque siga en la lista, así que `planify-frame.sh` la vacía y la vuelve a escribir en cada arranque.

## Actualizar por Wi-Fi

Tras la primera instalación por USB, `adbd` escucha también en TCP 5555. Todos los scripts (`install.sh`, `boot/install.sh`, `frame/backup.sh`) funcionan igual por Wi-Fi, y las copias siguen guardándose por número de serie (`ro.serialno`), no por IP.

- **Seguridad:** el cortafuegos solo deja entrar al 5555 desde **una IP**, la del PC de desarrollo (`ADB_FROM` en `scripts/config.local`; conviene reservarle la IP en el router); el resto de la red ni siquiera ve el puerto. Además, `adbd` sigue exigiendo la clave autorizada (`ro.adb.secure=1`). Si cambia la IP del PC, se edita `ADB_FROM` y se repite `install.sh` por USB (por USB, ADB funciona siempre). Con `ADB_FROM` vacío no se activa ADB por Wi-Fi.
- **IP fija para el marco:** reserva DHCP en el router para la **MAC real** del chip (la da el driver: `adb shell dmesg | grep mac_addr`). Android 10 usa por defecto una MAC aleatoria por red, que cambia si se olvida la red y se vuelve a añadir, así que configura tu red con la MAC del dispositivo: Ajustes → Wi-Fi → red → Avanzado → Privacidad → **Usar MAC del dispositivo** (`MacRandomizationSetting` = 0 en `WifiConfigStore.xml`).
- **Uso:**
  ```sh
  adb connect <ip-del-marco>:5555
  export ANDROID_SERIAL=<ip-del-marco>:5555
  scripts/install.sh --build --no-backup   # o cualquier adb shell / logcat
  ```
- **Desactivarlo:** `adb shell setprop persist.adb.tcp.port ""` y `adb shell setprop ctl.restart adbd` (por USB).
- Cuidado con lo que corta el Wi-Fi (`stop`/`start` del framework, cambiar la red, reiniciar): por Wi-Fi se pierde la conexión hasta que vuelva y hay que repetir `adb connect`.

## Horario de pantalla

- **Reloj nocturno (por defecto):** fuera de horario la pantalla no se apaga. Muestra en el centro la hora, la fecha y el tiempo de ahora sobre negro, con la retroiluminación al mínimo (1 de 255, `screenBrightness` de la ventana). Tocarlo enseña el dashboard durante 5 minutos y vuelve solo al reloj. A la hora de encendido vuelve el dashboard con el brillo de siempre.
- **Apagado de verdad** (Ajustes → desactivar «Reloj nocturno»): fuera de horario la pantalla se apaga con `lockNow()` (Device Owner) y se enciende a su hora con una alarma exacta. En esta placa **el táctil no despierta** la pantalla apagada; el botón de encendido sí. Si se enciende así fuera de horario, se vuelve a apagar sola a los 5 minutos.
- **La luna de la barra lateral** sigue el mismo ajuste: con el reloj nocturno activo pasa a él (hasta que se toque o hasta la siguiente hora de encendido); si no, apaga la pantalla en el momento.
- **El sistema no suspende nunca**, solo se apaga la pantalla. Lo impide un wakelock del kernel que pone el script de arranque. Por USB nunca se veía, porque el USB mantiene el sistema despierto; con el marco solo en Wi-Fi, la primera noche (14 de octubre, 23:30) fue así:
  - la suspensión falló por el acelerómetro (`mir3da`, `3-0027`, errno 43);
  - el táctil (`gslX680`) se quedó sin responder (`gsl_ts_read set data address fail!`);
  - `android.system.suspend@1.0-service` abortó (`ubsan: sub-overflow`, build eng);
  - al despertar, system_server se reinició.
  Si vuelve a pasar, el táctil se recupera sin reiniciar con `adb shell su 0 sh -c "rmmod gslX680new; insmod /vendor/modules/gslX680new.ko"`. Para comprobar que no hay suspensiones, mira `success`/`fail` en `/sys/kernel/debug/suspend_stats`.
- **Mientras la hora no sea válida no se apaga nunca.** El RTC del marco no guarda la hora: al arrancar marca la fecha de fábrica (30 de enero, 04:00) hasta que llega la hora por NTP, y el horario la tomaba por "fuera de horario" y apagaba la pantalla a los 5 minutos. Ahora no se programa nada hasta que la hora es posterior a la compilación de la app, y al llegar la hora (`TIME_SET`) se vuelve a programar.
- Probado en el emulador (API 29, 1920×1200) adelantando el reloj: se apaga a la hora de apagado, se enciende a la de encendido, y el apagado de cortesía funciona.

## Arranque personalizado

El arranque tiene dos fases, y las dos llevan ahora la misma composición (icono y nombre de Planify sobre el crema del dashboard), así que el paso de una a otra no se nota:

1. **Logo del bootloader:** `bootlogo.bmp` en la partición FAT `bootloader` (`mmcblk0p1`). Es un BMP de 24 bits de **1200×1920 en vertical**, porque así escanea el panel (`fb0` es 1200×1920 y `disp_reserve` mide justo 1200×1920×4). La imagen se ve en horizontal girando el BMP 90° a la izquierda.
2. **Animación de Android:** `/system/media/bootanimation.zip`. Es un recuadro de 640×520 centrado sobre el fondo `#F7EEE2` (`desc.txt`: `p 0 0 part0 #F7EEE2`) con tres puntos que se encienden en bucle hasta que arranca el dashboard.

```sh
scripts/boot/make.sh       # regenera out/ desde boot.html con Chrome headless (solo si cambia el diseño)
scripts/boot/install.sh    # instala logo y animación (lo llama install.sh)
scripts/boot/install.sh --restore   # vuelve a los de fábrica (guardados en backups/<serie>/)
```

**Cuidado con la partición `bootloader`:** su FAT16 declara 262144 sectores (128 MB), pero la partición solo mide 65536 (32 MB) y justo detrás va `env`. Si se monta en el marco y se escribe, un fichero puede acabar fuera de la partición y pisar `env` o `boot`. Por eso `install.sh` nunca la monta:
- lee la partición entera y la edita con mtools en el PC;
- comprueba que el último clúster de `bootlogo.bmp` termina antes del sector 65536 y que `fsck.vfat` no encuentra nada, salvo el aviso de la etiqueta de volumen, que ya viene así de fábrica;
- escribe la imagen con `dd` y la vuelve a leer para compararla.

## Mantenimiento y vuelta atrás

El dashboard no tiene opciones de mantenimiento: todo se hace por ADB, que en este marco es root.

- **Salir del modo quiosco o desinstalar:** hay que quitar el Device Owner; mientras lo sea, la app vuelve a fijarse sola (`am task lock stop` no suelta un lock task de Device Owner). Android solo deja quitarlo desde la propia app o si es *test-only*, así que se borra su registro y se reinicia (probado en el emulador):
  ```sh
  adb shell rm /data/system/device_owner_2.xml /data/system/device_policies.xml
  adb reboot
  adb uninstall com.lito.planify.dashboard
  ```
- **Volver a Uhale:**
  ```sh
  adb shell pm enable com.zeasn.frame
  adb shell cmd package set-home-activity com.zeasn.frame/.base.ui.boot.BootTipsActivity
  ```
- **Quitar el script de arranque:**
  ```sh
  adb remount
  adb shell rm /system/etc/planify-frame.sh /system/etc/init/planify-frame.rc
  adb shell chmod 6755 /system/xbin/su
  ```
- **Restaurar la eMMC entera** (último recurso): `mmcblk0-head.img` de la copia contiene todas las particiones salvo `/data`; los desplazamientos están en `partitions.txt`. Se escribe desde ADB root (`dd`), o desde fastboot/FEL si Android no arranca. Ver la escalera de recuperación en [FRAME.md](FRAME.md#escalera-de-recuperación).

## Qué se tomó del proyecto de wesbos ([`photo-frame-dashboard`](https://github.com/wesbos/photo-frame-dashboard))

| De `photo-frame-dashboard` | Uso aquí |
|---|---|
| [`docs/allwinner-a133-loofii-c3.md`](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/allwinner-a133-loofii-c3.md) | Mismo SoC, Android y distribución de la eMMC: copia de seguridad, recuperación y la posible actualización a 12.1 |
| [`scripts/a133/templates/firewall.sh`](https://github.com/wesbos/photo-frame-dashboard/blob/main/scripts/a133/templates/firewall.sh) | Base del cortafuegos. Aquí la salida a Internet se permite por UID (solo el dashboard) y no por red |
| Notas de calor y "pantalla apagada = táctil apagado" | El diseño no lleva animaciones continuas, y el horario usa apagado real con el botón como forma de despertar |
| Su web, la app WebView `kiosk/` y la WebView 155 re-firmada | No se usan: nuestro dashboard es nativo |
