# ¿Se puede instalar un Android más reciente en el XORO CPF 10PF?

**Sí, en teoría hasta Android 16, pero no compensa para este dashboard.** La recomendación es quedarse en el Android 10 de fábrica, limpio y aislado ([SETUP.md](SETUP.md)). Si algún día interesa, la actualización con más garantías es **AOSP 12.1 (phh v416)**, porque ya se ha instalado y usado en un A133 casi idéntico.

## De qué depende

| Requisito | En este marco | Consecuencia |
|---|---|---|
| Treble y particiones dinámicas | Sí (VNDK 29, `super` de 2 GiB, sin A/B) | Se puede sustituir solo `system` por un GSI y conservar el kernel y el vendor de fábrica |
| Arquitectura | Kernel arm64, **espacio de usuario de 32 bits** | Hace falta un GSI **`arm32_binder64`** (en TrebleDroid se llama "a64"). Los GSI oficiales de Google son solo ARM64 y no valen |
| Verificación de arranque | vbmeta con `HASHTREE_DISABLED`; build `eng` con test-keys | Se puede cambiar `system` sin que dm-verity lo bloquee |
| Kernel | 4.9.170 de Allwinner, sin fuentes | **No se puede actualizar.** Cualquier Android nuevo sigue con un kernel de 2019 y los drivers del fabricante (PowerVR, Realtek, gslX680) |
| RAM | **1 GB**, `ro.config.low_ram=true` | Es la mayor limitación. Las pruebas del repositorio de wesbos se hicieron con 2 GB |
| Espacio en `super` | Unos 1,5 GiB para `system` si se conservan `vendor` (123 MiB) y `product` (382 MiB) | Cabe AOSP 12.1 (1,25 GiB descomprimido). Android 15 y 16 van muy justos |

## Opciones

| Opción | Android | Estado en un A133 | Riesgo y esfuerzo | Qué aporta |
|---|---|---|---|---|
| **A. Fábrica, limpio y aislado** | 10 | Funciona | Bajo; todo reversible con `pm enable` | Lo justo para un dashboard nativo. Seguridad por aislamiento (cortafuegos), no por parches |
| **B. phh AOSP 12.1 v416** `system-squeak-arm32_binder64-ab-vanilla` | 12.1 | **Instalado y en uso** en el LOOFII C-3 (A133, Android 10, mismo `super`) | Medio: copia completa de la eMMC, construir `super` sin conexión, flashear por fastboot de U-Boot, corregir el giro del táctil | Sistema AOSP limpio, sin Uhale ni apps de fábrica, y con SELinux en modo *enforcing* |
| **C. TrebleDroid A15** `ci-20250415`, `system-td-arm32_binder64-ab-vanilla` | 15 | El LOOFII **lo arrancó solo de prueba, por DSU** | Alto: no está probado instalado; hay que ajustar propiedades (`ro.apex.updatable`, `per_app_memcg`) | Versión más reciente, pero con el mismo kernel 4.9 |
| **D. TrebleDroid A16** `ci-20250617`, `system-td-arm32_binder64-vanilla-old` (solo VNDK 28/29) | 16 | Nadie lo ha probado en un A133. El propio autor dice "*just be happy if it boots*" | Muy alto | Más reciente aún; también existe crDroid 12.0 (A16 QPR0, a64, solo microG, parche de julio de 2025) |
| E. Linux en microSD (Omarchy, Armbian…) | — | El BootROM de Allwinner arrancaría desde la SD | Muy alto | La GPU **PowerVR GE8300 no tiene driver libre**, así que todo iría por software a 1920×1200. Además habría que rehacer el dashboard en web |

### Por qué no compensa actualizar

1. **El dashboard es una app nativa** (Kotlin y Compose, minSdk 29). No necesita una WebView ni un Chrome modernos, que fueron el motivo de actualizar en los proyectos de wesbos.
2. **La seguridad no mejora tanto como parece.** El kernel y el vendor siguen siendo los de fábrica (4.9, sin parches), y el phh 12.1 tampoco tiene parches recientes. Lo que de verdad protege es **aislar el marco**: cortafuegos por UID, quitar Uhale y las apps de fábrica, y limitar `su`.
3. **Con 1 GB de RAM**, Android 12 o superior deja menos memoria libre que Android 10, y Android 14 o superior ya dio problemas de memoria en otro marco (`per_app_memcg`, reinicios de `system_server`; ver [`android14-dsu.md`](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/android14-dsu.md) de wesbos).
4. **TLS ya funciona en Android 10**: ISRG Root X1 está en el almacén y Open-Meteo (y un backend con Let's Encrypt) encadenan a ella (ver [FRAME.md](FRAME.md#tls)).

### Si algún día se hace la opción B

Hay que seguir [`allwinner-a133-loofii-c3.md`](https://github.com/wesbos/photo-frame-dashboard/blob/main/docs/allwinner-a133-loofii-c3.md) §3–§5 y [`scripts/a133/`](https://github.com/wesbos/photo-frame-dashboard/tree/main/scripts/a133) de wesbos, teniendo en cuenta lo que cambia en esta unidad:
- **Antes de nada, copia completa de la eMMC** (ver [SETUP.md](SETUP.md#1-copia-de-seguridad)).
- **Giro:** el panel es vertical de 1200×1920, con `ro.surface_flinger.primary_display_orientation=ORIENTATION_90` y `ro.input_flinger.primary_touch.rotation=0` (InputFlinger parcheado por el fabricante). El GSI no lleva ese parche, así que habrá que buscar la combinación correcta, igual que en el LOOFII, que necesitó `ORIENTATION_270` + `user_rotation 3`. Se comprueba con "Pointer location" en las cuatro esquinas.
- **Táctil `gslX680` y Wi-Fi Realtek:** el LOOFII llevaba un táctil HID-over-I²C y Wi-Fi AIC8800. Los dos vienen del vendor, así que deberían funcionar, pero hay que comprobarlo.
- **Sin sensor de luz:** las reglas de `sensors-sepolicy.cil` solo hacen falta para el acelerómetro.
- **FEL:** confirmar primero que el agujero de reset lleva a `1f3a:efe8`. Es la red de seguridad si algo sale mal al flashear.
- Probarlo primero por **DSU** (`gsi_tool install`): no toca `system`, y si falla, al reiniciar vuelve a Android 10.

## Fuentes

- [wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard): `docs/allwinner-a133-loofii-c3.md`, `docs/android14-dsu.md`, `scripts/a133/`
- [phh AOSP 12.1 v416](https://github.com/phhusson/treble_experimentations/releases/tag/v416)
- [Versiones de TrebleDroid](https://github.com/TrebleDroid/treble_experimentations/releases): `ci-20250415` (Android 15 r26) y `ci-20250617` (Android 16 r1), ambas con `arm32_binder64`
- [crDroid GSI 12.0 (A16 QPR0)](https://newreleases.io/project/github/Doze-off/crdroid_gsi_treble/release/2025-08-02)
- [Versiones del GSI de Android 15 de Google (solo ARM64)](https://developer.android.com/about/versions/15/gsi-release-notes)
- [Mesa lima](https://docs.mesa3d.org/drivers/lima.html), [linux-sunxi A133](https://linux-sunxi.org/A133)
