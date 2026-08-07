package msr.mirudl.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import msr.mirudl.shared.model.EpisodeItem
import java.util.Locale

class EpisodeAdapter(private val listener: Listener?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    private var fullItems: List<EpisodeItem> = emptyList()
    private var items: List<EpisodeItem> = emptyList()
    private var downloadedEpisodes: Set<String> = emptySet()

    private var gridMode = false
    private var reversed = false
    private var selectionMode = false
    private var query = ""

    interface Listener {
        fun onClick(episode: EpisodeItem)
        fun onSelectionChanged(selectedCount: Int)
    }

    // ── DATA ──

    fun setItems(newItems: List<EpisodeItem>?) {
        fullItems = newItems ?: emptyList()
        rebuild()
    }

    fun setDownloadedEpisodes(downloaded: Set<String>?) {
        downloadedEpisodes = downloaded ?: emptySet()
        notifyDataSetChanged()
    }

    // ── DISPLAY MODE ──

    fun setGridMode(grid: Boolean) {
        if (gridMode == grid) return
        gridMode = grid
        notifyDataSetChanged()
    }

    fun isGridMode(): Boolean = gridMode

    fun setReversed(reversed: Boolean) {
        this.reversed = reversed
        rebuild()
    }

    fun isReversed(): Boolean = reversed

    fun setQuery(q: String?) {
        query = q?.trim() ?: ""
        rebuild()
    }

    private fun rebuild() {
        val filtered = if (query.isEmpty()) {
            fullItems.toMutableList()
        } else {
            val lower = query.lowercase(Locale.US)
            fullItems.filter { ep ->
                ep.getLabel().contains(query) ||
                    (ep.title?.lowercase(Locale.US)?.contains(lower) == true)
            }.toMutableList()
        }
        if (reversed) filtered.reverse()
        items = filtered
        notifyDataSetChanged()
    }

    // ── SELECTION MODE ──

    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            for (ep in fullItems) ep.selected = false
        }
        notifyDataSetChanged()
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun selectAllVisible() {
        for (ep in items) ep.selected = true
        notifyDataSetChanged()
        listener?.onSelectionChanged(getSelectedEpisodes().size)
    }

    fun clearSelection() {
        for (ep in fullItems) ep.selected = false
        notifyDataSetChanged()
        listener?.onSelectionChanged(0)
    }

    fun getSelectedEpisodes(): List<EpisodeItem> {
        return fullItems.filter { it.selected }
    }

    // ── ADAPTER ──

    override fun getItemViewType(position: Int): Int =
        if (gridMode) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_GRID) {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode_grid, parent, false)
            GridHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode, parent, false)
            ListHolder(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val ep = items[pos]
        when (holder) {
            is GridHolder -> bindGrid(holder, ep)
            is ListHolder -> bindList(holder, ep)
        }
    }

    private fun bindList(h: ListHolder, ep: EpisodeItem) {
        val ctx = h.itemView.context
        h.epNum.text = ep.getLabel()

        if (ep.filler) {
            h.title.text = "Episode ${ep.getLabel()} [FILLER]"
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        } else {
            h.title.text = "Episode ${ep.getLabel()}"
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }

        h.downloadIcon.visibility = View.VISIBLE

        if (selectionMode) {
            h.itemView.setBackgroundColor(if (ep.selected) 0x140EA5E9 else 0x00000000)
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (ep.selected) {
                    setColor(ContextCompat.getColor(ctx, R.color.primary))
                    h.downloadIcon.setImageResource(R.drawable.ic_check)
                    h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.on_primary))
                } else {
                    setColor(0x00000000)
                    setStroke(dp(ctx, 2), ContextCompat.getColor(ctx, R.color.divider))
                    h.downloadIcon.setImageDrawable(null)
                }
            }
            h.downloadIcon.background = dot
        } else {
            h.itemView.setBackgroundResource(R.drawable.bg_card_ripple)
            if (downloadedEpisodes.contains(ep.getLabel())) {
                h.downloadIcon.background = null
                h.downloadIcon.setImageResource(R.drawable.ic_check)
                h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.success))
            } else {
                h.downloadIcon.background = null
                h.downloadIcon.setImageResource(R.drawable.ic_download)
                h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary))
            }
        }

        h.itemView.setOnClickListener { handleClick(ep) }
    }

    private fun bindGrid(h: GridHolder, ep: EpisodeItem) {
        val ctx = h.itemView.context
        h.number.text = ep.getLabel()
        val downloaded = downloadedEpisodes.contains(ep.getLabel())

        val bg = GradientDrawable().apply {
            cornerRadius = dp(ctx, 10).toFloat()
        }

        if (selectionMode && ep.selected) {
            bg.setColor(ContextCompat.getColor(ctx, R.color.primary))
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.on_primary))
            h.check.visibility = View.VISIBLE
        } else if (downloaded) {
            bg.setColor(ContextCompat.getColor(ctx, R.color.icon_bg))
            bg.setStroke(dp(ctx, 1), ContextCompat.getColor(ctx, R.color.success))
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.success))
            h.check.visibility = View.GONE
        } else {
            bg.setColor(ContextCompat.getColor(ctx, R.color.icon_bg))
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            h.check.visibility = View.GONE
        }
        h.number.background = bg
        h.number.setOnClickListener { handleClick(ep) }
    }

    private fun handleClick(ep: EpisodeItem) {
        if (selectionMode) {
            ep.selected = !ep.selected
            notifyDataSetChanged()
            listener?.onSelectionChanged(getSelectedEpisodes().size)
        } else {
            listener?.onClick(ep)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()

    class ListHolder(v: View) : RecyclerView.ViewHolder(v) {
        val epNum: TextView = v.findViewById(R.id.ep_number)
        val title: TextView = v.findViewById(R.id.ep_title)
        val downloadIcon: ImageView = v.findViewById(R.id.ep_download)
    }

    class GridHolder(v: View) : RecyclerView.ViewHolder(v) {
        val number: TextView = v.findViewById(R.id.grid_ep_number)
        val check: ImageView = v.findViewById(R.id.grid_ep_check)
    }
}
