package msr.mirudl.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import msr.mirudl.shared.model.AnimeItem

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val Primary = Color(0xFF0EA5E9)
private val CardBg = Color(0xFFF0F1F3)

@Composable
fun SharedAnimeGridContent(
    animeList: List<AnimeItem>,
    isLoading: Boolean,
    emptyMessage: String,
    onAnimeClick: (AnimeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(BgColor)) {
        if (animeList.isEmpty() && !isLoading) {
            Text(
                text = emptyMessage,
                color = Color(0xFF80868B),
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(animeList) { anime ->
                    SharedAnimeGridCard(anime = anime, onClick = { onAnimeClick(anime) })
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(40.dp).align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SharedAnimeGridCard(anime: AnimeItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFCFDFE))
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AsyncImage(
                url = anime.thumbnail ?: "",
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                placeholderColor = CardBg
            )
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
                        .background(Color(0x88000000))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
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
                        .background(Color(0x88000000))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = anime.title ?: "Unknown",
            color = TextPrimary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
        )
    }
}
