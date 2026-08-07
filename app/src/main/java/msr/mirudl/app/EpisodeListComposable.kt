package msr.mirudl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import msr.mirudl.shared.model.EpisodeItem

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val Success = Color(0xFF34A853)
private val DividerColor = Color(0xFFDADCE0)
private val IconBg = Color(0xFFF0F1F3)
private val OnPrimary = Color(0xFFFFFFFF)
private val SelectionBg = Primary.copy(alpha = 0.08f)

@Composable
fun EpisodeListContent(
    episodes: List<EpisodeItem>,
    gridMode: Boolean,
    selectionMode: Boolean,
    downloadedEpisodes: Set<String>,
    isLoading: Boolean,
    emptyMessage: String,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (episodes.isEmpty() && !isLoading) {
            Text(
                text = emptyMessage,
                color = TextTertiary,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (gridMode) {
            EpisodeGrid(
                episodes = episodes,
                selectionMode = selectionMode,
                downloadedEpisodes = downloadedEpisodes,
                onEpisodeClick = onEpisodeClick
            )
        } else {
            EpisodeList(
                episodes = episodes,
                selectionMode = selectionMode,
                downloadedEpisodes = downloadedEpisodes,
                onEpisodeClick = onEpisodeClick
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
internal fun EpisodeList(
    episodes: List<EpisodeItem>,
    selectionMode: Boolean,
    downloadedEpisodes: Set<String>,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(episodes, key = { it.id }) { ep ->
            EpisodeListItem(
                episode = ep,
                selectionMode = selectionMode,
                isDownloaded = downloadedEpisodes.contains(ep.getLabel()),
                onClick = { onEpisodeClick(ep) }
            )
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: EpisodeItem,
    selectionMode: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val isSelected = selectionMode && episode.selected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SelectionBg else BgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode number badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = episode.getLabel(),
                color = Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Episode title
        Text(
            text = if (episode.filler) "Episode ${episode.getLabel()} [FILLER]"
                   else "Episode ${episode.getLabel()}",
            color = if (episode.filler) TextSecondary else TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )

        // Download / selection icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> Primary
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Selected",
                    tint = OnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            } else if (isDownloaded) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "Downloaded",
                    tint = Success,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = "Download",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun EpisodeGrid(
    episodes: List<EpisodeItem>,
    selectionMode: Boolean,
    downloadedEpisodes: Set<String>,
    onEpisodeClick: (EpisodeItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(episodes, key = { it.id }) { ep ->
            EpisodeGridItem(
                episode = ep,
                selectionMode = selectionMode,
                isDownloaded = downloadedEpisodes.contains(ep.getLabel()),
                onClick = { onEpisodeClick(ep) }
            )
        }
    }
}

@Composable
private fun EpisodeGridItem(
    episode: EpisodeItem,
    selectionMode: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val isSelected = selectionMode && episode.selected

    val bgColor = when {
        isSelected -> Primary
        else -> IconBg
    }

    val textColor = when {
        isSelected -> OnPrimary
        isDownloaded -> Success
        else -> Primary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.getLabel(),
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (isSelected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = "Selected",
                tint = OnPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
            )
        }
    }
}
