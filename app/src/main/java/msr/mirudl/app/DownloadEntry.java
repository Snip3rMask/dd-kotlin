package msr.mirudl.app;

import msr.mirudl.shared.model.DownloadRecord;
import msr.mirudl.shared.storage.DownloadEntryStoreAndroid;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

public final class DownloadEntry {
    public final DownloadRecord record;
    public final Uri uri;
    public final String title;
    public final String parent;
    public final long size;
    public final long sortStamp;

    private DownloadEntry(DownloadRecord record) {
        this.record = record;
        this.uri = (record.uri != null && !record.uri.isEmpty()) ? Uri.parse(record.uri) : null;
        this.title = record.title;
        this.parent = record.parent;
        this.size = record.size;
        this.sortStamp = record.completedAt;
    }

    public static DownloadEntry fromRecord(DownloadRecord record) {
        return new DownloadEntry(record);
    }

    public String parentName() {
        return record.parentName();
    }

    public String key() {
        return record.key();
    }

    public void deleteRecordOnly(Context context) {
        DownloadEntryStoreAndroid.remove(context, record);
    }

    public boolean deleteFileAndRecord(Context context) {
        boolean fileDeleted = false;
        if (uri != null) {
            try {
                DocumentFile doc = DocumentFile.fromSingleUri(context, uri);
                fileDeleted = doc != null && doc.delete();
            } catch (Exception ignored) {}
        }
        DownloadEntryStoreAndroid.remove(context, record);
        return fileDeleted;
    }
}
