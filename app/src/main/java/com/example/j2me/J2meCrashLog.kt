package com.example.j2me

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last uncaught exception of a game run in filesDir/j2me/crash_last.txt.
 * The loader screen shows it on the next open, so the cause is readable without adb.
 */
object J2meCrashLog {

    private const val FILE_NAME = "crash_last.txt"

    fun file(context: Context): File = File(J2meStore.rootDir(context), FILE_NAME)

    fun record(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val trace = StringWriter()
            throwable.printStackTrace(PrintWriter(trace))
            val report = buildString {
                appendLine("Waktu: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                appendLine("Thread: " + thread.name)
                appendLine("Versi: " + appVersion(context))
                appendLine()
                append(trace.toString())
            }
            file(context).writeText(report)
        }
    }

    fun read(context: Context): String? = runCatching {
        file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
