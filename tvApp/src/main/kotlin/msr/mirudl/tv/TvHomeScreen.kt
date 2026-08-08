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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import msr.mirudl.shared.ui.AsyncImage

private val TvBgColor = Color(0xFF1A1A2E)
private val TvCardBg = Color(0xFF16213E)
private val TvTextPrimary = Color(0xFFE0E0E0)
private val TvTextSecondary = Color(0xFF80868B)
private val TvFocusBorder = Color(0xFF0EA5E9)
private val TvBadgeBg = Color(0x88000000)

@Composable
fun TvHomeScreen(
    animeList: List<AnimeItem>,
    isLoading: Boolean,
    onAnimeClick: (AnimeItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBgColor)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Header
        Text(
            text = "MiruDL",
            color = TvTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Browse anime",
            color = TvTextSecondary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            if (animeList.isEmpty() && !isLoading) {
                Text(
                    text = "Search anime to get started",
                    color = TvTextSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(animeList) { anime ->
                        TvAnimeCard(
                            anime = anime,
                            onClick = { onAnimeClick(anime) }
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
private fun TvAnimeCard(
    anime: AnimeItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale = if (isFocused) 1.05f else 1f
    val borderColor = if (isFocused) TvFocusBorder else Color.Transparent
    val borderWidth = if (isFocused) 3.dp else 0.dp

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
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .background(TvCardBg)
            .padding(8.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                url = anime.thumbnail ?: "",
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                placeholderColor = Color(0xFF0F3460)
            )
            // Rating badge
            if (!anime.rating.isNullOrEmpty() && anime.rating != "0") {
                Text(
                    text = anime.rating ?: "",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvBadgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            // Episode count badge
            if (anime.episodes > 0) {
                Text(
                    text = "${anime.episodes} EP",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TvBadgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Title
        Text(
            text = anime.title ?: "Unknown",
            color = TvTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Type + Year row
        if (!anime.type.isNullOrEmpty() || !anime.year.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                if (!anime.type.isNullOrEmpty()) {
                    Text(
                        text = anime.type ?: "",
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (!anime.type.isNullOrEmpty() && !anime.year.isNullOrEmpty()) {
                    Text(
                        text = " · ",
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (!anime.year.isNullOrEmpty()) {
                    Text(
                        text = anime.year ?: "",
                        color = TvTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
