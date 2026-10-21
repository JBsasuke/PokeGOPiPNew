package io.github.auag0.pokegopip

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

class HookPokeGO(private val module: XposedModule) {
    companion object {
        private const val ACTION_TOGGLE_BLACK_SCREEN = "ACTION_PIP_BLACK_SCREEN"
        private const val ACTION_TOGGLE_STARTUP_MODE = "ACTION_START_UP_BLACK_SCREEN"
        private const val UNITY_MAIN_ACTIVITY =
            "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity"
        private const val MIN_PIP_RATIO = 100.0 / 239.0
        private const val MAX_PIP_RATIO = 239.0 / 100.0

        private val receivers = WeakHashMap<Activity, BroadcastReceiver>()
        @Volatile private var toast: Toast? = null
        @Volatile private var unityMainActivityClass: Class<*>? = null
    }

    fun handleLoadPackage(param: PackageLoadedParam) {
        Log.i(TAG, "started pokemon go (${param.packageName})")
        unityMainActivityClass = runCatching {
            param.defaultClassLoader.loadClass(UNITY_MAIN_ACTIVITY)
        }.getOrNull()
        if (unityMainActivityClass == null) {
            Log.w(TAG, "$UNITY_MAIN_ACTIVITY not found, using generic Unity detection")
        } else {
            Log.i(TAG, "Unity game activity resolved: ${unityMainActivityClass!!.name}")
        }
        hookActivityLifecycle()
    }

