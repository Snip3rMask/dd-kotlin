package msr.mirudl.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

/**
 * Cross-platform async image composable.
 * Android: loads via Glide. Desktop: loads via Coil/ktor.
 */
@Composable
expect fun AsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderColor: Color = Color(0xFFF0F1F3)
)
