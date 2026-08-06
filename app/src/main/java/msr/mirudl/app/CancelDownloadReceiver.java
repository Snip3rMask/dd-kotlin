package msr.mirudl.app;

import msr.mirudl.shared.download.DownloadManagerAndroid;
import msr.mirudl.shared.download.Job;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CancelDownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String jobId = intent.getStringExtra("jobId");
        if (jobId == null) return;

        boolean hadActive = false;

        if ("all".equals(jobId)) {
            // Cancel ALL active jobs
            for (Job j : DownloadManagerAndroid.snapshot()) {
                if (!j.finished) {
                    DownloadManagerAndroid.cancel(j);
                    hadActive = true;
                }
            }
        } else {
            // Cancel a specific job by ID
            Job job = DownloadManagerAndroid.find(jobId);
            if (job != null && !job.finished) {
                DownloadManagerAndroid.cancel(job);
                hadActive = true;
            }
        }

        if (!hadActive) return;

        // Update notification to show cancelled
        NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) {
            android.app.Notification.Builder b = new android.app.Notification.Builder(context, MiruDLApp.CHANNEL_DOWNLOADS)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("MiruDL")
                    .setContentText(jobId != null && !"all".equals(jobId) ? "Download cancelled" : "Downloads cancelled")
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            mgr.notify(1001, b.build());

            // Auto-cancel after 3 seconds
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                mgr.cancel(1001);
            }, 3000);
        }

        // Stop the download service if no more active jobs
        if (DownloadManagerAndroid.activeCount() == 0) {
            Intent stopIntent = new Intent(context, DownloadService.class);
            context.stopService(stopIntent);
        }
    }
}