    private fun hookActivityLifecycle() {
        Activity::class.java.declaredMethods
            .filter { it.name == "onPostCreate" }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            val activity = chain.thisObject as? Activity
                            if (activity != null && isGameActivity(activity)) {
                                PipFileLog.attach(activity)
                                PipFileLog.write("onPostCreate: ${activity.javaClass.name}")
                                runSafely("onPostCreate") { handleOnCreate(activity) }
                            }
                            return result
                        }
                    })
                }.onFailure { Log.e(TAG, "failed to hook ${method.name}", it) }
            }

        // Hook the game's override when it has one. Hooking only Activity's base
        // method can miss Unity activities that override onUserLeaveHint().
        val gameClass = unityMainActivityClass
        val leaveHintMethod = gameClass?.declaredMethods?.firstOrNull {
            it.name == "onUserLeaveHint" && it.parameterTypes.isEmpty()
        } ?: Activity::class.java.declaredMethods.firstOrNull {
            it.name == "onUserLeaveHint" && it.parameterTypes.isEmpty()
        }
        if (leaveHintMethod != null) {
            runCatching {
                module.hook(leaveHintMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val activity = chain.thisObject as? Activity
                        if (activity != null && isGameActivity(activity) &&
                            !activity.isFinishing && !activity.isInPictureInPictureMode) {
                            PipFileLog.write("Home/PiP callback reached: ${activity.javaClass.name}")
                            // Request PiP before Android completes the leave-hint callback.
                            runSafely("onUserLeaveHint") { handleOnUserLeaveHint(activity) }
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "hooked onUserLeaveHint: ${leaveHintMethod.declaringClass.name}")
            }.onFailure { Log.e(TAG, "failed to hook onUserLeaveHint", it) }
        } else {
            Log.e(TAG, "onUserLeaveHint method not found")
        }

        // Keep Back from finishing the game when PiP can be entered. Hook the
        // concrete Unity override if present, otherwise use Activity's method.
        val backMethod = gameClass?.declaredMethods?.firstOrNull {
            it.name == "onBackPressed" && it.parameterTypes.isEmpty()
        } ?: Activity::class.java.declaredMethods.firstOrNull {
            it.name == "onBackPressed" && it.parameterTypes.isEmpty()
        }
        if (backMethod != null) {
            runCatching {
                module.hook(backMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val activity = chain.thisObject as? Activity
                        if (activity != null && isGameActivity(activity) &&
                            !activity.isFinishing && !activity.isInPictureInPictureMode) {
                            PipFileLog.write("Back callback reached: ${activity.javaClass.name}")
                            runSafely("onBackPressed") { handleOnUserLeaveHint(activity) }
                            if (activity.isInPictureInPictureMode) return null
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "hooked onBackPressed: ${backMethod.declaringClass.name}")
            }.onFailure { Log.e(TAG, "failed to hook onBackPressed", it) }
        } else {
            Log.w(TAG, "onBackPressed method not found; Android predictive Back may use a different callback")
        }

        Activity::class.java.declaredMethods
            .filter { it.name == "onDestroy" && it.parameterTypes.isEmpty() }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val activity = chain.thisObject as? Activity
                            if (activity != null && isGameActivity(activity)) {
                                runSafely("onDestroy") { handleOnDestroy(activity) }
                            }
                            return chain.proceed()
                        }
                    })
                }.onFailure { Log.e(TAG, "failed to hook ${method.name}", it) }
            }
    }

        fun runSafely(where: String, block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "error in $where", t)
                // Antes el error solo iba al logcat; ahora también al archivo de diagnóstico
                PipFileLog.write("ERROR in $where: ${t.javaClass.simpleName}: ${t.message}")
                t.stackTrace.take(6).forEach { PipFileLog.write("    at $it") }
            }
        }

        fun isGameActivity(activity: Activity): Boolean {
            unityMainActivityClass?.let { return it.isInstance(activity) }
            return findUnityPlayerField(activity.javaClass) != null
        }

        fun findUnityPlayerField(start: Class<*>): Field? {
            var clazz: Class<*>? = start
            while (clazz != null && clazz != Activity::class.java) {
                for (field in clazz.declaredFields) {
                    if (field.name == "mUnityPlayer" || field.type.name.contains("UnityPlayer")) {
                        field.isAccessible = true
                        return field
                    }
                }
                clazz = clazz.superclass
            }
            return null
        }

        fun getUnityPlayer(activity: Activity): Any? =
            runCatching { findUnityPlayerField(activity.javaClass)?.get(activity) }.getOrNull()

        fun callMethod(obj: Any, name: String): Any? =
            runCatching {
                val m: Method = obj.javaClass.methods.first { it.name == name && it.parameterTypes.isEmpty() }
                m.isAccessible = true
                m.invoke(obj)
            }.getOrNull()

        fun getPlayerView(activity: Activity): View {
            val player = getUnityPlayer(activity)
            if (player != null) {
                for (name in listOf("getView", "getFrameLayout")) {
                    val view = callMethod(player, name)
                    if (view is View) return view
                }
                if (player is View) return player
            }
            Log.w(TAG, "Unity player view not found, using window decor view")
            return activity.window.decorView
        }

        fun resumeUnityPlayer(activity: Activity) {
            val player = getUnityPlayer(activity) ?: return
            val resumed = listOf("resume", "onResume").any { name ->
                runCatching { callMethod(player, name) }.isSuccess
            }
            Log.i(TAG, if (resumed) "resumed unity player" else "could not resume unity player")
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun handleOnCreate(activity: Activity) {
            if (receivers.containsKey(activity)) return
            PipFileLog.attach(activity)
            PipFileLog.write("onCreate: ${activity.javaClass.name}")

            if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                Log.w(TAG, "this device has picture-in-picture disabled")
            }

            val playerView = getPlayerView(activity)
            val prefs = activity.getSharedPreferences("pip", Context.MODE_PRIVATE)
            if (prefs.getBoolean("start_up", false)) {
                playerView.visibility = View.INVISIBLE
                Log.i(TAG, "Startup black screen enabled: hide playerView")
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    runSafely("onReceive") {
                        when (intent.action) {
                            ACTION_TOGGLE_BLACK_SCREEN -> {
                                playerView.visibility =
                                    if (playerView.visibility == View.VISIBLE) View.INVISIBLE
                                    else View.VISIBLE
                            }
                            ACTION_TOGGLE_STARTUP_MODE -> {
                                val newStartUp = !prefs.getBoolean("start_up", false)
                                prefs.edit().putBoolean("start_up", newStartUp).apply()
                                showToast(activity, if (newStartUp) Texts.startupHidden else Texts.startupVisible)
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(ACTION_TOGGLE_BLACK_SCREEN)
                addAction(ACTION_TOGGLE_STARTUP_MODE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                activity.registerReceiver(receiver, filter)
            }
            receivers[activity] = receiver
            Log.i(TAG, "registered receiver")
        }

        fun handleOnUserLeaveHint(activity: Activity) {
            if (activity.isInPictureInPictureMode || activity.isFinishing) return
            PipFileLog.attach(activity)
            PipFileLog.write("onUserLeaveHint")

            val playerView = getPlayerView(activity)
            val viewBounds = Rect()
            playerView.getGlobalVisibleRect(viewBounds)

            val params = PictureInPictureParams.Builder().apply {
                setAspectRatio(safeAspectRatio(playerView))
                if (!viewBounds.isEmpty) setSourceRectHint(viewBounds)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(false)
                }
                setActions(createPipActions(activity))
            }.build()

            PipFileLog.write("requesting PiP")
            val entered = try {
                activity.enterPictureInPictureMode(params)
            } catch (t: Throwable) {
                // Si sale "does not support picture-in-picture": falta el Marco del sistema
                // en el ámbito del módulo, o no se reinició el teléfono.
                PipFileLog.write("ERROR enterPictureInPictureMode: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            PipFileLog.write("enterPictureInPictureMode returned $entered")
            if (!entered) {
                PipFileLog.write("ERROR: system refused PiP; check System Framework scope and reboot")
                return
            }

            val activityRef = WeakReference(activity)
            Handler(activity.mainLooper).postDelayed({
                activityRef.get()?.let { runSafely("resume") { resumeUnityPlayer(it) } }
                    ?: Log.e(TAG, "Activity reference lost: resume skipped")
            }, 1000)
        }

        fun handleOnDestroy(activity: Activity) {
            Log.i(TAG, "onDestroy")
            val receiver = receivers.remove(activity) ?: return
            try {
                activity.unregisterReceiver(receiver)
                Log.i(TAG, "unregistered receiver")
            } catch (e: Throwable) {
                Log.e(TAG, "failed to unregister receiver", e)
            }
        }

        fun safeAspectRatio(view: View): Rational {
            val w = view.width.takeIf { it > 0 } ?: 9
            val h = view.height.takeIf { it > 0 } ?: 16
            val ratio = w.toDouble() / h
            return when {
                ratio < MIN_PIP_RATIO -> Rational(100, 239)
                ratio > MAX_PIP_RATIO -> Rational(239, 100)
                else -> Rational(w, h)
            }
        }

        fun createPipActions(activity: Activity): List<RemoteAction> {
            return listOf(
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_lock_power_off),
                    Texts.toggleScreen,
                    Texts.toggleScreenDesc,
                    PendingIntent.getBroadcast(
                        activity, 0,
                        Intent(ACTION_TOGGLE_BLACK_SCREEN).setPackage(activity.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                ),
                RemoteAction(
                    Icon.createWithResource(activity, android.R.drawable.ic_popup_sync),
                    Texts.toggleStartup,
                    Texts.toggleStartupDesc,
                    PendingIntent.getBroadcast(
                        activity, 1,
                        Intent(ACTION_TOGGLE_STARTUP_MODE).setPackage(activity.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            )
        }

        fun showToast(context: Context, msg: String) {
            toast?.cancel()
            toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
            toast?.show()
        }

    }
