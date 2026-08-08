package msr.mirudl.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import msr.mirudl.shared.model.DownloadRecord
import msr.mirudl.shared.storage.DownloadEntryStoreAndroid

class DownloadEntry private constructor(
    @JvmField val record: DownloadRecord,
    @JvmField val uri: Uri?,
    @JvmField val title: String?,
    @JvmField val parent: String?,
    @JvmField val size: Long,
    @JvmField val sortStamp: Long
) {
    fun parentName(): String = record.parentName()

    fun key(): String? = record.key()

    fun deleteRecordOnly(context: Context) {
        DownloadEntryStoreAndroid.remove(context, record)
    }

    fun deleteFileAndRecord(context: Context): Boolean {
        var fileDeleted = false
        if (uri != null) {
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                fileDeleted = doc != null && doc.delete()
            } catch (_: Exception) {}
        }
        DownloadEntryStoreAndroid.remove(context, record)
        return fileDeleted
    }

    companion object {
        @JvmStatic
        fun fromRecord(record: DownloadRecord): DownloadEntry {
            val uri = if (!record.uri.isNullOrEmpty()) Uri.parse(record.uri) else null
            return DownloadEntry(
                record = record,
                uri = uri,
                title = record.title,
                parent = record.parent,
                size = record.size,
                sortStamp = record.completedAt
            )
        }
    }
}
