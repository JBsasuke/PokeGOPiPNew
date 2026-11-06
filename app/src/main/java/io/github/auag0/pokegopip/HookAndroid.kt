package io.github.auag0.pokegopip

import android.content.pm.ActivityInfo
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/** En system_server: hace que Android diga que Pokémon GO (y los otros juegos) admiten PiP. */
class HookAndroid(private val module: XposedModule) {
    fun handleLoadPackage(classLoader: ClassLoader?) {
        hookPip()
        if (classLoader != null) hookOverlayPermission(classLoader)
        else module.log(Log.ERROR, TAG, "system: no class loader, floating icon permission not hooked")
    }

    /**
     * Permiso del ícono flotante (minimizar a ícono) sin adb ni Termux: cuando uno de los juegos pide
     * poner una ventana "sobre otras apps", Android lo acepta como si tuviera el permiso.
     * PhoneWindowManager.checkAddPermission decide eso; 0 = ADD_OKAY.
     */
    private fun hookOverlayPermission(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass("com.android.server.policy.PhoneWindowManager")
            val methods = cls.declaredMethods.filter { it.name == "checkAddPermission" }
            for (method in methods) {
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val args = chain.args
                        var type: Int? = null
                        var pkg: String? = null
                        for (a in args) {
                            when (a) {
                                is android.view.WindowManager.LayoutParams -> { type = a.type; pkg = a.packageName }   // Android viejo
                                is Int -> if (type == null) type = a
                                is String -> if (pkg == null) pkg = a
                            }
                        }
                        if (type == android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY && pkg in GAME_PACKAGES) {
                            (args.firstOrNull { it is IntArray } as? IntArray)?.let { if (it.isNotEmpty()) it[0] = -1 }  // AppOpsManager.OP_NONE
                            return 0
                        }
                        return chain.proceed()
                    }
                })
            }
            module.log(Log.INFO, TAG, "system: hooked checkAddPermission (${methods.size})")
            if (methods.isEmpty()) module.log(Log.ERROR, TAG, "system: checkAddPermission not found")
        } catch (t: Throwable) {
            module.log(Log.ERROR, TAG, "failed to hook overlay permission", t)
        }
    }

    private fun hookPip() {
        try {
            // Todas las variantes del método (algunas versiones de Android tienen más de una)
            val methods = ActivityInfo::class.java.declaredMethods
                .filter { it.name == "supportsPictureInPicture" }

            for (method in methods) {
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val info = chain.thisObject as? ActivityInfo
                        return if (info != null && info.packageName in GAME_PACKAGES) true
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
