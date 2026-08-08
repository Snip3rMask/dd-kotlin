package msr.mirudl.desktop

import kotlinx.coroutines.*
import msr.mirudl.shared.download.DownloadManager
import msr.mirudl.shared.download.HlsDownloader
import msr.mirudl.shared.download.Job

/**
 * Desktop download service — runs downloads in background coroutines.
 *
 * Replaces Android's DownloadService (which uses Android Service + Intent).
 * On desktop, we just use coroutineScope + launch for background work.
 */
object DesktopDownloadService {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopRunning = false

    /**
     * Start the download processing loop.
     * Safe to call multiple times — only one loop runs.
     */
    fun startLoop() {
        if (loopRunning) return
        loopRunning = true
        scope.launch {
            while (isActive) {
                val job = DownloadManager.claimNextQueuedJob()
                if (job != null) {
                    DownloadManager.setRunning(true)
                    downloadJob(job)
                    DownloadManager.setRunning(false)
                }
                delay(500)
            }
        }
    }

    /**
     * Start a new download.
     */
    fun startDownload(
        playlistUrl: String,
        fileName: String,
        parallelSegments: Int,
        outputDir: String
    ) {
        val job = Job(
            animeTitle = fileName.substringBeforeLast(" - ").ifEmpty { fileName },
            episodeTitle = fileName.substringAfterLast(" - ").ifEmpty { null },
            hlsUrl = playlistUrl
        )
        DownloadManager.add(job)
        startLoop()

        scope.launch {
            try {
                val outputUri = HlsDownloader.download(
                    playlistUrl = playlistUrl,
                    fileName = fileName,
                    parallelSegments = parallelSegments,
                    downloadTreeUri = outputDir,
                    storage = msr.mirudl.shared.download.DesktopFileStorage,
                    entryStorage = msr.mirudl.shared.storage.DesktopAppStorage("mirudl_downloads"),
                    progress = object : HlsDownloader.ProgressListener {
                        override fun onProgress(percent: Int, downloaded: Int, total: Int) {
                            job.percent = percent
                        }
                        override fun onSpeed(bytesPerSecond: Long) {
                            job.bytesPerSecond = bytesPerSecond
                        }
                    },
                    cancel = object : HlsDownloader.CancelCheck {
                        override fun isCancelled(): Boolean = job.cancelled
                    }
                )
                DownloadManager.complete(job, outputUri)
            } catch (e: Exception) {
                DownloadManager.fail(job, e.message ?: "Download failed")
            }
        }
    }

    /**
     * Cancel an active download.
     */
    fun cancelDownload(jobId: String) {
        val job = DownloadManager.find(jobId) ?: return
        DownloadManager.cancel(job)
    }

    /**
     * Get a snapshot of all jobs.
     */
    fun snapshot(): List<Job> = DownloadManager.snapshot()

    /**
     * Shutdown the service.
     */
    fun shutdown() {
        loopRunning = false
        scope.cancel()
    }
}
