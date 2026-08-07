package msr.mirudl.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
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

class DetailActivity : BaseActivity() {

    private var animeId: String? = null
    private var animeTitle: String? = null
    private var animeThumb: String? = null
    private var animeUrl: String? = null

    private lateinit var titleText: TextView
    private lateinit var episodeCount: TextView
    private lateinit var detailMeta: TextView
    private lateinit var detailDesc: TextView
    private lateinit var detailStatus: TextView

    private lateinit var episodeSearchRow: View
    private lateinit var selectBar: View
    private lateinit var btnDownloadAll: View
    private lateinit var episodeSearchInput: EditText
    private lateinit var btnEpSearch: ImageView
    private lateinit var btnEpReverse: ImageView
    private lateinit var btnEpSelect: TextView
    private lateinit var selectCountText: TextView
    private lateinit var btnDownloadSelected: TextView

    private lateinit var episodeComposeView: ComposeView

    private var allEpisodes: MutableList<EpisodeItem> = mutableListOf()

    // Compose-visible state
    private var displayEpisodes by mutableStateOf<List<EpisodeItem>>(emptyList())
    private var gridMode by mutableStateOf(false)
    private var selectionMode by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var emptyMessage by mutableStateOf("")
    private var downloadedEpisodes by mutableStateOf<Set<String>>(emptySet())

    // Internal adapter-equivalent state
    private var searchQuery = ""
    private var reversed = false

    private val storage: AppStorage
        get() = msr.mirudl.shared.storage.AndroidAppStorage(this, "mirudl_settings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        animeId = intent.getStringExtra("anime_id")
        animeTitle = intent.getStringExtra("anime_title")
        animeThumb = intent.getStringExtra("anime_thumb")
        animeUrl = intent.getStringExtra("anime_url")

        titleText = findViewById(R.id.detail_title)
        episodeComposeView = findViewById(R.id.episode_compose_view)
        episodeCount = findViewById(R.id.episode_count)
        detailMeta = findViewById(R.id.detail_meta)
        detailDesc = findViewById(R.id.detail_description)
        detailStatus = findViewById(R.id.detail_status)

        episodeSearchRow = findViewById(R.id.episode_search_row)
        episodeSearchInput = findViewById(R.id.episode_search_input)
        selectBar = findViewById(R.id.select_bar)
        btnDownloadAll = findViewById(R.id.btn_download_all)
        btnEpSearch = findViewById(R.id.btn_ep_search)
        btnEpReverse = findViewById(R.id.btn_ep_reverse)
        btnEpSelect = findViewById(R.id.btn_ep_select)
        selectCountText = findViewById(R.id.select_count_text)
        btnDownloadSelected = findViewById(R.id.btn_download_selected)

        val thumbView = findViewById<ImageView>(R.id.detail_thumb)
        val titleView = findViewById<TextView>(R.id.detail_title_text)

        titleText.text = animeTitle
        if (titleView != null) titleView.text = animeTitle

        if (!animeThumb.isNullOrEmpty()) {
            Glide.with(this).load(animeThumb).centerCrop().into(thumbView)
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        btnDownloadAll.setOnClickListener {
            if (allEpisodes.isEmpty()) {
                Toast.makeText(this, "No episodes available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!StorageSettings.hasDownloadUri(storage)) {
                showFolderRequiredDialog()
                return@setOnClickListener
            }
            showDownloadAllDialog()
        }

        // Set up Compose episode list
        episodeComposeView.setContent {
            androidx.compose.material3.MaterialTheme {
                EpisodeListContent(
                    episodes = displayEpisodes,
                    gridMode = gridMode,
                    selectionMode = selectionMode,
                    downloadedEpisodes = downloadedEpisodes,
                    isLoading = isLoading,
                    emptyMessage = emptyMessage,
                    onEpisodeClick = { ep -> handleEpisodeClick(ep) }
                )
            }
        }

        btnEpSearch.setOnClickListener {
            val showing = episodeSearchRow.visibility == View.VISIBLE
            if (showing) {
                episodeSearchRow.visibility = View.GONE
                episodeSearchInput.setText("")
                searchQuery = ""
                rebuildDisplayList()
                btnEpSearch.setColorFilter(getColor(R.color.text_secondary))
            } else {
                episodeSearchRow.visibility = View.VISIBLE
                episodeSearchInput.requestFocus()
                btnEpSearch.setColorFilter(getColor(R.color.primary))
            }
        }
        episodeSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                rebuildDisplayList()
            }
        })

