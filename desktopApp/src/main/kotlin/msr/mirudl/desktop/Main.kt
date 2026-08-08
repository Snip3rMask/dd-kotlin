package msr.mirudl.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

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
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Home — search and browse anime")
    }
}

@Composable
fun DownloadsScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Downloads — active and completed downloads")
    }
}

@Composable
fun SettingsScreen() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings — app preferences")
    }
}
