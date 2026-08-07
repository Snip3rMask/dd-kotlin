package msr.mirudl.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import msr.mirudl.shared.download.DownloadManager
import msr.mirudl.shared.download.Job
import msr.mirudl.shared.model.EpisodeItem
import msr.mirudl.shared.model.VideoSource
import msr.mirudl.shared.network.MiruClient
import msr.mirudl.shared.storage.AppStorage
import msr.mirudl.shared.storage.DownloadEntryStore
import msr.mirudl.shared.storage.StorageSettings
import java.util.Locale

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val OnPrimary = Color(0xFFFFFFFF)
private val DividerColor = Color(0xFFDADCE0)
private val CardBg = Color(0xFFFFFFFF)
private val SurfaceVariant = Color(0xFFF0F1F3)
private val ChipBg = Color(0xFFE8F4FD)

class DetailActivity : BaseActivity() {

    private var animeId: String? = null
    private var animeTitle: String? = null
    private var animeThumb: String? = null
    private var animeUrl: String? = null

    private var allEpisodes: MutableList<EpisodeItem> = mutableListOf()

    private var displayEpisodes by mutableStateOf<List<EpisodeItem>>(emptyList())
    private var gridMode by mutableStateOf(false)
    private var selectionMode by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var emptyMessage by mutableStateOf("")
    private var downloadedEpisodes by mutableStateOf<Set<String>>(emptySet())
    private var showSearchRow by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var reversed by mutableStateOf(false)
    private var selectedCount by mutableStateOf(0)
    private var showDownloadAllDialog by mutableStateOf(false)
    private var showFolderDialog by mutableStateOf(false)

