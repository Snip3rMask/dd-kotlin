package msr.mirudl.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import msr.mirudl.shared.download.DownloadManager
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.DownloadRecord
import msr.mirudl.shared.network.MiruClient
import msr.mirudl.shared.network.UpdateChecker
import msr.mirudl.shared.storage.AppStorage
import msr.mirudl.shared.storage.AndroidAppStorage
import msr.mirudl.shared.storage.DownloadEntryStore
import msr.mirudl.shared.storage.StorageSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    // Tabs
    private var currentTab by mutableIntStateOf(0)
    private var crashBadgeVisible by mutableStateOf(false)

    // Home
    private var searchQuery by mutableStateOf("")
    private var animeList by mutableStateOf<List<AnimeItem>>(emptyList())
    private var homeLoading by mutableStateOf(true)
    private var homeEmptyMessage by mutableStateOf("")

    // Downloads
    private var downloadsItems by mutableStateOf<List<DownloadsItem>>(emptyList())
    private val expandedFolders = mutableSetOf<String>()

    // Settings
    private var folderPathDisplay by mutableStateOf("Not selected")
    private var isDarkMode by mutableStateOf(false)
    private var parallelSegments by mutableIntStateOf(1)
    private var concurrentDownloads by mutableIntStateOf(1)
    private var preferredQuality by mutableStateOf("1080p")
    private var preferredLanguage by mutableStateOf("jpn")
    private var updateStatusText by mutableStateOf("Tap to check for a newer version")
    private var hasUpdate by mutableStateOf(false)
    private var isCheckingUpdate by mutableStateOf(false)


    // Periodic refresh
    private var refreshJob: Job? = null

    private val storage: AppStorage
        get() = AndroidAppStorage(this, "mirudl_settings")

    // Folder picker
    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val treeUri = result.data?.data
            if (treeUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                StorageSettings.setDownloadUri(storage, treeUri.toString())
                updateFolderDisplay()
                Toast.makeText(this, "Download folder set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        try {
            onCreateInternal(savedInstanceState)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Startup Error")
                .setMessage("${e.javaClass.simpleName}: ${e.message}")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            CrashLogger.saveCaughtException(this, Thread.currentThread(), e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab", currentTab)
    }

    private fun onCreateInternal(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize settings state from storage
        isDarkMode = StorageSettings.isDarkTheme(storage)
        parallelSegments = StorageSettings.getParallelSegments(storage)
        concurrentDownloads = StorageSettings.getConcurrentDownloads(storage)
        preferredQuality = StorageSettings.getPreferredQuality(storage)
        preferredLanguage = StorageSettings.getPreferredLanguage(storage)
        updateFolderDisplay()
        updateCrashBadgeState()

        // Restore tab from saved state
        currentTab = savedInstanceState?.getInt("current_tab", 0) ?: 0

        // Load search input state from saved state
        searchQuery = savedInstanceState?.getString("search_query") ?: ""

        setContent {
            val currentTabState = remember { mutableIntStateOf(savedInstanceState?.getInt("current_tab", 0) ?: 0) }

            MaterialTheme {
                val navBarColor = Color(0xFFF8F9FA)
                val bottomBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = navBarColor,
                            modifier = Modifier.padding(bottom = bottomBarBottomPadding)
                        ) {
                            NavigationBarItem(
                                icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = "Home") },
                                label = { Text("Home") },
                                selected = currentTabState.intValue == 0,
                                onClick = {
                                    if (currentTabState.intValue != 0) {
                                        currentTabState.intValue = 0
                                        currentTab = 0
                                        loadPopular()
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = "Downloads") },
                                label = { Text("Downloads") },
                                selected = currentTabState.intValue == 1,
                                onClick = {
                                    if (currentTabState.intValue != 1) {
                                        currentTabState.intValue = 1
                                        currentTab = 1
                                        refreshDownloads()
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (crashBadgeVisible) {
                                                Badge(containerColor = Color(0xFFD93025))
                                            }
                                        }
                                    ) {
                                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                                    }
                                },
                                label = { Text("Settings") },
                                selected = currentTabState.intValue == 2,
                                onClick = {
                                    if (currentTabState.intValue != 2) {
                                        currentTabState.intValue = 2
                                        currentTab = 2
                                        updateFolderDisplay()
                                    }
                                }
                            )
                        }
                    }
                ) { padding ->
                    when (currentTabState.intValue) {
                        0 -> HomeTabContent(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSearch = { searchAnime(it) },
                            onAnimeClick = { showAnimeDetail(it) },
                            animeList = animeList,
                            isLoading = homeLoading,
                            emptyMessage = homeEmptyMessage,
                            modifier = Modifier.padding(padding)
                        )
                        1 -> DownloadsTabContent(
                            modifier = Modifier.padding(padding)
                        )
                        2 -> SettingsTabContent(
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }

        // Initialize home & downloads tabs (wiring only — no XML)
        initHomeTab()
        initDownloadsTab()

        // Silent update check
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
                if (info != null) {
                    val cached = withContext(Dispatchers.IO) { UpdateChecker.readCache(storage) }
                    val isNew = cached == null || UpdateChecker.isNewerVersion(info.tag, cached.tag)
                    withContext(Dispatchers.Main) {
                        refreshUpdateSectionState(if (isNew) info else null)
                        if (isNew) showUpdateDialog(info)
                    }
                } else {
                    withContext(Dispatchers.Main) { refreshUpdateSectionState(null) }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { refreshUpdateSectionState(null) }
            }
        }

        // Load initial content
        if (currentTab == 0) loadPopular()
    }

    // ── Compose tab contents ──

    @Composable
    private fun HomeTabContent(
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        onAnimeClick: (AnimeItem) -> Unit,
        animeList: List<AnimeItem>,
        isLoading: Boolean,
        emptyMessage: String,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFFFFFFF))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Color(0xFF80868B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            textStyle = TextStyle(color = Color(0xFF202124), fontSize = 15.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search anime...", color = Color(0xFF80868B), fontSize = 15.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // Anime grid
            AnimeGridContent(
                animeList = animeList,
                isLoading = isLoading,
                emptyMessage = emptyMessage,
                onAnimeClick = onAnimeClick
            )
        }
    }

    @Composable
    private fun DownloadsTabContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            // Clear finished button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F9FA))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Clear Finished",
                    color = Color(0xFF0EA5E9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearFinished() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            DownloadsContent(
                items = downloadsItems,
                onCancelJob = { cancelDownloadJob(it) },
                onOpenFile = { openDownloadFile(it) },
                onDeleteFile = { showDeleteDownloadDialog(DownloadEntry.fromRecord(it)) },
                onToggleFolder = { toggleDownloadFolder(it) },
                onGroupDeleteRequest = { requestGroupDelete(it) },
                onClearFinished = { clearFinished() }
            )
        }
    }

    @Composable
    private fun SettingsTabContent(modifier: Modifier = Modifier) {
        Box(modifier = modifier.fillMaxSize()) {
            SettingsContent(
                folderPath = folderPathDisplay,
                isDarkMode = isDarkMode,
                parallelSegments = parallelSegments,
                concurrentDownloads = concurrentDownloads,
                preferredQuality = preferredQuality,
                preferredLanguage = preferredLanguage,
                hasNewCrash = crashBadgeVisible,
                updateStatusText = updateStatusText,
                hasUpdate = hasUpdate,
                isCheckingUpdate = isCheckingUpdate,
                appVersion = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" },
                onSelectFolder = { pickFolder() },
                onDarkModeChanged = { checked ->
                    isDarkMode = checked
                    StorageSettings.setDarkTheme(storage, checked)
                    recreate()
                },
                onParallelChanged = { value ->
                    parallelSegments = value
                    StorageSettings.setParallelSegments(storage, value)
                },
                onConcurrentChanged = { value ->
                    concurrentDownloads = value
                    StorageSettings.setConcurrentDownloads(storage, value)
                },
                onQualityChanged = { value ->
                    preferredQuality = value
                    StorageSettings.setPreferredQuality(storage, value)
                },
                onLanguageChanged = { value ->
                    preferredLanguage = value
                    StorageSettings.setPreferredLanguage(storage, value)
                },
                onShowCrashLogs = { showCrashLogsDialog() },
                onCheckUpdates = {
                    isCheckingUpdate = true
                    updateStatusText = "Checking for updates\u2026"
                    lifecycleScope.launch {
                        try {
                            val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
                            isCheckingUpdate = false
                            refreshUpdateSectionState(info)
                            if (info != null) showUpdateDialog(info)
                            else Toast.makeText(this@MainActivity, "You are using the latest version", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            isCheckingUpdate = false
                            Toast.makeText(this@MainActivity, "Check failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenAbout = { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) },
                onOpenDeveloper = { startActivity(Intent(this@MainActivity, DeveloperActivity::class.java)) },
                onOpenGithub = {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msrofficial/MiruDL-App"))) }
                    catch (_: Exception) {}
                }
            )
        }
    }

    // ============ TAB SWITCHING ============

    // ============ HOME TAB ============

    private fun initHomeTab() {
        // Home tab is now fully managed by Compose in setContent
    }

    private fun loadPopular() {
        homeLoading = true
        homeEmptyMessage = ""
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { MiruClient.browseCurrentlyAiring() }
                homeLoading = false
                animeList = results
                if (results.isEmpty()) {
                    homeEmptyMessage = getString(R.string.no_results)
                }
            } catch (e: Exception) {
                homeLoading = false
                homeEmptyMessage = "Error loading"
            }
        }
    }

    private fun searchAnime(query: String) {
        homeLoading = true
        homeEmptyMessage = ""
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { MiruClient.search(query) }
                homeLoading = false
                animeList = results
                if (results.isEmpty()) {
                    homeEmptyMessage = getString(R.string.no_results)
                }
            } catch (e: Exception) {
                homeLoading = false
                homeEmptyMessage = "Error: ${e.message}"
            }
        }
    }

    private fun showAnimeDetail(anime: AnimeItem) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(R.layout.bottom_sheet_detail)
            .create()

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
        }

        val view = dialog.window?.decorView?.findViewById<View>(android.R.id.content)
        if (view == null) {
            dialog.dismiss()
            openDetail(anime)
            return
        }

        val thumb = view.findViewById<ImageView>(R.id.detail_thumb)
        val title = view.findViewById<TextView>(R.id.detail_title)
        val btnEpisodes = view.findViewById<View>(R.id.btn_view_episodes)

        if (!anime.thumbnail.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this).load(anime.thumbnail).centerCrop().into(thumb)
        }
        title.text = anime.title ?: "Unknown"

        btnEpisodes.setOnClickListener {
            dialog.dismiss()
            openDetail(anime)
        }
    }

    private fun openDetail(anime: AnimeItem) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra("anime_id", anime.id)
            putExtra("anime_url", anime.url)
            putExtra("anime_title", anime.title)
            putExtra("anime_thumb", anime.thumbnail)
        }
        startActivity(intent)
    }

    // ============ DOWNLOADS TAB ============

    private fun initDownloadsTab() {
        // Downloads tab is now fully managed by Compose in setContent
    }

    private fun refreshDownloads() {
        val active = DownloadManager.snapshot()
        val completed = DownloadEntryStore.all(storage)
        val items = mutableListOf<DownloadsItem>()

        val hasActive = active.any { !it.finished }
        if (hasActive) {
            items.add(DownloadsItem.Section("DOWNLOADING"))
            active.filter { !it.finished }.forEach { items.add(DownloadsItem.Active(it)) }
        }

        if (completed.isNotEmpty()) {
            items.add(DownloadsItem.Section("COMPLETED"))
            val grouped = linkedMapOf<String, MutableList<DownloadRecord>>()
            for (e in completed) {
                grouped.getOrPut(e.parentName()) { mutableListOf() }.add(e)
            }
            for ((folderName, folderEntries) in grouped) {
                val isExpanded = expandedFolders.contains(folderName)
                items.add(DownloadsItem.Folder(folderName, folderEntries, isExpanded))
                if (isExpanded) {
                    folderEntries.forEach { items.add(DownloadsItem.Completed(it)) }
                }
            }
        }

        if (items.isEmpty()) items.add(DownloadsItem.Empty)
        downloadsItems = items
    }

    private fun cancelDownloadJob(job: msr.mirudl.shared.download.Job) {
        DownloadManager.cancel(job)
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = "cancel"
            putExtra("jobId", job.id)
        }
        startService(cancelIntent)
        refreshDownloads()
    }

    private fun openDownloadFile(entry: DownloadRecord) {
        val dlEntry = DownloadEntry.fromRecord(entry)
        if (dlEntry.uri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(dlEntry.uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleDownloadFolder(name: String) {
        if (expandedFolders.contains(name)) expandedFolders.remove(name)
        else expandedFolders.add(name)
        refreshDownloads()
    }

    private fun requestGroupDelete(groupName: String) {
        val all = DownloadEntryStore.all(storage)
        val groupEntries = all.filter { groupName == it.parentName() }
            .map { DownloadEntry.fromRecord(it) }
        showDeleteMultipleDialog(groupEntries)
    }

    private fun clearFinished() {
        DownloadManager.removeFinished()
        val entries = DownloadEntryStore.all(storage)
        if (entries.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Remove all download history?")
                .setPositiveButton("Clear") { _, _ ->
                    DownloadEntryStore.removeAll(storage, entries)
                    refreshDownloads()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refreshDownloads()
    }

    private fun showDeleteDownloadDialog(dlEntry: DownloadEntry) {
        val errorColor = getColor(R.color.error)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_delete)
            setColorFilter(errorColor)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x22F87171)
            }
        }
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this).apply {
            text = "Delete download?"
            setTextColor(getColor(R.color.text_primary))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        var displayName = dlEntry.title ?: ""
        if (displayName.startsWith("Episode ")) displayName = displayName.substring(8)
        val epName = TextView(this).apply {
            text = displayName
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        box.addView(epName, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(58), dp(4), 0, dp(20)) })

        val deleteBtn = TextView(this).apply {
            text = "Delete file permanently"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(errorColor)
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
        }
        box.addView(deleteBtn)

        val removeBtn = TextView(this).apply {
            text = "Remove from history only"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.surface_variant))
            }
        }
        box.addView(removeBtn, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, 0) })

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x00000000)
                setStroke(dp(1), getColor(R.color.divider))
            }
        }
        box.addView(cancelBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(10), 0, 0) })

        val dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
        dialog.show()
        deleteBtn.setOnClickListener {
            dlEntry.deleteFileAndRecord(this)
            dialog.dismiss()
            refreshDownloads()
        }
        removeBtn.setOnClickListener {
            dlEntry.deleteRecordOnly(this)
            dialog.dismiss()
            refreshDownloads()
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun showDeleteMultipleDialog(entries: List<DownloadEntry>) {
        if (entries.isEmpty()) return
        val desc = if (entries.size == 1) entries[0].title else "${entries.size} files"
        val errorColor = getColor(R.color.error)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_delete)
            setColorFilter(errorColor)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x22F87171)
            }
        }
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this).apply {
            text = "Delete $desc?"
            setTextColor(getColor(R.color.text_primary))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val message = TextView(this).apply {
            text = "Removing from history keeps the file on your device."
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
        }
        box.addView(message, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(58), dp(4), 0, dp(20)) })

        val deleteBtn = TextView(this).apply {
            text = "Delete files permanently"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(errorColor)
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
        }
        box.addView(deleteBtn)

        val removeBtn = TextView(this).apply {
            text = "Remove from history only"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.surface_variant))
            }
        }
        box.addView(removeBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(10), 0, 0) })

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x00000000)
                setStroke(dp(1), getColor(R.color.divider))
            }
        }
        box.addView(cancelBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(10), 0, 0) })

        val dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
        dialog.show()
        deleteBtn.setOnClickListener {
            for (e in entries) e.deleteFileAndRecord(this)
            dialog.dismiss()
            refreshDownloads()
        }
        removeBtn.setOnClickListener {
            for (e in entries) e.deleteRecordOnly(this)
            dialog.dismiss()
            refreshDownloads()
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
    }

    // ============ SETTINGS TAB ============

    private fun showCrashLogsDialog() {
        CrashLogger.markViewed(this)
        updateCrashBadge()
        val files = CrashLogger.getCrashFiles(this)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = UiHelper.rounded(getColor(R.color.surface), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bug)
            setColorFilter(getColor(R.color.error))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = UiHelper.rounded(0x22F44336, dp(12))
        }
        titleRow.addView(titleIcon, LinearLayout.LayoutParams(dp(36), dp(36)))

        val title = TextView(this).apply {
            text = "Crash Reports (${files.size})"
            setTextColor(getColor(R.color.text_primary))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        if (files.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No crash logs \u2014 everything running smoothly."
                setTextColor(getColor(R.color.text_tertiary))
                textSize = 13f
                gravity = Gravity.CENTER
            }
            box.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(36), 0, dp(28)) })
        } else {
            val clearAll = TextView(this).apply {
                text = "Clear All"
                setTextColor(getColor(R.color.error))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = UiHelper.rounded(0x1AF44336, dp(10))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = Gravity.END
                    setMargins(0, dp(14), 0, dp(14))
                }
                setOnClickListener {
                    CrashLogger.clearAll(this@MainActivity)
                    showCrashLogsDialog()
                }
            }
            box.addView(clearAll)

            val listScroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            for (file in files) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(8), dp(10))
                    background = UiHelper.rounded(getColor(R.color.surface_variant), dp(12))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) }
                }

                val dateStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.US).format(Date(file.lastModified()))
                val sizeKb = file.length() / 1024

                val info = TextView(this).apply {
                    text = "$dateStr  \u2022  ${if (sizeKb > 0) "$sizeKb KB" else "<1 KB"}"
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 12.5f
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                row.addView(info)

                val delBtn = TextView(this).apply {
                    text = "Delete"
                    setTextColor(getColor(R.color.error))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener {
                        CrashLogger.deleteCrashFile(file)
                        showCrashLogsDialog()
                    }
                }
                row.addView(delBtn)

                val viewBtn = TextView(this).apply {
                    text = "View"
                    setTextColor(getColor(R.color.primary))
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { showCrashDetailDialog(file) }
                }
                row.addView(viewBtn)
                list.addView(row)
            }

            listScroll.addView(list)
            box.addView(listScroll, LinearLayout.LayoutParams(-1, -2).apply { height = dp(320) })
        }

        val closeBtn = TextView(this).apply {
            text = "Close"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.surface_variant), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(16), 0, 0) }
        }
        box.addView(closeBtn)

        val dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
        dialog.show()
        closeBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun showCrashDetailDialog(file: File) {
        val content = StringBuilder()
        try {
            val r = java.io.BufferedReader(java.io.FileReader(file))
            var line: String?
            while (r.readLine().also { line = it } != null) {
                if (content.length > 5000) {
                    content.append("\n... [TRUNCATED - file too large]")
                    break
                }
                content.append(line).append("\n")
            }
            r.close()
        } catch (_: Exception) {}
        val reportText = content.toString()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiHelper.rounded(getColor(R.color.surface), dp(20))
        }

        val title = TextView(this).apply {
            text = "Crash Report"
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        box.addView(title, LinearLayout.LayoutParams(-1, -2))

        val subtitle = TextView(this).apply {
            text = file.name
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 11.5f
        }
        box.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, 0) })

        val scroll = ScrollView(this).apply {
            background = UiHelper.rounded(getColor(R.color.surface_variant), dp(14))
        }
        val logText = TextView(this).apply {
            text = reportText
            setTextColor(getColor(R.color.text_secondary))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(logText)
        box.addView(scroll, LinearLayout.LayoutParams(-1, dp(360)).apply { setMargins(0, dp(12), 0, dp(14)) })

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val copyBtn = TextView(this).apply {
            text = "Copy"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.surface_variant), dp(12))
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Crash report", reportText))
                Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(copyBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(6), 0) })

        val saveBtn = TextView(this).apply {
            text = "Save"
            setTextColor(getColor(R.color.on_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.primary), dp(12))
            setOnClickListener { saveCrashReportToDownloads(file, reportText) }
        }
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(6), 0, 0, 0) })

        box.addView(actionRow, LinearLayout.LayoutParams(-1, -2))

        val closeBtn = TextView(this).apply {
            text = "Close"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(2))
        }
        box.addView(closeBtn, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(this).setView(box).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
        dialog.show()
        closeBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun saveCrashReportToDownloads(sourceFile: File, reportText: String) {
        val fileName = sourceFile.name
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Could not create file")
                contentResolver.openOutputStream(uri)?.use { it.write(reportText.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val outFile = File(downloads, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(4096)
                        var n: Int
                        while (input.read(buf).also { n = it } > 0) output.write(buf, 0, n)
                    }
                }
            }
            Toast.makeText(this, "Saved to Downloads/$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshUpdateSectionState(info: UpdateChecker.ReleaseInfo?) {
        if (info != null) {
            updateStatusText = "Update available \u2022 ${info.tag}"
            hasUpdate = true
            crashBadgeVisible = CrashLogger.hasNewCrash(this)
        } else {
            var v = ""
            try { v = packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) {}
            updateStatusText = "Version $v \u2022 Up to date"
            hasUpdate = false
        }
    }

    private fun updateCrashBadgeState() {
        crashBadgeVisible = CrashLogger.hasNewCrash(this)
    }

    private fun showUpdateDialog(info: UpdateChecker.ReleaseInfo) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = UiHelper.rounded(getColor(R.color.surface), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_refresh)
            setColorFilter(getColor(R.color.primary))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = UiHelper.rounded(0x220EA5E9, dp(12))
        }
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val title = TextView(this).apply {
            text = "Update Available"
            setTextColor(getColor(R.color.text_primary))
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
        }
        titleCol.addView(title)

        var currentV = ""
        try { currentV = packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) {}
        val versionLine = TextView(this).apply {
            text = "$currentV  \u2192  ${info.tag}"
            setTextColor(getColor(R.color.primary))
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
        }
        titleCol.addView(versionLine, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })

        titleRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val changelogLabel = TextView(this).apply {
            text = "What's new"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        }
        box.addView(changelogLabel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(18), 0, dp(6)) })

        val scroll = ScrollView(this).apply {
            background = UiHelper.rounded(getColor(R.color.surface_variant), dp(14))
        }
        val changelog = info.changelog?.trim()?.ifEmpty { null } ?: "No changelog provided for this release."
        val changelogText = TextView(this).apply {
            text = changelog
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12.5f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        scroll.addView(changelogText)
        box.addView(scroll, LinearLayout.LayoutParams(-1, dp(220)).apply { bottomMargin = dp(18) })

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val laterBtn = TextView(this).apply {
            text = "Not Now"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.surface_variant), dp(12))
        }
        btnRow.addView(laterBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(6), 0) })

        val updateBtn = TextView(this).apply {
            text = "Update Now"
            setTextColor(getColor(R.color.on_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.primary), dp(12))
        }
        btnRow.addView(updateBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(6), 0, 0, 0) })

        box.addView(btnRow, LinearLayout.LayoutParams(-1, -2))

        val dialog = AlertDialog.Builder(this).setCancelable(true).setView(box).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
        dialog.show()
        laterBtn.setOnClickListener { dialog.dismiss() }
        updateBtn.setOnClickListener {
            val target = info.apkUrl ?: info.htmlUrl
            if (target != null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) } catch (_: Exception) {}
            }
            dialog.dismiss()
        }
    }

    private fun pickFolder() {
        AlertDialog.Builder(this)
            .setTitle("Change download folder")
            .setMessage("This will move future downloads to the new folder. Already downloaded files will not be moved.")
            .setPositiveButton("Change") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                folderPicker.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateFolderDisplay() {
        val uriStr = StorageSettings.getDownloadUri(storage)
        if (uriStr != null) {
            val uri = Uri.parse(uriStr)
            val path = uri.path
            val display = if (path != null && path.contains(":")) path.substring(path.indexOf(":") + 1) else path
            folderPathDisplay = display ?: uri.toString()
        } else {
            folderPathDisplay = "Not selected"
        }
    }

    // ============ LIFECYCLE ============

    override fun onResume() {
        super.onResume()
        startPeriodicRefresh()
        updateCrashBadge()
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        stopPeriodicRefresh()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(500)
                refreshDownloads()
            }
        }
    }

    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun updateCrashBadge() {
        crashBadgeVisible = CrashLogger.hasNewCrash(this)
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
