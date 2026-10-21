package io.github.auag0.pokegopip

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * Entrada del módulo (API moderna de Xposed 101).
 *
 * Arreglo: con la API moderna, el "Marco del sistema" (system_server) NO llega por
 * onPackageLoaded("android"), sino por onSystemServerStarting. Antes HookAndroid nunca
 * se ejecutaba, Android seguía diciendo que Pokémon GO no admite PiP y
 * enterPictureInPictureMode fallaba.
 */
class Main : XposedModule() {
    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        log(Log.INFO, TAG, "loaded in $processName ($frameworkName $frameworkVersion, API $apiVersion)")
    }

    // Marco del sistema: hace que Android acepte PiP para Pokémon GO
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        HookAndroid(this).handleLoadPackage()
    }

    // Pokémon GO: solo el proceso principal del juego
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        if (param.packageName in POKEGO_PACKAGES && processName == param.packageName) {
            HookPokeGO(this).handleLoadPackage(param)
        }
    }
}
