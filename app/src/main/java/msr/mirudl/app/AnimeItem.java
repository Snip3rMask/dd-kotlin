package msr.mirudl.app;

import java.io.Serializable;

public class AnimeItem implements Serializable {
    public String id;
    public String title;
    public String thumbnail;
    public String rating;
    public String url;
    public String type;
    public String year;
    public String status;
    public int episodes;

    public AnimeItem() {}
}
