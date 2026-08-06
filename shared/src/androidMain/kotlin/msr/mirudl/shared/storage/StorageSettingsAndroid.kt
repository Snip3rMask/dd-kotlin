package msr.mirudl.shared.storage

import android.content.Context
import android.net.Uri

object StorageSettingsAndroid {

    private fun storage(context: Context): AppStorage {
        return AndroidAppStorage(context, "mirudl_settings")
    }

    @JvmStatic
    fun getDownloadUri(context: Context): Uri? {
        val s = StorageSettings.getDownloadUri(storage(context))
        return s?.let { Uri.parse(it) }
    }

    @JvmStatic
    fun setDownloadUri(context: Context, uri: Uri) {
        StorageSettings.setDownloadUri(storage(context), uri.toString())
    }

    @JvmStatic
    fun getParallelSegments(context: Context): Int {
        return StorageSettings.getParallelSegments(storage(context))
    }

    @JvmStatic
    fun setParallelSegments(context: Context, value: Int) {
        StorageSettings.setParallelSegments(storage(context), value)
    }

    @JvmStatic
    fun getConcurrentDownloads(context: Context): Int {
        return StorageSettings.getConcurrentDownloads(storage(context))
    }

    @JvmStatic
    fun setConcurrentDownloads(context: Context, value: Int) {
        StorageSettings.setConcurrentDownloads(storage(context), value)
    }

    @JvmStatic
    fun getPreferredQuality(context: Context): String {
        return StorageSettings.getPreferredQuality(storage(context))
    }

    @JvmStatic
    fun setPreferredQuality(context: Context, quality: String) {
        StorageSettings.setPreferredQuality(storage(context), quality)
    }

    @JvmStatic
    fun getPreferredLanguage(context: Context): String {
        return StorageSettings.getPreferredLanguage(storage(context))
    }

    @JvmStatic
    fun setPreferredLanguage(context: Context, lang: String) {
        StorageSettings.setPreferredLanguage(storage(context), lang)
    }

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean {
        return StorageSettings.isDarkTheme(storage(context))
    }

    @JvmStatic
    fun setDarkTheme(context: Context, dark: Boolean) {
        StorageSettings.setDarkTheme(storage(context), dark)
    }

    @JvmStatic
    fun hasDownloadUri(context: Context): Boolean {
        return StorageSettings.hasDownloadUri(storage(context))
    }
}
