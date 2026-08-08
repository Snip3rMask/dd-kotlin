package msr.mirudl.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.ui.*

fun main() = application {
    val state = rememberWindowState(width = 900.dp, height = 600.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "MiruDL"
    ) {
        MaterialTheme {
            DesktopApp()
        }
    }
}

@Composable
fun DesktopApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Home", "Downloads", "Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        when (selectedTab) {
            0 -> HomeScreen()
            1 -> DownloadsScreen()
            2 -> SettingsScreen()
        }
    }
}

@Composable
fun HomeScreen() {
    SharedAnimeGridContent(
        animeList = emptyList(),
        isLoading = false,
        emptyMessage = "Search anime to get started",
        onAnimeClick = { }
    )
}

@Composable
fun DownloadsScreen() {
    SharedDownloadsContent(
        items = listOf(DownloadsItem.Empty),
        onCancelJob = { },
        onOpenFile = { },
        onDeleteFile = { },
        onToggleFolder = { },
        onGroupDeleteRequest = { },
        onClearFinished = { },
        folderIcon = ColorPainter(Color.Gray),
        closeIcon = ColorPainter(Color.Red),
        deleteIcon = ColorPainter(Color.Gray),
        chevronDownIcon = ColorPainter(Color.DarkGray),
        chevronRightIcon = ColorPainter(Color.LightGray)
    )
}

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings — desktop preferences")
    }
}
