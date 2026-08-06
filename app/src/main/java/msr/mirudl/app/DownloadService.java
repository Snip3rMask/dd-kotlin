package msr.mirudl.app;

import msr.mirudl.shared.storage.StorageSettingsAndroid;
import msr.mirudl.shared.download.DownloadManagerAndroid;
import msr.mirudl.shared.download.HlsDownloader;
import msr.mirudl.shared.download.HlsDownloaderAndroid;
import msr.mirudl.shared.download.Job;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.app.NotificationManager;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import msr.mirudl.shared.network.MiruClientAndroid;

public class DownloadService extends Service {
    private static final String ACTION_START = "start";
    private static final String ACTION_CANCEL = "cancel";
    private static final String EXTRA_JOB_ID = "jobId";
    private static final int NOTIF_ID = 1001;
    private static final int MAX_LANES = 10; // hard safety ceiling regardless of user setting

    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Object lanesLock = new Object();
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger cancelledCount = new AtomicInteger(0);

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public static Intent startIntent(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START);
        return intent;
    }

    public static Intent cancelIntent(Context context, String jobId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_CANCEL);
        intent.putExtra(EXTRA_JOB_ID, jobId);
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            handleCancel(intent);
            return START_NOT_STICKY;
        }

        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        int activeJobs = DownloadManagerAndroid.activeCount();
        if (activeJobs == 0) return START_NOT_STICKY;

        boolean freshBatch = activeWorkers.get() == 0;
        if (freshBatch) {
            completedCount.set(0);
            failedCount.set(0);
            cancelledCount.set(0);
            DownloadManagerAndroid.setRunning(true);
            startForeground(NOTIF_ID, buildNotif(null, activeJobs + " downloads queued", 0, false));
        }

        launchWorkers();
        return START_NOT_STICKY;
    }

    /** Spawns worker lanes up to the user's concurrent-downloads limit, each claiming and
     *  processing jobs one after another until the queue is empty. */
    private void launchWorkers() {
        int limit = Math.max(1, Math.min(MAX_LANES, StorageSettingsAndroid.getConcurrentDownloads(this)));
        synchronized (lanesLock) {
            while (activeWorkers.get() < limit) {
                Job job = DownloadManagerAndroid.claimNextQueuedJob();
                if (job == null) break; // nothing left to claim right now
                activeWorkers.incrementAndGet();
                worker.execute(() -> workerLoop(job));
            }
        }
    }

    private void workerLoop(Job firstJob) {
        Job job = firstJob;
        try {
            while (job != null) {
                runSingleJob(job);
                job = DownloadManagerAndroid.claimNextQueuedJob();
            }
        } finally {
            int remaining = activeWorkers.decrementAndGet();
            if (DownloadManagerAndroid.activeCount() == 0) {
                finishBatchIfIdle();
            } else if (remaining == 0) {
                // Jobs may have been queued while this was the last lane finishing up — relaunch.
                launchWorkers();
            }
        }
    }

    /** Downloads a single claimed job, updating shared counters and the notification. */
    private void runSingleJob(Job job) {
        try {
            pushNotif(buildNotif(job, activeSummaryPrefix() + job.episodeTitle + " - Starting...", 0, false));

            String playUrl = job.hlsUrl;
            if (playUrl != null && playUrl.contains("/embed/")) {
                playUrl = MiruClientAndroid.resolveHlsFromEmbed(playUrl);
            }

            final Job currentJob = job;
            final long[] lastNotifTime = {0};

            String output = HlsDownloaderAndroid.download(
                    this, playUrl,
                    job.animeTitle + " - " + job.episodeTitle,
                    StorageSettingsAndroid.getParallelSegments(this),
                    new HlsDownloader.ProgressListener() {
                        @Override
                        public void onProgress(int percent, int done, int total) {
                            try {
                                DownloadManagerAndroid.update(currentJob, percent, Job.STATUS_DOWNLOADING);
                                currentJob.percent = percent;
                                long now = System.currentTimeMillis();
                                if (now - lastNotifTime[0] >= 500) {
                                    lastNotifTime[0] = now;
                                    String text = activeSummaryPrefix() + currentJob.episodeTitle + " - " + percent + "%";
                                    pushNotif(buildNotif(currentJob, text, percent, false));
                                }
                            } catch (Exception ignored) {}
                        }

                        @Override
                        public void onSpeed(long bytesPerSecond) {
                            currentJob.bytesPerSecond = bytesPerSecond;
                        }
                    },
                    () -> currentJob.cancelled
            );

            if (job.cancelled) {
                cancelledCount.incrementAndGet();
                pushNotif(buildNotif(job, "\u2716 " + job.episodeTitle + " Cancelled", 0, true));
            } else {
                DownloadManagerAndroid.complete(job, output);
                completedCount.incrementAndGet();
                pushNotif(buildNotif(job, "\u2713 " + job.episodeTitle + " Done", 100, true));
            }
        } catch (Exception e) {
            try {
                String msg = e.getMessage() != null ? e.getMessage() : "Failed";
                if (msg.contains("cancelled by user") || msg.contains("Cancelled") || job.cancelled) {
                    DownloadManagerAndroid.cancel(job);
                    cancelledCount.incrementAndGet();
                } else {
                    DownloadManagerAndroid.fail(job, msg);
                    failedCount.incrementAndGet();
                }
                pushNotif(buildNotif(job, "\u2716 " + job.episodeTitle + " " + msg, 0, true));
            } catch (Exception ignored) {}
        }
    }

    /** Called whenever a lane finishes; only actually wraps up the batch once nothing is left running. */
    private void finishBatchIfIdle() {
        synchronized (lanesLock) {
            if (activeWorkers.get() != 0 || DownloadManagerAndroid.activeCount() != 0) return;

            DownloadManagerAndroid.setRunning(false);

            int completed = completedCount.get();
            int failed = failedCount.get();
            int cancelled = cancelledCount.get();

            String summary = "\u2713 " + completed + " done";
            if (failed > 0) summary += ", \u2716 " + failed + " failed";
            if (cancelled > 0) summary += ", cancelled " + cancelled;
            if (completed + failed + cancelled > 0) {
                pushNotif(buildNotif(null, summary, 100, true));
            }

            mainHandler.postDelayed(() -> {
                try {
                    NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (mgr != null) mgr.cancel(NOTIF_ID);
                } catch (Exception ignored) {}
            }, 5000);

            try {
                stopForeground(true);
                stopSelf();
            } catch (Exception ignored) {}
        }
    }

    /** Shows how many downloads are running at once, e.g. "(3 active) " — omitted when only one. */
    private String activeSummaryPrefix() {
        int active = DownloadManagerAndroid.downloadingCount();
        return active > 1 ? "(" + active + " active) " : "";
    }

    private void handleCancel(Intent intent) {
        String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        if ("all".equals(jobId)) {
            for (Job j : DownloadManagerAndroid.snapshot()) {
                if (!j.finished) {
                    DownloadManagerAndroid.cancel(j);
                }
            }
            pushNotif(buildNotif(null, "Downloads cancelled", 0, true));
            if (DownloadManagerAndroid.activeCount() == 0) {
                try { stopForeground(true); } catch (Exception ignored) {}
                try { stopSelf(); } catch (Exception ignored) {}
            }
        } else {
            Job job = DownloadManagerAndroid.find(jobId);
            if (job != null) {
                DownloadManagerAndroid.cancel(job);
                pushNotif(buildNotif(job, "Cancelled", 0, true));
                if (DownloadManagerAndroid.activeCount() == 0) {
                    try { stopForeground(true); } catch (Exception ignored) {}
                    try { stopSelf(); } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        DownloadManagerAndroid.setRunning(false);
        super.onDestroy();
    }

    /** Push notification using job info */
    private void pushNotif(android.app.Notification notif) {
        NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (mgr != null) mgr.notify(NOTIF_ID, notif);
    }

    /** Build a notification from a Job (title + text + progress) */
    private android.app.Notification buildNotif(Job job, String text, int progress, boolean done) {
        String title = "MiruDL";
        String subText = "";
        if (job != null && job.animeTitle != null) {
            title = job.animeTitle;
            if (job.episodeTitle != null) {
                subText = job.episodeTitle;
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, MiruDLApp.CHANNEL_DOWNLOADS)
                .setSmallIcon(done ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(!done);

        if (!done) {
            b.setProgress(100, Math.max(0, Math.min(100, progress)), false);

            Intent cancelIntent = new Intent(this, CancelDownloadReceiver.class);
            cancelIntent.putExtra("jobId", "all");
            PendingIntent cancelPi = PendingIntent.getBroadcast(this, 1002, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel All", cancelPi);
        } else {
            b.setProgress(0, 0, false);
        }

        return b.build();
    }

    /** Overload for backward compat – builds a notification directly from job state */
    private android.app.Notification buildNotif(Job job) {
        if (job == null) {
            return buildNotif(null, "Starting...", 0, false);
        }
        String text;
        if (job.finished) {
            text = job.status != null ? job.status : "Done";
        } else {
            text = job.episodeTitle + " - " + job.percent + "%";
        }
        return buildNotif(job, text, job.percent, job.finished);
    }

}
