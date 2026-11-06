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
        private const val ACTION_MINIMIZE = "ACTION_PIP_MINIMIZE"

        // Ícono flotante (al minimizar): uno solo a la vez
        @Volatile private var bubble: View? = null
        @Volatile private var bubbleWm: android.view.WindowManager? = null
        private const val UNITY_MAIN_ACTIVITY =
            "com.nianticproject.holoholo.libholoholo.unity.UnityMainActivity"
        private val GAME_ACTIVITY_NAMES = Regex("Unity|GameActivity|Cocos2dx|Godot", RegexOption.IGNORE_CASE)
        private const val MIN_PIP_RATIO = 100.0 / 239.0
        private const val MAX_PIP_RATIO = 239.0 / 100.0

        private val receivers = WeakHashMap<Activity, BroadcastReceiver>()
        // Para no pedir el PiP dos veces seguidas (lo pueden llamar dos ganchos a la vez)
        private val lastPipRequest = WeakHashMap<Activity, Long>()
        @Volatile private var toast: Toast? = null
        @Volatile private var unityMainActivityClass: Class<*>? = null
    }

    fun handleLoadPackage(param: PackageLoadedParam) {
        Log.i(TAG, "started game (${param.packageName})")
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

        // Juegos que reemplazan onUserLeaveHint sin llamar al de Android (Clash of Clans, Supercell…):
        // ese gancho nunca llega. performUserLeaving es lo que Android llama siempre al salir con
        // Inicio, antes de onUserLeaveHint, así que se engancha también.
        Activity::class.java.declaredMethods.firstOrNull {
            it.name == "performUserLeaving" && it.parameterTypes.isEmpty()
        }?.let { method ->
            runCatching {
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val activity = chain.thisObject as? Activity
                        if (activity != null && isGameActivity(activity) &&
                            !activity.isFinishing && !activity.isInPictureInPictureMode) {
                            PipFileLog.write("performUserLeaving reached: ${activity.javaClass.name}")
                            runSafely("performUserLeaving") { handleOnUserLeaveHint(activity) }
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "hooked performUserLeaving")
            }.onFailure { Log.e(TAG, "failed to hook performUserLeaving", it) }
        } ?: Log.w(TAG, "performUserLeaving not found")

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
            if (findUnityPlayerField(activity.javaClass) != null) return true
            // Otros juegos (Digimon UP…): si no se ve el reproductor de Unity, se usa la pantalla
            // principal del juego (la que abre el ícono) o una actividad de juego conocida
            if (activity.packageName in POKEGO_PACKAGES) return false
            val nombre = activity.javaClass.name
            if (GAME_ACTIVITY_NAMES.containsMatchIn(nombre)) return true
            return runCatching {
                activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.component?.className == nombre
            }.getOrDefault(false)
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
                            ACTION_MINIMIZE -> minimizeToIcon(activity)
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
                addAction(ACTION_MINIMIZE)
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
            val ahora = System.currentTimeMillis()
            if (ahora - (lastPipRequest[activity] ?: 0L) < 1500) return
            lastPipRequest[activity] = ahora
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
            if (activity.isFinishing) removeBubble()
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
                    Icon.createWithResource(activity, android.R.drawable.arrow_down_float),
                    Texts.minimize,
                    Texts.minimizeDesc,
                    PendingIntent.getBroadcast(
                        activity, 2,
                        Intent(ACTION_MINIMIZE).setPackage(activity.packageName),
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

        // ---------- Minimizar a ícono (como la vista emergente de Samsung) ----------
        // Esconde el juego (sigue abierto en Recientes) y deja un ícono flotante con el logo del juego.
        // Tocar el ícono lo trae de vuelta en ventana flotante; dejarlo presionado quita el ícono.
        // El ícono necesita el permiso "mostrar sobre otras apps" para el juego
        //   (lo da el módulo solo desde el Marco del sistema; ya no hace falta adb ni Termux)
        fun minimizeToIcon(activity: Activity) {
            PipFileLog.write("minimize to icon")
            // El permiso del ícono lo da el módulo desde el Marco del sistema (HookAndroid), así que
            // no se pregunta a Settings.canDrawOverlays (diría que no): se intenta poner el ícono y ya
            val ok = try { showBubble(activity); true } catch (t: Throwable) {
                PipFileLog.write("minimize: bubble failed: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            if (!ok) showToast(activity, Texts.overlayMissing)
            activity.moveTaskToBack(true)
        }

        @SuppressLint("ClickableViewAccessibility")
        fun showBubble(activity: Activity) {
            removeBubble()
            val app = activity.applicationContext
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val dm = app.resources.displayMetrics
            // Como la vista emergente minimizada de Samsung: el ícono del juego en un cuadrito
            // de esquinas redondeadas con borde blanco y sombra
            val size = (64 * dm.density).toInt()
            val borde = (3 * dm.density).toInt()
            val icon = android.widget.ImageView(app).apply {
                setImageDrawable(runCatching { app.packageManager.getApplicationIcon(app.packageName) }.getOrNull())
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setPadding(borde, borde, borde, borde)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = size * 0.3f; setColor(0xFFFFFFFF.toInt())
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                elevation = 10 * dm.density
                contentDescription = Texts.minimize
            }
            val lp = android.view.WindowManager.LayoutParams(
                size, size,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = dm.widthPixels - size - (8 * dm.density).toInt()
                y = (dm.heightPixels * 0.3).toInt()
            }
            val activityRef = WeakReference(activity)
            val handler = Handler(app.mainLooper)
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            val longPress = Runnable { removeBubble() }
            icon.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                        handler.postDelayed(longPress, 700)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX; val dy = e.rawY - downY
                        if (!moved && (Math.abs(dx) > 12 * dm.density || Math.abs(dy) > 12 * dm.density)) { moved = true; handler.removeCallbacks(longPress) }
                        if (moved) { lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt(); runCatching { wm.updateViewLayout(v, lp) } }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPress)
                        if (moved) {
                            // se pega al borde más cercano
                            lp.x = if (lp.x + size / 2 < dm.widthPixels / 2) (8 * dm.density).toInt() else dm.widthPixels - size - (8 * dm.density).toInt()
                            runCatching { wm.updateViewLayout(v, lp) }
                        } else if (bubble === v) {
                            restoreFromIcon(app, activityRef, handler)
                            handler.postDelayed({ removeBubble() }, 300)
                        }
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
                }
                true
            }
            wm.addView(icon, lp)
            bubble = icon; bubbleWm = wm
            showToast(app, Texts.bubbleHint)
            PipFileLog.write("bubble shown")
        }

        fun removeBubble() {
            val v = bubble ?: return
            bubble = null
            runCatching { bubbleWm?.removeView(v) }
            bubbleWm = null
        }

        // Trae el juego al frente (como tocar su ícono) y lo vuelve a poner en ventana flotante
        fun restoreFromIcon(app: Context, activityRef: WeakReference<Activity>, handler: Handler) {
            PipFileLog.write("restore from icon")
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            runCatching { if (launch != null) app.startActivity(launch) }
                .onFailure { PipFileLog.write("ERROR restore: ${it.javaClass.simpleName}: ${it.message}") }
            handler.postDelayed({
                activityRef.get()?.let { a -> runSafely("re-enter PiP") { handleOnUserLeaveHint(a) } }
            }, 800)
        }

        fun showToast(context: Context, msg: String) {
            toast?.cancel()
            toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
            toast?.show()
        }

    }
