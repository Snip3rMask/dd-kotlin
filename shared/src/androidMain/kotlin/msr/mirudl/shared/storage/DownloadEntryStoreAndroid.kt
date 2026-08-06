package msr.mirudl.shared.storage

import android.content.Context
import msr.mirudl.shared.model.DownloadRecord

/**
 * Synchronous JVM bridge for the shared [DownloadEntryStore] — provides
 * `@JvmStatic` methods taking `Context` so existing Java callers in
 * `app` work unchanged.
 *
 * Delete once all callers convert to Kotlin (Phase 5).
 */
object DownloadEntryStoreAndroid {

    private fun storage(context: Context): AppStorage {
        return AndroidAppStorage(context, "mirudl_downloads")
    }

    @JvmStatic
    fun all(context: Context): List<DownloadRecord> {
        return DownloadEntryStore.all(storage(context))
    }

    @JvmStatic
    fun add(context: Context, entry: DownloadRecord?) {
        DownloadEntryStore.add(storage(context), entry)
    }

    @JvmStatic
    fun remove(context: Context, entry: DownloadRecord?) {
        DownloadEntryStore.remove(storage(context), entry)
    }

    @JvmStatic
    fun removeAll(context: Context, entries: List<DownloadRecord>?) {
        DownloadEntryStore.removeAll(storage(context), entries)
    }
}
