package com.jianzhang.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to app-private storage before forwarding
 * it to Android's default handler. This exists so a release-only crash can be
 * diagnosed without a connected adb session.
 */
internal object CrashReporter {
    private const val DIRECTORY = "crash"
    private const val LAST_CRASH = "last-crash.txt"

    fun install(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(application, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun write(application: Application, thread: Thread, error: Throwable) {
        val directory = File(application.filesDir, DIRECTORY).apply { mkdirs() }
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        File(directory, LAST_CRASH).writeText(
            buildString {
                appendLine("time=$timestamp")
                appendLine("thread=${thread.name}")
                appendLine("exception=${error.javaClass.name}")
                appendLine(stack)
            },
        )
    }
}