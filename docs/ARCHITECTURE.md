# Arquitectura del dashboard

Cómo está hecha la app del marco: pantallas, datos, estructura y desarrollo. Para montar el marco, ver [SETUP.md](SETUP.md).

## Pantallas

La barra lateral izquierda tiene arriba, solo con iconos, Hoy, Calendario, Tiempo, Tareas, **sincronizar** (vuelve a pedir el contenido de Hoy, las semanas del calendario ya cargadas y el tiempo; el icono gira mientras dura), Ajustes y apagar la pantalla. Abajo del todo, la hora (horas y minutos en dos líneas, con "Sin conexión" debajo si falla la red o la sincronización). Tras 2 minutos sin tocar, vuelve a Hoy.

Sin sesión iniciada se muestra el login (icono de Planify y formulario, centrados). Si no hay Internet, en su lugar aparece "Esperando conexión a Internet…" con "Ir a Ajustes" (ajustes de Wi-Fi), y el formulario aparece solo cuando llega la red.

- **Hoy:** rejilla de 3 columnas en dos filas (55 % y 45 % del alto).
  - Arriba, en 1/3, la hora y la fecha centradas y el **siguiente** evento (tarjeta del color de su calendario: sus personas y, a la derecha, lo que falta en una píldora, como en la lista de próximos eventos; debajo, el título y el día y la hora). Ese evento no se repite en la lista de próximos eventos. En 2/3, la **tarjeta del tiempo**: icono, temperatura y estado; lluvia del día, humedad y viento como iconos con su valor (Meteocons estáticos); la curva hora a hora del día de hoy, de 00 a 23 h (la hora actual marcada con una banda azul translúcida; la temperatura de todas las horas, pequeña y sin «°») con barras de lluvia solo donde llueve (el alto es la cantidad y el relleno, la probabilidad; ver más abajo); debajo, el icono del tiempo de cada hora y, abajo del todo, la hora; y, separados por una línea, los días en columnas, que se deslizan para ver la semana siguiente (vuelven a la primera a los 2 minutos). Al tocar un día, la cabecera y la curva pasan a ese día; tocarlo otra vez, o tocar hoy, vuelve a hoy (y vuelve solo a los 2 minutos).
  - Abajo, **próximos eventos** con el formato del widget de la app (barra de color, "MIÉ 14 OCT · 16:00" con el tiempo que falta en una píldora, título y personas; los pasados en gris), **rutinas de hoy** (la lista, con la siguiente resaltada y las pasadas atenuadas) y **tareas** por lista, sin casillas porque no se pueden marcar.
