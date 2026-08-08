package msr.mirudl.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.EpisodeItem

private val TvBgColor = Color(0xFF1A1A2E)
private val TvFocusBorder = Color(0xFF0EA5E9)
private val TvTabActive = Color(0xFFE0E0E0)
private val TvTabInactive = Color(0xFF80868B)

class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                var currentDetail by remember { mutableStateOf<AnimeItem?>(null) }

                // If viewing a detail screen, show that full-screen
                if (currentDetail != null) {
                    TvDetailScreen(
                        anime = currentDetail!!,
                        episodes = sampleEpisodes,
                        languages = listOf("jpn", "eng"),
                        selectedLanguage = "jpn",
                        isLoading = false,
                        emptyMessage = "No episodes available",
                        onLanguageSelected = { },
                        onEpisodeClick = { },
                        onBack = { currentDetail = null }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().background(TvBgColor)) {
                        // Top tab bar
                        TvTabBar(
                            tabs = listOf("Home", "Downloads", "Settings"),
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                        // Content
                        when (selectedTab) {
                            0 -> TvHomeScreen(
                                animeList = sampleAnime,
                                isLoading = false,
                                onAnimeClick = { anime -> currentDetail = anime }
                            )
                            1 -> TvDownloadsScreen(
                                items = sampleDownloads,
                                onCancelClick = { },
                                onOpenClick = { },
                                onDeleteClick = { }
                            )
                            2 -> TvSettingsScreen(
                                settings = sampleSettings,
                                onSettingClick = { }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvTabBar(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvBgColor)
            .padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MiruDL",
            color = TvFocusBorder,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(32.dp))
        tabs.forEachIndexed { index, title ->
            TvTabItem(
                title = title,
                selected = index == selectedTab,
                onClick = { onTabSelected(index) }
            )
            if (index < tabs.lastIndex) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun TvTabItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val textColor = when {
        selected -> TvTabActive
        isFocused -> TvFocusBorder
        else -> TvTabInactive
    }
    val borderColor = if (isFocused || selected) TvFocusBorder else Color.Transparent

    Text(
        text = title,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private val sampleAnime = listOf(
    AnimeItem(id = "1", title = "Naruto Shippuden", rating = "9.1", episodes = 500, type = "TV", year = "2007"),
    AnimeItem(id = "2", title = "One Piece", rating = "9.0", episodes = 1100, type = "TV", year = "1999"),
    AnimeItem(id = "3", title = "Attack on Titan", rating = "9.2", episodes = 87, type = "TV", year = "2013"),
    AnimeItem(id = "4", title = "Demon Slayer", rating = "8.8", episodes = 55, type = "TV", year = "2019"),
    AnimeItem(id = "5", title = "Jujutsu Kaisen", rating = "8.9", episodes = 47, type = "TV", year = "2020"),
    AnimeItem(id = "6", title = "Death Note", rating = "9.0", episodes = 37, type = "TV", year = "2006"),
    AnimeItem(id = "7", title = "Fullmetal Alchemist", rating = "9.2", episodes = 64, type = "TV", year = "2009"),
    AnimeItem(id = "8", title = "Steins;Gate", rating = "9.1", episodes = 24, type = "TV", year = "2011"),
    AnimeItem(id = "9", title = "Sword Art Online", rating = "7.5", episodes = 96, type = "TV", year = "2012"),
    AnimeItem(id = "10", title = "My Hero Academia", episodes = 138, type = "TV", year = "2016"),
    AnimeItem(id = "11", title = "Hunter x Hunter", rating = "9.1", episodes = 148, type = "TV", year = "2011"),
    AnimeItem(id = "12", title = "Bleach", rating = "8.5", episodes = 366, type = "TV", year = "2004"),
)

private val sampleEpisodes = (1..24).map { i ->
    EpisodeItem(
        id = i,
        number = i,
        filler = i in listOf(13, 14, 20),
        langName = if (i % 3 == 0) "eng" else "jpn"
    )
}

private val sampleDownloads = listOf(
    TvDownloadItem.Active("Naruto Shippuden - Ep 1", "Downloading HLS segments...", 67, "2.4 MB/s"),
    TvDownloadItem.Completed("Attack on Titan - Ep 1", "142 MB"),
    TvDownloadItem.Completed("Demon Slayer - Ep 5", "98 MB"),
)

private val sampleSettings = listOf(
    TvSettingItem("Download Folder", "Current: ~/Downloads/MiruDL"),
    TvSettingItem("Default Quality", "Best available"),
    TvSettingItem("Default Language", "Japanese"),
    TvSettingItem("Parallel Downloads", "4 concurrent"),
    TvSettingItem("App Version", "1.0.0 (TV)"),
    TvSettingItem("About", "MiruDL — Anime downloader"),
)
