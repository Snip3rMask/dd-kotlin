package msr.mirudl.shared.storage

/**
 * Platform-agnostic storage settings backed by [AppStorage].
 * URI is kept as String here; Android callers wrap it in `android.net.Uri`.
 */
object StorageSettings {

    private const val KEY_DOWNLOAD_URI = "download_uri"

    fun getDownloadUri(storage: AppStorage): String? {
        val s = storage.getString(KEY_DOWNLOAD_URI, "")
        return s.ifEmpty { null }
    }

    fun setDownloadUri(storage: AppStorage, uri: String) {
        storage.setString(KEY_DOWNLOAD_URI, uri)
    }

    private const val KEY_PARALLEL = "parallel_segments"
    private const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
    const val DEFAULT_PARALLEL = 16

    fun getParallelSegments(storage: AppStorage): Int {
        return storage.getInt(KEY_PARALLEL, DEFAULT_PARALLEL)
    }

    fun setParallelSegments(storage: AppStorage, value: Int) {
        storage.setInt(KEY_PARALLEL, value)
    }

    fun getConcurrentDownloads(storage: AppStorage): Int {
        return storage.getInt(KEY_CONCURRENT_DOWNLOADS, 1)
    }

    fun setConcurrentDownloads(storage: AppStorage, value: Int) {
        storage.setInt(KEY_CONCURRENT_DOWNLOADS, value)
    }

    private const val KEY_QUALITY = "preferred_quality"
    private const val KEY_LANGUAGE = "preferred_language"
    private const val KEY_DARK_THEME = "dark_theme"

    fun getPreferredQuality(storage: AppStorage): String {
        return storage.getString(KEY_QUALITY, "1080p")
    }

    fun setPreferredQuality(storage: AppStorage, quality: String) {
        storage.setString(KEY_QUALITY, quality)
    }

    fun getPreferredLanguage(storage: AppStorage): String {
        return storage.getString(KEY_LANGUAGE, "jpn")
    }

    fun setPreferredLanguage(storage: AppStorage, lang: String) {
        storage.setString(KEY_LANGUAGE, lang)
    }

    fun isDarkTheme(storage: AppStorage): Boolean {
        return storage.getBoolean(KEY_DARK_THEME, true)
    }

    fun setDarkTheme(storage: AppStorage, dark: Boolean) {
        storage.setBoolean(KEY_DARK_THEME, dark)
    }

    fun hasDownloadUri(storage: AppStorage): Boolean {
        return getDownloadUri(storage) != null
    }
}
