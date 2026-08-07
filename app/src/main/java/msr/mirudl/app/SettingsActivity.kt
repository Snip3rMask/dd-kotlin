package msr.mirudl.app

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import msr.mirudl.shared.storage.StorageSettingsAndroid

class SettingsActivity : BaseActivity() {

    private lateinit var folderText: android.widget.TextView
    private lateinit var parallelBar: SeekBar
    private lateinit var parallelValue: android.widget.TextView

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

                StorageSettingsAndroid.setDownloadUri(this, treeUri)
                updateFolderDisplay()
                Toast.makeText(this, "Download folder set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val versionTv = findViewById<android.widget.TextView>(R.id.settings_app_version)
        if (versionTv != null) {
            try {
                val v = packageManager.getPackageInfo(packageName, 0).versionName
                versionTv.text = "Version $v"
            } catch (_: Exception) {}
        }

        folderText = findViewById(R.id.folder_path)
        findViewById<View>(R.id.btn_select_folder).setOnClickListener { pickFolder() }

        parallelBar = findViewById(R.id.parallel_seekbar)
        parallelValue = findViewById(R.id.parallel_value)

        val currentParallel = StorageSettingsAndroid.getParallelSegments(this)
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
                    StorageSettingsAndroid.setParallelSegments(this@SettingsActivity, value)
                }
            }
        })

        val qualities = arrayOf("1080p", "720p", "480p", "360p", "Auto")
        val qualitySpinner = findViewById<android.widget.Spinner>(R.id.quality_spinner)
        val qAdapter = ArrayAdapter(this, R.layout.spinner_value_chevron, android.R.id.text1, qualities)
        qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        qualitySpinner.adapter = qAdapter
        val prefQ = StorageSettingsAndroid.getPreferredQuality(this)
        qualities.indexOf(prefQ).takeIf { it >= 0 }?.let { qualitySpinner.setSelection(it) }
        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View, pos: Int, id: Long) {
                StorageSettingsAndroid.setPreferredQuality(this@SettingsActivity, qualities[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val langs = arrayOf("jpn", "eng")
        val langLabels = arrayOf("Sub (Japanese)", "Dub (English)")
        val langSpinner = findViewById<android.widget.Spinner>(R.id.lang_spinner)
        val lAdapter = ArrayAdapter(this, R.layout.spinner_value_chevron, android.R.id.text1, langLabels)
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        langSpinner.adapter = lAdapter
        val prefLang = StorageSettingsAndroid.getPreferredLanguage(this)
        langs.indexOf(prefLang).takeIf { it >= 0 }?.let { langSpinner.setSelection(it) }
        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View, pos: Int, id: Long) {
                StorageSettingsAndroid.setPreferredLanguage(this@SettingsActivity, langs[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        findViewById<View?>(R.id.id_github_link)?.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msrofficial/MiruDL-App"))) } catch (_: Exception) {}
        }

        findViewById<View?>(R.id.btn_crash_reports)?.setOnClickListener {
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("showCrashLogs", true)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }

        updateFolderDisplay()
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
                StorageSettingsAndroid.setParallelSegments(this, safe)
            }
            .show()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        folderPicker.launch(intent)
    }

    private fun updateFolderDisplay() {
        val uri = StorageSettingsAndroid.getDownloadUri(this)
        if (uri != null) {
            val path = uri.path
            val display = if (path != null && path.contains(":")) path.substring(path.indexOf(":") + 1) else path
            folderText.text = display ?: uri.toString()
        } else {
            folderText.text = "Not selected"
        }
    }
}
