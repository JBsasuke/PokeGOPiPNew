# PokeGO PiP 2.3.2

- **Se quitaron Clash of Clans, Clash Royale y Hay Day:** su protección contra trampas detecta el
  módulo y cierra el juego. Para esos, usa la vista emergente de Samsung. Quedan Pokémon GO y Digimon UP.
- **El ícono al minimizar ya no lleva borde blanco:** sale solo el ícono del juego, como en Samsung.
- Se mantiene el permiso automático del ícono (sin adb ni Termux).

---

# PokeGO PiP 2.3.1 — arreglo para Clash of Clans y los juegos de Supercell

- El registro mostraba que el módulo veía abrir Clash of Clans pero nunca se enteraba de que salías
  con Inicio: esos juegos reemplazan `onUserLeaveHint` sin avisar a Android. Ahora el módulo
  engancha también `performUserLeaving`, que Android llama siempre al salir con Inicio.
- No se pide la ventana flotante dos veces seguidas aunque lleguen los dos avisos.

---

# PokeGO PiP 2.3.0 — juegos de Supercell y permiso del ícono automático

- Ahora también pone en ventana flotante **Clash of Clans** (`com.supercell.clashofclans`),
  **Clash Royale** (`com.supercell.clashroyale`) y **Hay Day** (`com.supercell.hayday`).
- **El permiso del ícono flotante ya no hace falta darlo con adb ni Termux:** el módulo se lo da
  solo a los juegos desde el Marco del sistema (al reiniciar el teléfono).
- Al tocar el ícono, el juego se abre antes de quitar el ícono, para que Android no lo bloquee.

## Activarlo

1. Instala el APK nuevo encima.
2. En LSPosed/Vector, en el módulo marca **Clash of Clans, Clash Royale y Hay Day** (además de Marco del sistema, Pokémon GO y Digimon UP).
3. **Reinicia el teléfono.**

---

# PokeGO PiP 2.2.0 — minimizar a ícono

- Nuevo botón en la ventana flotante: **Minimizar a ícono**. Esconde la ventana y deja un ícono
  flotante con el logo del juego en un cuadrito redondeado con borde blanco, igual que la vista
  emergente minimizada de Samsung. El juego sigue abierto.
  - **Tocar** el ícono: vuelve el juego en ventana flotante.
  - **Arrastrar**: lo mueves; al soltarlo se pega al borde.
  - **Dejarlo presionado**: quita el ícono (el juego sigue en Recientes).
- El ícono necesita que el juego pueda "mostrarse sobre otras apps". Se da una sola vez por juego,
  con la computadora (adb) o con root en Termux:

```
adb shell appops set com.bandainamcoent.dgup_ww SYSTEM_ALERT_WINDOW allow
adb shell appops set com.nianticlabs.pokemongo SYSTEM_ALERT_WINDOW allow
```
  (en Termux con root: `su -c appops set com.bandainamcoent.dgup_ww SYSTEM_ALERT_WINDOW allow`)

  Sin ese permiso, el botón igual esconde el juego (queda en Recientes) y avisa qué falta.

---

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
