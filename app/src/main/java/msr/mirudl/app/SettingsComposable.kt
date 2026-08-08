package msr.mirudl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFFF8F9FA)
private val CardBg = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val ErrorColor = Color(0xFFD93025)
private val IconBg = Color(0xFFF0F1F3)
private val DividerColor = Color(0xFFDADCE0)
private val WarningDot = Color(0xFFD93025)

@Composable
fun SettingsContent(
    folderPath: String,
    isDarkMode: Boolean,
    parallelSegments: Int,
    concurrentDownloads: Int,
    preferredQuality: String,
    preferredLanguage: String,
    hasNewCrash: Boolean,
    updateStatusText: String,
    hasUpdate: Boolean,
    isCheckingUpdate: Boolean,
    appVersion: String,
    onSelectFolder: () -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onParallelChanged: (Int) -> Unit,
    onConcurrentChanged: (Int) -> Unit,
    onQualityChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onShowCrashLogs: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenGithub: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
            )
        }

        // ===================== STORAGE =====================
        item {
            SectionHeader("STORAGE")
            Spacer(modifier = Modifier.height(10.dp))
            StorageCard(folderPath = folderPath, onSelectFolder = onSelectFolder)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ===================== APPEARANCE =====================
        item {
            SectionHeader("APPEARANCE")
            Spacer(modifier = Modifier.height(10.dp))
            AppearanceCard(isDarkMode = isDarkMode, onCheckedChange = onDarkModeChanged)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ===================== DOWNLOAD =====================
        item {
            SectionHeader("DOWNLOAD")
            Spacer(modifier = Modifier.height(10.dp))
            CardContainer {
                SliderRow(
                    icon = R.drawable.ic_sliders,
                    title = "Download Speed",
                    value = parallelSegments.toFloat(),
                    range = 1f..64f,
                    displayText = parallelSegments.toString(),
                    onValueChange = { onParallelChanged(it.toInt()) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CardContainer {
                SliderRow(
                    icon = R.drawable.ic_download,
                    title = "Concurrent Downloads",
                    subtitle = "Episodes downloading at the same time",
                    value = concurrentDownloads.toFloat(),
                    range = 1f..5f,
                    displayText = concurrentDownloads.toString(),
                    onValueChange = { onConcurrentChanged(it.toInt()) }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CardContainer {
                DropdownRow(
                    icon = R.drawable.ic_hd,
                    title = "Preferred Quality",
                    options = listOf("1080p", "720p", "480p", "360p", "Auto"),
                    selected = preferredQuality,
                    onSelected = onQualityChanged
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CardContainer {
                DropdownRow(
                    icon = R.drawable.ic_globe,
                    title = "Preferred Language",
                    options = listOf("Sub (Japanese)", "Dub (English)"),
                    values = listOf("jpn", "eng"),
                    selected = if (preferredLanguage == "eng") "Dub (English)" else "Sub (Japanese)",
                    onSelected = { label ->
                        val values = listOf("jpn", "eng")
                        val labels = listOf("Sub (Japanese)", "Dub (English)")
                        val idx = labels.indexOf(label)
                        if (idx >= 0) onLanguageChanged(values[idx])
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ===================== DEBUGGING =====================
        item {
            SectionHeader("DEBUGGING")
            Spacer(modifier = Modifier.height(10.dp))
            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowCrashLogs() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconSquare(icon = R.drawable.ic_bug, tint = ErrorColor, bgColor = Color(0x22D93025))
                        if (hasNewCrash) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .background(WarningDot, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crash Logs", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("View app crash reports & error logs", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ===================== ABOUT =====================
        item {
            SectionHeader("ABOUT")
            Spacer(modifier = Modifier.height(10.dp))
            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAbout() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconSquare(icon = R.mipmap.ic_launcher_foreground, tint = Primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MiruDL", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Version $appVersion", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Check for Updates
            CardContainer {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCheckUpdates() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconSquare(icon = R.drawable.ic_refresh, tint = Primary)
                        if (hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .background(WarningDot, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for Updates", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(updateStatusText, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (isCheckingUpdate) {
                        LinearProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Primary,
                            trackColor = IconBg
                        )
                    } else {
                        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ===================== DEVELOPER =====================
        item {
            SectionHeader("DEVELOPER")
            Spacer(modifier = Modifier.height(10.dp))
            CardContainer {
                // Developer info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDeveloper() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconSquare(icon = R.drawable.ic_globe, tint = Primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("msrofficial", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Developer", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 8.dp)
                        .background(DividerColor)
                )

                // GitHub link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGithub() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_terminal),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("GitHub", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ── Section header ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

// ── Card container ──

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
    ) {
        content()
    }
}

// ── Storage card ──

@Composable
private fun StorageCard(folderPath: String, onSelectFolder: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSquare(icon = R.drawable.ic_folder, tint = Primary)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Download Folder", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = folderPath,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "Select",
            color = Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x1A0EA5E9))
                .clickable { onSelectFolder() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

// ── Appearance card ──

@Composable
private fun AppearanceCard(isDarkMode: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconSquare(icon = R.drawable.ic_moon, tint = Color(0xFF7C4DFF))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Dark Mode", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (isDarkMode) "Dark theme active" else "Light theme active",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = isDarkMode,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = DividerColor,
                uncheckedThumbColor = Color.White
            )
        )
    }
}

// ── Slider row ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayText: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquare(icon = icon, tint = Primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text(displayText, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = DividerColor
            ),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ── Dropdown row ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(
    icon: Int,
    title: String,
    options: List<String>,
    values: List<String>? = null,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconSquare(icon = icon, tint = Primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(selected, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Icon square ──

@Composable
private fun IconSquare(icon: Int, tint: Color, bgColor: Color = IconBg) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
