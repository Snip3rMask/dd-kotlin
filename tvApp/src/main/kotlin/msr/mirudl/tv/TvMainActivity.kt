package msr.mirudl.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.EpisodeItem

class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Simple state-based navigation (no Nav component needed for TV placeholder)
                var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Home) }

                when (val screen = currentScreen) {
                    is TvScreen.Home -> {
                        TvHomeScreen(
                            animeList = sampleAnime,
                            isLoading = false,
                            onAnimeClick = { anime ->
                                currentScreen = TvScreen.Detail(anime)
                            }
                        )
                    }
                    is TvScreen.Detail -> {
                        TvDetailScreen(
                            anime = screen.anime,
                            episodes = sampleEpisodes,
                            languages = listOf("jpn", "eng"),
                            selectedLanguage = "jpn",
                            isLoading = false,
                            emptyMessage = "No episodes available",
                            onLanguageSelected = { },
                            onEpisodeClick = { },
                            onBack = { currentScreen = TvScreen.Home }
                        )
                    }
                }
            }
        }
    }
}

private sealed class TvScreen {
    object Home : TvScreen()
    data class Detail(val anime: AnimeItem) : TvScreen()
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
