# XORO CPF 10PF: ficha del dispositivo

Datos de **una unidad**, leídos por ADB el 14-10-2026 sin modificar nada. Los marcos que se venden con el mismo nombre pueden llevar otra placa, así que comprueba los identificadores antes de ejecutar nada que escriba en el dispositivo.

Los volcados en bruto (`getprop`, paquetes, particiones, cpuinfo, SurfaceFlinger, input) están en [`inventory/`](inventory/).

## Identidad

| | Valor |
|---|---|
| Producto | XORO CPF 10PF, 10,1" (Amazon B0G2MJBR3M, 78,56 €). Importa MAS Elektronik AG |
| Número de serie ADB | `XCPF10PF2P…` (`ro.serialno`, también en `/proc/cmdline` como `snum`). El final es único de cada unidad |
| USB | `1f3a:1001 Allwinner Technology CPF10PF` (ADB/MTP) |
| SoC | **Allwinner A133** (`ro.hardware`/`androidboot.hardware` = `sun50iw10p1`, plataforma `ceres`, placa `exdroid`) |
| CPU | 4× Cortex-A53 (`CPU part 0xd03`), anunciada a 1,5 GHz |
| GPU | PowerVR **GE8300** (hilos del kernel `pvr_*`, EGL `1.11@5516664`) |
| RAM | **1 GB** (`MemTotal 985816 kB`, unos 435 MB disponibles en reposo) con zram de 722 MB, `ro.config.low_ram=true` |
| Almacenamiento | eMMC de 32 GB (`mmcblk0` = 31.268.536.320 bytes); `/data` = 26 GB |
| Pantalla | Panel nativo **1200×1920** en vertical, girado por SurfaceFlinger (`ORIENTATION_90`) → **1920×1200 en horizontal (16:10)**. El anuncio dice 1920×1080, pero no es así. Densidad 320 (960×600 dp). Refresco 60,3 Hz |
| Táctil | `gslX680` (I²C), modo directo |
| Sensores | Solo el acelerómetro Mi3da. **No tiene sensor de luz ni de presencia** |
| Wi-Fi | Realtek RTL8189FS (driver `8189fs`, `persist.vendor.wlan_vendor=realtek`), **solo 2,4 GHz** b/g/n. **No soporta WPA3 (SAE)**: ver SETUP.md, Primer uso |
| RTC | `/dev/rtc0` = `sunxi-rtc`, pero **no conserva la hora**: tras arrancar sin red marcaba 30-01-2026 (la fecha de compilación) |
| Puertos | microSD, USB-A 2.0 (host), USB-C (dispositivo: ADB/MTP), DC-IN, interruptor, botón de reset (agujero), volumen |
| Alimentación | Adaptador DC sin batería. El manual indica dos modelos: 5 V ⎓ 2 A (RY15C050200EU) o 12 V ⎓ 1,5 A (XC018WH-1201500V1). Mira la etiqueta del tuyo |
| Montaje | VESA 75×75, gancho de pared y marco de haya desmontable |

## Software de fábrica

