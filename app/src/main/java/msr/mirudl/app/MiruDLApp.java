package msr.mirudl.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import msr.mirudl.shared.download.AndroidFileStorage;

public class MiruDLApp extends Application {
    public static final String CHANNEL_DOWNLOADS = "mirudl_downloads";

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidFileStorage.init(this);
        CrashLogger.init(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Download progress notifications");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }
}
