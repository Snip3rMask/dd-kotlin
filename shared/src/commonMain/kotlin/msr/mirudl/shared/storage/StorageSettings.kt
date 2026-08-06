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
}
