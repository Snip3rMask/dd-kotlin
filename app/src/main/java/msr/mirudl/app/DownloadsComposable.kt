package msr.mirudl.app

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import msr.mirudl.shared.download.Job
import msr.mirudl.shared.model.DownloadRecord
import java.util.Locale

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val ErrorColor = Color(0xFFD93025)
private val CardBg = Color(0xFFFFFFFF)
private val SurfaceVariant = Color(0xFFF0F1F3)

sealed class DownloadsItem {
    data class Section(val title: String) : DownloadsItem()
    data class Folder(val name: String, val entries: List<DownloadRecord>, val expanded: Boolean) : DownloadsItem()
    data class Active(val job: Job) : DownloadsItem()
    data class Completed(val record: DownloadRecord) : DownloadsItem()
    object Empty : DownloadsItem()
}

@Composable
fun DownloadsContent(
    items: List<DownloadsItem>,
    onCancelJob: (Job) -> Unit,
    onOpenFile: (DownloadRecord) -> Unit,
    onDeleteFile: (DownloadRecord) -> Unit,
    onToggleFolder: (String) -> Unit,
    onGroupDeleteRequest: (String) -> Unit,
    onClearFinished: () -> Unit
) {
    if (items.isEmpty() || (items.size == 1 && items[0] is DownloadsItem.Empty)) {
        Box(modifier = Modifier.fillMaxSize().background(BgColor), contentAlignment = Alignment.Center) {
            Text("No downloads yet", color = TextTertiary, fontSize = 15.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgColor).padding(vertical = 8.dp)
    ) {
        items(items) { item ->
            when (item) {
                is DownloadsItem.Section -> DownloadsSectionHeader(item.title)
                is DownloadsItem.Folder -> FolderItem(
                    folder = item,
                    onToggle = { onToggleFolder(item.name) },
                    onDeleteRequest = { onGroupDeleteRequest(item.name) }
                )
                is DownloadsItem.Active -> ActiveDownloadItem(
                    job = item.job,
                    onCancel = { onCancelJob(item.job) }
                )
                is DownloadsItem.Completed -> CompletedDownloadItem(
                    record = item.record,
                    onOpen = { onOpenFile(item.record) },
                    onDelete = { onDeleteFile(item.record) }
                )
                is DownloadsItem.Empty -> {}
            }
        }
    }
}

@Composable
private fun DownloadsSectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun FolderItem(
    folder: DownloadsItem.Folder,
    onToggle: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(32.dp).padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(folder.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folder.entries.size} files", color = TextTertiary, fontSize = 12.sp)
            }
            val chevron = if (folder.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
            Icon(
                painter = painterResource(id = chevron),
                contentDescription = if (folder.expanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "Delete group",
                tint = TextTertiary,
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onDeleteRequest() }
            )
        }

        if (folder.expanded) {
            Column(modifier = Modifier.animateContentSize()) {
                folder.entries.forEach { record ->
                    CompletedDownloadItem(
                        record = record,
                        onOpen = {},
                        onDelete = {},
                        indent = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadItem(
    job: Job,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.animeTitle ?: "",
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = job.episodeTitle ?: "",
                    color = TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            val speed = formatSpeed(job.bytesPerSecond)
            if (speed.isNotEmpty()) {
                Text(text = speed, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cancel",
                tint = ErrorColor,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onCancel() }
                    .padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${job.status ?: ""}  •  ${job.percent}%",
                color = TextTertiary, fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { (job.percent.coerceIn(0, 100) / 100f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = Primary,
            trackColor = SurfaceVariant,
        )
    }
}

@Composable
private fun CompletedDownloadItem(
    record: DownloadRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    indent: Boolean = false
) {
    var title = record.title ?: ""
    if (title.startsWith("Episode ")) title = title.substring(8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (indent) 8.dp else 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSize(record.size),
                color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_delete),
            contentDescription = "Delete",
            tint = TextTertiary,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onDelete() }
                .padding(6.dp)
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return ""
    val kb = bytesPerSecond / 1024.0
    return if (kb < 1024) String.format(Locale.US, "%.0f KB/s", kb)
    else String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
}
