package msr.mirudl.shared.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.runBlocking
import msr.mirudl.shared.storage.AndroidAppStorage
import msr.mirudl.shared.storage.AppStorage

/**
 * Synchronous JVM bridge for the shared [UpdateChecker] — wraps suspend
 * calls with [runBlocking] so existing Java callers in `app` work
 * unchanged. Posts results on the main thread via `Handler`.
 *
 * Delete once all callers convert to Kotlin coroutines (Phase 5).
 */
object UpdateCheckerAndroid {

    interface Callback {
        fun onResult(info: UpdateChecker.ReleaseInfo?)
    }

    private fun storage(context: Context): AppStorage {
        return AndroidAppStorage(context, "mirudl_update_checker")
    }

    private fun currentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }

    private fun postResult(callback: Callback?, info: UpdateChecker.ReleaseInfo?) {
        if (callback == null) return
        Handler(Looper.getMainLooper()).post { callback.onResult(info) }
    }

    @JvmStatic
    fun checkNow(context: Context, callback: Callback) {
        val appCtx = context.applicationContext
        Thread {
            val remote = runBlocking { UpdateChecker.fetchLatestRelease() }
            val s = storage(appCtx)
            if (remote != null) {
                UpdateChecker.writeCache(s, remote)
            }
            val newer = remote != null && UpdateChecker.isNewerVersion(remote.tag, currentVersion(appCtx))
            postResult(callback, if (newer) remote else null)
        }.start()
    }

    @JvmStatic
    fun checkOnStartup(context: Context, callback: Callback) {
        val appCtx = context.applicationContext
        val s = storage(appCtx)
        val lastCheck = UpdateChecker.readLastCheck(s)
        val stale = System.currentTimeMillis() - lastCheck > UpdateChecker.CACHE_TTL_MS

        if (!stale) {
            evaluateCached(appCtx, s, callback)
            return
        }

        Thread {
            val remote = runBlocking { UpdateChecker.fetchLatestRelease() }
            if (remote != null) {
                UpdateChecker.writeCache(s, remote)
                val newer = UpdateChecker.isNewerVersion(remote.tag, currentVersion(appCtx))
                postResult(callback, if (newer) remote else null)
            } else {
                evaluateCached(appCtx, s, callback)
            }
        }.start()
    }

    private fun evaluateCached(context: Context, s: AppStorage, callback: Callback) {
        val tag = s.getString("cached_tag", "")
        if (tag.isEmpty() || !UpdateChecker.isNewerVersion(tag, currentVersion(context))) {
            postResult(callback, null)
            return
        }
        val info = UpdateChecker.readCache(s)
        postResult(callback, info)
    }
}
