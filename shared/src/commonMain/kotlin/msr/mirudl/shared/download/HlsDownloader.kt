package msr.mirudl.shared.download

import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import msr.mirudl.shared.model.DownloadRecord
import msr.mirudl.shared.network.HlsParser
import msr.mirudl.shared.network.HttpClientProvider
import msr.mirudl.shared.storage.AppStorage
import msr.mirudl.shared.storage.DownloadEntryStore
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared download engine — port of `app`'s `HlsDownloader.java`.
 *
 * The full `download()` orchestration: playlist parsing, parallel segment
 * download, final stitching into the SAF output file, and download-record
 * persistence via [DownloadEntryStore].
 */
object HlsDownloader {

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_PARALLEL = 64

    interface ProgressListener {
        fun onProgress(percent: Int, downloaded: Int, total: Int)
        fun onSpeed(bytesPerSecond: Long) {}
    }

    interface CancelCheck {
        fun isCancelled(): Boolean
    }

    // ==================== FULL DOWNLOAD ====================

    /**
     * Downloads an HLS stream: parses the playlist, downloads segments
     * in parallel to a temp dir, stitches them into an MP4 output file
     * via [storage] SAF/local paths, and persists a [DownloadRecord].
     *
     * @param playlistUrl      master or variant playlist URL
     * @param fileName         display name (e.g. "Anime Title - Episode 5")
     * @param parallelSegments max concurrent segment downloads
     * @param downloadTreeUri  SAF tree URI for the download folder (null → error)
     * @param storage          platform file storage
     * @param entryStorage     AppStorage for persisting download records
     * @param progress         optional progress/speed callbacks
     * @param cancel           checked between segments and after download
     * @return the output file URI string
     */
    suspend fun download(
        playlistUrl: String,
        fileName: String,
        parallelSegments: Int,
        downloadTreeUri: String?,
        storage: FileStorage,
        entryStorage: AppStorage,
        progress: ProgressListener?,
        cancel: CancelCheck?
    ): String {
        // 1. Read master playlist
        var currentUrl = playlistUrl
        var master = HlsParser.getText(currentUrl)

        // 2. If master has variants, pick first quality
        if (master.contains("#EXT-X-STREAM-INF")) {
            val variants = HlsParser.qualities(currentUrl)
            if (variants.isNotEmpty()) {
                currentUrl = variants[0].url
                master = HlsParser.getText(currentUrl)
            }
        }

        // 3. Check for encryption
        if (master.contains("#EXT-X-KEY")) {
            throw IOException("Encrypted streams are not supported")
        }

        // 4. Parse segments
        val segments = HlsParser.parseSegments(master, currentUrl)
        if (segments.isEmpty()) {
            throw IOException("No HLS segments found")
        }

        // 5. Parse init map
        val mapUrl = HlsParser.parseInitMap(master, currentUrl)

        // 6. Create temp dir
        val tempDirName = "hls_${System.currentTimeMillis()}"
        val tempDir = storage.createDir(storage.cacheDir(), tempDirName)
            ?: throw IOException("Cannot create temp dir")

        // 7. Download segments
        downloadSegments(segments, storage, tempDir, HlsParser.clampParallel(parallelSegments), progress, cancel)

        // 8. Cancel check after download
        if (cancel != null && cancel.isCancelled()) {
            storage.deleteFile(tempDir)
            throw IOException("Download cancelled by user")
        }

        // 9. Create output file
        val cleanName = HlsParser.sanitize(fileName)
        val animeDir = HlsParser.extractParent(fileName)
        if (downloadTreeUri == null) {
            throw IOException("Select download folder in Settings")
        }
        val outFileUri = createOutputFile(storage, downloadTreeUri, animeDir, "$cleanName.mp4")

        // 10. Stitch segments into output
        val out = storage.openOutput(outFileUri)
            ?: throw IOException("Cannot open output stream")

        try {
            if (mapUrl != null) writeUrlToStream(mapUrl, out)
            for (i in segments.indices) {
                val segPath = "$tempDir/seg_$i.ts"
                val segInput = storage.openInput(segPath) ?: continue
                segInput.use { input ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        out.write(buf, 0, read)
                    }
                }
            }
        } finally {
            out.close()
        }

        // 11. Clean up temp dir
        storage.deleteFile(tempDir)

        // 12. Save download entry
        val size = storage.size(outFileUri)
        saveDownloadEntry(entryStorage, outFileUri, cleanName, animeDir, size)