    private val storage: AppStorage
        get() = msr.mirudl.shared.storage.AndroidAppStorage(this, "mirudl_settings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        animeId = intent.getStringExtra("anime_id")
        animeTitle = intent.getStringExtra("anime_title")
        animeThumb = intent.getStringExtra("anime_thumb")
        animeUrl = intent.getStringExtra("anime_url")

        downloadedEpisodes = currentDownloadedSet()

        setContent {
            MaterialTheme {
                DetailScreen(
                    title = animeTitle ?: "",
                    thumbUrl = animeThumb,
                    episodes = displayEpisodes,
                    gridMode = gridMode,
                    selectionMode = selectionMode,
                    selectedCount = selectedCount,
                    downloadedEpisodes = downloadedEpisodes,
                    isLoading = isLoading,
                    emptyMessage = emptyMessage,
                    showSearchRow = showSearchRow,
                    searchQuery = searchQuery,
                    reversed = reversed,
                    episodeCount = allEpisodes.size,
                    onBack = { finish() },
                    onSearchToggle = {
                        showSearchRow = !showSearchRow
                        if (!showSearchRow) {
                            searchQuery = ""
                            rebuildDisplayList()
                        }
                    },
                    onSearchQueryChange = { q ->
                        searchQuery = q
                        rebuildDisplayList()
                    },
                    onReverse = {
                        reversed = !reversed
                        rebuildDisplayList()
                    },
                    onSelectMode = { enterSelectionMode() },
                    onSelectClose = { exitSelectionMode() },
                    onSelectAll = { selectAllVisible() },
                    onDownloadAll = {
                        if (allEpisodes.isEmpty()) {
                            Toast.makeText(this, "No episodes available", Toast.LENGTH_SHORT).show()
                        } else if (!StorageSettings.hasDownloadUri(storage)) {
                            showFolderDialog = true
                        } else {
                            showDownloadAllDialog = true
                        }
                    },
                    onDownloadSelected = {
                        val sel = getSelectedEpisodes()
                        if (sel.isEmpty()) {
                            Toast.makeText(this, "Select at least one episode", Toast.LENGTH_SHORT).show()
                        } else if (!StorageSettings.hasDownloadUri(storage)) {
                            showFolderDialog = true
                        } else {
                            downloadSelectedEpisodes(sel)
                            exitSelectionMode()
                        }
                    },
                    onEpisodeClick = { ep -> handleEpisodeClick(ep) }
                )
            }

            if (showDownloadAllDialog) {
                DownloadAllDialog(
                    count = allEpisodes.size,
                    onConfirm = { showDownloadAllDialog = false; downloadAllEpisodes() },
                    onDismiss = { showDownloadAllDialog = false }
                )
            }
            if (showFolderDialog) {
                FolderRequiredDialog(
                    onOpenSettings = {
                        showFolderDialog = false
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onDismiss = { showFolderDialog = false }
                )
            }
        }

        loadEpisodes()
    }

    private fun rebuildDisplayList() {
        val filtered = if (searchQuery.isEmpty()) {
            allEpisodes.toMutableList()
        } else {
            val lower = searchQuery.lowercase(Locale.US)
            allEpisodes.filter { ep ->
                ep.getLabel().contains(searchQuery) ||
                    (ep.title?.lowercase(Locale.US)?.contains(lower) == true)
            }.toMutableList()
        }
        if (reversed) filtered.reverse()
        displayEpisodes = filtered
    }

    private fun handleEpisodeClick(ep: EpisodeItem) {
        if (selectionMode) {
            ep.selected = !ep.selected
            displayEpisodes = displayEpisodes.toList()
            selectedCount = getSelectedEpisodes().size
        } else {
            if (ep.hlsUrl != null) {
                startDownload(ep)
            } else {
                resolveAndDownload(ep)
            }
        }
    }

    private fun selectAllVisible() {
        for (ep in displayEpisodes) ep.selected = true
        displayEpisodes = displayEpisodes.toList()
        selectedCount = getSelectedEpisodes().size
    }

    private fun getSelectedEpisodes(): List<EpisodeItem> {
        return allEpisodes.filter { it.selected }
    }

    private fun enterSelectionMode() {
        selectionMode = true
        selectedCount = 0
    }

    private fun exitSelectionMode() {
        selectionMode = false
        for (ep in allEpisodes) ep.selected = false
        displayEpisodes = displayEpisodes.toList()
        selectedCount = 0
    }

    private fun loadEpisodes() {
        isLoading = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MiruClient.getEpisodes(animeId!!)
                }
                allEpisodes = result.toMutableList()
                isLoading = false
                gridMode = allEpisodes.size > GRID_THRESHOLD
                rebuildDisplayList()
                if (allEpisodes.isEmpty()) emptyMessage = "No episodes found"
            } catch (e: Exception) {
                isLoading = false
                emptyMessage = "Error: ${e.message}"
            }
        }
    }

    private fun resolveAndDownload(ep: EpisodeItem) {
        lifecycleScope.launch {
            try {
                val langs = withContext(Dispatchers.IO) {
                    MiruClient.getEpisodeLanguages(ep.id)
                }
                if (langs.isEmpty()) {
                    Toast.makeText(this@DetailActivity, "No sources found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val prefLang = StorageSettings.getPreferredLanguage(storage)
                var selected: VideoSource? = null
                for (vs in langs) { if (vs.language == prefLang) { selected = vs; break } }
                if (selected == null) selected = langs[0]
                val hlsUrl = withContext(Dispatchers.IO) {
                    MiruClient.resolveHlsFromEmbed(selected.url)
                }
                if (hlsUrl == null) {
                    Toast.makeText(this@DetailActivity, "No HLS URL found", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                ep.hlsUrl = hlsUrl; ep.language = selected.language
                ep.langName = selected.quality; ep.embedUrl = selected.url
                startDownload(ep)
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownload(ep: EpisodeItem) {
        if (!StorageSettings.hasDownloadUri(storage)) { showFolderDialog = true; return }
        val quality = StorageSettings.getPreferredQuality(storage)
        val language = ep.language ?: "jpn"
        val hlsUrl = ep.hlsUrl
        lifecycleScope.launch {
            try {
                val variants = withContext(Dispatchers.IO) { MiruClient.getQualities(hlsUrl!!) }
                var finalUrl = hlsUrl!!
                for (v in variants) { if (v.quality.contains(quality.replace("p", ""))) { finalUrl = v.url; break } }
                queueDownload(ep, finalUrl, quality, language)
            } catch (_: Exception) { queueDownload(ep, hlsUrl!!, quality, language) }
        }
    }

    private fun queueDownload(ep: EpisodeItem, hlsUrl: String, quality: String, language: String) {
        val label = "Episode ${ep.getLabel()}"
        val exists = DownloadManager.findByAnimeAndEpisode(animeTitle, ep.getLabel())
        if (exists != null) {
            val s = exists.status
            if (s == Job.STATUS_COMPLETED) Toast.makeText(this, "Already downloaded: $label", Toast.LENGTH_SHORT).show()
            else if (s == Job.STATUS_QUEUED || s == Job.STATUS_DOWNLOADING) Toast.makeText(this, "Already queued: $label", Toast.LENGTH_SHORT).show()
            return
        }
        DownloadManager.enqueue(animeTitle, label, quality, language, hlsUrl)
        downloadedEpisodes = currentDownloadedSet()
        val intent = DownloadService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Queued: $label", Toast.LENGTH_SHORT).show()
    }

    private fun downloadAllEpisodes() { enqueueEpisodes(allEpisodes, "${allEpisodes.size} episodes queued") }
    private fun downloadSelectedEpisodes(selected: List<EpisodeItem>) { enqueueEpisodes(selected, "${selected.size} episode(s) queued") }

    private fun enqueueEpisodes(list: List<EpisodeItem>, successMessage: String) {
        lifecycleScope.launch {
            try {
                val jobs = mutableListOf<Job>()
                val prefLang = StorageSettings.getPreferredLanguage(storage)
                for (ep in list) {
                    try {
                        var hlsUrl = ep.hlsUrl; var language = ep.language
                        if (hlsUrl == null) {
                            val langs = withContext(Dispatchers.IO) { MiruClient.getEpisodeLanguages(ep.id) }
                            var sel: VideoSource? = null
                            for (vs in langs) { if (vs.language == prefLang) { sel = vs; break } }
                            if (sel == null && langs.isNotEmpty()) sel = langs[0]
                            if (sel == null) continue
                            hlsUrl = withContext(Dispatchers.IO) { MiruClient.resolveHlsFromEmbed(sel.url) } ?: continue
                            language = sel.language; ep.hlsUrl = hlsUrl; ep.language = language; ep.langName = sel.quality
                        }
                        jobs.add(DownloadManager.enqueue(animeTitle, "Episode ${ep.getLabel()}", StorageSettings.getPreferredQuality(storage), language ?: "jpn", hlsUrl!!))
                    } catch (_: Exception) {}
                }
                if (jobs.isNotEmpty()) {
                    val intent = DownloadService.startIntent(this@DetailActivity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    downloadedEpisodes = currentDownloadedSet()
                    Toast.makeText(this@DetailActivity, successMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(this@DetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun currentDownloadedSet(): Set<String> {
        val downloaded = mutableSetOf<String>()
        for (e in DownloadEntryStore.all(storage)) {
            if (animeTitle != null && animeTitle == e.parentName()) {
                val epLabel = (e.title ?: "").replace("Episode ", "").trim()
                if (epLabel.isNotEmpty()) downloaded.add(epLabel)
            }
        }
        return downloaded
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selectionMode) { exitSelectionMode(); return }
        super.onBackPressed()
    }

    companion object { private const val GRID_THRESHOLD = 40 }
}

// ── COMPOSE UI ──

@Composable
private fun DetailScreen(
    title: String, thumbUrl: String?, episodes: List<EpisodeItem>,
    gridMode: Boolean, selectionMode: Boolean, selectedCount: Int,
    downloadedEpisodes: Set<String>, isLoading: Boolean, emptyMessage: String,
    showSearchRow: Boolean, searchQuery: String, reversed: Boolean,
    episodeCount: Int,
    onBack: () -> Unit, onSearchToggle: () -> Unit, onSearchQueryChange: (String) -> Unit,
    onReverse: () -> Unit, onSelectMode: () -> Unit, onSelectClose: () -> Unit,
    onSelectAll: () -> Unit, onDownloadAll: () -> Unit,
    onDownloadSelected: () -> Unit, onEpisodeClick: (EpisodeItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        // Header
        DetailHeader(title = title, onBack = onBack)

        // Scrollable content (anime info + episode controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Anime info card
            if (thumbUrl != null) {
                AnimeInfoCard(
                    thumbUrl = thumbUrl, title = title,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // Episodes section header
            EpisodeSectionHeader(
                episodeCount = episodeCount,
                reversed = reversed,
                onSearchToggle = onSearchToggle,
                onReverse = onReverse,
                onSelectMode = onSelectMode
            )

            // Search row
            if (showSearchRow) {
                SearchRow(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Download all button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDownloadAll() },
                color = Primary
            ) {
                Text(
                    text = "Download All",
                    color = OnPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
        }

        // Episode list (takes remaining space)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (episodes.isEmpty() && !isLoading) {
                Text(text = emptyMessage, color = TextTertiary, fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center))
            } else if (gridMode) {
                EpisodeGrid(episodes = episodes, selectionMode = selectionMode,
                    downloadedEpisodes = downloadedEpisodes, onEpisodeClick = onEpisodeClick)
            } else {
                EpisodeList(episodes = episodes, selectionMode = selectionMode,
                    downloadedEpisodes = downloadedEpisodes, onEpisodeClick = onEpisodeClick)
            }
            if (isLoading) {
                CircularProgressIndicator(color = Primary,
                    modifier = Modifier.size(36.dp).align(Alignment.Center))
            }
        }

        // Select bar
        if (selectionMode) {
            SelectBar(
                selectedCount = selectedCount,
                onClose = onSelectClose,
                onSelectAll = onSelectAll,
                onDownload = onDownloadSelected
            )
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(BgColor).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_back), contentDescription = "Back",
                tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
        Text(text = title, color = TextPrimary, fontSize = 16.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp, end = 16.dp))
    }
}

@Composable
private fun AnimeInfoCard(thumbUrl: String, title: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CardBg) {
        Row(modifier = Modifier.padding(16.dp)) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        Glide.with(ctx).load(thumbUrl).centerCrop().into(this)
                    }
                },
                modifier = Modifier.size(96.dp, 136.dp).clip(RoundedCornerShape(14.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(text = title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                // meta, description etc could be added here if needed
            }
        }
    }
}

@Composable
private fun EpisodeSectionHeader(
    episodeCount: Int, reversed: Boolean,
    onSearchToggle: () -> Unit, onReverse: () -> Unit, onSelectMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Episodes", color = TextPrimary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (episodeCount > 0) {
            Text(text = "$episodeCount episodes", color = TextTertiary, fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp))
        }
        // Search button
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
            .background(CardBg).clickable { onSearchToggle() },
            contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = R.drawable.ic_search), contentDescription = "Search",
                tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(4.dp))
        // Reverse button
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
            .background(CardBg).clickable { onReverse() },
            contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = R.drawable.ic_swap_vert), contentDescription = "Reverse",
                tint = if (reversed) Primary else TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Select button
        Surface(shape = RoundedCornerShape(17.dp), color = ChipBg,
            modifier = Modifier.clickable { onSelectMode() }) {
            Text(text = "Select", color = Primary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange,
        placeholder = { Text("Search episode number or title", color = TextTertiary, fontSize = 13.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary, unfocusedBorderColor = DividerColor,
            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
            cursorColor = Primary, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        ),
        modifier = modifier.fillMaxWidth().height(42.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
    )
}

@Composable
private fun SelectBar(
    selectedCount: Int, onClose: () -> Unit,
    onSelectAll: () -> Unit, onDownload: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = CardBg, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(17.dp))
                .background(CardBg).clickable { onClose() },
                contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.ic_close), contentDescription = "Close",
                    tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            Text(text = "$selectedCount selected", color = TextPrimary, fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Surface(shape = RoundedCornerShape(19.dp), color = ChipBg,
                modifier = Modifier.clickable { onSelectAll() }) {
                Text(text = "Select All", color = TextPrimary, fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(19.dp), color = Primary,
                modifier = Modifier.clickable { onDownload() }) {
                Text(text = "Download", color = OnPrimary, fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun DownloadAllDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Download All Episodes", color = TextPrimary) },
        text = { Text("Start downloading all $count episodes?", color = TextSecondary, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Download All ($count)", color = Primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextTertiary) } }
    )
}

@Composable
private fun FolderRequiredDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Download Folder Required", color = TextPrimary) },
        text = { Text("Please select a download folder in Settings first.", color = TextSecondary, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("Open Settings", color = Primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextTertiary) } }
    )
}
