package msr.mirudl.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TvBgColor = Color(0xFF1A1A2E)
private val TvCardBg = Color(0xFF16213E)
private val TvTextPrimary = Color(0xFFE0E0E0)
private val TvTextSecondary = Color(0xFF80868B)
private val TvFocusBorder = Color(0xFF0EA5E9)
private val TvDivider = Color(0xFF0F3460)

data class TvSettingItem(
    val title: String,
    val subtitle: String,
    val enabled: Boolean = true
)

@Composable
fun TvSettingsScreen(
    settings: List<TvSettingItem>,
    onSettingClick: (TvSettingItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBgColor)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            color = TvTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Configure MiruDL TV",
            color = TvTextSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn {
            items(settings) { item ->
                TvSettingRow(
                    item = item,
                    onClick = { onSettingClick(item) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TvSettingRow(
    item: TvSettingItem,
    onClick: () -> Unit
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
                    onClick()
                    true
                } else false
            }
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(TvCardBg)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (item.enabled) TvTextPrimary else TvTextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.subtitle,
                color = TvTextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = "›",
            color = if (isFocused) TvFocusBorder else TvTextSecondary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
