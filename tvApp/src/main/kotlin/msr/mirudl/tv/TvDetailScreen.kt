package msr.mirudl.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.EpisodeItem
import msr.mirudl.shared.ui.AsyncImage

private val TvBgColor = Color(0xFF1A1A2E)
private val TvCardBg = Color(0xFF16213E)
private val TvTextPrimary = Color(0xFFE0E0E0)
private val TvTextSecondary = Color(0xFF80868B)
private val TvFocusBorder = Color(0xFF0EA5E9)
private val TvChipBg = Color(0xFF0F3460)
private val TvChipSelectedBg = Color(0xFF0EA5E9)
private val TvEpisodeBg = Color(0xFF16213E)
private val TvEpisodeSelectedBg = Color(0xFF0EA5E9)
private val TvSuccess = Color(0xFF34A853)

@Composable
fun TvDetailScreen(
    anime: AnimeItem,
    episodes: List<EpisodeItem>,
    languages: List<String>,
    selectedLanguage: String,
    isLoading: Boolean,
    emptyMessage: String,
    onLanguageSelected: (String) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBgColor)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    url = anime.thumbnail ?: "",
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholderColor = Color(0xFF0F3460)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title ?: "Unknown",
                    color = TvTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Metadata row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!anime.type.isNullOrEmpty()) {
                        MetadataChip(label = anime.type!!)
                    }
                    if (!anime.year.isNullOrEmpty()) {
                        MetadataChip(label = anime.year!!)
                    }
                    if (!anime.status.isNullOrEmpty()) {
                        MetadataChip(label = anime.status!!)
                    }
                    if (anime.episodes > 0) {
                        MetadataChip(label = "${anime.episodes} Episodes")
                    }
                }

                if (!anime.rating.isNullOrEmpty() && anime.rating != "0") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "★ ${anime.rating}",
                            color = Color(0xFFFBBF24),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Language chips
        if (languages.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                languages.forEach { lang ->
                    TvLanguageChip(
                        label = lang,
                        selected = lang == selectedLanguage,
                        onClick = { onLanguageSelected(lang) }
                    )
                }
            }
        }

        // Episodes section
        Text(
            text = "Episodes",
            color = TvTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (episodes.isEmpty() && !isLoading) {
                Text(
                    text = emptyMessage,
                    color = TvTextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Grid mode for TV (better for D-pad navigation)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodes) { ep ->
                        TvEpisodeGridItem(
                            episode = ep,
                            onClick = { onEpisodeClick(ep) }
                        )
                    }
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    color = TvFocusBorder,
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun MetadataChip(label: String) {
    Text(
        text = label,
        color = TvTextSecondary,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TvChipBg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun TvLanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale = if (isFocused) 1.05f else 1f
    val bgColor = if (selected) TvChipSelectedBg else TvChipBg
    val borderColor = if (isFocused) TvFocusBorder else Color.Transparent

    Text(
        text = label,
        color = if (selected) Color.White else TvTextPrimary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TvEpisodeGridItem(
    episode: EpisodeItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale = if (isFocused) 1.08f else 1f
    val borderColor = if (isFocused) TvFocusBorder else Color.Transparent

    Box(
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
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(TvEpisodeBg)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = episode.getLabel(),
                color = if (episode.filler) TvTextSecondary else TvTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (episode.filler) {
                Text(
                    text = "FILLER",
                    color = Color(0xFFEF5350),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!episode.langName.isNullOrEmpty()) {
                Text(
                    text = episode.langName ?: "",
                    color = TvTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
