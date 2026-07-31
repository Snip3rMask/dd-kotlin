package msr.mirudl.app;

import msr.mirudl.shared.model.VideoSource;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class HlsDownloader {
    public static final int DEFAULT_PARALLEL = 16;
    private static final int MAX_PARALLEL = 64;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public interface ProgressListener {
        void onProgress(int percent, int downloaded, int total);
        /** Called periodically with the current live download speed in bytes/sec. */
        default void onSpeed(long bytesPerSecond) {}
    }

    public interface CancelCheck {
        boolean isCancelled();
    }

    public static List<VideoSource> qualities(String masterUrl) throws Exception {
        String master = getText(masterUrl);
        List<VideoSource> variants = new ArrayList<>();
        String pendingInf = null;
        for (String raw : master.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                pendingInf = line;
            } else if (pendingInf != null && !line.isEmpty() && !line.startsWith("#")) {
                variants.add(new VideoSource(labelFor(pendingInf), resolve(line, masterUrl)));
                pendingInf = null;
            }
        }
        if (variants.isEmpty()) {
            variants.add(new VideoSource("Auto", masterUrl));
        }
        return variants;
    }

    private static String labelFor(String infLine) {
        int res = infLine.indexOf("RESOLUTION=");
        if (res >= 0) {
            int x = infLine.indexOf('x', res);
            if (x > res) {
                int end = x + 1;
                while (end < infLine.length() && Character.isDigit(infLine.charAt(end))) end++;
                if (end > x + 1) return infLine.substring(x + 1, end) + "p";
            }
        }
        int bw = parseBandwidth(infLine);
        if (bw >= 4500000) return "1080p";
        if (bw >= 2200000) return "720p";
        if (bw >= 1000000) return "480p";
        if (bw > 0) return "360p";
        return "Auto";
    }

    private static int parseBandwidth(String line) {
        int idx = line.indexOf("BANDWIDTH=");
        if (idx < 0) return 0;
        idx += "BANDWIDTH=".length();
        int end = idx;
        while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
        try {
            return Integer.parseInt(line.substring(idx, end));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String download(Context context, String playlistUrl, String fileName,
                                  int parallelSegments, ProgressListener progress, CancelCheck cancel)
            throws Exception {

        String master = getText(playlistUrl);
        if (master.contains("#EXT-X-STREAM-INF")) {
            List<VideoSource> variants = qualities(playlistUrl);
            if (!variants.isEmpty()) {
                playlistUrl = variants.get(0).url;
                master = getText(playlistUrl);
            }
        }

        if (master.contains("#EXT-X-KEY")) {
            throw new IOException("Encrypted streams are not supported");
        }

        List<String> segments = parseSegments(master, playlistUrl);
        if (segments.isEmpty()) {
            throw new IOException("No HLS segments found");
        }

        String mapUrl = parseInitMap(master, playlistUrl);

        File tempDir = new File(context.getCacheDir(), "hls_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) throw new IOException("Cannot create temp dir");

        downloadSegments(segments, tempDir, clampParallel(parallelSegments), progress, cancel);

        // Check if cancelled after segment download
        if (cancel != null && cancel.isCancelled()) {
            deleteRecursive(tempDir);
            throw new IOException("Download cancelled by user");
        }

        String cleanName = sanitize(fileName);
        String animeDir = extractParent(fileName);
        Uri treeUri = getDownloadDir(context);
        if (treeUri == null) throw new IOException("Select download folder in Settings");

        DocumentFile outFile = createOutputFile(context, treeUri, animeDir, cleanName + ".mp4");
        OutputStream out = context.getContentResolver().openOutputStream(outFile.getUri(), "w");
        if (out == null) throw new IOException("Cannot open output stream");

        try {
            if (mapUrl != null) writeUrlToStream(mapUrl, out);
            for (int i = 0; i < segments.size(); i++) {
                File segFile = new File(tempDir, "seg_" + i + ".ts");
                if (segFile.exists()) {
                    try (InputStream in = new FileInputStream(segFile)) {
                        byte[] buf = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = in.read(buf)) >= 0) out.write(buf, 0, read);
                    }
                }
            }
        } finally {
            out.close();
        }

        deleteRecursive(tempDir);

        long size = outFile.length();
        saveDownloadEntry(context, outFile.getUri().toString(), cleanName, animeDir, size);

        return outFile.getUri().toString();
    }

    private static void downloadSegments(List<String> urls, File tempDir, int parallel,
                                          ProgressListener progress, CancelCheck cancel) {
        ExecutorService executor = Executors.newFixedThreadPool(parallel);
        AtomicInteger done = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        int total = urls.size();

        // Live speed sampler: measures bytes downloaded every 700ms and smooths the
        // reading with a moving average so it doesn't flicker between segment bursts
        ScheduledExecutorService speedTicker = Executors.newSingleThreadScheduledExecutor();
        final long[] lastSample = {System.currentTimeMillis(), 0L};
        final double[] smoothedSpeed = {-1};
        speedTicker.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long bytesNow = totalBytes.get();
            long elapsedMs = now - lastSample[0];
            long deltaBytes = bytesNow - lastSample[1];
            lastSample[0] = now;
            lastSample[1] = bytesNow;
            if (elapsedMs > 0 && progress != null) {
                double instant = (deltaBytes * 1000.0) / elapsedMs;
                smoothedSpeed[0] = (smoothedSpeed[0] < 0)
                        ? instant
                        : (0.35 * instant + 0.65 * smoothedSpeed[0]);
                progress.onSpeed((long) smoothedSpeed[0]);
            }
        }, 700, 700, TimeUnit.MILLISECONDS);

        for (int i = 0; i < total; i++) {
            if (cancel != null && cancel.isCancelled()) {
                executor.shutdownNow();
                speedTicker.shutdownNow();
                return;
            }
            final int idx = i;
            executor.execute(() -> {
                if (cancel != null && cancel.isCancelled()) return;
                try {
                    byte[] data = downloadBytes(urls.get(idx));
                    totalBytes.addAndGet(data.length);
                    File out = new File(tempDir, "seg_" + idx + ".ts");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(data);
                    }
                } catch (Exception ignored) {}
                int current = done.incrementAndGet();
                if (progress != null) {
                    progress.onProgress(current * 100 / total, current, total);
                }
            });
        }

        executor.shutdown();
        try {
            // Poll cancel token every second while waiting for downloads
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                if (cancel != null && cancel.isCancelled()) {
                    executor.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException ignored) {
            executor.shutdownNow();
        } finally {
            speedTicker.shutdownNow();
            if (progress != null) progress.onSpeed(0);
        }
    }

    // ============ PLAYLIST PARSING ============

    private static List<String> parseSegments(String playlist, String baseUrl) {
        List<String> segments = new ArrayList<>();
        for (String raw : playlist.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                segments.add(resolve(line, baseUrl));
            }
        }
        return segments;
    }


    private static String parseInitMap(String playlist, String baseUrl) {
        for (String raw : playlist.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-MAP")) {
                int urlIdx = line.indexOf("URI=\"");
                if (urlIdx >= 0) {
                    int urlEnd = line.indexOf("\"", urlIdx + 5);
                    if (urlEnd > 0) {
                        return resolve(line.substring(urlIdx + 5, urlEnd), baseUrl);
                    }
                }
            }
        }
        return null;
    }

    // ============ HTTP ============

    private static String getText(String url) throws IOException {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", MiruClient.BASE + "/")
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IOException("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    private static byte[] downloadBytes(String url) {
        try {
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = CLIENT.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null)
                    return resp.body().bytes();
            }
        } catch (Exception ignored) {}
        return new byte[0];
    }

    private static void writeUrlToStream(String url, OutputStream out) throws IOException {
        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            if (resp.isSuccessful() && resp.body() != null) {
                try (InputStream in = resp.body().byteStream()) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buf)) >= 0) out.write(buf, 0, read);
                }
            }
        }
    }

    // ============ FILE HELPERS ============

    private static Uri getDownloadDir(Context context) {
        return StorageSettings.getDownloadUri(context);
    }

    private static DocumentFile createOutputFile(Context context, Uri treeUri,
                                                  String folderName, String fileName) throws IOException {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.canWrite())
            throw new IOException("Cannot write to download folder");

        // Create "MiruDL Downloads" subfolder like Anifux does
        DocumentFile mirudlRoot = findOrCreateDir(root, "MiruDL Downloads");
        if (mirudlRoot == null) throw new IOException("Cannot create root folder");

        DocumentFile anime = findOrCreateDir(mirudlRoot, sanitize(folderName));
        if (anime == null) throw new IOException("Cannot create anime folder");

        DocumentFile existing = anime.findFile(fileName);
        if (existing != null) existing.delete();

        DocumentFile file = anime.createFile("video/mp4", fileName);
        if (file == null) throw new IOException("Cannot create output file");
        return file;
    }

    private static DocumentFile findOrCreateDir(DocumentFile parent, String name) {
        DocumentFile existing = parent.findFile(name);
        if (existing != null && existing.isDirectory()) return existing;
        return parent.createDirectory(name);
    }

    private static void saveDownloadEntry(Context context, String uri, String title,
                                           String parent, long size) {
        try {
            DownloadEntryStore.Entry entry = new DownloadEntryStore.Entry();
            entry.uri = uri;
            entry.title = title;
            entry.parent = parent;
            entry.size = size;
            entry.completedAt = System.currentTimeMillis();
            DownloadEntryStore.add(context, entry);
        } catch (Exception ignored) {}
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null)
                for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "Unknown";
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\s+", " ").trim();
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    private static String extractParent(String fileName) {
        if (fileName == null) return "Unknown";
        int idx = fileName.indexOf(" - Episode");
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private static String resolve(String value, String base) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) {
            int slash = base.indexOf("/", 8);
            return (slash > 0 ? base.substring(0, slash) : base) + value;
        }
        int slash = base.lastIndexOf("/");
        return (slash > 0 ? base.substring(0, slash + 1) : base + "/") + value;
    }

    private static int clampParallel(int value) {
        if (value < 1) return 1;
        return Math.min(value, MAX_PARALLEL);
    }
}
