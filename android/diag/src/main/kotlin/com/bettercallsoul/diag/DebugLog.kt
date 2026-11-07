package com.bettercallsoul.diag

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * A log the app keeps in its own files, for testing on phones that hide logcat from apps (Honor,
 * Huawei) and for days away from a computer. Lines also go to logcat. Phone numbers are written
 * through [mask], so a shared log shows only their last digits.
 *
 * Two files of [MAX_BYTES] each are kept; the older one is dropped when the newer fills up.
 */
object DebugLog {
    private const val MAX_BYTES = 1_000_000L
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "debug-log").apply { isDaemon = true } }
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Volatile private var folder: File? = null
    @Volatile private var enabled: () -> Boolean = { false }

    fun init(context: Context, enabled: () -> Boolean) {
        folder = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        this.enabled = enabled
        catchCrashes()
    }

    fun i(tag: String, message: String) = write('I', tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write('W', tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write('E', tag, message, error)

    /** "+90 532 123 45 67" becomes "…4567": enough to tell calls apart, not enough to identify anyone. */
    fun mask(number: String?): String {
        val digits = number?.filter(Char::isDigit).orEmpty()
        return when {
            digits.isEmpty() -> "(none)"
            digits.length <= 4 -> "…$digits"
            else -> "…" + digits.takeLast(4)
        }
    }

    private fun write(level: Char, tag: String, message: String, error: Throwable?) {
        when (level) {
            'E' -> Log.e(tag, message, error)
            'W' -> Log.w(tag, message, error)
            else -> Log.i(tag, message)
        }
        if (!enabled()) return
        val line = buildString {
            append(LocalDateTime.now().format(time)).append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (error != null) append('\n').append(stackTrace(error))
            append('\n')
        }
        writer.execute { append(line) }
    }

    private fun append(line: String) {
        val dir = folder ?: return
        val current = File(dir, "bcs.log")
        if (current.length() > MAX_BYTES) {
            val old = File(dir, "bcs.1.log")
            old.delete()
            current.renameTo(old)
        }
        runCatching { current.appendText(line) }
    }

    /** The crash is written straight away, since the process is about to die. */
    private fun catchCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (enabled()) {
                val dir = folder
                if (dir != null) {
                    runCatching {
                        File(dir, "bcs.log").appendText(
                            "${LocalDateTime.now().format(time)} E/Crash: thread ${thread.name}\n${stackTrace(error)}\n",
                        )
                    }
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun stackTrace(error: Throwable) = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    /** Size of what would be shared, in bytes. */
    fun size(): Long = listOf("bcs.1.log", "bcs.log").sumOf { name -> folder?.let { File(it, name).length() } ?: 0L }

    fun clear() {
        writer.execute { folder?.listFiles()?.forEach(File::delete) }
    }

    /**
     * Joins both files under a header with the phone and app details into one file that can be
     * shared, and returns it. Runs the pending writes first.
     */
    fun export(context: Context, header: List<String>): File {
        val done = java.util.concurrent.CountDownLatch(1)
        writer.execute { done.countDown() }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
        val dir = folder ?: File(context.filesDir, "logs").apply { mkdirs() }
        val out = File(dir, "export/better-call-soul-log.txt").apply { parentFile?.mkdirs() }
        out.printWriter().use { print ->
            print.println("Better Call Soul debug log")
            print.println("Exported: ${LocalDateTime.now().format(time)}")
            print.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}")
            header.forEach(print::println)
            print.println("----")
            listOf("bcs.1.log", "bcs.log").map { File(dir, it) }.filter(File::exists).forEach { print.print(it.readText()) }
        }
        return out
    }
}
