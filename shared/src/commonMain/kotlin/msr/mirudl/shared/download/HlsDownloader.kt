package msr.mirudl.shared.download

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import msr.mirudl.shared.network.HttpClientProvider
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared download engine — port of the segment-download loop and byte
 * fetch from `app`'s `HlsDownloader.java`.
 *
 * This step (4.5) ports only the parallel download loop and byte-fetch
 * helper. Playlist parsing lives in [msr.mirudl.shared.network.HlsParser]
 * (already ported in an earlier step). Final assembly and the top-level
 * `download()` method arrive in 4.6.
 *
 * Concurrency is handled via Kotlin coroutines + [Semaphore] instead of
 * the Java `ExecutorService`, and the speed sampler runs as a coroutine
 * with `delay()` instead of `ScheduledExecutorService`.
 */
object HlsDownloader {

    private const val MAX_PARALLEL = 64

    interface ProgressListener {
        fun onProgress(percent: Int, downloaded: Int, total: Int)
        fun onSpeed(bytesPerSecond: Long) {}
    }

    interface CancelCheck {
        fun isCancelled(): Boolean
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
                response.body<ByteArray>()
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
     *
     * @param storage    platform file storage (cache dir, file I/O)
     * @param tempDir    absolute path of the caller-created temp directory
     * @param parallel   max concurrent downloads (clamped to [MAX_PARALLEL])
     * @param progress   optional progress/speed callbacks
     * @param cancel     checked between segments and during the wait loop
     */
    suspend fun downloadSegments(
        urls: List<String>,
        storage: FileStorage,
        tempDir: String,
        parallel: Int,
        progress: ProgressListener?,
        cancel: CancelCheck
    ) {
        val total = urls.size
        if (total == 0) return

        val clampedParallel = clampParallel(parallel)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val totalBytes = AtomicLong(0)

        // Speed ticker runs in its own scope so coroutineScope below
        // (which waits only for download jobs) doesn't deadlock on it.
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
                    if (cancel.isCancelled()) break
                    launch(Dispatchers.Default) {
                        semaphore.withPermit {
                            if (!cancel.isCancelled()) {
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
