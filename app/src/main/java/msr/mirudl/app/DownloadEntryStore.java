package msr.mirudl.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DownloadEntryStore {
    private static final String PREF = "mirudl_downloads";
    private static final String KEY = "entries";

    public static final class Entry {
        public String uri;
        public String filePath;
        public String title;
        public String parent;
        public long size;
        public long completedAt;

        public String key() {
            if (uri != null && !uri.isEmpty()) return uri;
            return filePath != null ? filePath : title;
        }

        public String parentName() {
            return (parent != null && !parent.trim().isEmpty()) ? parent : "MiruDL";
        }
    }

    public enum DeleteScope {
        RECORD_ONLY,
        FILE_ONLY,
        RECORD_AND_FILE
    }

    private DownloadEntryStore() {}

    public static List<Entry> all(Context context) {
        List<Entry> items = new ArrayList<>();
        String raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject json = arr.getJSONObject(i);
                Entry e = new Entry();
                e.uri = json.optString("uri", "");
                e.filePath = json.optString("filePath", "");
                e.title = json.optString("title", "");
                e.parent = json.optString("parent", "");
                e.size = json.optLong("size", 0);
                e.completedAt = json.optLong("completedAt", 0);
                items.add(e);
            }
        } catch (Exception ignored) {}
        items.sort((a, b) -> Long.compare(b.completedAt, a.completedAt));
        return items;
    }

    public static void add(Context context, Entry entry) {
        if (entry == null) return;
        List<Entry> items = all(context);
        String key = entry.key();
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(items.get(i).key())) {
                items.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) items.add(0, entry);
        save(context, items);
    }

    public static void remove(Context context, Entry entry) {
        if (entry == null) return;
        String key = entry.key();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all(context)) {
            if (!key.equals(e.key())) out.add(e);
        }
        save(context, out);
    }

    public static void removeAll(Context context, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return;
        Set<String> keys = new HashSet<>();
        for (Entry e : entries) keys.add(e.key());
        List<Entry> out = new ArrayList<>();
        for (Entry e : all(context))
            if (!keys.contains(e.key())) out.add(e);
        save(context, out);
    }

    private static void save(Context context, List<Entry> items) {
        JSONArray arr = new JSONArray();
        for (Entry e : items) {
            JSONObject json = new JSONObject();
            try {
                json.put("uri", e.uri != null ? e.uri : "");
                json.put("filePath", e.filePath != null ? e.filePath : "");
                json.put("title", e.title != null ? e.title : "");
                json.put("parent", e.parent != null ? e.parent : "");
                json.put("size", e.size);
                json.put("completedAt", e.completedAt);
                arr.put(json);
            } catch (Exception ignored) {}
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
