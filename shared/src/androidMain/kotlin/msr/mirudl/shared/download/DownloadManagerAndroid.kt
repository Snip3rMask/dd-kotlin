package msr.mirudl.shared.download

/**
 * Synchronous JVM bridge for the shared [DownloadManager] — provides
 * `@JvmStatic` methods so existing Java callers in `app` work unchanged.
 *
 * The shared object uses `@Synchronized` methods (no coroutines needed),
 * so these are plain delegates. Delete once all callers convert to Kotlin
 * (Phase 5).
 */
object DownloadManagerAndroid {

    @JvmStatic
    fun findByAnimeAndEpisode(anime: String?, episode: String?): Job? =
        DownloadManager.findByAnimeAndEpisode(anime, episode)

    @JvmStatic
    fun hasActiveJob(anime: String?, episode: String?): Boolean =
        DownloadManager.hasActiveJob(anime, episode)

    @JvmStatic
    fun find(id: String): Job? = DownloadManager.find(id)

    @JvmStatic
    fun enqueue(anime: String?, episode: String?, quality: String?,
                language: String?, hlsUrl: String?): Job =
        DownloadManager.enqueue(anime, episode, quality, language, hlsUrl)

    @JvmStatic
    fun enqueueAll(jobs: List<Job>) = DownloadManager.enqueueAll(jobs)

    @JvmStatic
    fun update(job: Job?, percent: Int, status: String) =
        DownloadManager.update(job, percent, status)

    @JvmStatic
    fun updateMulti(job: Job?, currentIndex: Int, episode: String,
                    percent: Int, status: String) =
        DownloadManager.updateMulti(job, currentIndex, episode, percent, status)

    @JvmStatic
    fun complete(job: Job?, outputUri: String) =
        DownloadManager.complete(job, outputUri)

    @JvmStatic
    fun fail(job: Job?, error: String?) = DownloadManager.fail(job, error)

    @JvmStatic
    fun cancel(job: Job?) = DownloadManager.cancel(job)

    @JvmStatic
    fun remove(job: Job?) = DownloadManager.remove(job)

    @JvmStatic
    fun removeFinished() = DownloadManager.removeFinished()

    @JvmStatic
    fun snapshot(): List<Job> = DownloadManager.snapshot()

    @JvmStatic
    fun activeCount(): Int = DownloadManager.activeCount()

    @JvmStatic
    fun queuedCount(): Int = DownloadManager.queuedCount()

    @JvmStatic
    fun downloadingCount(): Int = DownloadManager.downloadingCount()

    @JvmStatic
    fun claimNextQueuedJob(): Job? = DownloadManager.claimNextQueuedJob()

    @JvmStatic
    fun isRunning(): Boolean = DownloadManager.isRunning()

    @JvmStatic
    fun setRunning(value: Boolean) = DownloadManager.setRunning(value)
}
