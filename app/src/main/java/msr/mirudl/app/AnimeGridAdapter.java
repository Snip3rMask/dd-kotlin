package msr.mirudl.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class AnimeGridAdapter extends RecyclerView.Adapter<AnimeGridAdapter.ViewHolder> {
    private List<AnimeItem> items = new ArrayList<>();
    private final OnAnimeClickListener listener;

    public interface OnAnimeClickListener {
        void onClick(AnimeItem anime);
    }

    public AnimeGridAdapter(OnAnimeClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<AnimeItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_anime_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder h, int pos) {
        AnimeItem item = items.get(pos);
        h.title.setText(item.title != null ? item.title : "Unknown");
        if (item.thumbnail != null && !item.thumbnail.isEmpty()) {
            Glide.with(h.thumb.getContext())
                    .load(item.thumbnail)
                    .placeholder(R.drawable.bg_thumb_placeholder)
                    .centerCrop()
                    .into(h.thumb);
        }

        if (item.rating != null && !item.rating.isEmpty() && !item.rating.equals("0")) {
            h.ratingBadge.setVisibility(View.VISIBLE);
            h.ratingText.setText(item.rating);
        } else {
            h.ratingBadge.setVisibility(View.GONE);
        }

        if (item.episodes > 0) {
            h.episodesBadge.setVisibility(View.VISIBLE);
            h.episodesBadge.setText(item.episodes + " EP");
        } else {
            h.episodesBadge.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView title;
        View ratingBadge;
        TextView ratingText;
        TextView episodesBadge;
        ViewHolder(View v) {
            super(v);
            thumb = v.findViewById(R.id.anime_thumb);
            title = v.findViewById(R.id.anime_title);
            ratingBadge = v.findViewById(R.id.anime_rating_badge);
            ratingText = v.findViewById(R.id.anime_rating);
            episodesBadge = v.findViewById(R.id.anime_episodes);
        }
    }
}
