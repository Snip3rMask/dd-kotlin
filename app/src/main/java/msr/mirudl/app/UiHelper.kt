package msr.mirudl.app

import android.graphics.drawable.GradientDrawable

object UiHelper {

    @JvmStatic
    fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp.toFloat()
        }
    }
}
