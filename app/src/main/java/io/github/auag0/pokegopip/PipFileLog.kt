package io.github.auag0.pokegopip

import android.app.Activity
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes diagnostic events to Pokémon GO's app-specific external files directory. */
internal object PipFileLog {
    private const val TAG = "PokeGOPiP"
    @Volatile private var logFile: File? = null

    fun attach(activity: Activity) {
        runCatching {
            val dir = activity.getExternalFilesDir(null) ?: activity.filesDir
            logFile = File(dir, "PokeGOPiP-debug.log")
            write("--- logger attached; package=${activity.packageName}; activity=${activity.javaClass.name} ---")
        }.onFailure { Log.e(TAG, "Cannot initialize diagnostic file", it) }
    }

    fun write(message: String) {
        val line = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} $message\n"
        Log.i(TAG, message)
        runCatching {
            val file = logFile ?: return
            synchronized(this) {
                file.appendText(line)
                // Keep the file bounded to avoid unbounded storage growth.
                if (file.length() > 512 * 1024) {
                    val tail = file.readLines().takeLast(1500).joinToString("\n", postfix = "\n")
                    file.writeText(tail)
                }
            }
        }.onFailure { Log.e(TAG, "Cannot write diagnostic file", it) }
    }
}
