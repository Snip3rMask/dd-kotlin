package msr.mirudl.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DownloadsBgColor = Color(0xFFF8F9FA)
val DownloadsCardBg = Color(0xFFFFFFFF)
val DownloadsTextPrimary = Color(0xFF202124)
val DownloadsTextSecondary = Color(0xFF5F6368)
val DownloadsTextTertiary = Color(0xFF80868B)
val DownloadsPrimary = Color(0xFF0EA5E9)
val DownloadsError = Color(0xFFD93025)

sealed class DownloadsItem {
    data class Section(val title: String) : DownloadsItem()
    data class Folder(val name: String, val entries: List<String>, val expanded: Boolean) : DownloadsItem()
    data class Active(val title: String, val subtitle: String, val percent: Int, val speed: String) : DownloadsItem()
    data class Completed(val title: String, val size: String) : DownloadsItem()
    object Empty : DownloadsItem()
}

@Composable
fun SharedDownloadsContent(
    items: List<DownloadsItem>,
    onCancelJob: (Int) -> Unit,
    onOpenFile: (Int) -> Unit,
    onDeleteFile: (Int) -> Unit,
    onToggleFolder: (String) -> Unit,
    onGroupDeleteRequest: (String) -> Unit,
    onClearFinished: () -> Unit,
    folderIcon: Painter,
    closeIcon: Painter,
    deleteIcon: Painter,
    chevronDownIcon: Painter,
    chevronRightIcon: Painter,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() || (items.size == 1 && items[0] is DownloadsItem.Empty)) {
        Box(modifier = modifier.fillMaxSize().background(DownloadsBgColor), contentAlignment = Alignment.Center) {
            Text("No downloads yet", color = DownloadsTextTertiary, fontSize = 15.sp)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(DownloadsBgColor).padding(vertical = 8.dp)
    ) {
        items(items) { item ->
            when (item) {
                is DownloadsItem.Section -> {
                    Text(
                        text = item.title,
                        color = DownloadsTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                is DownloadsItem.Folder -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DownloadsCardBg)
                            .clickable { onToggleFolder(item.name) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(folderIcon, contentDescription = null, tint = DownloadsTextSecondary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = DownloadsTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${item.entries.size} files", color = DownloadsTextSecondary, fontSize = 12.sp)
                        }
                        Icon(
                            if (item.expanded) chevronDownIcon else chevronRightIcon,
                            contentDescription = null, tint = DownloadsTextTertiary, modifier = Modifier.size(20.dp)
                        )
                    }
                }
                is DownloadsItem.Active -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DownloadsCardBg)
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = DownloadsTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = DownloadsTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (item.speed.isNotEmpty()) {
                                Text(item.speed, color = DownloadsTextTertiary, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                            }
                            Icon(closeIcon, contentDescription = "Cancel", tint = DownloadsError, modifier = Modifier.size(32.dp).clickable { onCancelJob(0) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${item.percent}%", color = DownloadsTextTertiary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (item.percent.coerceIn(0, 100) / 100f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = DownloadsPrimary,
                        )
                    }
                }
                is DownloadsItem.Completed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DownloadsCardBg)
                            .clickable { onOpenFile(0) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = DownloadsTextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.size, color = DownloadsTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(deleteIcon, contentDescription = "Delete", tint = DownloadsTextTertiary, modifier = Modifier.size(36.dp).clickable { onDeleteFile(0) })
                    }
                }
                is DownloadsItem.Empty -> {}
            }
        }
    }
}