        return outFileUri
    }

    // ==================== OUTPUT FILE CREATION ====================

    private fun createOutputFile(
        storage: FileStorage,
        treeUri: String,
        folderName: String,
        fileName: String
    ): String {
        // Create "MiruDL Downloads" root
        val mirudlRoot = storage.createDir(treeUri, "MiruDL Downloads")
            ?: throw IOException("Cannot create root folder")

        // Create anime subfolder
        val animeDir = storage.createDir(mirudlRoot, HlsParser.sanitize(folderName))
            ?: throw IOException("Cannot create anime folder")

        // Delete existing file with same name
        val existing = storage.findFile(animeDir, fileName)
        if (existing != null) storage.deleteFile(existing)

        // Create new file
        return storage.createFile(animeDir, "video/mp4", fileName)
            ?: throw IOException("Cannot create output file")
    }

    // ==================== WRITE URL TO STREAM ====================

    private suspend fun writeUrlToStream(url: String, out: OutputStream) {
        val data = downloadBytes(url)
        if (data.isNotEmpty()) {
            out.write(data)
        }
    }

    // ==================== DOWNLOAD RECORD ====================

    private fun saveDownloadEntry(
        entryStorage: AppStorage,
        uri: String,
        title: String,
        parent: String,
        size: Long
    ) {
        try {
            val entry = DownloadRecord(
                uri = uri,
                title = title,
                parent = parent,
                size = size,
                completedAt = System.currentTimeMillis()
            )
            DownloadEntryStore.add(entryStorage, entry)
        } catch (_: Exception) {}
    }

    // ==================== BYTE FETCH ====================

    /**
     * Downloads a single URL and returns the bytes.
     * Returns an empty array on failure (matching the Java original).
     */
    suspend fun downloadBytes(url: String): ByteArray {
        return try {
            val client = HttpClientProvider.get()
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.UserAgent, "Mozilla/5.0")
                }
            }
            if (response.status.value in 200..299) {
                val channel = response.bodyAsChannel()
                channel.readRemaining().readBytes()
            } else {
                ByteArray(0)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    // ==================== SEGMENT DOWNLOAD LOOP ====================

    /**
     * Downloads HLS segments in parallel, writing each to a temp file
     * under [tempDir] via [storage]. Speed is sampled every 700 ms with
     * exponential moving-average smoothing, exactly like the Java original.
     */
    suspend fun downloadSegments(
        urls: List<String>,
        storage: FileStorage,
        tempDir: String,
        parallel: Int,
        progress: ProgressListener?,
        cancel: CancelCheck?
    ) {
        val total = urls.size
        if (total == 0) return

        val clampedParallel = clampParallel(parallel)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val totalBytes = AtomicLong(0)

        val tickerScope = CoroutineScope(Dispatchers.Default)
        val tickerJob = tickerScope.launch {
            var lastSampleTime = System.currentTimeMillis()
            var lastBytes: Long = 0
            var smoothedSpeed: Double = -1.0
            while (isActive) {
                delay(700)
                val now = System.currentTimeMillis()
                val bytesNow = totalBytes.get()
                val elapsedMs = now - lastSampleTime
                val deltaBytes = bytesNow - lastBytes
                lastSampleTime = now
                lastBytes = bytesNow
                if (elapsedMs > 0 && progress != null) {
                    val instant = (deltaBytes * 1000.0) / elapsedMs
                    smoothedSpeed = if (smoothedSpeed < 0) instant
                    else 0.35 * instant + 0.65 * smoothedSpeed
                    progress.onSpeed(smoothedSpeed.toLong())
                }
            }
        }

        try {
            coroutineScope {
                val semaphore = Semaphore(clampedParallel)
                for ((idx, url) in urls.withIndex()) {
                    if (cancel?.isCancelled() == true) break
                    launch(Dispatchers.Default) {
                        semaphore.withPermit {
                            if (cancel?.isCancelled() != true) {
                                try {
                                    val data = downloadBytes(url)
                                    totalBytes.addAndGet(data.size.toLong())
                                    val segPath = "$tempDir/seg_$idx.ts"
                                    storage.openOutput(segPath)?.use { out: OutputStream ->
                                        out.write(data)
                                    }
                                } catch (_: Exception) {}
                            }
                            val current = done.incrementAndGet()
                            progress?.onProgress(
                                current * 100 / total, current, total
                            )
                        }
                    }
                }
            }
        } finally {
            tickerJob.cancelAndJoin()
            progress?.onSpeed(0)
        }
    }

    // ==================== HELPERS ====================

    fun clampParallel(value: Int): Int {
        if (value < 1) return 1
        return minOf(value, MAX_PARALLEL)
    }
}
