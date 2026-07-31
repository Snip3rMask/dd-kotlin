package msr.mirudl.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public class EpisodeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    private List<EpisodeItem> fullItems = new ArrayList<>();  // original load order, unfiltered
    private List<EpisodeItem> items = new ArrayList<>();      // filtered + ordered view shown on screen
    private Set<String> downloadedEpisodes = new HashSet<>();

    private boolean gridMode = false;
    private boolean reversed = false;
    private boolean selectionMode = false;
    private String query = "";

    private final Listener listener;

    public interface Listener {
        /** Normal tap (not in selection mode) — start/resolve download for this episode. */
        void onClick(EpisodeItem episode);
        /** Fired whenever the selected-episode count changes while in selection mode. */
        void onSelectionChanged(int selectedCount);
    }

    public EpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    // ── DATA ──

    public void setItems(List<EpisodeItem> newItems) {
        this.fullItems = newItems != null ? newItems : new ArrayList<>();
        rebuild();
    }

    public void setDownloadedEpisodes(Set<String> downloaded) {
        this.downloadedEpisodes = downloaded != null ? downloaded : new HashSet<>();
        notifyDataSetChanged();
    }

    // ── DISPLAY MODE ──

    public void setGridMode(boolean grid) {
        if (this.gridMode == grid) return;
        this.gridMode = grid;
        notifyDataSetChanged();
    }

    public boolean isGridMode() { return gridMode; }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
        rebuild();
    }

    public boolean isReversed() { return reversed; }

    public void setQuery(String q) {
        this.query = q != null ? q.trim() : "";
        rebuild();
    }

    private void rebuild() {
        List<EpisodeItem> filtered = new ArrayList<>();
        if (query.isEmpty()) {
            filtered.addAll(fullItems);
        } else {
            String lower = query.toLowerCase(Locale.US);
            for (EpisodeItem ep : fullItems) {
                boolean numMatch = ep.getLabel().contains(query);
                boolean titleMatch = ep.title != null && ep.title.toLowerCase(Locale.US).contains(lower);
                if (numMatch || titleMatch) filtered.add(ep);
            }
        }
        if (reversed) Collections.reverse(filtered);
        this.items = filtered;
        notifyDataSetChanged();
    }

    // ── SELECTION MODE (for multi-episode download) ──

    public void setSelectionMode(boolean enabled) {
        this.selectionMode = enabled;
        if (!enabled) {
            for (EpisodeItem ep : fullItems) ep.selected = false;
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void selectAllVisible() {
        for (EpisodeItem ep : items) ep.selected = true;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(getSelectedEpisodes().size());
    }

    public void clearSelection() {
        for (EpisodeItem ep : fullItems) ep.selected = false;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    /** Returns selected episodes in their original (natural) episode order. */
    public List<EpisodeItem> getSelectedEpisodes() {
        List<EpisodeItem> selected = new ArrayList<>();
        for (EpisodeItem ep : fullItems) if (ep.selected) selected.add(ep);
        return selected;
    }

    // ── ADAPTER ──

    @Override
    public int getItemViewType(int position) {
        return gridMode ? TYPE_GRID : TYPE_LIST;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_GRID) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_episode_grid, parent, false);
            return new GridHolder(v);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode, parent, false);
        return new ListHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int pos) {
        EpisodeItem ep = items.get(pos);
        if (holder instanceof GridHolder) {
            bindGrid((GridHolder) holder, ep);
        } else {
            bindList((ListHolder) holder, ep);
        }
    }

    private void bindList(ListHolder h, EpisodeItem ep) {
        Context ctx = h.itemView.getContext();
        h.epNum.setText(ep.getLabel());

        if (ep.filler) {
            h.title.setText("Episode " + ep.getLabel() + " [FILLER]");
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        } else {
            h.title.setText("Episode " + ep.getLabel());
            h.title.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        }

        h.downloadIcon.setVisibility(View.VISIBLE);

        if (selectionMode) {
            h.itemView.setBackgroundColor(ep.selected ? 0x140EA5E9 : 0x00000000);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            if (ep.selected) {
                dot.setColor(ContextCompat.getColor(ctx, R.color.primary));
                h.downloadIcon.setImageResource(R.drawable.ic_check);
                h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.on_primary));
            } else {
                dot.setColor(0x00000000);
                dot.setStroke(dp(ctx, 2), ContextCompat.getColor(ctx, R.color.divider));
                h.downloadIcon.setImageDrawable(null);
            }
            h.downloadIcon.setBackground(dot);
        } else {
            h.itemView.setBackground(ContextCompat.getDrawable(ctx, R.drawable.bg_card_ripple));
            if (downloadedEpisodes.contains(ep.getLabel())) {
                h.downloadIcon.setBackground(null);
                h.downloadIcon.setImageResource(R.drawable.ic_check);
                h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.success));
            } else {
                h.downloadIcon.setBackground(null);
                h.downloadIcon.setImageResource(R.drawable.ic_download);
                h.downloadIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary));
            }
        }

        h.itemView.setOnClickListener(v -> handleClick(ep));
    }

    private void bindGrid(GridHolder h, EpisodeItem ep) {
        Context ctx = h.itemView.getContext();
        h.number.setText(ep.getLabel());
        boolean downloaded = downloadedEpisodes.contains(ep.getLabel());

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 10));

        if (selectionMode && ep.selected) {
            bg.setColor(ContextCompat.getColor(ctx, R.color.primary));
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.on_primary));
            h.check.setVisibility(View.VISIBLE);
        } else if (downloaded) {
            bg.setColor(ContextCompat.getColor(ctx, R.color.icon_bg));
            bg.setStroke(dp(ctx, 1), ContextCompat.getColor(ctx, R.color.success));
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.success));
            h.check.setVisibility(View.GONE);
        } else {
            bg.setColor(ContextCompat.getColor(ctx, R.color.icon_bg));
            h.number.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
            h.check.setVisibility(View.GONE);
        }
        h.number.setBackground(bg);
        h.number.setOnClickListener(v -> handleClick(ep));
    }

    private void handleClick(EpisodeItem ep) {
        if (selectionMode) {
            ep.selected = !ep.selected;
            notifyDataSetChanged();
            if (listener != null) listener.onSelectionChanged(getSelectedEpisodes().size());
        } else if (listener != null) {
            listener.onClick(ep);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    private int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    static class ListHolder extends RecyclerView.ViewHolder {
        TextView epNum, title;
        ImageView downloadIcon;
        ListHolder(View v) {
            super(v);
            epNum = v.findViewById(R.id.ep_number);
            title = v.findViewById(R.id.ep_title);
            downloadIcon = v.findViewById(R.id.ep_download);
        }
    }

    static class GridHolder extends RecyclerView.ViewHolder {
        TextView number;
        ImageView check;
        GridHolder(View v) {
            super(v);
            number = v.findViewById(R.id.grid_ep_number);
            check = v.findViewById(R.id.grid_ep_check);
        }
    }
}
