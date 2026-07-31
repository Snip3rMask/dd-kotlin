package msr.mirudl.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrashLogger implements Thread.UncaughtExceptionHandler {

    private static final String CRASH_DIR = "crash_logs";
    private static final int MAX_CRASH_FILES = 20;
    private static final long MAX_FILE_AGE_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final String PREFS_NAME = "mirudl_crash_logger";
    private static final String KEY_NEW_CRASH = "has_new_crash";

    // Live state tracking
    private static String lastScreen = "";
    private static String lastUserAction = "";

    /** Call from any Activity to record current screen */
    public static void updateScreen(String screen) { lastScreen = screen; }

    /** Call from any Activity to record user action */
    public static void updateAction(String action) { lastUserAction = action; }

    /** Check if there are new crashes since last viewed */
    public static boolean hasNewCrash(Context context) {
        return context.getSharedPreferences(PREFS_NAME, 0).getBoolean(KEY_NEW_CRASH, false);
    }

    /** Mark crashes as viewed (call when crash logs screen is opened) */
    public static void markViewed(Context context) {
        context.getSharedPreferences(PREFS_NAME, 0).edit().putBoolean(KEY_NEW_CRASH, false).apply();
    }

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public CrashLogger(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /** Call this in Application.onCreate() */
    public static void init(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashLogger(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            saveCrashLogSync(thread, ex);
        } catch (Exception ignored) {}
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        }
    }

    private void saveCrashLogSync(Thread thread, Throwable ex) {
        File dir = getCrashDir();
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                .format(new Date());
        String fileName = "crash_" + timestamp + ".txt";
        File file = new File(dir, fileName);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(buildReport(thread, ex));
            w.flush();
        } catch (Exception ignored) {}

        markNewCrash();
        cleanupOldLogs(dir);
    }

    /** Save a caught exception manually (from try/catch blocks) */
    public static void saveCaughtException(Context context, Thread thread, Throwable ex) {
        File dir = getCrashDir(context);
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                .format(new Date());
        String fileName = "crash_" + timestamp + ".txt";
        File file = new File(dir, fileName);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(new CrashLogger(context).buildReport(thread, ex));
            w.flush();
        } catch (Exception ignored) {}
    }

    private String buildReport(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        String line = "========================================";
        sb.append(line).append("\n");
        sb.append("  MIRUDL CRASH REPORT\n");
        sb.append(line).append("\n\n");

        // App info
        sb.append("--- APP ---\n");
        sb.append("Version    : ").append(getAppVersion()).append("\n\n");

        // Device info
        sb.append("--- DEVICE ---\n");
        sb.append("Brand      : ").append(Build.BRAND).append("\n");
        sb.append("Model      : ").append(Build.MODEL).append("\n");
        sb.append("Device     : ").append(Build.DEVICE).append("\n");
        sb.append("Product    : ").append(Build.PRODUCT).append("\n");
        sb.append("SDK        : ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Release    : ").append(Build.VERSION.RELEASE).append("\n\n");

        // Build info
        sb.append("--- BUILD ---\n");
        sb.append("Type       : ").append(Build.TYPE).append("\n");
        sb.append("Tags       : ").append(Build.TAGS).append("\n");
        sb.append("Timestamp  : ").append(Build.TIME).append("\n");
        sb.append("Fingerprint: ").append(Build.FINGERPRINT).append("\n\n");

        // Time & locale
        sb.append("--- ENVIRONMENT ---\n");
        sb.append("Time       : ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
                .format(new Date())).append("\n");
        sb.append("Locale     : ").append(Locale.getDefault()).append("\n\n");

        // Memory info
        sb.append("--- MEMORY ---\n");
        Runtime rt = Runtime.getRuntime();
        sb.append("Max        : ").append(rt.maxMemory() / 1048576).append(" MB\n");
        sb.append("Total      : ").append(rt.totalMemory() / 1048576).append(" MB\n");
        sb.append("Free       : ").append(rt.freeMemory() / 1048576).append(" MB\n");
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long avail = (long) stat.getAvailableBlocks() * stat.getBlockSize();
            sb.append("Disk Free  : ").append(avail / 1048576).append(" MB\n");
        } catch (Exception ignored) {}
        sb.append("\n");

        // Thread info
        sb.append("--- THREAD ---\n");
        sb.append("Name       : ").append(thread.getName()).append("\n");
        sb.append("Priority   : ").append(thread.getPriority()).append("\n");
        sb.append("Daemon     : ").append(thread.isDaemon()).append("\n\n");

        // Screen state
        sb.append("--- SCREEN ---\n");
        sb.append("Current    : ").append(lastScreen).append("\n");
        sb.append("Last action: ").append(lastUserAction).append("\n\n");

        // Exception
        sb.append("--- EXCEPTION ---\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        pw.flush();
        sb.append(sw).append("\n");

        // Cause chain
        Throwable cause = ex.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            sb.append("Caused by  : ").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage()).append("\n");
            cause = cause.getCause();
            depth++;
        }
        sb.append("\n");

        sb.append(line).append("\n");
        sb.append("  End of report\n");
        sb.append(line).append("\n");
        return sb.toString();
    }

    private void markNewCrash() {
        context.getSharedPreferences(PREFS_NAME, 0).edit().putBoolean(KEY_NEW_CRASH, true).apply();
    }

    /** Returns the crash log directory */
    public static File getCrashDir(Context context) {
        Context appCtx = context.getApplicationContext();
        File externalDir = appCtx.getExternalFilesDir(null);
        if (externalDir != null) {
            return new File(externalDir, CRASH_DIR);
        }
        return new File(appCtx.getFilesDir(), CRASH_DIR);
    }

    private File getCrashDir() {
        return getCrashDir(context);
    }

    /** Returns all crash log files sorted newest-first */
    public static File[] getCrashFiles(Context context) {
        File dir = getCrashDir(context);
        if (!dir.exists()) return new File[0];
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /** Returns the crash file count */
    public static int getCrashCount(Context context) {
        return getCrashFiles(context).length;
    }

    /** Deletes a single crash log file */
    public static boolean deleteCrashFile(File file) {
        return file != null && file.exists() && file.delete();
    }

    /** Deletes all crash log files */
    public static void clearAll(Context context) {
        File[] files = getCrashFiles(context);
        for (File f : files) f.delete();
    }

    /** Log to Logcat */
    public static void e(String tag, String message, Throwable ex) {
        Log.e(tag, message, ex);
    }

    private void cleanupOldLogs(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        if (files == null) return;

        long now = System.currentTimeMillis();
        for (File f : files) {
            if (now - f.lastModified() > MAX_FILE_AGE_MS) f.delete();
        }

        files = dir.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".txt"));
        if (files != null && files.length > MAX_CRASH_FILES) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - MAX_CRASH_FILES; i++) {
                files[i].delete();
            }
        }
    }

    private String getAppVersion() {
        String version = "Unknown";
        try {
            version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        return version;
    }
}
