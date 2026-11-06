package io.github.auag0.pokegopip

import java.util.Locale

const val TAG = "PokeGoPiP"

/**
 * Pokémon GO packages supported by this module.
 * - com.nianticlabs.pokemongo      : Google Play version
 * - com.nianticlabs.pokemongo.ares : Samsung Galaxy Store version
 */
val POKEGO_PACKAGES = setOf(
    "com.nianticlabs.pokemongo",
    "com.nianticlabs.pokemongo.ares"
)

/**
 * Otros juegos con ventana flotante.
 * - com.bandainamcoent.dgup_ww   : DIGIMON UP (Bandai Namco, versión mundial)
 *
 * Los juegos de Supercell (Clash of Clans, Clash Royale, Hay Day) NO: su protección contra trampas
 * detecta el módulo dentro del juego y lo cierra. Para esos, la vista emergente de Samsung.
 */
val OTHER_GAME_PACKAGES = setOf(
    "com.bandainamcoent.dgup_ww"
)

/** Todos los juegos que el módulo pone en ventana flotante. */
val GAME_PACKAGES = POKEGO_PACKAGES + OTHER_GAME_PACKAGES

/**
 * Textos de los botones de la ventana flotante.
 *
 * Arreglo: antes se leían con getModuleRes(), que abría el APK de POKÉMON GO
 * (applicationInfo del juego) en vez del del módulo. Los textos no existían ahí,
 * saltaba un error al crear los botones y nunca se llegaba a pedir el PiP.
 * Ahora van escritos aquí en español, inglés y japonés.
 */
object Texts {
    private fun pick(es: String, en: String, ja: String) = when (Locale.getDefault().language) {
        "es" -> es
        "ja" -> ja
        else -> en
    }

    val toggleScreen
        get() = pick("Mostrar/ocultar pantalla", "Toggle Screen", "画面表示の切り替え")
    val toggleScreenDesc
        get() = pick("Muestra u oculta la pantalla", "Show or hide the screen", "画面の表示／非表示を切り替えます")
    val toggleStartup
        get() = pick("Pantalla al iniciar", "Toggle Startup Screen Visibility", "起動時の画面表示切り替え")
    val toggleStartupDesc
        get() = pick(
            "Mostrar u ocultar la pantalla al abrir el juego",
            "Toggle screen visibility on startup",
            "起動時に画面を表示するか切り替えます"
        )
    val startupVisible
        get() = pick("La pantalla se mostrará al iniciar", "Screen will be shown on startup", "起動時に画面を表示します")
    val startupHidden
        get() = pick("La pantalla se ocultará al iniciar", "Screen will be hidden on startup", "起動時に画面を非表示にします")
    val minimize
        get() = pick("Minimizar a ícono", "Minimize to icon", "アイコンに最小化")
    val minimizeDesc
        get() = pick(
            "Esconde la ventana y deja un ícono flotante; el juego sigue abierto",
            "Hide the window and leave a floating icon; the game stays open",
            "ウィンドウを隠してフローティングアイコンを表示します"
        )
    val bubbleHint
        get() = pick(
            "Toca el ícono para volver · déjalo presionado para quitarlo",
            "Tap the icon to come back · long-press to remove it",
            "タップで戻る・長押しで消す"
        )
    val overlayMissing
        get() = pick(
            "No se pudo mostrar el ícono flotante: el juego sigue en Recientes. Revisa que el Marco del sistema esté marcado en el módulo y reinicia",
            "Could not show the floating icon: the game is still in Recents. Check that System Framework is enabled for the module and reboot",
            "フローティングアイコンの権限がありません"
        )
}
