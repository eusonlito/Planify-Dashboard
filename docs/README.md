# Documentación del dashboard

| Documento | Contenido |
|---|---|
| [FRAME.md](FRAME.md) | Ficha del XORO CPF 10PF: hardware, particiones, software de fábrica, seguridad, TLS y cómo recuperarlo |
| [ANDROID.md](ANDROID.md) | Hasta qué versión de Android se puede actualizar (GSI `arm32_binder64`, Linux en SD) y por qué no compensa |
| [SETUP.md](SETUP.md) | Montaje recomendado paso a paso: copia de seguridad, launcher, quitar Uhale, quiosco, cortafuegos |
| [inventory/](inventory/) | Volcados en bruto del marco (14-10-2026): `getprop`, paquetes, particiones, sistema |

## Material de referencia

- [wesbos/photo-frame-dashboard](https://github.com/wesbos/photo-frame-dashboard): otro hardware, pero `docs/allwinner-a133-loofii-c3.md` y `scripts/a133/` son del **mismo SoC A133**, con el mismo Android 10 y la misma distribución de la eMMC. Para tenerlo a mano, clónalo en `docs/photo-frame-dashboard/` (está en `.gitignore`).

## Dispositivo descartado

Primero se compró un **dxmart Frameo 10,1"** (B0DNMGKW2H): probablemente RK3126 (Cortex-A7), Android 6.0.1, 1 GB y 1280×800. Se descartó a favor del XORO por varios motivos:
- Android 6 obliga a minSdk 23 y a incluir raíces TLS propias.
- No es compatible con Treble.
- Su Rockchip arranca primero desde la eMMC, así que no se puede arrancar desde SD de forma reversible.
