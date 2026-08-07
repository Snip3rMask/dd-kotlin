package msr.mirudl.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import msr.mirudl.shared.download.AndroidFileStorage
import msr.mirudl.shared.download.DownloadManager
import msr.mirudl.shared.download.HlsDownloader
import msr.mirudl.shared.download.Job
import msr.mirudl.shared.network.MiruClient
import msr.mirudl.shared.storage.AndroidAppStorage
import msr.mirudl.shared.storage.AppStorage
import msr.mirudl.shared.storage.StorageSettings
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private val workerActiveCount = AtomicInteger(0)
    private val completedCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val cancelledCount = AtomicInteger(0)

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun settingsStorage(): AppStorage =
        AndroidAppStorage(applicationContext, "mirudl_settings")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (ACTION_CANCEL == action) {
            handleCancel(intent)
            return START_NOT_STICKY
        }

        if (ACTION_START != action) return START_NOT_STICKY

        val activeJobs = DownloadManager.activeCount()
        if (activeJobs == 0) return START_NOT_STICKY

        if (workerActiveCount.get() == 0) {
            completedCount.set(0)
            failedCount.set(0)
            cancelledCount.set(0)
            DownloadManager.setRunning(true)
            startForeground(
                NOTIF_ID,
                buildNotif(null, "$activeJobs downloads queued", 0, false)
            )
        }

        launchWorkers()
        return START_NOT_STICKY
    }

    /** Spawns worker lanes up to the user's concurrent-downloads limit. */
    private fun launchWorkers() {
        val limit = maxOf(
            1,
            minOf(MAX_LANES, StorageSettings.getConcurrentDownloads(settingsStorage()))
        )
        synchronized(lanesLock) {
            while (workerActiveCount.get() < limit) {
                val job = DownloadManager.claimNextQueuedJob() ?: break
                workerActiveCount.incrementAndGet()
                serviceScope.launch { workerLoop(job) }
            }
        }
    }

    private suspend fun workerLoop(firstJob: Job) {
        var job: Job? = firstJob
        try {
            while (job != null) {
                runSingleJob(job)
                job = DownloadManager.claimNextQueuedJob()
            }
        } finally {
            val remaining = workerActiveCount.decrementAndGet()
            if (DownloadManager.activeCount() == 0) {
                finishBatchIfIdle()
            } else if (remaining == 0) {
                launchWorkers()
            }
        }
    }

    /** Downloads a single claimed job, updating shared counters and the notification. */
    private suspend fun runSingleJob(job: Job) {
        try {
            pushNotif(
                buildNotif(job, activeSummaryPrefix() + job.episodeTitle + " - Starting...", 0, false)
            )

            var playUrl = job.hlsUrl
            if (playUrl != null && playUrl.contains("/embed/")) {
                playUrl = MiruClient.resolveHlsFromEmbed(playUrl)
            }

            var lastNotifTime = 0L
            val output = withContext(Dispatchers.IO) {
                HlsDownloader.download(
                    playlistUrl = playUrl ?: throw IllegalStateException("No HLS URL available"),
                    fileName = "${job.animeTitle} - ${job.episodeTitle}",
                    parallelSegments = StorageSettings.getParallelSegments(settingsStorage()),
                    downloadTreeUri = StorageSettings.getDownloadUri(settingsStorage()),
                    storage = AndroidFileStorage,
                    entryStorage = AndroidAppStorage(applicationContext, "mirudl_downloads"),
                    progress = object : HlsDownloader.ProgressListener {
                        override fun onProgress(percent: Int, downloaded: Int, total: Int) {
                            try {
                                DownloadManager.update(job, percent, Job.STATUS_DOWNLOADING)
                                job.percent = percent
                                val now = System.currentTimeMillis()
                                if (now - lastNotifTime >= 500) {
                                    lastNotifTime = now
                                    val text = activeSummaryPrefix() + job.episodeTitle + " - $percent%"
                                    pushNotif(buildNotif(job, text, percent, false))
                                }
                            } catch (_: Exception) {}
                        }

                        override fun onSpeed(bytesPerSecond: Long) {
                            job.bytesPerSecond = bytesPerSecond
                        }
                    },
                    cancel = object : HlsDownloader.CancelCheck { override fun isCancelled() = job.cancelled }
                )
            }

            if (job.cancelled) {
                cancelledCount.incrementAndGet()
                pushNotif(buildNotif(job, "\u2716 ${job.episodeTitle} Cancelled", 0, true))
            } else {
                DownloadManager.complete(job, output)
                completedCount.incrementAndGet()
                pushNotif(buildNotif(job, "\u2713 ${job.episodeTitle} Done", 100, true))
            }
        } catch (e: Exception) {
            try {
                val msg = e.message ?: "Failed"
                if (msg.contains("cancelled by user") || msg.contains("Cancelled") || job.cancelled) {
                    DownloadManager.cancel(job)
                    cancelledCount.incrementAndGet()
                } else {
                    DownloadManager.fail(job, msg)
                    failedCount.incrementAndGet()
                }
                pushNotif(buildNotif(job, "\u2716 ${job.episodeTitle} $msg", 0, true))
            } catch (_: Exception) {}
        }
    }

    /** Called whenever a lane finishes; wraps up the batch once nothing is left running. */
    private fun finishBatchIfIdle() {
        synchronized(lanesLock) {
            if (workerActiveCount.get() != 0 || DownloadManager.activeCount() != 0) return

            DownloadManager.setRunning(false)
            val completed = completedCount.get()
            val failed = failedCount.get()
            val cancelled = cancelledCount.get()

            val summary = buildString {
                append("\u2713 $completed done")
                if (failed > 0) append(", \u2716 $failed failed")
                if (cancelled > 0) append(", cancelled $cancelled")
            }
            if (completed + failed + cancelled > 0) {
                pushNotif(buildNotif(null, summary, 100, true))
            }

            mainHandler.postDelayed({
                try {
                    val mgr = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.cancel(NOTIF_ID)
                } catch (_: Exception) {}
            }, 5000)

            try {
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
            } catch (_: Exception) {}
        }
    }

    /** Shows how many downloads are running at once, e.g. "(3 active) ". */
    private fun activeSummaryPrefix(): String {
        val active = DownloadManager.downloadingCount()
        return if (active > 1) "($active active) " else ""
    }

    private fun handleCancel(intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID)
        if ("all" == jobId) {
            for (j in DownloadManager.snapshot()) {
                if (!j.finished) {
                    DownloadManager.cancel(j)
                }
            }
            pushNotif(buildNotif(null, "Downloads cancelled", 0, true))
            if (DownloadManager.activeCount() == 0) {
                try { @Suppress("DEPRECATION") stopForeground(true) } catch (_: Exception) {}
                try { stopSelf() } catch (_: Exception) {}
            }
        } else {
            val job = DownloadManager.find(jobId ?: return)
            if (job != null) {
                DownloadManager.cancel(job)
                pushNotif(buildNotif(job, "Cancelled", 0, true))
                if (DownloadManager.activeCount() == 0) {
                    try { @Suppress("DEPRECATION") stopForeground(true) } catch (_: Exception) {}
                    try { stopSelf() } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        DownloadManager.setRunning(false)
        super.onDestroy()
    }

    // ==================== Notifications ====================

    private fun pushNotif(notif: Notification) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        mgr?.notify(NOTIF_ID, notif)
    }

    private fun buildNotif(job: Job?, text: String, progress: Int, done: Boolean): Notification {
        var title = "MiruDL"
        var subText = ""
        if (job != null && job.animeTitle != null) {
            title = job.animeTitle!!
            if (job.episodeTitle != null) {
                subText = job.episodeTitle!!
            }
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val b = NotificationCompat.Builder(this, MiruDLApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(
                if (done) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            )
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)

        if (!done) {
            b.setProgress(100, progress.coerceIn(0, 100), false)

            val cancelIntent = Intent(this, CancelDownloadReceiver::class.java).apply {
                putExtra("jobId", "all")
            }
            val cancelPi = PendingIntent.getBroadcast(
                this, 1002, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel All", cancelPi)
        } else {
            b.setProgress(0, 0, false)
        }

        return b.build()
    }

    private fun buildNotif(job: Job?): Notification {
        if (job == null) return buildNotif(null, "Starting...", 0, false)
        val text = if (job.finished) {
            job.status ?: "Done"
        } else {
            "${job.episodeTitle} - ${job.percent}%"
        }
        return buildNotif(job, text, job.percent, job.finished)
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_CANCEL = "cancel"
        private const val EXTRA_JOB_ID = "jobId"
        private const val NOTIF_ID = 1001
        private const val MAX_LANES = 10

        private val lanesLock = Any()

        @JvmStatic
        fun startIntent(context: Context): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
            }
        }

        @JvmStatic
        fun cancelIntent(context: Context, jobId: String): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_JOB_ID, jobId)
            }
        }
    }
}
