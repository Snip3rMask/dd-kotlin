package msr.mirudl.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class StorageSettings {
    private static final String PREF = "mirudl_settings";
    private static final String KEY_DOWNLOAD_URI = "download_uri";
    private static final String KEY_PARALLEL = "parallel_segments";
    private static final String KEY_QUALITY = "preferred_quality";
    private static final String KEY_LANGUAGE = "preferred_language";

    private StorageSettings() {}

    public static Uri getDownloadUri(Context context) {
        String s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_DOWNLOAD_URI, null);
        return s != null ? Uri.parse(s) : null;
    }

    public static void setDownloadUri(Context context, Uri uri) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_DOWNLOAD_URI, uri.toString()).apply();
    }

    public static int getParallelSegments(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(KEY_PARALLEL, HlsDownloader.DEFAULT_PARALLEL);
    }

    public static void setParallelSegments(Context context, int value) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(KEY_PARALLEL, value).apply();
    }

    private static final String KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads";

    /** How many episodes may download at the same time. Default is 1 (one at a time). */
    public static int getConcurrentDownloads(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(KEY_CONCURRENT_DOWNLOADS, 1);
    }

    public static void setConcurrentDownloads(Context context, int value) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putInt(KEY_CONCURRENT_DOWNLOADS, value).apply();
    }

    public static String getPreferredQuality(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_QUALITY, "1080p");
    }

    public static void setPreferredQuality(Context context, String quality) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_QUALITY, quality).apply();
    }

    public static String getPreferredLanguage(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "jpn");
    }

    public static void setPreferredLanguage(Context context, String lang) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, lang).apply();
    }

    private static final String KEY_DARK_THEME = "dark_theme";

    public static boolean isDarkTheme(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK_THEME, true);
    }

    public static void setDarkTheme(Context context, boolean dark) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK_THEME, dark).apply();
    }

    public static boolean hasDownloadUri(Context context) {
        return getDownloadUri(context) != null;
    }
}
