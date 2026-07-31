package msr.mirudl.app;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DownloadManager {
    public static synchronized Job findByAnimeAndEpisode(String anime, String episode) {
        for (Job j : JOBS) {
            if (!j.finished &&
                j.animeTitle != null && j.animeTitle.equals(anime) &&
                j.episodeTitle != null && j.episodeTitle.equals(episode)) {
                return j;
            }
        }
        return null;
    }

    public static synchronized boolean hasActiveJob(String anime, String episode) {
        for (Job j : JOBS) {
            if (!j.finished &&
                j.animeTitle != null && j.animeTitle.equals(anime) &&
                j.episodeTitle != null && j.episodeTitle.equals(episode)) {
                return true;
            }
        }
        return false;
    }

    public static final String STATUS_QUEUED = "Queued";
    public static final String STATUS_DOWNLOADING = "Downloading";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_FAILED = "Failed";
    public static final String STATUS_CANCELLED = "Cancelled";

    public static class Job implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public final String animeTitle;
        public String episodeTitle;
        public final String quality;
        public final String language;
        public final String hlsUrl;
        public volatile int percent;
        public volatile String status;
        public volatile long bytesPerSecond;
        public volatile boolean cancelled;
        public volatile boolean finished;
        public volatile String error;
        public String outputUri;
        // Multi-episode support
        public volatile int currentIndex;
        public volatile int totalEpisodes;

        public Job(String animeTitle, String episodeTitle, String quality,
                   String language, String hlsUrl) {
            this.id = UUID.randomUUID().toString();
            this.animeTitle = animeTitle;
            this.episodeTitle = episodeTitle;
            this.quality = quality;
            this.language = language;
            this.hlsUrl = hlsUrl;
            this.status = STATUS_QUEUED;
            this.currentIndex = 1;
            this.totalEpisodes = 1;
        }
    }

    private static final List<Job> JOBS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean running = false;

    public static synchronized Job enqueue(String anime, String episode, String quality,
                                            String language, String hlsUrl) {
        // Prevent duplicate: same anime + episode + quality that is not finished
        for (Job existing : JOBS) {
            if (!existing.finished &&
                existing.animeTitle != null && existing.animeTitle.equals(anime) &&
                existing.episodeTitle != null && existing.episodeTitle.equals(episode) &&
                existing.quality != null && existing.quality.equals(quality)) {
                return existing;
            }
        }
        Job job = new Job(anime, episode, quality, language, hlsUrl);
        JOBS.add(job);
        return job;
    }

    public static synchronized void enqueueAll(List<Job> jobs) {
        // Deduplicate by anime+episode+quality - keep first (newest) occurrence
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.List<Job> deduped = new java.util.ArrayList<>();
        // Process in reverse to keep newest entry for each key
        java.util.List<Job> all = new java.util.ArrayList<>();
        all.addAll(jobs);
        all.addAll(JOBS);
        for (int i = all.size() - 1; i >= 0; i--) {
            Job j = all.get(i);
            String key = (j.animeTitle != null ? j.animeTitle : "") + "|||" + (j.episodeTitle != null ? j.episodeTitle : "") + "|||" + (j.quality != null ? j.quality : "");
            if (!seen.contains(key) || j.finished) {
                seen.add(key);
                deduped.add(0, j);
            }
        }
        JOBS.clear();
        JOBS.addAll(deduped);
    }

    public static synchronized Job find(String id) {
        for (Job j : JOBS) if (id.equals(j.id)) return j;
        return null;
    }

    public static synchronized void update(Job job, int percent, String status) {
        if (job == null) return;
        job.percent = percent;
        job.status = status;
    }

    public static synchronized void updateMulti(Job job, int currentIndex, String episode, int percent, String status) {
        if (job == null) return;
        job.currentIndex = currentIndex;
        job.episodeTitle = episode;
        job.percent = percent;
        job.status = status;
    }

    public static synchronized void complete(Job job, String outputUri) {
        if (job == null) return;
        job.percent = 100;
        job.status = STATUS_COMPLETED;
        job.finished = true;
        job.bytesPerSecond = 0;
        job.outputUri = outputUri;
    }

    public static synchronized void fail(Job job, String error) {
        if (job == null) return;
        job.error = error;
        job.status = error != null ? error : STATUS_FAILED;
        job.finished = true;
        job.bytesPerSecond = 0;
    }

    public static synchronized void cancel(Job job) {
        if (job == null) return;
        job.cancelled = true;
        job.status = STATUS_CANCELLED;
        job.finished = true;
        job.bytesPerSecond = 0;
    }

    public static synchronized void remove(Job job) {
        JOBS.remove(job);
    }

    public static synchronized void removeFinished() {
        JOBS.removeIf(j -> j.finished);
    }

    public static synchronized List<Job> snapshot() {
        return new ArrayList<>(JOBS);
    }

    public static synchronized int activeCount() {
        int count = 0;
        for (Job j : JOBS)
            if (!j.finished) count++;
        return count;
    }

    /** Atomically finds the next not-yet-started job and marks it as downloading,
     *  so multiple concurrent worker lanes never pick the same job. */
    public static synchronized Job claimNextQueuedJob() {
        for (Job j : JOBS) {
            if (!j.finished && STATUS_QUEUED.equals(j.status)) {
                j.status = STATUS_DOWNLOADING;
                j.percent = 0;
                return j;
            }
        }
        return null;
    }

    public static synchronized int queuedCount() {
        int count = 0;
        for (Job j : JOBS)
            if (!j.finished && STATUS_QUEUED.equals(j.status)) count++;
        return count;
    }

    public static synchronized int downloadingCount() {
        int count = 0;
        for (Job j : JOBS)
            if (!j.finished && STATUS_DOWNLOADING.equals(j.status)) count++;
        return count;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized void setRunning(boolean r) {
        running = r;
    }
}
