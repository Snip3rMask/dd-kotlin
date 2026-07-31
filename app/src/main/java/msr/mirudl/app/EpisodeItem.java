package msr.mirudl.app;

import java.io.Serializable;

public class EpisodeItem implements Serializable {
    public int id;
    public int number;
    public int number2;
    public boolean filler;
    public String title;
    public String embedUrl;
    public String hlsUrl;
    public String language;
    public String langName;
    public transient boolean selected;

    public EpisodeItem() {}

    public String getLabel() {
        if (number2 > 0 && number2 != number) {
            return number + "-" + number2;
        }
        return String.valueOf(number);
    }
}
