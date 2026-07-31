package msr.mirudl.app;

import msr.mirudl.shared.model.AnimeItem;
import msr.mirudl.shared.model.EpisodeItem;
import msr.mirudl.shared.model.VideoSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MiruClient {
    // Encoded base URL to deter casual decompilation
    private static final byte[] _B = {(byte)50, (byte)46, (byte)46, (byte)42, (byte)41, (byte)96, (byte)117, (byte)117, (byte)59, (byte)52, (byte)51, (byte)62, (byte)56, (byte)116, (byte)59, (byte)42, (byte)42};
    public static final String BASE = decodeBase();

    private static String decodeBase() {
        byte[] d = new byte[_B.length];
        for (int i = 0; i < _B.length; i++) d[i] = (byte)(_B[i] ^ 0x5A);
        return new String(d);
    }
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT = 30;
    private static MiruClient instance;

    private final OkHttpClient client;

    private MiruClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public static synchronized MiruClient getInstance() {
        if (instance == null) instance = new MiruClient();
        return instance;
    }

    // ============ SEARCH ============

    public List<AnimeItem> search(String query) throws Exception {
        String url = BASE + "/search/suggestions?q=" + encode(query);
        String html = getHtml(url);
        return parseSearchResults(html);
    }

    private List<AnimeItem> parseSearchResults(String html) {
        List<AnimeItem> results = new ArrayList<>();
        // Parse search results
        int idx = 0;
        while (true) {
            int aStart = html.indexOf("<a ", idx);
            if (aStart < 0) break;
            int aEnd = html.indexOf("</a>", aStart);
            if (aEnd < 0) break;
            String block = html.substring(aStart, aEnd);

            if (block.contains("data-search-item")) {
                AnimeItem item = new AnimeItem();
                // Extract href
                int hrefIdx = block.indexOf("href=\"");
                if (hrefIdx >= 0) {
                    int hrefEnd = block.indexOf("\"", hrefIdx + 6);
                    if (hrefEnd > 0) {
                        item.url = block.substring(hrefIdx + 6, hrefEnd);
                        String[] parts = item.url.split("-");
                        item.id = parts[parts.length - 1];
                    }
                }
                // Extract title
                int titleStart = block.indexOf("line-clamp-1\">");
                if (titleStart >= 0) {
                    int titleEnd = block.indexOf("</p>", titleStart);
                    if (titleEnd > 0) {
                        item.title = block.substring(titleStart + 14, titleEnd).trim();
                        item.title = android.text.Html.fromHtml(item.title).toString();
                    }
                }
                // Extract poster
                int imgIdx = block.indexOf("src=\"");
                if (imgIdx >= 0) {
                    int imgEnd = block.indexOf("\"", imgIdx + 5);
                    if (imgEnd > 0) {
                        item.thumbnail = block.substring(imgIdx + 5, imgEnd);
                    }
                }
                if (item.id != null) results.add(item);
            }
            idx = aEnd + 4;
        }
        return results;
    }

    // ============ EPISODES ============

    public List<EpisodeItem> getEpisodes(String animeId) throws Exception {
        String url = BASE + "/api/frontend/anime/" + animeId + "/episodes";
        String json = get(url);
        JSONObject obj = new JSONObject(json);
        JSONArray arr = obj.getJSONArray("episodes");
        List<EpisodeItem> episodes = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject ep = arr.getJSONObject(i);
            EpisodeItem item = new EpisodeItem();
            item.id = ep.getInt("id");
            item.number = ep.getInt("number");
            item.number2 = ep.optInt("number2", 0);
            item.filler = ep.optBoolean("filler", false);
            episodes.add(item);
        }
        return episodes;
    }

    // ============ EMBED / HLS ============

    public List<VideoSource> getEpisodeLanguages(int episodeId) throws Exception {
        String url = BASE + "/api/frontend/episode/" + episodeId + "/languages";
        String json = get(url);
        JSONObject obj = new JSONObject(json);
        JSONArray arr = obj.getJSONArray("languages");
        List<VideoSource> sources = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject lang = arr.getJSONObject(i);
            VideoSource vs = new VideoSource(
                    lang.optString("name", "Source"),
                    lang.optString("embed_url", ""),
                    lang.optString("code", "jpn"),
                    "MiruDL"
            );
            sources.add(vs);
        }
        return sources;
    }

    public String resolveHlsFromEmbed(String embedUrl) throws Exception {
        String html = getHtml(embedUrl);
        // Match file: '...master.m3u8' or file: "...master.m3u8"
        int fileIdx = html.indexOf("file:");
        if (fileIdx < 0) return null;
        int quoteStart = html.indexOf("'", fileIdx);
        if (quoteStart < 0) quoteStart = html.indexOf("\"", fileIdx);
        if (quoteStart < 0) quoteStart = html.indexOf("`", fileIdx);
        if (quoteStart < 0) return null;
        char quote = html.charAt(quoteStart);
        int quoteEnd = html.indexOf(quote, quoteStart + 1);
        if (quoteEnd < 0) return null;
        return html.substring(quoteStart + 1, quoteEnd);
    }

    // ============ HLS QUALITIES ============

    public List<VideoSource> getQualities(String masterUrl) throws Exception {
        String playlist = get(masterUrl);
        List<VideoSource> variants = new ArrayList<>();
        String pendingLine = null;
        for (String raw : playlist.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                pendingLine = line;
            } else if (pendingLine != null && !line.isEmpty() && !line.startsWith("#")) {
                String quality = extractResolution(pendingLine);
                String url = resolveUrl(line, masterUrl);
                variants.add(new VideoSource(quality, url));
                pendingLine = null;
            }
        }
        if (variants.isEmpty()) {
            variants.add(new VideoSource("Auto", masterUrl));
        }
        return variants;
    }

    // ============ HTTP ============

    private String get(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE + "/")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    private String getHtml(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,*/*")
                .header("Referer", BASE + "/")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    // ============ BROWSE ============

    public List<AnimeItem> browseCurrentlyAiring() throws Exception {
        String url = BASE + "/browse?sort=order_top_airing&status=Currently+Airing";
        String html = getHtml(url);
        return parseBrowseResults(html);
    }

    private List<AnimeItem> parseBrowseResults(String html) {
        List<AnimeItem> results = new ArrayList<>();
        // Look for anime links: /anime/{slug-id}
        int idx = 0;
        while (true) {
            int linkStart = html.indexOf("/anime/", idx);
            if (linkStart < 0) break;

            // Find wrapping <a tag
            int aStart = html.lastIndexOf("<a ", linkStart);
            if (aStart < 0 || aStart < idx - 50) {
                idx = linkStart + 7;
                continue;
            }

            int aEnd = html.indexOf("</a>", linkStart);
            if (aEnd < 0) break;

            String block = html.substring(aStart, aEnd + 4);
            AnimeItem item = new AnimeItem();

            // URL + ID from /anime/slug-id
            int hrefEnd = html.indexOf("\"", linkStart + 7);
            if (hrefEnd > linkStart) {
                item.url = BASE + html.substring(linkStart, hrefEnd);
                String slugPart = item.url.startsWith("/anime/") ? item.url.substring(7) : item.url;
                String[] parts = slugPart.split("-");
                item.id = parts.length > 0 ? parts[parts.length - 1] : null;
            }

            // Title from img alt
            int altIdx = block.indexOf("alt=\"");
            if (altIdx >= 0) {
                altIdx += 5;
                int altEnd = block.indexOf("\"", altIdx);
                if (altEnd > altIdx) {
                    String alt = block.substring(altIdx, altEnd).trim();
                    if (!alt.isEmpty() && !alt.toLowerCase().contains("thumbnail")
                            && !alt.toLowerCase().contains("poster")) {
                        item.title = android.text.Html.fromHtml(alt).toString();
                    }
                }
            }

            // Fallback: h2/h3/p text
            if (item.title == null || item.title.isEmpty()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "<(h[23]|p)\\b[^>]*>(.*?)</\\1>",
                        java.util.regex.Pattern.DOTALL).matcher(block);
                if (m.find()) {
                    String t = android.text.Html.fromHtml(m.group(2)).toString().trim();
                    if (!t.isEmpty() && t.length() < 100) item.title = t;
                }
            }
            if (item.title == null) item.title = "";

            // Poster image
            int imgIdx = block.indexOf("src=\"");
            if (imgIdx >= 0) {
                imgIdx += 5;
                int imgEnd = block.indexOf("\"", imgIdx);
                if (imgEnd > imgIdx) {
                    String src = block.substring(imgIdx, imgEnd);
                    if (!src.startsWith("http")) {
                        src = (src.startsWith("/") ? BASE : BASE + "/") + src;
                    }
                    item.thumbnail = src;
                }
            }

            if (item.id != null && !item.title.isEmpty()) {
                boolean dup = false;
                for (AnimeItem ex : results) {
                    if (ex.id != null && ex.id.equals(item.id)) { dup = true; break; }
                }
                if (!dup) results.add(item);
            }

            idx = aEnd + 4;
        }
        return results;
    }

    // ============ HELPERS ============

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private String extractResolution(String infLine) {
        int resIdx = infLine.indexOf("RESOLUTION=");
        if (resIdx >= 0) {
            int end = infLine.indexOf(",", resIdx);
            if (end < 0) end = infLine.length();
            return infLine.substring(resIdx + 11, end);
        }
        int bwIdx = infLine.indexOf("BANDWIDTH=");
        if (bwIdx >= 0) {
            int end = infLine.indexOf(",", bwIdx);
            if (end < 0) end = infLine.length();
            return infLine.substring(bwIdx + 10, end) + "bps";
        }
        return "Auto";
    }

    private String resolveUrl(String value, String base) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("/")) {
            int slash = base.indexOf("/", 8);
            return (slash > 0 ? base.substring(0, slash) : base) + value;
        }
        int slash = base.lastIndexOf("/");
        return (slash > 0 ? base.substring(0, slash + 1) : base + "/") + value;
    }


    
    // ============ ANIME DETAILS ============


    public List<EpisodeItem> getEpisodesWithSeasons(String animeId) throws Exception {
        return getEpisodes(animeId);
    }

    public String getAnimeTitle(String animeId) throws Exception {
        try {
            String url;
            if (animeId != null && animeId.startsWith("http")) {
                url = animeId;
            } else if (animeId != null && animeId.startsWith("/")) {
                url = BASE + animeId;
            } else {
                url = BASE + "/anime/" + animeId;
            }
            String html = getHtml(url);
            int titleStart = html.indexOf("property=\"og:title\" content=\"");
            if (titleStart >= 0) {
                titleStart += 32;
                int titleEnd = html.indexOf("\"", titleStart);
                if (titleEnd > 0) return html.substring(titleStart, titleEnd);
            }
        } catch (Exception ignored) {}
        return null;
    }
    public JSONObject getAnimeDetails(String animeUrl) throws Exception {
        JSONObject result = new JSONObject();
        result.put("id", animeUrl != null ? animeUrl : "");
        return result;
    }
}
