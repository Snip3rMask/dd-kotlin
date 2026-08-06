package msr.mirudl.app

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import msr.mirudl.shared.download.DownloadManager

class CancelDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return

        var hadActive = false

        if ("all" == jobId) {
            for (j in DownloadManager.snapshot()) {
                if (!j.finished) {
                    DownloadManager.cancel(j)
                    hadActive = true
                }
            }
        } else {
            val job = DownloadManager.find(jobId)
            if (job != null && !job.finished) {
                DownloadManager.cancel(job)
                hadActive = true
            }
        }

        if (!hadActive) return

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val b = Notification.Builder(context, MiruDLApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MiruDL")
            .setContentText(
                if (jobId != "all") "Download cancelled" else "Downloads cancelled"
            )
            .setOngoing(false)
            .setProgress(0, 0, false)
        mgr.notify(NOTIF_ID, b.build())

        Handler(Looper.getMainLooper()).postDelayed({ mgr.cancel(NOTIF_ID) }, 3000)

        if (DownloadManager.activeCount() == 0) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val EXTRA_JOB_ID = "jobId"
    }
}
