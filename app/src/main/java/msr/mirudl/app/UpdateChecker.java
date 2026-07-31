package msr.mirudl.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Checks the app's GitHub repo for a newer tagged release than the one currently installed. */
public final class UpdateChecker {

    private static final String REPO = "msrofficial/MiruDL-App";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private static final String PREFS_NAME = "mirudl_update_checker";
    private static final String KEY_TAG = "cached_tag";
    private static final String KEY_BODY = "cached_body";
    private static final String KEY_URL = "cached_url";
    private static final String KEY_APK_URL = "cached_apk_url";
    private static final String KEY_LAST_CHECK = "last_check_at";

    /** How long a cached release check is trusted before re-hitting the network on app start. */
    private static final long CACHE_TTL_MS = 3 * TimeUnit.HOURS.toMillis(1);

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private UpdateChecker() {}

    public static class ReleaseInfo {
        public String tag;
        public String changelog;
        public String htmlUrl;
        public String apkUrl; // direct .apk asset link, if the release has one
    }

    public interface Callback {
        /** Called on the main thread. {@code info} is null if there's no newer version or the check failed. */
        void onResult(ReleaseInfo info);
    }

    /** Always hits the network. Used by the manual "Check for Updates" action in Settings. */
    public static void checkNow(Context context, Callback callback) {
        Context appCtx = context.getApplicationContext();
        new Thread(() -> {
            ReleaseInfo remote = fetchLatestRelease();
            if (remote != null) cacheRelease(appCtx, remote);
            boolean newer = remote != null && isNewerVersion(remote.tag, currentVersion(appCtx));
            postResult(callback, newer ? remote : null);
        }).start();
    }

    /**
     * Used on app startup. Reuses a recent cached result instead of hitting the network on every
     * launch, but the newer-version verdict is always re-evaluated against the cache — so the
     * reminder keeps appearing on every app open until the user actually updates.
     */
    public static void checkOnStartup(Context context, Callback callback) {
        Context appCtx = context.getApplicationContext();
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0);
        boolean stale = System.currentTimeMillis() - lastCheck > CACHE_TTL_MS;

        if (!stale) {
            evaluateCached(appCtx, callback);
            return;
        }

        new Thread(() -> {
            ReleaseInfo remote = fetchLatestRelease();
            if (remote != null) {
                cacheRelease(appCtx, remote);
                boolean newer = isNewerVersion(remote.tag, currentVersion(appCtx));
                postResult(callback, newer ? remote : null);
            } else {
                evaluateCached(appCtx, callback); // network failed — fall back to last known cache
            }
        }).start();
    }

    private static void evaluateCached(Context appCtx, Callback callback) {
        SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String tag = prefs.getString(KEY_TAG, null);
        if (tag == null || !isNewerVersion(tag, currentVersion(appCtx))) {
            postResult(callback, null);
            return;
        }
        ReleaseInfo info = new ReleaseInfo();
        info.tag = tag;
        info.changelog = prefs.getString(KEY_BODY, "");
        info.htmlUrl = prefs.getString(KEY_URL, null);
        info.apkUrl = prefs.getString(KEY_APK_URL, null);
        postResult(callback, info);
    }

    private static void postResult(Callback callback, ReleaseInfo info) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(() -> callback.onResult(info));
    }

    private static ReleaseInfo fetchLatestRelease() {
        try {
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MiruDL-App")
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JSONObject json = new JSONObject(response.body().string());

                ReleaseInfo info = new ReleaseInfo();
                info.tag = json.optString("tag_name", null);
                info.changelog = json.optString("body", "").trim();
                info.htmlUrl = json.optString("html_url", null);

                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase(Locale.US).endsWith(".apk")) {
                            info.apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                return (info.tag != null) ? info : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void cacheRelease(Context appCtx, ReleaseInfo info) {
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TAG, info.tag)
                .putString(KEY_BODY, info.changelog)
                .putString(KEY_URL, info.htmlUrl)
                .putString(KEY_APK_URL, info.apkUrl)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    private static String currentVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** True if remoteTag represents a newer version than localVersion (e.g. "v1.0.110" > "1.0.108"). */
    public static boolean isNewerVersion(String remoteTag, String localVersion) {
        int[] r = normalize(remoteTag);
        int[] l = normalize(localVersion);
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) return rv > lv;
        }
        return false;
    }

    private static int[] normalize(String v) {
        if (v == null) return new int[]{0};
        v = v.trim();
        if (v.toLowerCase(Locale.US).startsWith("v")) v = v.substring(1);
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
