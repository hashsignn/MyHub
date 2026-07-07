package com.contentreg.app.core.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A local-only crash log. On an uncaught exception we append the stack trace to a file in the app's
 * private storage, then hand off to the platform's default handler so the OS still shows its dialog.
 *
 * Nothing is ever sent anywhere automatically — the user views the log in the in-app Logs screen and
 * chooses whether to share it. This is the deliberate privacy trade for being fully offline: we stay
 * blind to field crashes unless the user opts to hand us the log.
 */
object CrashReporter {

    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "crash.log"
    private const val MAX_BYTES = 256 * 1024 // keep the file small; oldest entries are dropped

    /** Installs the handler. Call once, first thing in [android.app.Application.onCreate]. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { append(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable) // preserve the system's crash behaviour
        }
    }

    fun logFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        return File(dir, LOG_FILE)
    }

    /** The current log contents, or empty string if nothing has been recorded. */
    fun read(context: Context): String {
        val file = logFile(context)
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        runCatching { logFile(context).writeText("") }
    }

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_BYTES) file.writeText("") // rotate by reset

        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        val when0 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = "=== CRASH $when0 · thread=${thread.name} · " +
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} ==="
        file.appendText("$header\n$stack\n\n")
    }
}
