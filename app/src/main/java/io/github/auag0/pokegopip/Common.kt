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
}
