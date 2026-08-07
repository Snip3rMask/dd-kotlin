package msr.mirudl.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import msr.mirudl.shared.model.AnimeItem

class AnimeGridAdapter(private val listener: OnAnimeClickListener?) :
    RecyclerView.Adapter<AnimeGridAdapter.ViewHolder>() {

    private var items: List<AnimeItem> = emptyList()

    interface OnAnimeClickListener {
        fun onClick(anime: AnimeItem)
    }

    fun setItems(newItems: List<AnimeItem>?) {
        items = newItems ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val item = items[pos]
        h.title.text = item.title ?: "Unknown"
        if (!item.thumbnail.isNullOrEmpty()) {
            Glide.with(h.thumb.context)
                .load(item.thumbnail)
                .placeholder(R.drawable.bg_thumb_placeholder)
                .centerCrop()
                .into(h.thumb)
        }

        if (!item.rating.isNullOrEmpty() && item.rating != "0") {
            h.ratingBadge.visibility = View.VISIBLE
            h.ratingText.text = item.rating
        } else {
            h.ratingBadge.visibility = View.GONE
        }

        if (item.episodes > 0) {
            h.episodesBadge.visibility = View.VISIBLE
            h.episodesBadge.text = "${item.episodes} EP"
        } else {
            h.episodesBadge.visibility = View.GONE
        }

        h.itemView.setOnClickListener { listener?.onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.anime_thumb)
        val title: TextView = v.findViewById(R.id.anime_title)
        val ratingBadge: View = v.findViewById(R.id.anime_rating_badge)
        val ratingText: TextView = v.findViewById(R.id.anime_rating)
        val episodesBadge: TextView = v.findViewById(R.id.anime_episodes)
    }
}
