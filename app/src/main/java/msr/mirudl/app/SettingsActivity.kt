package msr.mirudl.app

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import msr.mirudl.shared.storage.StorageSettingsAndroid

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val Error = Color(0xFFD93025)
private val DividerColor = Color(0xFFDADCE0)
private val CardBg = Color(0xFFFFFFFF)
private val SurfaceHigh = Color(0xFFE8EAED)

class SettingsActivity : BaseActivity() {

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val treeUri = result.data?.data
            if (treeUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                StorageSettingsAndroid.setDownloadUri(this, treeUri)
                Toast.makeText(this, "Download folder set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(
                    onBack = { finish() },
                    onPickFolder = { pickFolder() },
                    getVersion = {
                        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }
                        catch (_: Exception) { "" }
                    },
                    onCrashLogs = {
                        try {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                putExtra("showCrashLogs", true)
                            })
                        } catch (_: Exception) {}
                    },
                    onAbout = {
                        try { startActivity(Intent(this, AboutActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left) }
                        catch (_: Exception) {}
                    },
                    onDeveloper = {
                        try { startActivity(Intent(this, DeveloperActivity::class.java)); overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left) }
                        catch (_: Exception) {}
                    },
                    onGitHub = {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/msrofficial/MiruDL-App")))
                        } catch (_: Exception) {}
                    }
                    onDarkModeChanged = { recreate() }
                )
            }
        }
    }

    private fun pickFolder() {
        folderPicker.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    getVersion: () -> String,
    onCrashLogs: () -> Unit,
    onAbout: () -> Unit,
    onDeveloper: () -> Unit,
    onGitHub: () -> Unit,
    onDarkModeChanged: () -> Unit
) {
    val context = LocalContext.current

    var folderUri by remember { mutableStateOf(StorageSettingsAndroid.getDownloadUri(context)) }
    var parallelValue by remember { mutableStateOf(StorageSettingsAndroid.getParallelSegments(context)) }
    var quality by remember { mutableStateOf(StorageSettingsAndroid.getPreferredQuality(context)) }
    var language by remember { mutableStateOf(StorageSettingsAndroid.getPreferredLanguage(context)) }
    var darkMode by remember { mutableStateOf(StorageSettingsAndroid.isDarkTheme(context)) }
    var showParallelWarning by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // STORAGE section
                SectionHeader("STORAGE")
                FolderCard(
                    folderUri = folderUri,
                    onSelectFolder = {
                        onPickFolder()
                        folderUri = StorageSettingsAndroid.getDownloadUri(context)
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // APPEARANCE section
                SectionHeader("APPEARANCE")
                AppearanceCard(
                    darkMode = darkMode,
                    onToggle = {
                        darkMode = it
                        StorageSettingsAndroid.setDarkTheme(context, it)
                        onDarkModeChanged()
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // DOWNLOAD section
                SectionHeader("DOWNLOAD")
                ParallelCard(
                    value = parallelValue,
                    onValueChange = { newValue ->
                        parallelValue = newValue
                        StorageSettingsAndroid.setParallelSegments(context, newValue)
                    },
                    onWarning = { showParallelWarning = true },
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                QualityCard(
                    quality = quality,
                    onQualityChange = { q ->
                        quality = q
                        StorageSettingsAndroid.setPreferredQuality(context, q)
                    },
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                LanguageCard(
                    language = language,
                    onLanguageChange = { l ->
                        language = l
                        StorageSettingsAndroid.setPreferredLanguage(context, l)
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // DEBUGGING section
                SectionHeader("DEBUGGING")
                CrashLogsCard(
                    onClick = onCrashLogs,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // ABOUT section
                SectionHeader("ABOUT")
                AboutCard(
                    version = getVersion(),
                    onClick = onAbout,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // DEVELOPER section
                SectionHeader("DEVELOPER")
                DeveloperCard(
                    onGitHub = onGitHub,
                    onDeveloper = onDeveloper
                )
            }
        }
    }

    if (showParallelWarning) {
        ParallelWarningDialog(
            value = parallelValue,
            onDismiss = { showParallelWarning = false },
            onReduce = {
                showParallelWarning = false
                parallelValue = 32
                StorageSettingsAndroid.setParallelSegments(context, 32)
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(BgColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun FolderCard(
    folderUri: Uri?,
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPath = if (folderUri != null) {
        val path = folderUri.path
        if (path != null && path.contains(":")) path.substring(path.indexOf(":") + 1) else path
    } else null

    CardRow(modifier = modifier) {
        IconBox(iconRes = R.drawable.ic_folder)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Download Folder", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = displayPath ?: "Not selected",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "Select",
            color = Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onSelectFolder() }
                .padding(8.dp)
        )
    }
}

@Composable
private fun AppearanceCard(
    darkMode: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CardRow(modifier = modifier) {
        IconBox(iconRes = R.drawable.ic_moon)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Dark Mode", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (darkMode) "Dark theme active" else "Light theme active",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = darkMode,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Primary,
                checkedTrackColor = Primary.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = SurfaceHigh
            )
        )
    }
}

@Composable
private fun ParallelCard(
    value: Int,
    onValueChange: (Int) -> Unit,
    onWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWarning = value > 32
    val valueColor = if (isWarning) Error else Primary

    CardColumn(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBox(iconRes = R.drawable.ic_sliders)
            Text(
                text = "Download Speed",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 14.dp)
            )
            Text(
                text = value.toString(),
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceAtLeast(1)) },
            onValueChangeFinished = {
                val v = value.coerceAtLeast(1)
                if (v > 32) onWarning()
            },
            valueRange = 1f..64f,
            colors = SliderDefaults.colors(
                thumbColor = valueColor,
                activeTrackColor = valueColor,
                inactiveTrackColor = SurfaceHigh
            ),
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth()
        )
    }
}

@Composable
private fun QualityCard(
    quality: String,
    onQualityChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val qualities = listOf("1080p", "720p", "480p", "360p", "Auto")
    var expanded by remember { mutableStateOf(false) }

    CardRow(modifier = modifier) {
        IconBox(iconRes = R.drawable.ic_hd)
        Text(
            text = "Preferred Quality",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 14.dp)
        )
        Box {
            Text(
                text = quality,
                color = TextTertiary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                items = qualities,
                onSelect = { q ->
                    expanded = false
                    onQualityChange(q)
                }
            )
        }
    }
}

@Composable
private fun LanguageCard(
    language: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val langs = listOf("jpn" to "Sub (Japanese)", "eng" to "Dub (English)")
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = langs.find { it.first == language }?.second ?: language

    CardRow(modifier = modifier) {
        IconBox(iconRes = R.drawable.ic_globe)
        Text(
            text = "Audio Language",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 14.dp)
        )
        Box {
            Text(
                text = displayLabel,
                color = TextTertiary,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                items = langs.map { it.second },
                onSelect = { label ->
                    expanded = false
                    onLanguageChange(langs.find { it.second == label }?.first ?: "jpn")
                }
            )
        }
    }
}

@Composable
private fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<String>,
    onSelect: (String) -> Unit
) {
    if (expanded) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            containerColor = CardBg,
            title = null,
            text = {
                Column {
                    items.forEach { item ->
                        Text(
                            text = item,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = null
        )
    }
}

@Composable
private fun CrashLogsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardRow(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        IconBox(iconRes = R.drawable.ic_bug, isDanger = true)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Crash Logs", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "View app crash reports & error logs",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AboutCard(
    version: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardRow(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text("MiruDL", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Version $version",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DeveloperCard(
    onGitHub: () -> Unit,
    onDarkModeChanged: () -> Unit,
    onDeveloper: () -> Unit
) {
    CardColumn {
        // Developer info
        CardRow(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onDeveloper() }
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.dev_avatar),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text("msrofficial", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Developer",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))

        // GitHub link
        CardRow(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onGitHub() }
                .padding(2.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_terminal),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "GitHub",
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun CardColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun IconBox(iconRes: Int, isDanger: Boolean = false) {
    val bgColor = if (isDanger) Color(0xFFFCEAE9) else Primary.copy(alpha = 0.1f)
    val tint = if (isDanger) Error else Primary

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ParallelWarningDialog(
    value: Int,
    onDismiss: () -> Unit,
    onReduce: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("High Speed Warning", color = TextPrimary) },
        text = {
            Text(
                text = "Setting parallel segments to $value may cause:\n\n" +
                    "\u2022 Rate limiting from the server\n" +
                    "\u2022 Increased data usage\n" +
                    "\u2022 Higher battery consumption\n" +
                    "\u2022 Possible download failures on slow connections\n\n" +
                    "Use only if you have a stable, high-speed internet connection.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Use Anyway", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onReduce) {
                Text("Reduce", color = Primary)
            }
        }
    )
}
