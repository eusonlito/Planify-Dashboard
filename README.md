# Planify - Dashboard 🖼️

Un marco de fotos digital de unos 80 € convertido en la pantalla de la familia: hora, tiempo, eventos, rutinas y tareas de un vistazo, colgado en la pared, sin la app del fabricante y sin suscripciones.

**Este repositorio es, sobre todo, una guía para agentes** (Claude Code, Codex…) que quieran crear su propia versión: qué hay que hacer, en qué orden, con qué requisitos y qué se aprendió por el camino. Nosotros partimos de [wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard) de la misma forma.

Este proyecto está conectado con la aplicación y el backend de Planify:
- **[Planify-APP](https://github.com/eusonlito/Planify-APP):** la app Android donde se crean y editan calendarios, eventos, rutinas, personas y tareas.
- **[Planify-Backend](https://github.com/eusonlito/Planify-Backend):** la API (PHP + SQLite) que guarda todo.

**Pero puedes crear tu propia versión de todo sin problema:** usar otro backend (Google Calendar, CalDAV, Home Assistant, un JSON propio…), otro diseño, otras pantallas u otro marco. La [fase 3](#fase-3-decide-qué-app-vas-a-instalar) explica qué hay que cambiar en cada caso.

<p align="center">
  <img src="docs/images/frame.jpg" width="80%" alt="El dashboard funcionando en el marco XORO CPF 10PF" />
</p>

---

## Índice

1. [Capturas](#capturas)
2. [Para agentes: empieza aquí](#para-agentes-empieza-aquí)
3. [El hardware](#el-hardware)
4. [Requisitos](#requisitos)
5. [Las fases, paso a paso](#las-fases-paso-a-paso)
6. [Qué hace la app](#qué-hace-la-app)
7. [Lo que aprendimos (léelo antes de improvisar)](#lo-que-aprendimos)
8. [Adaptarlo a otro marco](#adaptarlo-a-otro-marco)
9. [Estructura, licencias y créditos](#estructura)

---

## Capturas

Todas las secciones de la app, en el emulador con las medidas del marco (1920×1200) y datos de demostración.

### Hoy
La pantalla principal: reloj de paletas, el siguiente evento con cuenta atrás, el tiempo (ahora, el día de hoy de 00 a 23 h con la hora actual marcada y los próximos días), los próximos eventos, las rutinas de hoy y las tareas.

<img src="docs/images/today.png" alt="Hoy" />

### Calendario
La semana de lunes a domingo, deslizando entre semanas: el tiempo de cada día, los eventos, una banda aparte para las rutinas y la leyenda de personas. Los días pasados, atenuados.

<img src="docs/images/calendar.png" alt="Calendario" />

### Tiempo
Dos páginas que se deslizan. La primera: el día de hoy por horas, de 00 a 23 con la hora actual marcada, y 7 días. La segunda: la semana siguiente en gráfica (máximas, mínimas, lluvia y viento). Tocar cualquier día abre sus 24 horas con su propia cabecera. La lluvia se dibuja por cantidad: el alto de la barra son los mm de esa hora, y la opacidad del relleno, la probabilidad (maciza al 100 %, vacía al 0 %).

<p align="center">
  <img src="docs/images/weather.png" width="49%" alt="Tiempo: hoy por horas y 7 días" />
  <img src="docs/images/weather-week.png" width="49%" alt="Tiempo: la semana siguiente" />
</p>
<p align="center">
  <img src="docs/images/weather-day.png" width="49%" alt="Tiempo: un día de lluvia por horas" />
</p>

### Tareas
Las tareas pendientes de las listas elegidas en la APP, una columna por lista.

<img src="docs/images/tasks.png" alt="Tareas" />

### Ajustes
Ubicación del tiempo (búsqueda en Open-Meteo), horario de pantalla (entre semana y fin de semana, con el interruptor «Reloj nocturno»: si se desactiva, fuera de horario la pantalla se apaga del todo), brillo y Sistema: estado del Wi-Fi, Ajustes de Android, cerrar sesión y apagar o reiniciar.

<img src="docs/images/settings.png" alt="Ajustes" />

### Reloj nocturno
Fuera de horario, o al pulsar la luna de la barra lateral, la pantalla no se apaga: queda la hora en grande, la fecha y el tiempo de ahora, en negrita y en blanco sobre negro (también el icono, que en color apenas se veía), con la retroiluminación al mínimo (1 de 255). Tocarlo enseña el dashboard; fuera de horario, durante 5 minutos.

<img src="docs/images/night.png" alt="Reloj nocturno" />

### Inicio de sesión
Con la cuenta de Planify. Sin Internet, en su lugar aparece "Esperando conexión a Internet…" con un botón a los ajustes de Wi-Fi.

<img src="docs/images/login.png" alt="Inicio de sesión" />

---

## Para agentes: empieza aquí

Lo más probable es que estés ayudando a alguien con un marco conectado por USB a su ordenador. Lee esta sección entera, después el [hardware](#el-hardware) y, antes de cada fase, su apartado. No ejecutes nada de una fase si las comprobaciones de identidad no coinciden.

### Reglas

1. **Lee primero, escribe después.** Leer del marco está bien. Cualquier escritura (instalar, `settings put`, `/system`, particiones, flashear) necesita el permiso explícito del dueño **para esa acción concreta**; un permiso anterior no vale para la siguiente.
2. **Nada se escribe sin una copia verificada** de la eMMC ([fase 2](#fase-2-copia-de-seguridad-de-la-emmc)): tamaño exacto y SHA-256 de cada pieza comparado con el dispositivo.
3. **Para en la primera diferencia** de modelo, hash, tamaño o desplazamiento. Avisa al dueño en vez de buscar un rodeo.
4. **Un cambio cada vez**, con la forma de deshacerlo anotada antes de hacerlo.
5. **Los volcados son privados.** Contienen contraseñas Wi-Fi, tokens, números de serie y MACs. Van a `backups/` (en `.gitignore`) y nunca a un commit. Si publicas cambios, quita serie, MAC, IPs, SSID y dominios propios.
6. **No cierres la puerta por la que entraste.** Antes de quitar una vía de acceso (la app del fabricante, ADB por Wi-Fi, el cortafuegos), comprueba en el propio marco que tienes otra. Por USB, ADB funciona siempre en este modelo.
7. **Mide en el aparato.** El emulador no reproduce el calor, la memoria, el táctil ni la suspensión de esta placa (ver [lo que aprendimos](#lo-que-aprendimos)).
8. **Escribe notas mientras trabajas.** La ficha del marco ([docs/FRAME.md](docs/FRAME.md)) separa lo observado de lo supuesto; haz lo mismo con tu unidad.

### Documentación de este repositorio

| Documento | Para qué |
|---|---|
| [docs/FRAME.md](docs/FRAME.md) | Ficha completa del XORO CPF 10PF: identidad, particiones, software de fábrica, riesgos, TLS y escalera de recuperación |
| [docs/SETUP.md](docs/SETUP.md) | Qué hace `scripts/install.sh` paso a paso, primer uso, ADB por Wi-Fi, horario, arranque personalizado y cómo deshacerlo todo |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Cómo está hecha la app: pantallas, capa de datos, estructura, emulador y tests |
| [docs/ANDROID.md](docs/ANDROID.md) | Hasta qué Android se podría actualizar el marco y por qué no compensa |
| [docs/inventory/](docs/inventory/) | Volcados del marco de fábrica (`getprop`, paquetes, particiones, sistema) para comparar con tu unidad |

---

## El hardware

**[XORO CPF 10PF](https://www.amazon.es/dp/B0G2MJBR3M)**, marco digital de 10,1" (unos 80 € en Amazon España). Lo importa MAS Elektronik AG y trae la app Uhale.

| | |
|---|---|
| **SoC** | Allwinner **A133** (`ro.hardware` = `sun50iw10p1`) |
| **CPU** | 4× ARM Cortex-A53, hasta 1,5 GHz |
| **GPU** | PowerVR GE8300 |
| **RAM** | **1 GB** (unos 435 MB libres en reposo), con zram |
| **Almacenamiento** | eMMC de 32 GB (26 GB para `/data`) y ranura microSD |
| **Pantalla** | IPS de 10,1", **1920×1200** (16:10). El panel es vertical (1200×1920) y Android lo gira. El anuncio dice 1920×1080, pero no es así. Densidad 320 dpi, 60 Hz |
| **Táctil** | Capacitivo `gslX680` (I²C) |
| **Wi-Fi** | Realtek RTL8189FS: **solo 2,4 GHz y sin WPA3** |
| **Sensores** | Solo acelerómetro. Sin sensor de luz ni de presencia |
| **Puertos** | USB-C (datos: ADB/MTP), USB-A (host), microSD, entrada DC, botón de encendido, reset (agujero) y volumen |
| **Alimentación** | Adaptador DC, **sin batería** |
| **Montaje** | VESA 75×75, gancho de pared y marco de madera desmontable |
| **Android** | **10** (API 29), parche de seguridad de enero de 2021, kernel 4.9 arm64 con **espacio de usuario de 32 bits** |
| **Build** | **`eng`**: ADB por USB **como root** de fábrica, SELinux permisivo, dm-verity desactivado |
| **Reloj (RTC)** | No conserva la hora sin red: arranca en la fecha de fábrica hasta que llega NTP |

**Por qué este marco:** el build `eng` da root por ADB desde el primer minuto, así que no hace falta ningún exploit ni desbloquear nada, y todo el proceso es reversible. El panel de 1920×1200 y los 4 núcleos dan para una app nativa fluida.

**Comprobaciones de identidad** (solo lectura; si algo no coincide, para):

```sh
adb devices -l                                  # USB 1f3a:1001, "CPF10PF"
adb shell getprop ro.hardware                   # sun50iw10p1
adb shell getprop ro.build.fingerprint          # Allwinner/uhale/uhale:10/QP1A.191105.004/eng.…:eng/test-keys
adb shell getprop ro.build.type                 # eng
adb shell id                                    # uid=0(root)
adb shell wm size                               # Physical size: 1920x1200
adb shell cat /sys/block/mmcblk0/size           # 61071360 (eMMC de 32 GB)
```

La ficha completa, con todas las particiones y el software de fábrica, está en [docs/FRAME.md](docs/FRAME.md).

---

## Requisitos

**En el ordenador** (probado en Linux):

| Herramienta | Para qué |
|---|---|
| `adb` (Android platform-tools) | Todo |
| JDK 17 o superior y el SDK de Android (compileSdk 37) | Compilar la app (`android/gradlew`) |
| `sha256sum`, `gzip` | Copia de seguridad |
| `mtools`, `dosfstools` (`fsck.vfat`) | Logo del bootloader (`scripts/boot/install.sh`) |
| Chrome/Chromium, ImageMagick, `zip` | Solo si regeneras el logo o la animación (`scripts/boot/make.sh`) |
| Python 3 y `npm` | Solo si regeneras los iconos del tiempo (`scripts/meteocons.py`) |

**Además:**
- Un cable **USB-C de datos** (no solo carga).
- Una red Wi-Fi de **2,4 GHz con WPA2** (el chip no soporta WPA3 ni 5 GHz). Mejor una red de invitados.
- Si usas esta app tal cual: un [Planify-Backend](https://github.com/eusonlito/Planify-Backend) accesible por HTTPS y una cuenta creada con la [Planify-APP](https://github.com/eusonlito/Planify-APP).

---

## Las fases, paso a paso

`scripts/install.sh` hace las fases 2, 4, 5, 6, 7 y 8 de una vez y es idempotente (repetirlo actualiza la app y vuelve a aplicar la configuración). Aun así, entiende cada fase antes de lanzarlo: el detalle de cada paso está en [docs/SETUP.md](docs/SETUP.md).

### Fase 1: identifica el marco

- **Objetivo:** confirmar que es exactamente este hardware y software.
- **Cómo:** las [comprobaciones de identidad](#el-hardware), y compara `getprop` y la lista de paquetes con [docs/inventory/](docs/inventory/).
- **Si no coincide:** no sigas con estos scripts. Ve a [Adaptarlo a otro marco](#adaptarlo-a-otro-marco).

### Fase 2: copia de seguridad de la eMMC

- **Objetivo:** poder volver al estado de fábrica pase lo que pase.
- **Cómo:** `scripts/frame/backup.sh` (lo llama `install.sh`). Copia todo lo anterior a `/data` (GPT, boot0/U-Boot, todas las particiones, unos 3 GiB), las particiones de hardware `boot0`/`boot1` y la GPT de respaldo, y compara el SHA-256 de cada pieza con el dispositivo. Lo guarda en `backups/<serie>/`.
- **Verificación:** `sha256sum -c SHA256SUMS` en esa carpeta. Guarda además una copia fuera del repositorio.
- **Deshacer:** no aplica; es la red de seguridad del resto.

### Fase 3: decide qué app vas a instalar

La app de este repositorio (`android/`) es un dashboard nativo en Kotlin y Jetpack Compose, de solo lectura, que habla con Planify-Backend y con Open-Meteo. Tienes tres caminos:

1. **Usarla con Planify** (lo más rápido): monta [Planify-Backend](https://github.com/eusonlito/Planify-Backend), crea tu cuenta con [Planify-APP](https://github.com/eusonlito/Planify-APP) y marca en la APP qué calendarios y listas se ven (interruptor «Mostrar en el dashboard»).
2. **Usarla con otra fuente de datos:** la app solo necesita esto del backend, así que basta con reimplementarlo (o cambiar `data/Api.kt` y los repositorios de `data/repository/`):

   | Petición | Respuesta |
   |---|---|
   | `POST user/auth` `{email, password}` | `{id, name, email, token}` |
   | `GET event?start_date&end_date&routine&timezone&dashboard=true` con `Authorization: Bearer <token>` | `[{id, calendar_id, calendar_name, calendar_color, title, date_at: "Y-m-d H:i:s", routine_id, persons: [{id, name, color}]}]`. `routine=false` da los eventos y `routine=true` las sesiones de rutinas |
   | `GET task?dashboard=true` | `[{id, task_list_id, task_list_name, task_list_color, title, order, completed_at}]` |

   El tiempo viene de Open-Meteo (sin clave), así que no depende del backend.
3. **Hacer tu propia app:** respeta lo que pide el marco ([lo que aprendimos](#lo-que-aprendimos)): minSdk 29, sin librerías nativas (32 bits), diseño en los píxeles nativos y cuidado con las animaciones continuas. Si es una web, wesbos usa una WebView en modo quiosco; aquí se descartó por memoria y calor.

### Fase 4: configura y compila

```sh
cp android/planify.properties.example android/planify.properties
#   planify.baseUrl=https://tu-backend/        URL de la API (obligatoria para la release)

cp scripts/config.example scripts/config.local
#   ADB_FROM=<ip-de-tu-pc>                     única IP que podrá usar ADB por Wi-Fi (vacía: solo USB)
#   FRAME_TIMEZONE=Europe/Madrid               opcional (por defecto, la de tu PC)
#   FRAME_NTP=pool.ntp.org                     opcional
```

Los dos ficheros están en `.gitignore`. La URL también se puede pasar con `-Pplanify.baseUrl=…` o la variable `PLANIFY_BASE_URL`, y la release no compila sin ella. La versión debug apunta al backend local visto desde el emulador (`planify.debugBaseUrl`, por defecto `http://10.0.2.2:3000/`).

Antes de tocar el marco, pruébala en un emulador con sus medidas (API 29, 1920×1200, 320 dpi, 1 GB): ver [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#desarrollo).

### Fase 5: instala y convierte el marco en quiosco

```sh
scripts/install.sh --build        # la primera vez, por USB
```

- **Device Owner:** `dpm set-device-owner`. Android solo lo permite **sin cuentas** en el dispositivo; el script lo comprueba. Desde ahí la app se fija en lock task (sin botones de inicio ni recientes), quita el bloqueo y la barra de estado, y se registra como launcher.
- **Apps de fábrica:** desactiva Uhale (`com.zeasn.frame`, con [problemas de seguridad conocidos](https://go.quokka.io/hubfs/App-Intel/Technical_Uhale-Digital-Picture-Frame-Security-Assessment.pdf)) y las herramientas de Allwinner con `pm disable-user`, que es reversible. La app de pruebas de fábrica `com.example` se desinstala para el usuario 0 porque es *persistent* y arrancaría igualmente.
- **Ajustes del sistema:** NTP y zona horaria, sin sonidos ni animaciones, Bluetooth y localización apagados, sin pantalla de bloqueo.
- **Permisos de la app:** `SYSTEM_ALERT_WINDOW` para el botón flotante de volver al dashboard desde Ajustes de Android, y un servicio de accesibilidad para abrir el menú de apagar/reiniciar.
- **Deshacer:** [docs/SETUP.md, Mantenimiento y vuelta atrás](docs/SETUP.md#mantenimiento-y-vuelta-atrás).

### Fase 6: aísla el marco

- **Objetivo:** que solo el dashboard salga a Internet.
- **Cómo:** `install.sh` copia `scripts/frame/planify-frame.sh` y su `.rc` a `/system` (necesita `adb remount`). En cada arranque montan un **cortafuegos por UID**: el UID del dashboard sale a Internet y el resto del sistema solo a la red local, DNS y NTP. No admite conexiones entrantes salvo ADB desde `ADB_FROM`. Además, `su` pasa a `root:shell 4750`: ADB sigue siendo root, pero las apps ya no.
- **Verificación:** `adb shell iptables -S FRAME_OUT` y `adb shell iptables -S FRAME_IN`.

### Fase 7: actualizaciones por Wi-Fi

- **Cómo:** con `ADB_FROM` configurado, `install.sh` activa `persist.adb.tcp.port=5555`. El cortafuegos solo deja entrar al 5555 desde esa IP, y `adbd` sigue pidiendo la clave autorizada.
- **Uso:**
  ```sh
  adb connect <ip-del-marco>:5555
  ANDROID_SERIAL=<ip-del-marco>:5555 scripts/install.sh --build --no-backup
  ```
- **Importante:** reserva en el router una IP para tu PC y otra para el marco, y configura la red del marco con «Usar MAC del dispositivo» (Android 10 usa una MAC aleatoria por red).

### Fase 8: arranque y energía

- **Arranque personalizado** (`scripts/boot/install.sh`): sustituye el logo del bootloader y la animación de arranque por los de Planify. La partición FAT del bootloader **declara 128 MB pero mide 32 MB**, y justo detrás va `env`, así que nunca se monta en el marco: se edita una copia en el PC, se comprueba que ningún clúster cae fuera y se escribe verificando el hash. `--restore` vuelve a los de fábrica.
- **Horario de pantalla:** fuera de horario, por defecto, la pantalla se queda con un **reloj nocturno** (hora, fecha y tiempo sobre negro, con la retroiluminación al mínimo); tocarlo enseña el dashboard 5 minutos. Si se desactiva en Ajustes, la pantalla se apaga de verdad con `lockNow()` y se enciende con una alarma exacta; el táctil no la despierta (el botón de encendido sí) y, encendida fuera de horario, se vuelve a apagar a los 5 minutos.
- **Sin suspensión del kernel:** el script de arranque deja un wakelock del kernel (`/sys/power/wake_lock`), porque en esta placa la suspensión está rota (ver [lo que aprendimos](#lo-que-aprendimos)).

### Fase 9: primer uso

1. **Wi-Fi:** "Ir a Ajustes" en la pantalla de inicio de sesión. Si el router está en modo mixto WPA2/WPA3, añade la red a mano como WPA/WPA2-Personal ([docs/SETUP.md, Primer uso](docs/SETUP.md#primer-uso)).
2. **Inicia sesión** con la cuenta de Planify.
3. **Ajustes del dashboard:** ubicación del tiempo, horario de pantalla y brillo.

### Fase 10: verificación final

- [ ] Reinicia el marco: arranca en el logo propio y después en el dashboard, en horizontal, sin barra de estado ni bloqueo.
- [ ] `iptables -S FRAME_OUT` rechaza la salida de otros UIDs, y ADB por Wi-Fi solo funciona desde tu IP.
- [ ] Sin red, el dashboard sigue mostrando los últimos datos y dice "Sin conexión".
- [ ] El horario apaga y enciende la pantalla, y tras una noche el táctil responde.
- [ ] Tras una hora: `dumpsys meminfo com.lito.planify.dashboard` (unos 175 MB) y la temperatura (`/sys/class/thermal/thermal_zone*/temp`, unos 60 °C).

---

## Qué hace la app

Barra lateral con Hoy, Calendario, Tiempo, Tareas, sincronizar, Ajustes y apagar pantalla. Tras 2 minutos sin tocar, vuelve a Hoy.

| Pantalla | Contenido |
|---|---|
| **Hoy** | Reloj de paletas (split-flap), el siguiente evento con cuenta atrás, el tiempo (ahora, el día de hoy de 00 a 23 h con la hora actual marcada y los próximos días, que se deslizan), próximos eventos, rutinas de hoy y tareas |
| **Calendario** | Semana a semana (deslizando): el tiempo de cada día, los eventos, una banda aparte para las rutinas y los colores de cada persona |
| **Tiempo** | Hoy de 00 a 23 h y 7 días; la semana siguiente en gráfica; tocar un día abre sus 24 horas con su cabecera |
| **Tareas** | Todas las listas elegidas, en columnas |
| **Reloj nocturno** | Fuera de horario, o al pulsar la luna: hora, fecha y tiempo sobre negro con el brillo al mínimo; tocarlo enseña el dashboard |
| **Ajustes** | Ubicación del tiempo (Open-Meteo), horario de pantalla (con reloj nocturno o apagado), brillo y Sistema: estado del Wi-Fi, Ajustes de Android, cerrar sesión y apagar o reiniciar |

Por dentro (detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):
- **Píxeles nativos:** la app fija `Density(1f)`, así que todo se diseña en los 1920×1200 píxeles del panel (1 dp = 1 px), sin tocar la densidad del sistema.
- **Sin red, todo como la última vez:** cada dato se guarda en disco con escritura atómica y se pinta desde ahí (*stale-if-error*). Contenido cada 5 minutos, tiempo cada 30.
- **Hora fiable:** como el RTC no guarda la hora, nada anterior a la compilación de la app se toma por bueno ("Sincronizando hora…"), y sin hora válida no se apaga la pantalla ni se pide nada.
- **Iconos del tiempo animados** ([Meteocons](https://github.com/basmilius/meteocons)), pre-renderizados una vez a su tamaño para no calentar el marco.
- **Máximas y mínimas** calculadas a partir de las horas, para que coincidan siempre con la gráfica.

---

## Lo que aprendimos

Cosas de este marco que no se ven en el emulador y que costaron tiempo. Tenlas en cuenta en cualquier versión:

- **Suspensión del kernel rota.** Con la pantalla apagada y sin USB, Android intenta suspender: falla por el acelerómetro (`mir3da`), el táctil se queda muerto hasta recargar su módulo (`rmmod`/`insmod gslX680new.ko`) y el servicio de suspensión aborta, así que Android se reinicia al despertar. Por USB nunca se ve, porque el USB mantiene el sistema despierto. Solución: un wakelock del kernel permanente.
- **Wi-Fi sin WPA3 ni 5 GHz.** Con routers en modo mixto, Android guarda la red como WPA3 y el driver la rechaza. Además, el driver no le pasa la señal a Android (RSSI -127) y, con la localización apagada, Android oculta el nombre de la red: los dos se leen de `/proc/net/rtl8189fs/wlan0/`.
- **El RTC no guarda la hora.** Arranca en la fecha de fábrica hasta que llega NTP. Cualquier horario o fecha que se calcule antes sale mal.
- **1 GB de RAM y calor.** Dibujar animaciones Lottie en cada fotograma llevó el marco a 1–2 núcleos y 72 °C. Pre-renderizando los fotogramas, se queda en un 25–37 % de un núcleo y unos 60 °C. Nada de vídeo ni animaciones continuas innecesarias.
- **Escalas de animación a 0.** El script las pone a 0 para que Android no anime nada, y Compose las respeta: las animaciones que sí quieras tienen que forzar su propia escala.
- **Sin barra de navegación.** Al abrir Ajustes de Android dentro del quiosco no hay forma de volver: la app pone un botón flotante de inicio y vuelve sola tras un minuto sin tocar.
- **Apagar el marco.** Ni siendo Device Owner se puede apagar Android desde una app; un servicio de accesibilidad sí puede abrir el menú nativo de apagar/reiniciar. Mantener el botón de encendido unos 10 s corta la corriente de golpe.
- **El táctil no despierta la pantalla apagada**; el botón de encendido sí.
- **La partición FAT del bootloader miente sobre su tamaño.** Montarla y escribir en el marco puede pisar `env` o `boot`.
- **El anuncio miente sobre la resolución** (dice 1920×1080 y es 1920×1200).

---

## Adaptarlo a otro marco

Muchos marcos parecidos usan el mismo SoC A133 o placas Rockchip, pero cambian el build (muchos son `user`, sin root), las particiones, el táctil y el Wi-Fi.

**Específico de este marco (revísalo siempre):**
- La comprobación de `ro.hardware` en `scripts/install.sh` (no la quites: cámbiala por la de tu marco).
- `scripts/frame/backup.sh`: la partición de datos es `mmcblk0p17` y hay `boot0`/`boot1`.
- `scripts/boot/`: el logo es un BMP de 1200×1920 en vertical dentro de una FAT con el tamaño mal declarado.
- La lista de paquetes de fábrica que se desactivan (Uhale, Allwinner, `com.example`).
- El wakelock contra la suspensión, y los nombres del driver de Wi-Fi y del módulo del táctil.
- Las medidas de la app: está diseñada en píxeles nativos para 1920×1200.

**Genérico (sirve casi igual en cualquier Android 10 con root por ADB):** el quiosco con Device Owner, el cortafuegos por UID, ADB por Wi-Fi desde una sola IP, el horario de pantalla y la capa de datos con caché.

**Sin root por ADB:** el camino es otro (fastboot, FEL, parchear `boot`…). Empieza por [wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard), que documenta varios modelos y sus rutas. Si tu marco es un A133 como el LOOFII C-3, su [guía del A133](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/allwinner-a133-loofii-c3.md) es la más cercana a esta.

---

## Estructura

```
android/              app Android (Kotlin, Jetpack Compose, minSdk 29); planify.properties.example
scripts/install.sh    fases 2 y 4 a 8; se configura con scripts/config.local (config.example)
scripts/frame/        backup.sh (eMMC), planify-frame.sh y .rc (cortafuegos y wakelock al arrancar)
scripts/boot/         logo del bootloader y animación de arranque (boot.html → make.sh → install.sh)
scripts/meteocons.py  iconos del tiempo para la app
docs/                 FRAME, SETUP, ARCHITECTURE, ANDROID, inventory/ e images/
```

## Licencias

- Código: [MIT](LICENSE).
- Fuentes [Geist y Geist Mono](https://vercel.com/font): SIL Open Font License ([licenses/Geist-OFL.txt](licenses/Geist-OFL.txt)).
- Iconos del tiempo [Meteocons](https://github.com/basmilius/meteocons) de Bas Milius: MIT ([third_party/meteocons/LICENSE](third_party/meteocons/LICENSE)).

## Créditos

- [wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard): la idea, el método y la documentación del A133 (LOOFII C-3) en la que se basan la copia de seguridad, la recuperación y el cortafuegos.
- [Open-Meteo](https://open-meteo.com/): la previsión del tiempo, libre y sin clave.
