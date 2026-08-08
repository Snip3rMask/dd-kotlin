package msr.mirudl.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TvBgColor = Color(0xFF1A1A2E)
private val TvCardBg = Color(0xFF16213E)
private val TvTextPrimary = Color(0xFFE0E0E0)
private val TvTextSecondary = Color(0xFF80868B)
private val TvFocusBorder = Color(0xFF0EA5E9)
private val TvProgressBg = Color(0xFF0F3460)
private val TvProgressFill = Color(0xFF0EA5E9)
private val TvError = Color(0xFFEF5350)

sealed class TvDownloadItem {
    data class Active(
        val title: String,
        val subtitle: String,
        val percent: Int,
        val speed: String
    ) : TvDownloadItem()
    data class Completed(
        val title: String,
        val size: String
    ) : TvDownloadItem()
    object Empty : TvDownloadItem()
}

@Composable
fun TvDownloadsScreen(
    items: List<TvDownloadItem>,
    onCancelClick: () -> Unit,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBgColor)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Downloads",
            color = TvTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Manage your downloaded anime",
            color = TvTextSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty() || (items.size == 1 && items[0] is TvDownloadItem.Empty)) {
                Text(
                    text = "No downloads yet",
                    color = TvTextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        when (item) {
                            is TvDownloadItem.Active -> TvActiveDownloadCard(
                                item = item,
                                onCancelClick = onCancelClick
                            )
                            is TvDownloadItem.Completed -> TvCompletedDownloadCard(
                                item = item,
                                onOpenClick = onOpenClick,
                                onDeleteClick = onDeleteClick
                            )
                            is TvDownloadItem.Empty -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvActiveDownloadCard(
    item: TvDownloadItem.Active,
    onCancelClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale = if (isFocused) 1.02f else 1f
    val borderColor = if (isFocused) TvFocusBorder else Color.Transparent

    Column(
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    onCancelClick()
                    true
                } else false
            }
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(TvCardBg)
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TvTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    color = TvTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.speed.isNotEmpty()) {
                Text(
                    text = item.speed,
                    color = TvTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            // Cancel button (focused state shows as highlighted)
            Text(
                text = "CANCEL",
                color = if (isFocused) Color.White else TvError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) TvError else TvError.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${item.percent}%",
                color = TvTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { (item.percent.coerceIn(0, 100) / 100f) },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TvProgressFill,
                trackColor = TvProgressBg,
            )
        }
    }
}

@Composable
private fun TvCompletedDownloadCard(
    item: TvDownloadItem.Completed,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale = if (isFocused) 1.02f else 1f
    val borderColor = if (isFocused) TvFocusBorder else Color.Transparent

    Row(
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
                ) {
                    onOpenClick()
                    true
                } else false
            }
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(TvCardBg)
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TvTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.size,
                color = TvTextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = "OPEN",
            color = if (isFocused) Color.White else TvProgressFill,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) TvProgressFill else TvProgressFill.copy(alpha = 0.15f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
