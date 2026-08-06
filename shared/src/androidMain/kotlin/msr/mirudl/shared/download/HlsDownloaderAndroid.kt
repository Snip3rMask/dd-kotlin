package msr.mirudl.shared.download

import android.content.Context
import kotlinx.coroutines.runBlocking
import msr.mirudl.shared.storage.AndroidAppStorage
import msr.mirudl.shared.storage.StorageSettingsAndroid

/**
 * Synchronous JVM bridge for the shared [HlsDownloader] — wraps the
 * suspend `download()` with [runBlocking] so existing Java callers in
 * `app` work unchanged.
 *
 * Delete once DownloadService converts to Kotlin (Phase 4.7).
 */
object HlsDownloaderAndroid {

    @JvmStatic
    fun download(
        context: Context,
        playlistUrl: String,
        fileName: String,
        parallelSegments: Int,
        progress: HlsDownloader.ProgressListener?,
        cancel: HlsDownloader.CancelCheck?
    ): String? {
        AndroidFileStorage.init(context)
        val downloadUri = StorageSettingsAndroid.getDownloadUri(context)?.toString()
        val entryStorage = AndroidAppStorage(context, "mirudl_downloads")
        return runBlocking {
            HlsDownloader.download(
                playlistUrl = playlistUrl,
                fileName = fileName,
                parallelSegments = parallelSegments,
                downloadTreeUri = downloadUri,
                storage = AndroidFileStorage,
                entryStorage = entryStorage,
                progress = progress,
                cancel = cancel
            )
        }
    }
}
