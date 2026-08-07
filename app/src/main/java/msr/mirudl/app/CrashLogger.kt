package msr.mirudl.app

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CrashLogger private constructor(context: Context) : Thread.UncaughtExceptionHandler {

    private val appContext: Context = context.applicationContext
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    private val io = Executors.newSingleThreadExecutor()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        try {
            saveCrashLogSync(thread, ex)
        } catch (_: Exception) {}
        defaultHandler?.uncaughtException(thread, ex)
    }

    private fun saveCrashLogSync(thread: Thread, ex: Throwable) {
        val dir = getCrashDir(appContext)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        try {
            BufferedWriter(FileWriter(file)).use { w ->
                w.write(buildReport(thread, ex))
                w.flush()
            }
        } catch (_: Exception) {}

        markNewCrash()
        cleanupOldLogs(dir)
    }

    private fun buildReport(thread: Thread, ex: Throwable): String {
        val sb = StringBuilder()
        val line = "========================================"
        sb.appendLine(line)
        sb.appendLine("  MIRUDL CRASH REPORT")
        sb.appendLine(line)
        sb.appendLine()

        sb.appendLine("--- APP ---")
        sb.appendLine("Version    : ${getAppVersion()}")
        sb.appendLine()

        sb.appendLine("--- DEVICE ---")
        sb.appendLine("Brand      : ${Build.BRAND}")
        sb.appendLine("Model      : ${Build.MODEL}")
        sb.appendLine("Device     : ${Build.DEVICE}")
        sb.appendLine("Product    : ${Build.PRODUCT}")
        sb.appendLine("SDK        : ${Build.VERSION.SDK_INT}")
        sb.appendLine("Release    : ${Build.VERSION.RELEASE}")
        sb.appendLine()

        sb.appendLine("--- BUILD ---")
        sb.appendLine("Type       : ${Build.TYPE}")
        sb.appendLine("Board      : ${Build.BOARD}")
        sb.appendLine("Host       : ${Build.HOST}")
        sb.appendLine("Display    : ${Build.DISPLAY}")
        sb.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        sb.appendLine("ID         : ${Build.ID}")
        sb.appendLine("Tags       : ${Build.TAGS}")
        sb.appendLine("User       : ${Build.USER}")
        sb.appendLine()

        sb.appendLine("--- ENVIRONMENT ---")
        sb.appendLine("Time       : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        sb.appendLine("Locale     : ${Locale.getDefault()}")
        sb.appendLine()

        sb.appendLine("--- MEMORY ---")
        val rt = Runtime.getRuntime()
        sb.appendLine("Max        : ${rt.maxMemory() / 1048576} MB")
        sb.appendLine("Total      : ${rt.totalMemory() / 1048576} MB")
        sb.appendLine("Free       : ${rt.freeMemory() / 1048576} MB")
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val avail = stat.availableBlocks.toLong() * stat.blockSize
            sb.appendLine("Disk Free  : ${avail / 1048576} MB")
        } catch (_: Exception) {}
        sb.appendLine()

        sb.appendLine("--- THREAD ---")
        sb.appendLine("Name       : ${thread.name}")
        sb.appendLine("Priority   : ${thread.priority}")
        sb.appendLine("Daemon     : ${thread.isDaemon}")
        sb.appendLine()

        sb.appendLine("--- SCREEN ---")
        sb.appendLine("Current    : $lastScreen")
        sb.appendLine("Last action: $lastUserAction")
        sb.appendLine()

        sb.appendLine("--- EXCEPTION ---")
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        ex.printStackTrace(pw)
        pw.flush()
        sb.append(sw)
        sb.appendLine()

        var cause: Throwable? = ex.cause
        var depth = 0
        while (cause != null && depth < 5) {
            sb.appendLine("Caused by  : ${cause.javaClass.name}: ${cause.message}")
            cause = cause.cause
            depth++
        }
        sb.appendLine()

        sb.appendLine(line)
        sb.appendLine("  End of report")
        sb.appendLine(line)
        return sb.toString()
    }

    private fun markNewCrash() {
        appContext.getSharedPreferences(PREFS_NAME, 0)
            .edit().putBoolean(KEY_NEW_CRASH, true).apply()
    }

    private fun cleanupOldLogs(dir: File) {
        var files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
        if (files == null) return

        val now = System.currentTimeMillis()
        for (f in files) {
            if (now - f.lastModified() > MAX_FILE_AGE_MS) f.delete()
        }

        files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
        if (files != null && files.size > MAX_CRASH_FILES) {
            Arrays.sort(files, Comparator.comparingLong { it.lastModified() })
            for (i in 0 until files.size - MAX_CRASH_FILES) {
                files[i].delete()
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0).versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    companion object {
        private const val CRASH_DIR = "crash_logs"
        private const val MAX_CRASH_FILES = 20
        private const val MAX_FILE_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val PREFS_NAME = "mirudl_crash_logger"
        private const val KEY_NEW_CRASH = "has_new_crash"

        private var lastScreen = ""
        private var lastUserAction = ""

        @JvmStatic
        fun updateScreen(screen: String) { lastScreen = screen }

        @JvmStatic
        fun updateAction(action: String) { lastUserAction = action }

        @JvmStatic
        fun hasNewCrash(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, 0).getBoolean(KEY_NEW_CRASH, false)
        }

        @JvmStatic
        fun markViewed(context: Context) {
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit().putBoolean(KEY_NEW_CRASH, false).apply()
        }

        @JvmStatic
        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashLogger(context))
        }

        @JvmStatic
        fun saveCaughtException(context: Context, thread: Thread, ex: Throwable) {
            val dir = getCrashDir(context)
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "crash_$timestamp.txt")

            try {
                BufferedWriter(FileWriter(file)).use { w ->
                    w.write(CrashLogger(context).buildReport(thread, ex))
                    w.flush()
                }
            } catch (_: Exception) {}
        }

        @JvmStatic
        fun getCrashDir(context: Context): File {
            val appCtx = context.applicationContext
            val externalDir = appCtx.getExternalFilesDir(null)
            return if (externalDir != null) {
                File(externalDir, CRASH_DIR)
            } else {
                File(appCtx.filesDir, CRASH_DIR)
            }
        }

        @JvmStatic
        fun getCrashFiles(context: Context): Array<File> {
            val dir = getCrashDir(context)
            if (!dir.exists()) return emptyArray()
            val files = dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
                ?: return emptyArray()
            if (files.isEmpty()) return emptyArray()
            Arrays.sort(files) { a, b -> java.lang.Long.compare(b.lastModified(), a.lastModified()) }
            return files
        }

        @JvmStatic
        fun getCrashCount(context: Context): Int = getCrashFiles(context).size

        @JvmStatic
        fun deleteCrashFile(file: File?): Boolean = file != null && file.exists() && file.delete()

        @JvmStatic
        fun clearAll(context: Context) {
            for (f in getCrashFiles(context)) f.delete()
        }

        @JvmStatic
        fun e(tag: String, message: String, ex: Throwable) {
            Log.e(tag, message, ex)
        }
    }
}