- **Calendario:** semanas de lunes a domingo, deslizando a izquierda y derecha (un año hacia cada lado; "Volver a hoy" regresa a la actual). 7 columnas. La cabecera de cada día mide siempre lo mismo: día y número con el icono del tiempo a la derecha, y debajo mín / máx · lluvia (0 % si no llueve). Luego los eventos y, aparte, una banda de rutinas (hora y colores de las personas arriba, nombre debajo); los días pasados, atenuados. Cada semana se descarga al mostrarla, junto con las vecinas. Abajo, la leyenda de personas.
- **Tiempo:** dos páginas que se deslizan. La primera es la tarjeta del tiempo a pantalla completa: el mismo día de hoy de 00 a 23 h que en Hoy (la temperatura de cada hora, también sin «°»), con el icono de cada hora encima de la hora, y 7 días. La segunda, la **semana siguiente** (días 8 a 14): gráfica de máximas y mínimas, lluvia (% y mm) y viento, y las columnas de cada día. En las dos páginas, tocar un día abre sus 24 horas (en la segunda, en lugar de la gráfica semanal) y tocarlo otra vez lo cierra.
- **Iconos del tiempo:** [Meteocons](https://github.com/basmilius/meteocons) de Bas Milius (estilo *line*, licencia MIT, en `third_party/meteocons/`), uno por código WMO de Open-Meteo, de día o de noche. `scripts/meteocons.py` los descarga en la versión fijada, cambia los colores pensados para fondo oscuro (nubes, niebla y viento casi blancos) por tonos pizarra y los deja en `res/raw` (animados, Lottie) y `res/drawable` (los estáticos de la cabecera: nube con lluvia para la probabilidad, gota para la humedad y viento, con la unidad más pequeña). El código diario es el tiempo más significativo de todo el día (si hay niebla por la mañana, el día sale con niebla), así que la columna de hoy, en el tiempo y en el calendario, usa el tiempo de ahora, como la cabecera.
  - **Animados:** el icono de la cabecera y los de las columnas de días (Hoy, Tiempo y Calendario); la fila de horas, estática. Dibujar los Lottie en cada fotograma costaba 1–2 núcleos y subía el marco a 72 °C, así que cada icono se renderiza una vez en segundo plano a su tamaño exacto (90 fotogramas: su bucle de 6 s a 15 fps, caché de 48 MB) y animarlo es pintar un bitmap, todos sincronizados con un único reloj. Medido en el marco: Hoy 31 % de un núcleo, Tiempo 37 %, Calendario 25 %, sin pasar de 61 °C; la app ocupa unos 175 MB y quedan unos 400 MB libres. Con la pantalla apagada no se anima nada.
- **Reloj:** en Hoy, un reloj de paletas (`FlipClock`): cada cifra en una placa grafito partida por la mitad y, al cambiar, la mitad de arriba cae y descubre la nueva (medio segundo, solo las cifras que cambian). En la barra lateral, las cifras se deslizan (`RollingText`). Las escalas de animación del sistema están a 0 en el marco, así que estas animaciones fuerzan su propia escala para verse.
- **Máximas y mínimas:** salen de las 24 horas de cada día (`withDailyFromHourly`, una vez al recibir la previsión), para que el resumen, las cabeceras y las gráficas digan siempre lo mismo. Las diarias de Open-Meteo se calculan con más detalle que la hora en punto y no coincidían.
- **Tareas:** todas las listas del dashboard en columnas.

Las tarjetas llevan una cabecera común (`CardHeader`): icono en un cuadro tintado, título y el total en una píldora del mismo color. En las gráficas del tiempo, la barra de lluvia mide la **cantidad**, no la probabilidad: el alto son los mm (en raíz cuadrada, llena a 15 mm en una hora o 30 mm en un día, y con un azul más intenso a partir de ahí) y la opacidad del relleno es la probabilidad, sin número: maciza al 100 % y vacía al 0 %, con un contorno suave siempre para que se vea la barra. Encima, los mm. Sin lluvia apreciable (menos de 0,1 mm) no hay barra. Open-Meteo da la lluvia de la hora anterior, así que cada columna enseña la de la hora que empieza en ella (la de las 05, de 05 a 06). La cabecera y los días llevan también el total del día en mm: primero los mm y después el % (en los días, nada si es un 0 %). Las listas que no caben se desvanecen en el borde (`fadingScroll`) en vez de cortar el texto. Las tarjetas sin contenido muestran un estado vacío (`EmptyState`): icono en círculos del color de la tarjeta, título y una línea.
- **Reloj nocturno** (`NightScreen`): fuera de horario, si no se ha elegido apagar la pantalla, la hora, la fecha y el tiempo de ahora en negrita y en blanco sobre negro (el icono del tiempo, teñido de blanco), estáticos, con el brillo al mínimo (`MainActivity` pone `screenBrightness` a 1/255 mientras `UiState.night`). Tocar enseña el dashboard 5 minutos (`peekUntil`). La luna de la barra lateral también lo activa si está el reloj nocturno (`nightNow`, hasta que se toque o hasta el siguiente encendido); si no, apaga la pantalla con `lockNow()`.
- **Ajustes:**
  - tres columnas que caben sin desplazamiento;
  - ubicación (Open-Meteo); horario de pantalla; brillo y **Sistema** (estado del Wi-Fi, Wi-Fi, Ajustes de Android, Cerrar sesión y Apagar o reiniciar);
  - todos los botones, a lo ancho y del mismo alto.

## Cómo funciona

- **App nativa** con Jetpack Compose, minSdk 29 y sin librerías nativas (el marco es de 32 bits).
- **Píxeles nativos:** la densidad del sistema no se toca. La app fija `Density(1f)`, así que todo se diseña en los 1920×1200 píxeles del panel (1 dp = 1 px).
- **Datos:**
  - `GET /event?dashboard=true` (eventos: todos desde hoy, sin fecha de fin; sesiones de rutina: hoy + 6 días) y `GET /task?dashboard=true` cada 5 minutos;
  - el Calendario pide cada semana al mostrarla (y la vuelve a pedir si tiene más de 5 minutos);
  - Open-Meteo cada 30 minutos: el día anterior (para las horas previas de madrugada) y 14 días;
  - **Sin red, todo como la última vez.** Cada dato remoto (contenido de Hoy, previsión y cada semana del calendario) tiene su copia en disco y se pinta desde ella al arrancar; si una descarga falla, se sigue mostrando la última buena y la barra indica "Sin conexión". Al volver la red se actualiza solo. Ver [Capa de datos](#capa-de-datos).
  - al recuperar la red se sincroniza enseguida;
  - con la pantalla apagada no se refresca. Lo último se guarda en caché: sin red se sigue mostrando y la barra lateral dice "Sin conexión".
- **Hora:** el RTC del marco no la conserva. Hasta que NTP la corrige, cualquier hora anterior a la compilación de la app se trata como falsa ("Sincronizando hora…").
- **Quiosco** con Device Owner: lock task, HOME persistente, sin keyguard ni barra de estado. El horario apaga la pantalla (`lockNow`, como pulsar el botón de encendido: el marco sigue encendido y conectado) y la enciende con una alarma. "Apagar" en la barra lateral hace lo mismo al momento.
- **Autenticación:** email y contraseña de Planify. El dashboard solo hace peticiones de lectura.
- **Diseño:** fondo crema, tarjetas blancas sin borde y el color solo en calendarios, listas y personas, con las fuentes Geist y Geist Mono (OFL, `licenses/Geist-OFL.txt`).

## Estructura

```
android/                     proyecto Gradle (mismas versiones que Planify-APP)
  app/src/main/java/com/lito/planify/dashboard/
    MainActivity.kt          launcher, inmersivo, Density(1f), quiosco y brillo
    DashboardViewModel.kt    estado, refrescos, login, ubicación, horario
    data/                    Api (Planify + Open-Meteo), modelos, Store (sesión y ajustes), ReferenceClock
    data/cache/              DiskCache y CachedResource (copia en disco, stale-if-error)
    data/repository/         ContentRepository, WeatherRepository, CalendarRepository
    admin/Kiosk.kt           Device Owner: lock task, HOME, lockNow, salir y quitar
    power/ScreenSchedule.kt  horario de pantalla (alarmas exactas, wakelock, apagado de cortesía)
    ui/                      barra lateral y pantallas; WeatherUi (HourlyForecast en Canvas), WeatherIcons y
                             MeteoconFrames (Meteocons pre-renderizados), FlipClock y RollingText
scripts/install.sh           instala y prepara el marco (ver SETUP.md); se configura en scripts/config.local
scripts/frame/               copia de la eMMC, cortafuegos de arranque e init .rc
scripts/boot/                logo del bootloader y animación de arranque (boot.html → make.sh → install.sh)
scripts/meteocons.py         iconos del tiempo (Meteocons) para res/raw y res/drawable
third_party/meteocons/       licencia MIT de Meteocons
docs/                        FRAME (ficha del dispositivo), ANDROID (versiones), SETUP (instalación)
```

## Desarrollo

Para el emulador hace falta un AVD con las medidas del marco (API 29, 1920×1200, 320 dpi, 1 GB):

```sh
sdkmanager "system-images;android-29;default;x86"
avdmanager create avd -n Frame_API_29 -k "system-images;android-29;default;x86" -d pixel_c
# En ~/.android/avd/Frame_API_29.avd/config.ini:
#   hw.lcd.width=1920  hw.lcd.height=1200  hw.lcd.density=320  hw.ramSize=1024  hw.initialOrientation=landscape
emulator -avd Frame_API_29
```

- La build **debug** usa el backend local (`planify.debugBaseUrl`, por defecto `http://10.0.2.2:3000/`: `php -S 0.0.0.0:3000 -t public public/index.php` en Planify-Backend).
- La **release** usa `planify.baseUrl` de `android/planify.properties` (ver `planify.properties.example`); sin ella no compila.
- Pruebas: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (en `android/`).

Para probar el quiosco en el emulador basta con `adb shell dpm set-device-owner com.lito.planify.dashboard/.admin.AdminReceiver` en un AVD sin cuentas.

## Capa de datos

La pantalla nunca habla con la red directamente: `DashboardViewModel` usa un repositorio por tipo de dato y pinta lo que publican.

- **`DiskCache`** (`data/cache/`): un fichero JSON por clave en `files/cache/`. Cada escritura va a un temporal, se sincroniza con el disco (`fsync`) y se renombra encima del anterior: un corte de luz deja la versión vieja o la nueva, nunca una a medias. Un fichero ilegible cuenta como vacío y se vuelve a descargar.
- **`CachedResource<T>`**: un dato remoto con su copia (`Cached(value, fetchedAt)`), con la estrategia *stale-if-error*. Al crearse publica la copia de disco (el primer fotograma ya sale completo); `refresh` descarga, guarda y publica, y si falla deja la copia tal cual y devuelve el error; dos refrescos del mismo dato nunca se solapan; `isStale()` dice si ha caducado.
- **Repositorios** (`data/repository/`): `ContentRepository` (eventos desde hoy, rutinas de la semana y tareas; caduca a los 5 min), `WeatherRepository` (Open-Meteo; 30 min; se borra al cambiar de ubicación) y `CalendarRepository` (una entrada `week-<lunes>` por semana, 5 min, se conservan las 12 descargadas más recientemente). Las respuestas HTTP sin éxito son `HttpError`; un 401 cierra la sesión y borra los datos de la cuenta.
- **`ReferenceClock`**: el RTC del marco no guarda la hora y, sin red, arranca en la fecha de fábrica. Mientras tanto todo se pinta con la última hora válida (guardada como mucho una vez por minuto), así que la caché se ve igual que la última vez; el reloj muestra "--:--". Sin hora válida no se pide nada a la red, porque las fechas de la petición serían las de fábrica.
- `Store` (SharedPreferences) solo guarda la sesión y los ajustes del dispositivo.

Tests (`./gradlew :app:testDebugUnitTest`): `DiskCacheTest`, `CachedResourceTest`, `ReferenceClockTest`, `CalendarRepositoryTest` (con una API falsa), `ForecastTest` y `ScreenScheduleTest`.

