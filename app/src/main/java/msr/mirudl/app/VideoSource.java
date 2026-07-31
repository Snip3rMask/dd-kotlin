package msr.mirudl.app;

import java.io.Serializable;

public class VideoSource implements Serializable {
    public String quality;
    public String url;
    public String language;
    public String server;
    public String source;
    public String subtitleUrl;

    public VideoSource(String quality, String url) {
        this.quality = quality;
        this.url = url;
        this.language = "jpn";
        this.server = "MiruDL";
        this.source = "primary";
    }

    public VideoSource(String quality, String url, String language, String server) {
        this.quality = quality;
        this.url = url;
        this.language = language;
        this.server = server;
        this.source = "primary";
    }
}
