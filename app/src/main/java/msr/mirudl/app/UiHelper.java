package msr.mirudl.app;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;

public final class UiHelper {

    private UiHelper() {}

    public static Drawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }
}
