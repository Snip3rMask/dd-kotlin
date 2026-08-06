package msr.mirudl.app;

import msr.mirudl.shared.model.DownloadRecord;
import msr.mirudl.shared.download.Job;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DownloadAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_FOLDER = 1;
    private static final int TYPE_ACTIVE = 2;
    private static final int TYPE_COMPLETED = 3;

    private final Context ctx;
    private final List<Object> items = new ArrayList<>();
    private final java.util.Set<String> expandedFolders = new java.util.HashSet<>();
    private OnActionListener listener;

    public interface OnActionListener {
        void onCancel(Job job);
        void onClick(DownloadRecord entry);
        void onDelete(DownloadRecord entry);
        void onSelectionToggle(DownloadRecord entry);
        boolean isSelected(DownloadRecord entry);
        void onGroupDelete(String groupName);
        void onGroupDeleteRequest(String groupName);
    }

    public DownloadAdapter(Context context, OnActionListener listener) {
        this.ctx = context;
        this.listener = listener;
    }

    public void setData(List<Job> jobs, List<DownloadRecord> entries) {
        items.clear();

        // ── ACTIVE SECTION ──
        boolean hasActive = false;
        for (Job j : jobs) if (!j.finished) { hasActive = true; break; }

        if (hasActive) {
            items.add("DOWNLOADING");
            for (Job job : jobs) {
                if (!job.finished) items.add(job);
            }
        }

        // ── COMPLETED SECTION — grouped by anime folder ──
        if (entries != null && !entries.isEmpty()) {
            Map<String, List<DownloadRecord>> grouped = new LinkedHashMap<>();
            for (DownloadRecord e : entries) {
                grouped.computeIfAbsent(e.parentName(), k -> new ArrayList<>()).add(e);
            }

            items.add("COMPLETED");

            for (Map.Entry<String, List<DownloadRecord>> group : grouped.entrySet()) {
                String folderName = group.getKey();
                List<DownloadRecord> folderEntries = group.getValue();
                boolean isExpanded = expandedFolders.contains(folderName);

                items.add(new FolderHeader(folderName, folderEntries, isExpanded));

                if (isExpanded) {
                    for (DownloadRecord e : folderEntries) {
                        items.add(e);
                    }
                }
            }
        }

        if (items.isEmpty()) items.add(""); // empty placeholder
        notifyDataSetChanged();
    }

    public void toggleFolder(String name) {
        if (expandedFolders.contains(name)) expandedFolders.remove(name);
        else expandedFolders.add(name);
    }

    public boolean isExpanded(String name) {
        return expandedFolders.contains(name);
    }

    // ── VIEW TYPES ──

    @Override public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) return TYPE_SECTION;
        if (item instanceof FolderHeader) return TYPE_FOLDER;
        if (item instanceof Job) return TYPE_ACTIVE;
        if (item instanceof DownloadRecord) return TYPE_COMPLETED;
        return TYPE_SECTION;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_SECTION:
                return new SectionHolder(makeSectionView());
            case TYPE_FOLDER:
                return new FolderHolder(makeFolderView());
            case TYPE_ACTIVE:
                return new ActiveHolder(makeActiveView());
            case TYPE_COMPLETED:
                return new CompletedHolder(makeCompletedView());
        }
        return new SectionHolder(makeSectionView());
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
        Object item = items.get(pos);
        if (h instanceof SectionHolder) {
            String text = (String) item;
            ((SectionHolder) h).tv.setText(text.isEmpty() ? "No downloads yet" : text);
        } else if (h instanceof FolderHolder) {
            bindFolder((FolderHolder) h, (FolderHeader) item);
        } else if (h instanceof ActiveHolder) {
            bindActive((ActiveHolder) h, (Job) item);
        } else if (h instanceof CompletedHolder) {
            bindCompleted((CompletedHolder) h, (DownloadRecord) item);
        }
    }

    // ── MAKE VIEWS ──

    private TextView makeSectionView() {
        TextView tv = new TextView(ctx);
        tv.setPadding(dp(20), dp(20), dp(20), dp(8));
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.06f);
        tv.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        return tv;
    }

    private View makeFolderView() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(4), dp(6), dp(12), dp(6));
        card.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(68)));
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) card.getLayoutParams();
        lp.setMargins(dp(12), dp(6), dp(12), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(ContextCompat.getColor(ctx, R.color.surface_variant));
        card.setBackground(bg);

        // Expand icon (icon-square badge, matching Settings row language)
        ImageView icon = new ImageView(ctx);
        icon.setId(R.id.folder_icon);
        icon.setImageResource(android.R.drawable.arrow_down_float);
        icon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary));
        icon.setPadding(dp(14), dp(14), dp(14), dp(14));
        icon.setBackground(roundedBg(R.color.icon_bg, 12));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconLp.setMargins(dp(8), 0, 0, 0);
        card.addView(icon, iconLp);

        // Text column
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(textCol, new LinearLayout.LayoutParams(0, -1, 1));

        TextView groupTitle = new TextView(ctx);
        groupTitle.setId(R.id.group_title);
        groupTitle.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        groupTitle.setTextSize(16);
        groupTitle.setTypeface(null, Typeface.BOLD);
        groupTitle.setMaxLines(1);
        groupTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(groupTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView groupStatus = new TextView(ctx);
        groupStatus.setId(R.id.group_status);
        groupStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        groupStatus.setTextSize(12);
        groupStatus.setTypeface(null, Typeface.NORMAL);
        groupStatus.setMaxLines(1);
        groupStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams gsLp = new LinearLayout.LayoutParams(-1, -2);
        gsLp.setMargins(0, dp(4), 0, 0);
        textCol.addView(groupStatus, gsLp);

        // Delete icon
        ImageView deleteIcon = new ImageView(ctx);
        deleteIcon.setId(R.id.folder_delete);
        deleteIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        deleteIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.text_tertiary));
        deleteIcon.setPadding(dp(6), dp(6), dp(6), dp(6));
        makeRippleBorderless(deleteIcon);
        card.addView(deleteIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        return card;
    }

    private View makeActiveView() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) card.getLayoutParams();
        lp.setMargins(dp(16), dp(4), dp(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(ContextCompat.getColor(ctx, R.color.surface_variant));
        card.setBackground(bg);

        // Top row: icon-square badge + (anime name / episode title)
        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        ImageView leadingIcon = new ImageView(ctx);
        leadingIcon.setImageResource(R.drawable.ic_download);
        leadingIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.primary));
        leadingIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        leadingIcon.setBackground(roundedBg(R.color.icon_bg, 12));
        topRow.addView(leadingIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, -2, 1);
        textColLp.setMargins(dp(12), 0, 0, 0);
        topRow.addView(textCol, textColLp);

        // Anime name row (with speed indicator in the top-right corner)
        LinearLayout animeRow = new LinearLayout(ctx);
        animeRow.setOrientation(LinearLayout.HORIZONTAL);
        animeRow.setGravity(Gravity.CENTER_VERTICAL);
        textCol.addView(animeRow, new LinearLayout.LayoutParams(-1, -2));

        TextView animeTv = new TextView(ctx);
        animeTv.setId(R.id.dl_anime);
        animeTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
        animeTv.setTextSize(11);
        animeTv.setMaxLines(1);
        animeTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        animeRow.addView(animeTv, new LinearLayout.LayoutParams(0, -2, 1));

        TextView speedTv = new TextView(ctx);
        speedTv.setId(R.id.dl_speed);
        speedTv.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        speedTv.setTextSize(11);
        speedTv.setTypeface(null, Typeface.BOLD);
        speedTv.setMaxLines(1);
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(-2, -2);
        speedLp.setMargins(dp(8), 0, 0, 0);
        animeRow.addView(speedTv, speedLp);

        // Episode title
        TextView epTv = new TextView(ctx);
        epTv.setId(R.id.dl_title);
        epTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        epTv.setTextSize(14);
        epTv.setTypeface(null, Typeface.BOLD);
        epTv.setMaxLines(1);
        epTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-1, -2);
        epLp.setMargins(0, dp(2), 0, 0);
        textCol.addView(epTv, epLp);

        // Progress row
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(8), 0, 0);
        card.addView(row, rowLp);

        TextView statusTv = new TextView(ctx);
        statusTv.setId(R.id.dl_status);
        statusTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        statusTv.setTextSize(12);
        statusTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(statusTv);

        ProgressBar progress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progress.setId(R.id.dl_progress);
        progress.setMax(100);
        progress.setProgressDrawable(ctx.getResources().getDrawable(R.drawable.progress_track, ctx.getTheme()));
        row.addView(progress, new LinearLayout.LayoutParams(0, dp(6), 2));

        ImageView cancelBtn = new ImageView(ctx);
        cancelBtn.setId(R.id.dl_cancel);
        cancelBtn.setImageResource(R.drawable.ic_close);
        cancelBtn.setColorFilter(ContextCompat.getColor(ctx, R.color.error));
        cancelBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(30), dp(30));
        clp.setMargins(dp(8), 0, 0, 0);
        cancelBtn.setLayoutParams(clp);
        makeRippleBorderless(cancelBtn);
        row.addView(cancelBtn);

        return card;
    }

        private View makeCompletedView() {
        // Each completed item card
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        RecyclerView.LayoutParams clp = new RecyclerView.LayoutParams(-1, -2);
        clp.setMargins(dp(16), dp(4), dp(16), 0);
        card.setLayoutParams(clp);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(ContextCompat.getColor(ctx, R.color.surface_variant));
        card.setBackground(bg);

        // Completed icon-square badge
        ImageView checkIcon = new ImageView(ctx);
        checkIcon.setImageResource(R.drawable.ic_check);
        checkIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.success));
        checkIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
        checkIcon.setBackground(roundedBg(R.color.icon_bg, 10));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        checkLp.setMargins(0, 0, dp(10), 0);
        card.addView(checkIcon, checkLp);

        // Title
        TextView titleTv = new TextView(ctx);
        titleTv.setId(R.id.dl_title);
        titleTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        titleTv.setTextSize(13);
        titleTv.setMaxLines(1);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1));

        // Size chip
        TextView sizeTv = new TextView(ctx);
        sizeTv.setId(R.id.dl_size);
        sizeTv.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary));
        sizeTv.setTextSize(10);
        sizeTv.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(dp(8));
        chipBg.setColor(ContextCompat.getColor(ctx, R.color.surface_high));
        sizeTv.setBackground(chipBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(0, 0, dp(6), 0);
        sizeTv.setLayoutParams(sLp);
        card.addView(sizeTv);

        // Delete button
        ImageView deleteBtn = new ImageView(ctx);
        deleteBtn.setId(R.id.dl_delete);
        deleteBtn.setImageResource(R.drawable.ic_delete);
        deleteBtn.setColorFilter(ContextCompat.getColor(ctx, R.color.text_tertiary));
        deleteBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
        makeRippleBorderless(deleteBtn);
        card.addView(deleteBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        return card;
    }
    private void bindFolder(FolderHolder h, FolderHeader fh) {
        h.title.setText(fh.name);

        // Calculate total size
        long totalBytes = 0;
        for (DownloadRecord e : fh.entries) totalBytes += e.size;
        String sizeStr = formatSize(totalBytes);
        String status = fh.entries.size() + " episode" + (fh.entries.size() > 1 ? "s" : "")
                + "  \u2022  " + sizeStr;
        h.status.setText(status);

        h.icon.setImageResource(fh.expanded
                ? android.R.drawable.arrow_up_float
                : android.R.drawable.arrow_down_float);

        // Card click = toggle expand. Listener handles toggle + refresh.
        // Do NOT call toggleFolder() here — the listener does it.
        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onGroupDelete(fh.name);
        });

        // Delete icon = show delete dialog for entire group
        h.deleteIcon.setOnClickListener(v -> {
            if (listener != null) listener.onGroupDeleteRequest(fh.name);
        });
    }

    private void bindActive(ActiveHolder h, Job job) {
        h.animeTv.setText(job.animeTitle != null ? job.animeTitle : "");
        h.epTv.setText(job.episodeTitle != null ? job.episodeTitle : "");

        String speed = formatSpeed(job.bytesPerSecond);
        h.speedTv.setText(speed);
        h.speedTv.setVisibility(speed.isEmpty() ? View.GONE : View.VISIBLE);

        String base = job.status != null ? job.status : "";
        h.statusTv.setText(base + "  \u2022  " + job.percent + "%");

        h.progress.setProgress(Math.max(0, Math.min(100, job.percent)));
        h.cancelBtn.setOnClickListener(v -> {
            if (listener != null) listener.onCancel(job);
        });
    }

    private void bindCompleted(CompletedHolder h, DownloadRecord entry) {
        String title = entry.title != null ? entry.title : "";
        // Remove "Episode " prefix if present for cleaner display
        if (title.startsWith("Episode ")) {
            title = title.substring(8);
        }
        h.titleTv.setText(title);
        h.sizeTv.setText(formatSize(entry.size));

        h.card.setOnClickListener(v -> {
            if (listener != null) listener.onClick(entry);
        });

        h.deleteBtn.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(entry);
        });
    }

    // ── HOLDERS ──

    static class SectionHolder extends RecyclerView.ViewHolder {
        TextView tv;
        SectionHolder(View v) { super(v); tv = (TextView) v; }
    }

    static class FolderHolder extends RecyclerView.ViewHolder {
        View card;
        ImageView icon, deleteIcon;
        TextView title, status;
        FolderHolder(View v) {
            super(v);
            card = v;
            icon = v.findViewById(R.id.folder_icon);
            title = v.findViewById(R.id.group_title);
            status = v.findViewById(R.id.group_status);
            deleteIcon = v.findViewById(R.id.folder_delete);
        }
    }

    static class ActiveHolder extends RecyclerView.ViewHolder {
        TextView animeTv, epTv, statusTv, speedTv;
        ProgressBar progress;
        ImageView cancelBtn;
        ActiveHolder(View v) {
            super(v);
            animeTv = v.findViewById(R.id.dl_anime);
            epTv = v.findViewById(R.id.dl_title);
            statusTv = v.findViewById(R.id.dl_status);
            speedTv = v.findViewById(R.id.dl_speed);
            progress = v.findViewById(R.id.dl_progress);
            cancelBtn = v.findViewById(R.id.dl_cancel);
        }
    }

    static class CompletedHolder extends RecyclerView.ViewHolder {
        View card;
        TextView titleTv, sizeTv;
        ImageView deleteBtn;
        CompletedHolder(View v) {
            super(v);
            card = v;
            titleTv = v.findViewById(R.id.dl_title);
            sizeTv = v.findViewById(R.id.dl_size);
            deleteBtn = v.findViewById(R.id.dl_delete);
        }
    }

    // ── DATA TYPES ──

    static class FolderHeader {
        final String name;
        final List<DownloadRecord> entries;
        final boolean expanded;
        FolderHeader(String name, List<DownloadRecord> entries, boolean expanded) {
            this.name = name; this.entries = entries; this.expanded = expanded;
        }
    }

    // ── HELPERS ──

    private int dp(int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBg(int colorRes, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(ContextCompat.getColor(ctx, colorRes));
        return d;
    }

    private void makeRippleBorderless(View v) {
        android.util.TypedValue outValue = new android.util.TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        if (outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024)
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "";
        double kb = bytesPerSecond / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB/s", kb);
        return String.format(Locale.US, "%.1f MB/s", kb / 1024.0);
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(new Date(millis));
    }
}
