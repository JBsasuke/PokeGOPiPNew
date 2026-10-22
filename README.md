# PokeGO PiP (API 101)

Módulo de Xposed que activa la **ventana flotante (Picture-in-Picture)** en Pokémon GO. Así puedes seguir jugando en una ventanita pequeña mientras usas otras apps.

> **Versión adaptada a la API moderna de Xposed (101) por [JBsasuke](https://github.com/JBsasuke)**, basada en [PokeGOPiP](https://github.com/auag0/PokeGOPiP) de **auag0**.
> Todo el mérito de la idea y del módulo original es de su autor.

## Características

- Ventana flotante (PiP) para Pokémon GO al salir del juego con el botón Inicio.
- Funciona con las dos versiones del juego:
  - **Google Play** (`com.nianticlabs.pokemongo`)
  - **Samsung Galaxy Store** (`com.nianticlabs.pokemongo.ares`)
- Botones en la ventana:
  - **Mostrar/ocultar pantalla**: deja la ventana en negro para ahorrar batería sin cerrar el juego.
  - **Pantalla al iniciar**: hace que el juego arranque con la pantalla oculta.
- Textos en español, inglés y japonés, según el idioma del teléfono.
- Registro de diagnóstico en `Android/data/com.nianticlabs.pokemongo/files/PokeGOPiP-debug.log`.

## Requisitos

- Android 8.0 o superior.
- Root, con **LSPosed** o **Vector** compatible con la **API moderna de Xposed 101** o más nueva.

## Instalación

1. Descarga el APK de la sección [Releases](../../releases) o compílalo tú mismo (ver abajo).
2. Si tenías una versión anterior de PokeGO PiP, **desinstálala primero**.
3. Instala el APK.
4. En LSPosed/Vector, activa el módulo y marca en el ámbito:
   - **Marco del sistema** (System Framework). Es obligatorio, porque sin él Android rechaza la ventana flotante.
   - **Pokémon GO**, la versión que tengas o las dos.
5. **Reinicia el teléfono.**
6. Abre Pokémon GO y pulsa **Inicio**: el juego pasará a una ventana flotante.

## Cambios respecto al original

**2.0.1**
- Migrado de la API antigua de XposedBridge (82) a la **API moderna libxposed 101**.
- El módulo se declara en `META-INF/xposed/` (`java_init.list`, `module.prop`, `scope.list`) en vez de `assets/xposed_init` y los `meta-data` del manifiesto.
- El Marco del sistema se engancha con `onSystemServerStarting` (en el formato moderno, el ámbito se llama `system`).
- Los textos de los botones van dentro del código, porque la API moderna no permite leer los recursos del módulo dentro del juego.
- Los errores y el motivo por el que Android rechaza la ventana quedan escritos en `PokeGOPiP-debug.log`.

**1.4.0**
- Soporte para Pokémon GO de Galaxy Store.
- Detección de Unity aunque Niantic cambie el nombre de la actividad (Unity viejo y Unity 6).
- Proporción de la ventana ajustada a los límites de Android.
- Los errores ya no cierran el juego.

## Compilar

Con Android Studio, abre el proyecto y ejecuta **Build > Build APK(s)**.

También se puede compilar con **GitHub Actions**: en la pestaña **Actions**, ejecuta **Build APK** y descarga el APK cuando termine.

## Solución de problemas

Si la ventana no sale, abre `PokeGOPiP-debug.log`:

| Mensaje en el registro | Qué hacer |
|---|---|
| `Current activity does not support picture-in-picture` | Falta marcar **Marco del sistema** en el ámbito del módulo, o falta reiniciar. |
| `enterPictureInPictureMode returned true` | El módulo funciona. Si no ves la ventana, revisa que el PiP no esté desactivado en los ajustes de Android para Pokémon GO. |
| No aparece ninguna línea nueva | El módulo no está activo para Pokémon GO: revisa el ámbito y reinicia. |

## Aviso

Este módulo es solo para uso personal y no está afiliado ni respaldado por Niantic, Inc. ni The Pokémon Company. Usar módulos de Xposed puede ir contra los términos de servicio de Pokémon GO. **Úsalo bajo tu propia responsabilidad.**

## Licencia

Licencia **MIT**. Consulta el archivo [LICENSE](LICENSE).

Copyright (c) 2024 anago (auag0), autor del módulo original.
Adaptación a la API 101 (c) 2026 JBsasuke.
