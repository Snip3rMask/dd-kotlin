package msr.mirudl.shared.ui

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide

@Composable
actual fun AsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    placeholderColor: Color
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        update = { iv ->
            Glide.with(iv.context)
                .load(url)
                .placeholder(object : android.graphics.drawable.ColorDrawable(placeholderColor.hashCode()))
                .centerCrop()
                .into(iv)
        },
        modifier = modifier
    )
}
