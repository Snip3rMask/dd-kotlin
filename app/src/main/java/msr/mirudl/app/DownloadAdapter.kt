package msr.mirudl.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import msr.mirudl.shared.download.Job
import msr.mirudl.shared.model.DownloadRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadAdapter(
    private val ctx: Context,
    private var listener: OnActionListener?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_ACTIVE = 2
        private const val TYPE_COMPLETED = 3
    }

    private val items = mutableListOf<Any>()
    private val expandedFolders = mutableSetOf<String>()

    interface OnActionListener {
        fun onCancel(job: Job)
        fun onClick(entry: DownloadRecord)
        fun onDelete(entry: DownloadRecord)
        fun onSelectionToggle(entry: DownloadRecord)
        fun isSelected(entry: DownloadRecord): Boolean
        fun onGroupDelete(groupName: String)
        fun onGroupDeleteRequest(groupName: String)
    }

    fun setData(jobs: List<Job>?, entries: List<DownloadRecord>?) {
        items.clear()

        val hasActive = jobs?.any { !it.finished } == true
        if (hasActive) {
            items.add("DOWNLOADING")
            jobs?.filter { !it.finished }?.forEach { items.add(it) }
        }

        if (!entries.isNullOrEmpty()) {
            val grouped = linkedMapOf<String, MutableList<DownloadRecord>>()
            for (e in entries) {
                grouped.getOrPut(e.parentName()) { mutableListOf() }.add(e)
            }

            items.add("COMPLETED")

            for ((folderName, folderEntries) in grouped) {
                val isExpanded = expandedFolders.contains(folderName)
                items.add(FolderHeader(folderName, folderEntries, isExpanded))
                if (isExpanded) {
                    items.addAll(folderEntries)
                }
            }
        }

        if (items.isEmpty()) items.add("")
        notifyDataSetChanged()
    }

    fun toggleFolder(name: String) {
        if (expandedFolders.contains(name)) expandedFolders.remove(name)
        else expandedFolders.add(name)
    }

    fun isExpanded(name: String): Boolean = expandedFolders.contains(name)

    // ── VIEW TYPES ──

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is String -> TYPE_SECTION
            is FolderHeader -> TYPE_FOLDER
            is Job -> TYPE_ACTIVE
            is DownloadRecord -> TYPE_COMPLETED
            else -> TYPE_SECTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION -> SectionHolder(makeSectionView())
            TYPE_FOLDER -> FolderHolder(makeFolderView())
            TYPE_ACTIVE -> ActiveHolder(makeActiveView())
            TYPE_COMPLETED -> CompletedHolder(makeCompletedView())
            else -> SectionHolder(makeSectionView())
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionHolder -> {
                val text = items[position] as String
                holder.tv.text = if (text.isEmpty()) "No downloads yet" else text
            }
            is FolderHolder -> bindFolder(holder, items[position] as FolderHeader)
            is ActiveHolder -> bindActive(holder, items[position] as Job)
            is CompletedHolder -> bindCompleted(holder, items[position] as DownloadRecord)
        }
    }

    // ── VIEWS ──

    private fun makeSectionView(): TextView {
        return TextView(ctx).apply {
            setPadding(dp(16), dp(16), dp(16), dp(8))
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun makeFolderView(): View {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val icon = ImageView(ctx).apply {
            id = R.id.folder_icon
            setImageResource(R.drawable.ic_folder)
            setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(8) }
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(ctx).apply {
            id = R.id.group_title
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }

        val status = TextView(ctx).apply {
            id = R.id.group_status
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 12f
        }

        texts.addView(title)
        texts.addView(status)

        val deleteIcon = ImageView(ctx).apply {
            id = R.id.folder_delete
            setImageResource(R.drawable.ic_delete)
            setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            makeRippleBorderless(this)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

        layout.addView(icon)
        layout.addView(texts)
        layout.addView(deleteIcon)
        layout.setBackgroundResource(R.drawable.bg_card_ripple)
        return layout
    }

    private fun makeActiveView(): View {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val animeTv = TextView(ctx).apply {
            id = R.id.dl_anime
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val speedTv = TextView(ctx).apply {
            id = R.id.dl_speed
            setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            textSize = 12f
        }

        titleRow.addView(animeTv)
        titleRow.addView(speedTv)

        val epTv = TextView(ctx).apply {
            id = R.id.dl_title
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 12f
        }

        val statusRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val statusTv = TextView(ctx).apply {
            id = R.id.dl_status
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cancelBtn = ImageView(ctx).apply {
            id = R.id.dl_cancel
            setImageResource(R.drawable.ic_close)
            setColorFilter(ContextCompat.getColor(ctx, R.color.error))
            makeRippleBorderless(this)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }

        statusRow.addView(statusTv)
        statusRow.addView(cancelBtn)

        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            id = R.id.dl_progress
            max = 100
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply {
                topMargin = dp(4)
            }
            progressDrawable = ContextCompat.getDrawable(ctx, R.drawable.progress_bar_download)
        }

        layout.addView(titleRow)
        layout.addView(epTv)
        layout.addView(statusRow)
        layout.addView(progress)
        layout.setBackgroundResource(R.drawable.bg_card_ripple)
        return layout
    }

    private fun makeCompletedView(): View {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(ctx).apply {
            id = R.id.dl_title
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
        }

        val sizeTv = TextView(ctx).apply {
            id = R.id.dl_size
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 12f
        }

        texts.addView(titleTv)
        texts.addView(sizeTv)

        val deleteBtn = ImageView(ctx).apply {
            id = R.id.dl_delete
            setImageResource(R.drawable.ic_delete)
            setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
            makeRippleBorderless(this)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

        layout.addView(texts)
        layout.addView(deleteBtn)
        layout.setBackgroundResource(R.drawable.bg_card_ripple)
        return layout
    }

    // ── BIND ──

    private fun bindFolder(h: FolderHolder, fh: FolderHeader) {
        h.title.text = fh.name
        h.status.text = "${fh.entries.size} files"

        val count = fh.entries.size
        h.icon.setImageResource(if (fh.expanded) R.drawable.ic_folder_open else R.drawable.ic_folder)

        h.card.setOnClickListener {
            toggleFolder(fh.name)
            notifyDataSetChanged()
        }

        h.deleteIcon.setOnClickListener {
            listener?.onGroupDeleteRequest(fh.name)
        }
    }

    private fun bindActive(h: ActiveHolder, job: Job) {
        h.animeTv.text = job.animeTitle ?: ""
        h.epTv.text = job.episodeTitle ?: ""

        val speed = formatSpeed(job.bytesPerSecond)
        h.speedTv.text = speed
        h.speedTv.visibility = if (speed.isEmpty()) View.GONE else View.VISIBLE

        val base = job.status ?: ""
        h.statusTv.text = "$base  \u2022  ${job.percent}%"

        h.progress.progress = job.percent.coerceIn(0, 100)
        h.cancelBtn.setOnClickListener { listener?.onCancel(job) }
    }

    private fun bindCompleted(h: CompletedHolder, entry: DownloadRecord) {
        var title = entry.title ?: ""
        if (title.startsWith("Episode ")) {
            title = title.substring(8)
        }
        h.titleTv.text = title
        h.sizeTv.text = formatSize(entry.size)

        h.card.setOnClickListener { listener?.onClick(entry) }
        h.deleteBtn.setOnClickListener { listener?.onDelete(entry) }
    }

    // ── HOLDERS ──

    class SectionHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v as TextView
    }

    class FolderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v
        val icon: ImageView = v.findViewById(R.id.folder_icon)
        val title: TextView = v.findViewById(R.id.group_title)
        val status: TextView = v.findViewById(R.id.group_status)
        val deleteIcon: ImageView = v.findViewById(R.id.folder_delete)
    }

    class ActiveHolder(v: View) : RecyclerView.ViewHolder(v) {
        val animeTv: TextView = v.findViewById(R.id.dl_anime)
        val epTv: TextView = v.findViewById(R.id.dl_title)
        val statusTv: TextView = v.findViewById(R.id.dl_status)
        val speedTv: TextView = v.findViewById(R.id.dl_speed)
        val progress: ProgressBar = v.findViewById(R.id.dl_progress)
        val cancelBtn: ImageView = v.findViewById(R.id.dl_cancel)
    }

    class CompletedHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v
        val titleTv: TextView = v.findViewById(R.id.dl_title)
        val sizeTv: TextView = v.findViewById(R.id.dl_size)
        val deleteBtn: ImageView = v.findViewById(R.id.dl_delete)
    }

    // ── DATA TYPES ──

    class FolderHeader(
        val name: String,
        val entries: List<DownloadRecord>,
        val expanded: Boolean
    )

    // ── HELPERS ──

    private fun dp(value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    private fun roundedBg(colorRes: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(ContextCompat.getColor(ctx, colorRes))
        }
    }

    private fun makeRippleBorderless(v: View) {
        val outValue = android.util.TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        if (outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return ""
        val kb = bytesPerSecond / 1024.0
        return if (kb < 1024) String.format(Locale.US, "%.0f KB/s", kb)
        else String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
    }

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(millis))
    }
}