| | Valor |
|---|---|
| Android | **10** (API 29, `QP1A.191105.004`), parche de seguridad **2021-01-05** |
| Build | `Allwinner/uhale/uhale:10/QP1A.191105.004/eng.ctk.20260130.093021:eng/test-keys`, tipo **`eng`** |
| Kernel | 4.9.170 **arm64** (`CONFIG_ARM64=y`, `CONFIG_COMPAT=y`), compilado el 29-01-2026 |
| Espacio de usuario | **Solo 32 bits**: `zygote32`, abilist `armeabi-v7a,armeabi`, sin `linker64` ni `/vendor/lib64`. Para un GSI hace falta el tipo **`arm32_binder64`** |
| Bootloader | U-Boot 2018.05 (compilado el 11-08-2025) |
| Treble | `ro.treble.enabled=true`, VNDK 29, particiones dinámicas (`super` de 2 GiB), **sin A/B** |
| AVB | vbmeta con `device_state=locked` pero **flags = 1 (HASHTREE_DISABLED)**: dm-verity desactivado. `androidboot.trustchain=false`, `secure_os_exist=0` |
| SELinux | **Permissive** (`androidboot.selinux=permissive`) |
| Root | `adbd` corre como root (`ro.secure=0`, `ro.debuggable=1`). También está `/system/xbin/su`, **setuid root y ejecutable por cualquier app** (`-rwsr-sr-x root shell`) |
| ADB | Por USB-C, con autenticación (`ro.adb.secure=1`). Tras `install.sh`, también por Wi-Fi en el 5555, solo desde el PC de desarrollo (ver [SETUP.md](SETUP.md#actualizar-por-wi-fi)) |
| Wi-Fi | Realtek RTL8189FS (`8189fs`), solo 2,4 GHz y sin WPA3. Android usa una MAC aleatoria por red (ver SETUP.md) |
| Zona horaria | `Europe/Amsterdam` (hay que cambiarla) |
| WebView | `com.android.webview` de serie (el dashboard nativo no la necesita) |

### Particiones (`/dev/block/by-name`)

| Partición | Dispositivo | Tamaño |
|---|---|---|
| bootloader | mmcblk0p1 | 32 MiB (FAT16: `bootlogo.bmp`, avisos, batería, fuentes). Su FAT declara 128 MB: no montar ni escribir en el marco, ver SETUP.md, Arranque personalizado |
| env | mmcblk0p2 | 16 MiB |
| boot | mmcblk0p3 | 32 MiB |
| super | mmcblk0p4 | 2 GiB: `system` 543 MiB · `vendor` 123 MiB · `product` 382 MiB (≈1 GiB libre) |
| misc | mmcblk0p5 | 16 MiB |
| recovery | mmcblk0p6 | 32 MiB |
| cache | mmcblk0p7 | 768 MiB |
| vbmeta / vbmeta_system / vbmeta_vendor | mmcblk0p8–10 | 16 MiB cada una |
| metadata | mmcblk0p11 | 16 MiB |
| private | mmcblk0p12 | 16 MiB |
| frp | mmcblk0p13 | 512 KiB |
| empty | mmcblk0p14 | 15,5 MiB |
| dtbo | mmcblk0p15 | 2 MiB |
| media_data | mmcblk0p16 | 16 MiB |
| UDISK (= /data) | mmcblk0p17 | 26,1 GiB |
| mmcblk0boot0 / boot1 / rpmb | | 4 MiB cada una |

La distribución es **idéntica** a la del LOOFII C-3 (A133) documentado en [`wesbos/photo-frame-dashboard`: allwinner-a133-loofii-c3.md](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/allwinner-a133-loofii-c3.md), incluido el `super` de exactamente 2.147.483.648 bytes.

### Apps de sistema relevantes

| Paquete | Qué es | Riesgo |
|---|---|---|
| `com.zeasn.frame` 4.3.1 (`/product/app/Uhale`) | App Uhale: launcher (HOME) y gestión del marco. **Corre con el UID de sistema** (`android.uid.system`). Lleva Firebase Crashlytics y Analytics | Alto: es la familia de apps del [informe de Quokka](https://go.quokka.io/hubfs/App-Intel/Technical_Uhale-Digital-Picture-Frame-Security-Assessment.pdf) (malware descargado al arrancar en la v4.2.0, RCE como root) |
| `com.example` 1.0 (`/product/app/TS8ProdutionTest`) | App de pruebas de fábrica con receptores de arranque y de USB. UID de sistema y *persistent* (arranca aunque esté desactivada). Incluye servicios que recogen logcat y kmsg y un `FileUploader` que los sube a una `REPORT_UPLOAD_URL` leída de `hxzl_service.properties` (fichero que no existe en el marco) | Medio: no tiene ninguna función útil una vez fuera de fábrica. Se desinstala para el usuario 0 |
| `com.softwinner.dragonaging`, `dragonfire`, `com.allwinner.batterytest` | Herramientas de envejecimiento y pruebas de Allwinner | Bajo, pero sobran |
| `com.softwinner.awlogsettings` + servicios `awlogd`/`kmsgd` | Guardan logcat y kmsg en `/data/media/awlog/` | Desgaste de la eMMC y fuga de datos en los logs |
| `com.softwinner.fireplayer` | Reproductor multimedia de Allwinner | Bajo |

Comprobado: **no hay indicios del malware de Quokka**. No existe `/data/data/com.zeasn.frame/files/.honor`, no hay paquetes `com.app.mz.*` y no está el servicio `com.zeasn.cook.CookService`. El Wi-Fi no se había conectado nunca. Lo que no hay es un agente FOTA aparte (el LOOFII sí lo tenía): las actualizaciones las gestiona Uhale.

## TLS

- El almacén del sistema tiene 138 CA, incluida **ISRG Root X1** (`6187b673.0`).
- `api.open-meteo.com`/`geocoding-api.open-meteo.com` y un backend con certificado de Let's Encrypt sirven la cadena `YR1|YR2 → Root YR → ISRG Root X1` (Root YR con firma cruzada de X1).
- Por tanto **Android 10 valida estos certificados sin incluir raíces propias** en la app. Si tu backend usa otra CA, comprueba que esté en el almacén del marco (`/system/etc/security/cacerts`).

## Escalera de recuperación

Por orden de menos a más invasivo. Lo que no se ha probado en esta unidad está marcado.

1. **ADB por USB-C como root.** Probado.
2. **Fastboot de U-Boot:** `adb reboot bootloader`. En el A133 del LOOFII aparece como `1f3a:1010`, y el `fastboot` estándar se cuelga. Ahí usaron [`scripts/a133/fb.py`](https://github.com/wesbos/photo-frame-dashboard/blob/main/scripts/a133/fb.py) (pyusb) de wesbos/photo-frame-dashboard. *No probado en esta unidad.*
3. **FEL (BootROM, independiente de la eMMC):** sin alimentación ni USB-C, mantener pulsado el agujero de reset, conectar y soltar a los 3–5 s. Debería aparecer `1f3a:efe8` y `sunxi-fel version` debería responder `soc=00001855(A133)`. Desde FEL se puede llegar a fastboot (`a133-flash.sh fel-to-uboot`). *No probado en esta unidad, y el botón de reset podría estar cableado de otra forma.*
4. **Arranque desde microSD:** el BootROM de Allwinner prueba la SD antes que la eMMC. Sirve para arrancar Linux sin tocar la eMMC, pero no resulta práctico: la GPU PowerVR no tiene driver libre (ver [ANDROID.md](ANDROID.md)).

## Notas para el dashboard

- **minSdk 29**, y APK sin librerías nativas (el espacio de usuario es de 32 bits).
- **Resolución:** la densidad del sistema no se toca. La app fija `Density(1f)`, así que se diseña directamente en los 1920×1200 píxeles del panel (1 dp = 1 px).
- **Hora:** el RTC no la conserva. Hasta que haya NTP, el reloj marca la fecha de compilación. El dashboard no debe enseñar "hoy" mientras `System.currentTimeMillis()` sea anterior a la última hora válida guardada.
- **Pantalla apagada = táctil apagado** en este tipo de placas (observado en el LOOFII). Para la noche conviene usar una capa negra con brillo mínimo en vez de apagar la pantalla.
- **Suspensión del kernel rota** en el XORO: falla por el acelerómetro `mir3da`, deja el táctil sin responder y tumba el servicio de suspensión. Se evita con un wakelock del kernel (ver `SETUP.md`, Horario de pantalla).
- **Calor:** en el LOOFII, casi todo el calor en reposo lo genera la tubería de pantalla, no la CPU. Mejor evitar animaciones continuas y vídeo.
- Solo tiene acelerómetro: el modo noche tendrá que ir por horario, no por sensor de luz ni de presencia.
