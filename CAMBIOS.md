# PokeGO PiP 2.1.0 — Digimon UP

- Ahora también pone en ventana flotante **DIGIMON UP** (`com.bandainamcoent.dgup_ww`).
- El ámbito (`scope.list`) ya incluye Digimon UP; Android acepta su PiP igual que el de Pokémon GO.
- Si un juego no deja ver su reproductor de Unity, el módulo usa la pantalla principal del juego.
- El registro de diagnóstico de Digimon UP queda en `Android/data/com.bandainamcoent.dgup_ww/files/PokeGOPiP-debug.log`.

## Activarlo

1. Instala el APK nuevo encima.
2. En LSPosed/Vector abre el módulo y marca **DIGIMON UP** (además de Marco del sistema y Pokémon GO).
3. **Reinicia el teléfono** (el Marco del sistema solo toma el cambio al reiniciar).
4. Abre Digimon UP y pulsa Inicio: debe quedar en ventana flotante.

---

# PokeGO PiP 2.0.1 — arreglo de la ventana flotante (API 101)

El registro mostraba que el módulo detectaba la salida del juego ("onUserLeaveHint")
pero nunca llegaba a pedir el PiP. Había dos fallos:

- **Los textos de los botones** se leían con `getModuleRes()`, que abría el APK de
  Pokémon GO en vez del del módulo. Los textos no existían ahí y saltaba un error antes
  de pedir el PiP. Ahora los textos van en el código (español, inglés y japonés).
- **El Marco del sistema nunca se hookeaba.** Con la API moderna, system_server llega por
  `onSystemServerStarting`, no por `onPackageLoaded("android")`. Además el ámbito ahora
  está en `META-INF/xposed/scope.list` con el nombre moderno `system`, y se quitaron los
  `meta-data` viejos del manifiesto.
- Cualquier error se escribe ahora también en `PokeGOPiP-debug.log`, y si Android rechaza
  el PiP queda apuntado el motivo exacto.

## Instalar la 2.0.1

1. Instala el APK nuevo encima (misma firma que tu versión de prueba) o desinstala antes.
2. En LSPosed/Vector abre el módulo y comprueba que estén marcados **Marco del sistema**
   (Sistema / System Framework) y **Pokémon GO**.
3. **Reinicia el teléfono** (en el Samsung con root temporal: vuelve a rootear después).
4. Abre Pokémon GO, pulsa Inicio y, si no sale la ventana, mándame el
   `PokeGOPiP-debug.log` nuevo (Android/data/com.nianticlabs.pokemongo/files/).

---

# PokeGO PiP 1.4.0 — cambios

- Soporta Pokémon GO de **Galaxy Store** (`com.nianticlabs.pokemongo.ares`) además de la de Play Store.
- Ya no depende del nombre exacto de la actividad del juego: si Niantic lo cambia, detecta Unity igual.
- Encuentra la pantalla del juego en Unity viejo (`getView`) y en Unity 6 (`getFrameLayout`).
- Ajusta la proporción de la ventana a lo que Android permite (antes podía fallar en pantallas muy alargadas).
- Los errores ya no cierran el juego: se escriben en el log de LSPosed con la etiqueta `PokeGoPiP`.
- Si Android rechaza la ventana flotante, lo dice en el log de LSPosed.

## Cómo compilar el APK (sin computadora con Android Studio)

1. Haz un *fork* de github.com/auag0/PokeGOPiP o crea un repositorio nuevo.
2. Sube todos estos archivos (incluida la carpeta `.github`).
3. Entra en la pestaña **Actions** → **Build APK** → **Run workflow**.
4. Cuando termine, abre la ejecución y descarga **PokeGOPiP-apk**.

## Instalar

1. **Desinstala primero** el PokeGO PiP viejo (la firma es distinta y no deja actualizar encima).
2. Instala el APK nuevo.
3. En LSPosed, activa el módulo y marca **Marco del sistema** y la versión de Pokémon GO que tengas.
4. Reinicia (en el Samsung con root temporal: vuelve a rootear después).
