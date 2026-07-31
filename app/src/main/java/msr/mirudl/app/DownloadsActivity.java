package msr.mirudl.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends BaseActivity {
    private RecyclerView downloadsList;
    private TextView emptyText;
    private DownloadAdapter downloadAdapter;

    private boolean sortByAnime = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_finished).setOnClickListener(v -> clearFinished());
        findViewById(R.id.btn_sort).setOnClickListener(v -> {
            sortByAnime = !sortByAnime;
            refresh();
        });

        downloadsList = findViewById(R.id.downloads_list);
        emptyText = findViewById(R.id.empty_text);

        downloadAdapter = new DownloadAdapter(this, new DownloadAdapter.OnActionListener() {
            @Override public void onCancel(DownloadManager.Job job) {
                if (job != null) {
                    DownloadManager.cancel(job);
                    // Also notify the service
                    Intent cancelIntent = new Intent(DownloadsActivity.this, DownloadService.class);
                    cancelIntent.setAction("cancel");
                    cancelIntent.putExtra("jobId", job.id);
                    startService(cancelIntent);
                    refresh();
                }
            }
            @Override public void onClick(DownloadEntryStore.Entry entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                if (dlEntry.uri != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(dlEntry.uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(DownloadsActivity.this,
                                "Cannot open file", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override public void onDelete(DownloadEntryStore.Entry entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                showDeleteDownloadDialog(dlEntry);
            }
            @Override public void onSelectionToggle(DownloadEntryStore.Entry entry) {
                refresh();
            }
            @Override public boolean isSelected(DownloadEntryStore.Entry entry) {
                return false;
            }
            @Override public void onGroupDelete(String groupName) {
                downloadAdapter.toggleFolder(groupName);
                refresh();
            }
            @Override public void onGroupDeleteRequest(String groupName) {
                showGroupDeleteDialog(groupName);
            }
        });

        downloadsList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        downloadsList.setAdapter(downloadAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeStuckJobs();
        handler.post(refreshTask);
    }

    /** If any job is stuck as not-finished but the service isn't actually
     *  running/processing it (e.g. it got killed by the OS), restart the
     *  service so it isn't stuck showing "Queued" forever. */
    private void resumeStuckJobs() {
        if (DownloadManager.isRunning()) return;
        List<DownloadManager.Job> stuck = new ArrayList<>();
        for (DownloadManager.Job j : DownloadManager.snapshot()) {
            if (!j.finished) stuck.add(j);
        }
        if (stuck.isEmpty()) return;
        Intent intent = DownloadService.startIntent(this, stuck);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    private void refresh() {
        List<DownloadManager.Job> active = DownloadManager.snapshot();
        List<DownloadEntryStore.Entry> completed = DownloadEntryStore.all(this);

        // Sort by anime name if mode is 'by anime'
        if (sortByAnime && completed != null) {
            completed.sort((a, b) -> {
                int cmp = a.parentName().compareToIgnoreCase(b.parentName());
                if (cmp == 0) return Long.compare(b.completedAt, a.completedAt);
                return cmp;
            });
        }

        boolean hasActive = false;
        for (DownloadManager.Job j : active) {
            if (!j.finished) { hasActive = true; break; }
        }

        emptyText.setVisibility(
            (!hasActive && (completed == null || completed.isEmpty())) ? View.VISIBLE : View.GONE);

        downloadAdapter.setData(active, completed);
    }

    private void clearFinished() {
        List<DownloadEntryStore.Entry> entries = DownloadEntryStore.all(this);
        if (!entries.isEmpty()) {
            // Modern alert dialog
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(24), dp(24), dp(24), dp(20));

            // Title row with icon
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_delete);
            icon.setColorFilter(getColor(R.color.error));
            icon.setPadding(dp(8), dp(8), dp(8), dp(8));
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(0x22F87171);
            icon.setBackground(iconBg);
            titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView title = new TextView(this);
            title.setText("Clear History");
            title.setTextColor(getColor(R.color.text_primary));
            title.setTextSize(17);
            title.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
            tLp.setMargins(dp(14), 0, 0, 0);
            titleRow.addView(title, tLp);
            box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

            TextView msg = new TextView(this);
            msg.setText("Remove all download history? This won't delete the actual files.");
            msg.setTextColor(getColor(R.color.text_tertiary));
            msg.setTextSize(13);
            msg.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
            msgLp.setMargins(0, dp(16), 0, dp(22));
            box.addView(msg, msgLp);

            // Clear button
            TextView clearBtn = new TextView(this);
            clearBtn.setText("Clear History");
            clearBtn.setTextColor(getColor(R.color.text_primary));
            clearBtn.setTextSize(14);
            clearBtn.setTypeface(null, Typeface.BOLD);
            clearBtn.setGravity(Gravity.CENTER);
            GradientDrawable clearBg = new GradientDrawable();
            clearBg.setCornerRadius(dp(12));
            clearBg.setColor(getColor(R.color.error));
            clearBtn.setBackground(clearBg);
            clearBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
            box.addView(clearBtn);
            

            // Cancel
            TextView cancelBtn = new TextView(this);
            cancelBtn.setText("Cancel");
            cancelBtn.setTextColor(getColor(R.color.text_tertiary));
            cancelBtn.setTextSize(13);
            cancelBtn.setTypeface(null, Typeface.BOLD);
            cancelBtn.setGravity(Gravity.CENTER);
            GradientDrawable canBg = new GradientDrawable();
            canBg.setCornerRadius(dp(12));
            canBg.setColor(0x00000000);
            canBg.setStroke(dp(1), getColor(R.color.divider));
            cancelBtn.setBackground(canBg);
            LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(-1, dp(48));
            canLp.setMargins(0, dp(10), 0, 0);
            box.addView(cancelBtn, canLp);
            

            android.app.AlertDialog dialog = builder.setView(box).create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
            }
            dialog.show();
            clearBtn.setOnClickListener(v -> {
                DownloadEntryStore.removeAll(DownloadsActivity.this, entries);
                DownloadManager.removeFinished();
                dialog.dismiss();
                refresh();
            });
            cancelBtn.setOnClickListener(v -> dialog.dismiss());
        }
        DownloadManager.removeFinished();
        refresh();
    }

    private void showGroupDeleteDialog(String groupName) {
        List<DownloadEntryStore.Entry> all = DownloadEntryStore.all(this);
        List<DownloadEntry> groupEntries = new ArrayList<>();
        for (DownloadEntryStore.Entry e : all) {
            if (groupName.equals(e.parentName())) {
                groupEntries.add(DownloadEntry.fromRecord(e));
            }
        }
        if (!groupEntries.isEmpty()) {
            showDeleteDownloadDialog(groupEntries.get(0));
        }
    }

        private void showDeleteDownloadDialog(DownloadEntry dlEntry) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setColorFilter(getColor(R.color.error));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0x22F87171);
        icon.setBackground(iconBg);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        String displayName = dlEntry.title != null ? dlEntry.title : "";
        if (displayName.startsWith("Episode ")) displayName = displayName.substring(8);

        TextView title = new TextView(this);
        title.setText("Delete download?");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.setMargins(dp(14), 0, 0, 0);
        titleRow.addView(title, tLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView epName = new TextView(this);
        epName.setText(displayName);
        epName.setTextColor(getColor(R.color.text_tertiary));
        epName.setTextSize(13);
        epName.setMaxLines(2);
        epName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams enLp = new LinearLayout.LayoutParams(-1, -2);
        enLp.setMargins(dp(58), dp(4), 0, dp(20));
        box.addView(epName, enLp);

        // Delete button
        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("Delete file permanently");
        deleteBtn.setTextColor(getColor(R.color.text_primary));
        deleteBtn.setTextSize(14);
        deleteBtn.setTypeface(null, Typeface.BOLD);
        deleteBtn.setGravity(Gravity.CENTER);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setCornerRadius(dp(12));
        delBg.setColor(getColor(R.color.error));
        deleteBtn.setBackground(delBg);
        deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        box.addView(deleteBtn);

        // Remove from history button
        TextView removeBtn = new TextView(this);
        removeBtn.setText("Remove from history only");
        removeBtn.setTextColor(getColor(R.color.text_primary));
        removeBtn.setTextSize(13);
        removeBtn.setTypeface(null, Typeface.BOLD);
        removeBtn.setGravity(Gravity.CENTER);
        GradientDrawable remBg = new GradientDrawable();
        remBg.setCornerRadius(dp(12));
        remBg.setColor(getColor(R.color.surface_variant));
        removeBtn.setBackground(remBg);
        LinearLayout.LayoutParams remLp = new LinearLayout.LayoutParams(-1, dp(48));
        remLp.setMargins(0, dp(10), 0, 0);
        box.addView(removeBtn, remLp);

        // Cancel button
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Cancel");
        cancelBtn.setTextColor(getColor(R.color.text_tertiary));
        cancelBtn.setTextSize(13);
        cancelBtn.setTypeface(null, Typeface.BOLD);
        cancelBtn.setGravity(Gravity.CENTER);
        GradientDrawable canBg = new GradientDrawable();
        canBg.setCornerRadius(dp(12));
        canBg.setColor(0x00000000);
        canBg.setStroke(dp(1), getColor(R.color.divider));
        cancelBtn.setBackground(canBg);
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(-1, dp(48));
        canLp.setMargins(0, dp(10), 0, 0);
        box.addView(cancelBtn, canLp);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();

        deleteBtn.setOnClickListener(v -> {
            dlEntry.deleteFileAndRecord(this);
            dialog.dismiss();
            refresh();
        });
        removeBtn.setOnClickListener(v -> {
            dlEntry.deleteRecordOnly(this);
            dialog.dismiss();
            refresh();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
    }
private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
