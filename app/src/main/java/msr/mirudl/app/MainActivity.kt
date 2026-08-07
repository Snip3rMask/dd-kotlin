package msr.mirudl.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {

    // Tabs
    private lateinit var tabHome: View
    private lateinit var tabDownloads: View
    private lateinit var tabSettings: View
    private var currentTab = 0
    private var suppressTabListener = false
    private lateinit var bottomNav: BottomNavigationView
    private var crashCardDot: View? = null

    // Home
    private lateinit var searchInput: EditText
    private lateinit var animeGrid: RecyclerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var gridAdapter: AnimeGridAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // Downloads
    private lateinit var downloadsList: RecyclerView
    private lateinit var emptyTextDl: TextView
    private lateinit var btnClearFinished: TextView
    private lateinit var downloadAdapter: DownloadAdapter

    private val selectedDownloadKeys = mutableSetOf<String>()

    // Settings
    private lateinit var folderText: TextView
    private lateinit var parallelBar: SeekBar
    private lateinit var parallelValue: TextView
    private lateinit var concurrentBar: SeekBar
    private lateinit var concurrentValue: TextView
    private lateinit var qualitySpinner: Spinner
    private lateinit var langSpinner: Spinner

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
        setContentView(R.layout.activity_main)

        tabHome = findViewById(R.id.tab_home)
        tabDownloads = findViewById(R.id.tab_downloads)
        tabSettings = findViewById(R.id.tab_settings)
        bottomNav = findViewById(R.id.bottom_nav)

        val rootLayout = findViewById<View>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            bottomNav.setPadding(
                bottomNav.paddingLeft, bottomNav.paddingTop,
                bottomNav.paddingRight, navBarBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        bottomNav.setOnItemSelectedListener { item ->
            if (suppressTabListener) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_home -> { showTab(0); true }
                R.id.nav_downloads -> { showTab(1); true }
                R.id.nav_settings -> { showTab(2); true }
                else -> false
            }
        }

        findViewById<View>(R.id.toolbar_title).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        initHomeTab()
        initDownloadsTab()
        initSettingsTab()
        updateCrashBadge()

        // Silent update check
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
                if (info != null) {
                    val cached = withContext(Dispatchers.IO) { UpdateChecker.readCache(storage) }
                    val isNew = cached == null || UpdateChecker.isNewerVersion(info.tag, cached.tag)
                    withContext(Dispatchers.Main) {
                        refreshUpdateSection(if (isNew) info else null)
                        if (isNew) showUpdateDialog(info)
                    }
                } else {
                    withContext(Dispatchers.Main) { refreshUpdateSection(null) }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { refreshUpdateSection(null) }
            }
        }

        // Restore tab
        val restoreTab = savedInstanceState?.getInt("current_tab", 0) ?: 0
        suppressTabListener = true
        when (restoreTab) {
            0 -> bottomNav.selectedItemId = R.id.nav_home
            1 -> bottomNav.selectedItemId = R.id.nav_downloads
            2 -> bottomNav.selectedItemId = R.id.nav_settings
        }
        suppressTabListener = false
        showTab(restoreTab)
        if (restoreTab == 0) loadPopular()
    }

    // ============ TAB SWITCHING ============

    private fun showTab(tab: Int) {
        currentTab = tab
        tabHome.visibility = if (tab == 0) View.VISIBLE else View.GONE
        tabDownloads.visibility = if (tab == 1) View.VISIBLE else View.GONE
        tabSettings.visibility = if (tab == 2) View.VISIBLE else View.GONE
        if (tab == 1) refreshDownloads()
        if (tab == 2) updateFolderDisplay()
    }

    // ============ HOME TAB ============

    private fun initHomeTab() {
        searchInput = findViewById(R.id.search_input)
        animeGrid = findViewById(R.id.anime_grid)
        loadingBar = findViewById(R.id.loading_bar)
        emptyText = findViewById(R.id.empty_text)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.primary_dark)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_variant)
        swipeRefresh.setOnRefreshListener {
            val q = searchInput.text.toString().trim()
            if (q.isEmpty()) loadPopular() else searchAnime(q)
        }

        gridAdapter = AnimeGridAdapter { anime -> showAnimeDetail(anime) }
        animeGrid.layoutManager = GridLayoutManager(this, 2)
        animeGrid.adapter = gridAdapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    if (s.length >= 2) searchAnime(s.toString())
                    else if (s.isEmpty()) loadPopular()
                }
            }
        })

        searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && v.text.length >= 2) {
                searchAnime(v.text.toString())
                true
            } else false
        }
    }

    private fun loadPopular() {
        loadingBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { MiruClient.browseCurrentlyAiring() }
                loadingBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                gridAdapter.setItems(results)
                if (results.isEmpty()) {
                    emptyText.setText(R.string.no_results)
                    emptyText.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                loadingBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                emptyText.setText("Error loading")
                emptyText.visibility = View.VISIBLE
            }
        }
    }

    private fun searchAnime(query: String) {
        loadingBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { MiruClient.search(query) }
                loadingBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                gridAdapter.setItems(results)
                if (results.isEmpty()) {
                    emptyText.setText(R.string.no_results)
                    emptyText.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                loadingBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                emptyText.text = "Error: ${e.message}"
                emptyText.visibility = View.VISIBLE
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
        downloadsList = findViewById(R.id.downloads_list)
        emptyTextDl = findViewById(R.id.empty_text_dl)
        btnClearFinished = findViewById(R.id.btn_clear_finished)
        btnClearFinished.setOnClickListener { clearFinished() }

        downloadAdapter = DownloadAdapter(this, object : DownloadAdapter.OnActionListener {
            override fun onCancel(job: msr.mirudl.shared.download.Job) {
                DownloadManager.cancel(job)
                val cancelIntent = Intent(this@MainActivity, DownloadService::class.java).apply {
                    action = "cancel"
                    putExtra("jobId", job.id)
                }
                startService(cancelIntent)
                refreshDownloads()
            }

            override fun onClick(entry: DownloadRecord) {
                val dlEntry = DownloadEntry.fromRecord(entry)
                if (dlEntry.uri != null) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(dlEntry.uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot open file", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onDelete(entry: DownloadRecord) {
                showDeleteDownloadDialog(DownloadEntry.fromRecord(entry))
            }

            override fun onSelectionToggle(entry: DownloadRecord) {
                val key = DownloadEntry.fromRecord(entry).key()
                if (selectedDownloadKeys.contains(key)) selectedDownloadKeys.remove(key)
                else selectedDownloadKeys.add(key)
                refreshDownloads()
            }

            override fun isSelected(entry: DownloadRecord): Boolean {
                return selectedDownloadKeys.contains(DownloadEntry.fromRecord(entry).key())
            }

            override fun onGroupDeleteRequest(groupName: String) {
                val all = DownloadEntryStore.all(storage)
                val groupEntries = all.filter { groupName == it.parentName() }
                    .map { DownloadEntry.fromRecord(it) }
                showDeleteMultipleDialog(groupEntries)
            }

            override fun onGroupDelete(groupName: String) {
                downloadAdapter.toggleFolder(groupName)
                refreshDownloads()
            }
        })

        downloadsList.layoutManager = LinearLayoutManager(this)
        downloadsList.adapter = downloadAdapter
    }

    private fun refreshDownloads() {
        val active = DownloadManager.snapshot()
        val completed = DownloadEntryStore.all(storage)
        val hasActive = active.any { !it.finished }
        emptyTextDl.visibility = if (!hasActive && completed.isEmpty()) View.VISIBLE else View.GONE
        downloadAdapter.setData(active, completed)
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
            selectedDownloadKeys.clear()
            dialog.dismiss()
            refreshDownloads()
        }
        removeBtn.setOnClickListener {
            for (e in entries) e.deleteRecordOnly(this)
            selectedDownloadKeys.clear()
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
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1).apply { setMargins(dp(12), 0, 0, 0) })
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
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1)
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
            setLineSpacing(dp(2), 1f)
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
        actionRow.addView(copyBtn, LinearLayout.LayoutParams(0, dp(46), 1).apply { setMargins(0, 0, dp(6), 0) })

        val saveBtn = TextView(this).apply {
            text = "Save"
            setTextColor(getColor(R.color.on_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.primary), dp(12))
            setOnClickListener { saveCrashReportToDownloads(file, reportText) }
        }
        actionRow.addView(saveBtn, LinearLayout.LayoutParams(0, dp(46), 1).apply { setMargins(dp(6), 0, 0, 0) })

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

    private fun speedLabel(parallel: Int): String = when {
        parallel <= 4 -> "Slow"
        parallel <= 8 -> "Normal"
        parallel <= 16 -> "Fast"
        parallel <= 32 -> "Very Fast"
        else -> "Extreme"
    }

    private fun updateParallelTint(value: Int) {
        val color = if (value > 32) ContextCompat.getColor(this, R.color.error)
        else ContextCompat.getColor(this, R.color.primary)
        parallelBar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        parallelBar.thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        parallelValue.setTextColor(color)
    }

    private fun showParallelWarning(value: Int) {
        AlertDialog.Builder(this)
            .setTitle("High Speed Warning")
            .setMessage(
                "Setting parallel segments to $value may cause:\n\n" +
                    "\u2022 Rate limiting from the server\n" +
                    "\u2022 Increased data usage\n" +
                    "\u2022 Higher battery consumption\n" +
                    "\u2022 Possible download failures on slow connections\n\n" +
                    "Use only if you have a stable, high-speed internet connection."
            )
            .setPositiveButton("Use Anyway", null)
            .setNegativeButton("Reduce") { _, _ ->
                val safe = 32
                parallelBar.progress = safe
                parallelValue.text = safe.toString()
                updateParallelTint(safe)
                StorageSettings.setParallelSegments(storage, safe)
            }
            .show()
    }

    private fun initSettingsTab() {
        folderText = findViewById(R.id.folder_path)
        findViewById<View>(R.id.btn_select_folder).setOnClickListener { pickFolder() }

        parallelBar = findViewById(R.id.parallel_seekbar)
        parallelValue = findViewById(R.id.parallel_value)

        val currentParallel = StorageSettings.getParallelSegments(storage)
        parallelBar.progress = currentParallel
        parallelValue.text = currentParallel.toString()
        updateParallelTint(currentParallel)
        parallelBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                val value = bar.progress.coerceAtLeast(1)
                if (value > 32) showParallelWarning(value)
            }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = progress.coerceAtLeast(1)
                    parallelValue.text = value.toString()
                    updateParallelTint(value)
                    StorageSettings.setParallelSegments(storage, value)
                }
            }
        })

        concurrentBar = findViewById(R.id.concurrent_seekbar)
        concurrentValue = findViewById(R.id.concurrent_value)

        val currentConcurrent = StorageSettings.getConcurrentDownloads(storage)
        concurrentBar.progress = (currentConcurrent - 1).coerceAtLeast(0)
        concurrentValue.text = currentConcurrent.toString()
        concurrentBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val value = progress + 1
                    concurrentValue.text = value.toString()
                    StorageSettings.setConcurrentDownloads(storage, value)
                }
            }
        })

        qualitySpinner = findViewById(R.id.quality_spinner)
        val qualities = arrayOf("1080p", "720p", "480p", "360p", "Auto")
        val qAdapter = ArrayAdapter(this, R.layout.spinner_value_chevron, android.R.id.text1, qualities)
        qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        qualitySpinner.adapter = qAdapter
        val prefQ = StorageSettings.getPreferredQuality(storage)
        qualities.indexOf(prefQ).takeIf { it >= 0 }?.let { qualitySpinner.setSelection(it) }
        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View, pos: Int, id: Long) {
                StorageSettings.setPreferredQuality(storage, qualities[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        langSpinner = findViewById(R.id.lang_spinner)
        val langs = arrayOf("jpn", "eng")
        val langLabels = arrayOf("Sub (Japanese)", "Dub (English)")
        val lAdapter = ArrayAdapter(this, R.layout.spinner_value_chevron, android.R.id.text1, langLabels)
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        langSpinner.adapter = lAdapter
        val prefLang = StorageSettings.getPreferredLanguage(storage)
        langs.indexOf(prefLang).takeIf { it >= 0 }?.let { langSpinner.setSelection(it) }
        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View, pos: Int, id: Long) {
                StorageSettings.setPreferredLanguage(storage, langs[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val themeSwitch = findViewById<SwitchCompat?>(R.id.theme_switch)
        val themeLabel = findViewById<TextView?>(R.id.theme_label)
        if (themeSwitch != null && themeLabel != null) {
            val isDark = StorageSettings.isDarkTheme(storage)
            themeSwitch.isChecked = isDark
            themeLabel.setText(if (isDark) R.string.dark_mode_on else R.string.dark_mode_off)
            themeSwitch.setOnCheckedChangeListener { _, isChecked ->
                StorageSettings.setDarkTheme(storage, isChecked)
                recreate()
            }
        }

        findViewById<View?>(R.id.about_section)?.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        val settingsVersionTv = findViewById<TextView?>(R.id.settings_app_version)
        if (settingsVersionTv != null) {
            try {
                val v = packageManager.getPackageInfo(packageName, 0).versionName
                settingsVersionTv.text = "Version $v"
            } catch (_: Exception) {}
        }

        findViewById<View?>(R.id.dev_info_section)?.setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }

        findViewById<View?>(R.id.id_github_link)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msrofficial/MiruDL-App")))
            } catch (_: Exception) {}
        }

        val crashBtn = findViewById<View?>(R.id.btn_crash_reports)
        crashCardDot = findViewById(R.id.crash_card_dot)
        crashBtn?.setOnClickListener { showCrashLogsDialog() }

        val updateSection = findViewById<View?>(R.id.update_section)
        val updateProgress = findViewById<View?>(R.id.update_progress)
        val updateChevron = findViewById<View?>(R.id.update_chevron)
        if (updateSection != null) {
            refreshUpdateSection(null)
            updateSection.setOnClickListener {
                updateProgress?.visibility = View.VISIBLE
                updateChevron?.visibility = View.GONE
                val statusTv = findViewById<TextView?>(R.id.update_status_text)
                statusTv?.text = "Checking for updates\u2026"
                lifecycleScope.launch {
                    try {
                        val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }
                        updateProgress?.visibility = View.GONE
                        updateChevron?.visibility = View.VISIBLE
                        refreshUpdateSection(info)
                        if (info != null) showUpdateDialog(info)
                        else Toast.makeText(this@MainActivity, "You're using the latest version", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        updateProgress?.visibility = View.GONE
                        updateChevron?.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Check failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        updateFolderDisplay()
    }

    private fun refreshUpdateSection(info: UpdateChecker.ReleaseInfo?) {
        val statusTv = findViewById<TextView?>(R.id.update_status_text) ?: return
        val dot = findViewById<View?>(R.id.update_dot)
        if (info != null) {
            statusTv.text = "Update available \u2022 ${info.tag}"
            dot?.visibility = View.VISIBLE
        } else {
            var v = ""
            try { v = packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) {}
            statusTv.text = "Version $v \u2022 Up to date"
            dot?.visibility = View.GONE
        }
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

        titleRow.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1).apply { setMargins(dp(12), 0, 0, 0) })
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
            setLineSpacing(dp(3), 1f)
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
        btnRow.addView(laterBtn, LinearLayout.LayoutParams(0, dp(48), 1).apply { setMargins(0, 0, dp(6), 0) })

        val updateBtn = TextView(this).apply {
            text = "Update Now"
            setTextColor(getColor(R.color.on_primary))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = UiHelper.rounded(getColor(R.color.primary), dp(12))
        }
        btnRow.addView(updateBtn, LinearLayout.LayoutParams(0, dp(48), 1).apply { setMargins(dp(6), 0, 0, 0) })

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
            folderText.text = display ?: uri.toString()
        } else {
            folderText.text = "Not selected"
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
        val hasNew = CrashLogger.hasNewCrash(this)
        if (::bottomNav.isInitialized) {
            if (hasNew) {
                val badge = bottomNav.getOrCreateBadge(R.id.nav_settings)
                badge.isVisible = true
                badge.clearNumber()
            } else {
                bottomNav.removeBadge(R.id.nav_settings)
            }
        }
        crashCardDot?.visibility = if (hasNew) View.VISIBLE else View.GONE
    }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
