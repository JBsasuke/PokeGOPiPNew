package io.github.auag0.pokegopip

import android.content.pm.ActivityInfo
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/** En system_server: hace que Android diga que Pokémon GO admite PiP. */
class HookAndroid(private val module: XposedModule) {
    fun handleLoadPackage() {
        try {
            // Todas las variantes del método (algunas versiones de Android tienen más de una)
            val methods = ActivityInfo::class.java.declaredMethods
                .filter { it.name == "supportsPictureInPicture" }

            for (method in methods) {
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val info = chain.thisObject as? ActivityInfo
                        return if (info != null && info.packageName in POKEGO_PACKAGES) true
                        else chain.proceed()
                    }
                })
            }
            // module.log sí sale en los registros de LSPosed/Vector (Log.i no siempre)
            module.log(Log.INFO, TAG, "system: hooked supportsPictureInPicture (${methods.size})")
            if (methods.isEmpty()) {
                module.log(Log.ERROR, TAG, "system: supportsPictureInPicture not found")
            }
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "failed to hook system process", t)
        }
    }
}