        btnEpReverse.setOnClickListener {
            reversed = !reversed
            rebuildDisplayList()
            btnEpReverse.setColorFilter(getColor(if (reversed) R.color.primary else R.color.text_secondary))
        }

        btnEpSelect.setOnClickListener { enterSelectionMode() }
        findViewById<View>(R.id.btn_select_close).setOnClickListener { exitSelectionMode() }
        findViewById<View>(R.id.btn_select_all).setOnClickListener { selectAllVisible() }
        btnDownloadSelected.setOnClickListener {
            val selected = getSelectedEpisodes()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one episode", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!StorageSettings.hasDownloadUri(storage)) {
                showFolderRequiredDialog()
                return@setOnClickListener
            }
            downloadSelectedEpisodes(selected)
            exitSelectionMode()
        }

        downloadedEpisodes = currentDownloadedSet()

        loadEpisodes()
        loadDetails()
    }

    // ── Episode list management (replaces EpisodeAdapter logic) ──

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
            selectCountText.text = "${getSelectedEpisodes().size} selected"
            btnDownloadSelected.alpha = if (getSelectedEpisodes().isNotEmpty()) 1f else 0.5f
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
        selectCountText.text = "${getSelectedEpisodes().size} selected"
        btnDownloadSelected.alpha = 1f
    }

    private fun getSelectedEpisodes(): List<EpisodeItem> {
        return allEpisodes.filter { it.selected }
    }

    private fun enterSelectionMode() {
        selectionMode = true
        selectCountText.text = "0 selected"
        btnDownloadSelected.alpha = 0.5f
        selectBar.visibility = View.VISIBLE
        btnDownloadAll.visibility = View.GONE
        btnEpSelect.visibility = View.GONE
    }

    private fun exitSelectionMode() {
        selectionMode = false
        for (ep in allEpisodes) ep.selected = false
        displayEpisodes = displayEpisodes.toList()
        selectBar.visibility = View.GONE
        btnDownloadAll.visibility = View.VISIBLE
        btnEpSelect.visibility = View.VISIBLE
    }

    // ── Existing logic (unchanged) ──

    private fun loadDetails() {}

    private fun loadEpisodes() {
        isLoading = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MiruClient.getEpisodes(animeId!!)
                }
                allEpisodes = result.toMutableList()
                isLoading = false
                val count = "${allEpisodes.size} episodes"
                episodeCount.text = count

                gridMode = allEpisodes.size > GRID_THRESHOLD
                rebuildDisplayList()

                if (allEpisodes.isEmpty()) {
                    emptyMessage = "No episodes found"
                }
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
                for (vs in langs) {
                    if (vs.language == prefLang) {
                        selected = vs
                        break
                    }
                }
                if (selected == null) selected = langs[0]

                val hlsUrl = withContext(Dispatchers.IO) {
                    MiruClient.resolveHlsFromEmbed(selected.url)
                }
                if (hlsUrl == null) {
                    Toast.makeText(this@DetailActivity, "No HLS URL found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                ep.hlsUrl = hlsUrl
                ep.language = selected.language
                ep.langName = selected.quality
                ep.embedUrl = selected.url

                startDownload(ep)
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFolderRequiredDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_folder)
            setColorFilter(getColor(R.color.primary))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x2238BDF8)
            }
        }
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this).apply {
            text = "Download Folder Required"
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val msg = TextView(this).apply {
            text = "Please select a download folder in Settings first."
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
        }
        box.addView(msg, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, dp(22)) })

        val settingsBtn = TextView(this).apply {
            text = "Open Settings"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.primary))
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
        }
        box.addView(settingsBtn)

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
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
        settingsBtn.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this@DetailActivity, SettingsActivity::class.java))
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun showDownloadAllDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_download)
            setColorFilter(getColor(R.color.primary))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x2238BDF8)
            }
        }
        titleRow.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val title = TextView(this).apply {
            text = "Download All Episodes"
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), 0, 0, 0) })
        box.addView(titleRow, LinearLayout.LayoutParams(-1, -2))

        val msg = TextView(this).apply {
            text = "Start downloading all ${allEpisodes.size} episodes?"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
        }
        box.addView(msg, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, dp(22)) })

        val downloadBtn = TextView(this).apply {
            text = "Download All (${allEpisodes.size})"
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(getColor(R.color.primary))
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(50))
        }
        box.addView(downloadBtn)

        val cancelBtn = TextView(this).apply {
            text = "Cancel"
            setTextColor(getColor(R.color.text_tertiary))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
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
        downloadBtn.setOnClickListener {
            dialog.dismiss()
            downloadAllEpisodes()
        }
        cancelBtn.setOnClickListener { dialog.dismiss() }
    }

    private fun startDownload(ep: EpisodeItem) {
        if (!StorageSettings.hasDownloadUri(storage)) {
            showFolderRequiredDialog()
            return
        }

        val quality = StorageSettings.getPreferredQuality(storage)
        val language = ep.language ?: "jpn"
        val hlsUrl = ep.hlsUrl

        lifecycleScope.launch {
            try {
                val variants = withContext(Dispatchers.IO) {
                    MiruClient.getQualities(hlsUrl!!)
                }
                var finalUrl = hlsUrl!!
                for (v in variants) {
                    if (v.quality.contains(quality.replace("p", ""))) {
                        finalUrl = v.url
                        break
                    }
                }
                queueDownload(ep, finalUrl, quality, language)
            } catch (e: Exception) {
                queueDownload(ep, hlsUrl!!, quality, language)
            }
        }
    }

    private fun queueDownload(ep: EpisodeItem, hlsUrl: String, quality: String, language: String) {
        val label = "Episode ${ep.getLabel()}"
        val exists = DownloadManager.findByAnimeAndEpisode(animeTitle, ep.getLabel())
        if (exists != null) {
            val status = exists.status
            if (status == Job.STATUS_COMPLETED) {
                Toast.makeText(this, "Already downloaded: $label", Toast.LENGTH_SHORT).show()
            } else if (status == Job.STATUS_QUEUED || status == Job.STATUS_DOWNLOADING) {
                Toast.makeText(this, "Already queued: $label", Toast.LENGTH_SHORT).show()
            }
            return
        }

        DownloadManager.enqueue(animeTitle, label, quality, language, hlsUrl)
        downloadedEpisodes = currentDownloadedSet()

        val intent = DownloadService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "Queued: $label", Toast.LENGTH_SHORT).show()
    }

    private fun downloadAllEpisodes() {
        enqueueEpisodes(allEpisodes, "${allEpisodes.size} episodes queued")
    }

    private fun downloadSelectedEpisodes(selected: List<EpisodeItem>) {
        enqueueEpisodes(selected, "${selected.size} episode(s) queued")
    }

    private fun enqueueEpisodes(list: List<EpisodeItem>, successMessage: String) {
        lifecycleScope.launch {
            try {
                val jobs = mutableListOf<Job>()
                val prefLang = StorageSettings.getPreferredLanguage(storage)

                for (ep in list) {
                    try {
                        var hlsUrl = ep.hlsUrl
                        var language = ep.language
                        var langName = ep.langName

                        if (hlsUrl == null) {
                            val langs = withContext(Dispatchers.IO) {
                                MiruClient.getEpisodeLanguages(ep.id)
                            }
                            var selected: VideoSource? = null
                            for (vs in langs) {
                                if (vs.language == prefLang) {
                                    selected = vs
                                    break
                                }
                            }
                            if (selected == null && langs.isNotEmpty()) selected = langs[0]
                            if (selected == null) continue

                            hlsUrl = withContext(Dispatchers.IO) {
                                MiruClient.resolveHlsFromEmbed(selected.url)
                            } ?: continue

                            language = selected.language
                            langName = selected.quality
                            ep.hlsUrl = hlsUrl
                            ep.language = language
                            ep.langName = langName
                        }

                        val job = DownloadManager.enqueue(
                            animeTitle, "Episode ${ep.getLabel()}",
                            StorageSettings.getPreferredQuality(storage),
                            language ?: "jpn", hlsUrl!!
                        )
                        jobs.add(job)
                    } catch (_: Exception) {
                    }
                }

                if (jobs.isNotEmpty()) {
                    val intent = DownloadService.startIntent(this@DetailActivity)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    downloadedEpisodes = currentDownloadedSet()
                    Toast.makeText(this@DetailActivity, successMessage, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        super.onBackPressed()
    }

    private fun dp(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val GRID_THRESHOLD = 40
        private const val GRID_SPAN = 5
    }
}
