package msr.mirudl.app;

import msr.mirudl.shared.storage.StorageSettingsAndroid;

import msr.mirudl.shared.model.DownloadRecord;
import msr.mirudl.shared.storage.DownloadEntryStoreAndroid;
import msr.mirudl.shared.model.EpisodeItem;
import msr.mirudl.shared.model.VideoSource;
import msr.mirudl.shared.network.MiruClientAndroid;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends BaseActivity {
    private static final int GRID_THRESHOLD = 40; // switch to numeric grid beyond this many episodes
    private static final int GRID_SPAN = 5;

    private String animeId, animeTitle, animeThumb, animeUrl;
    private RecyclerView episodeList;
    private ProgressBar loadingBar;
    private TextView titleText, emptyText, episodeCount, detailMeta, detailDesc, detailStatus;
    private EpisodeAdapter adapter;

    private View episodeSearchRow, selectBar, btnDownloadAll;
    private EditText episodeSearchInput;
    private ImageView btnEpSearch, btnEpReverse;
    private TextView btnEpSelect, selectCountText, btnDownloadSelected;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<EpisodeItem> episodes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        animeId = getIntent().getStringExtra("anime_id");
        animeTitle = getIntent().getStringExtra("anime_title");
        animeThumb = getIntent().getStringExtra("anime_thumb");
        animeUrl = getIntent().getStringExtra("anime_url");

        titleText = findViewById(R.id.detail_title);
        episodeList = findViewById(R.id.episode_list);
        loadingBar = findViewById(R.id.loading_bar);
        emptyText = findViewById(R.id.empty_text);
        episodeCount = findViewById(R.id.episode_count);
        detailMeta = findViewById(R.id.detail_meta);
        detailDesc = findViewById(R.id.detail_description);
        detailStatus = findViewById(R.id.detail_status);

        episodeSearchRow = findViewById(R.id.episode_search_row);
        episodeSearchInput = findViewById(R.id.episode_search_input);
        selectBar = findViewById(R.id.select_bar);
        btnDownloadAll = findViewById(R.id.btn_download_all);
        btnEpSearch = findViewById(R.id.btn_ep_search);
        btnEpReverse = findViewById(R.id.btn_ep_reverse);
        btnEpSelect = findViewById(R.id.btn_ep_select);
        selectCountText = findViewById(R.id.select_count_text);
        btnDownloadSelected = findViewById(R.id.btn_download_selected);

        ImageView thumbView = findViewById(R.id.detail_thumb);
        TextView titleView = findViewById(R.id.detail_title_text);

        titleText.setText(animeTitle);
        if (titleView != null) titleView.setText(animeTitle);

        if (animeThumb != null && !animeThumb.isEmpty()) {
            Glide.with(this).load(animeThumb).centerCrop().into(thumbView);
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Download All button
        btnDownloadAll.setOnClickListener(v -> {
            if (episodes.isEmpty()) {
                Toast.makeText(this, "No episodes available", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!StorageSettingsAndroid.hasDownloadUri(this)) {
                showFolderRequiredDialog();
                return;
            }
            showDownloadAllDialog();
        });

        adapter = new EpisodeAdapter(new EpisodeAdapter.Listener() {
            @Override
            public void onClick(EpisodeItem ep) {
                if (ep.hlsUrl != null) {
                    startDownload(ep);
                } else {
                    resolveAndDownload(ep);
                }
            }

            @Override
            public void onSelectionChanged(int count) {
                selectCountText.setText(count + " selected");
                btnDownloadSelected.setAlpha(count > 0 ? 1f : 0.5f);
            }
        });

        episodeList.setLayoutManager(new LinearLayoutManager(this));
        episodeList.setAdapter(adapter);

        // Search toggle
        btnEpSearch.setOnClickListener(v -> {
            boolean showing = episodeSearchRow.getVisibility() == View.VISIBLE;
            if (showing) {
                episodeSearchRow.setVisibility(View.GONE);
                episodeSearchInput.setText("");
                adapter.setQuery("");
                btnEpSearch.setColorFilter(getColor(R.color.text_secondary));
            } else {
                episodeSearchRow.setVisibility(View.VISIBLE);
                episodeSearchInput.requestFocus();
                btnEpSearch.setColorFilter(getColor(R.color.primary));
            }
        });
        episodeSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
            }
        });

        // Reverse order toggle (e.g. useful to grab the newest episodes of a long-running anime first)
        btnEpReverse.setOnClickListener(v -> {
            boolean nowReversed = !adapter.isReversed();
            adapter.setReversed(nowReversed);
            btnEpReverse.setColorFilter(getColor(nowReversed ? R.color.primary : R.color.text_secondary));
        });

        // Multi-select toggle
        btnEpSelect.setOnClickListener(v -> enterSelectionMode());
        findViewById(R.id.btn_select_close).setOnClickListener(v -> exitSelectionMode());
        findViewById(R.id.btn_select_all).setOnClickListener(v -> adapter.selectAllVisible());
        btnDownloadSelected.setOnClickListener(v -> {
            List<EpisodeItem> selected = adapter.getSelectedEpisodes();
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one episode", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!StorageSettingsAndroid.hasDownloadUri(this)) {
                showFolderRequiredDialog();
                return;
            }
            downloadSelectedEpisodes(selected);
            exitSelectionMode();
        });

        // Load downloaded episodes for status indicator
        adapter.setDownloadedEpisodes(currentDownloadedSet());

        loadEpisodes();
        loadDetails();
    }

    private void loadDetails() {
        // No external title/details fetching needed
        // Title comes from intent extra (search/anime card name)
    }
    private void loadEpisodes() {
        loadingBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                episodes = MiruClientAndroid.getEpisodes(animeId);
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    String count = episodes.size() + " episodes";
                    episodeCount.setText(count);

                    boolean gridMode = episodes.size() > GRID_THRESHOLD;
                    episodeList.setLayoutManager(gridMode
                            ? new GridLayoutManager(this, GRID_SPAN)
                            : new LinearLayoutManager(this));
                    adapter.setGridMode(gridMode);
                    adapter.setItems(episodes);

                    if (episodes.isEmpty()) {
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    emptyText.setText("Error: " + e.getMessage());
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void resolveAndDownload(EpisodeItem ep) {
        executor.execute(() -> {
            try {
                List<VideoSource> langs = MiruClientAndroid.getEpisodeLanguages(ep.id);
                if (langs.isEmpty()) {
                    handler.post(() -> Toast.makeText(this, "No sources found", Toast.LENGTH_SHORT).show());
                    return;
                }
                String prefLang = StorageSettingsAndroid.getPreferredLanguage(this);
                VideoSource selected = null;
                for (VideoSource vs : langs) {
                    if (vs.language.equals(prefLang)) { selected = vs; break; }
                }
                if (selected == null) selected = langs.get(0);

                String hlsUrl = MiruClientAndroid.resolveHlsFromEmbed(selected.url);
                if (hlsUrl == null) {
                    handler.post(() -> Toast.makeText(this, "No HLS URL found", Toast.LENGTH_SHORT).show());
                    return;
                }

                ep.hlsUrl = hlsUrl;
                ep.language = selected.language;
                ep.langName = selected.quality;
                ep.embedUrl = selected.url;

                handler.post(() -> startDownload(ep));
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showFolderRequiredDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_folder);
        icon.setColorFilter(getColor(R.color.primary));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0x2238BDF8);
        icon.setBackground(iconBg);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Download Folder Required");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.setMargins(dp(14), 0, 0, 0);
        titleRow.addView(title, tLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView msg = new TextView(this);
        msg.setText("Please select a download folder in Settings first.");
        msg.setTextColor(getColor(R.color.text_tertiary));
        msg.setTextSize(13);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, dp(16), 0, dp(22));
        box.addView(msg, msgLp);

        // Settings button
        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("Open Settings");
        settingsBtn.setTextColor(getColor(R.color.text_primary));
        settingsBtn.setTextSize(14);
        settingsBtn.setTypeface(null, Typeface.BOLD);
        settingsBtn.setGravity(Gravity.CENTER);
        GradientDrawable setBg = new GradientDrawable();
        setBg.setCornerRadius(dp(12));
        setBg.setColor(getColor(R.color.primary));
        settingsBtn.setBackground(setBg);
        settingsBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        box.addView(settingsBtn);


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
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
        settingsBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(DetailActivity.this, SettingsActivity.class));
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());


        // Now set click listeners (dialog is initialized)


    }

    private void showDownloadAllDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_download);
        icon.setColorFilter(getColor(R.color.primary));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0x2238BDF8);
        icon.setBackground(iconBg);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Download All Episodes");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.setMargins(dp(14), 0, 0, 0);
        titleRow.addView(title, tLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView msg = new TextView(this);
        msg.setText("Start downloading all " + episodes.size() + " episodes?");
        msg.setTextColor(getColor(R.color.text_tertiary));
        msg.setTextSize(13);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(0, dp(16), 0, dp(22));
        box.addView(msg, msgLp);

        // Download All button
        TextView downloadBtn = new TextView(this);
        downloadBtn.setText("Download All (" + episodes.size() + ")");
        downloadBtn.setTextColor(getColor(R.color.text_primary));
        downloadBtn.setTextSize(14);
        downloadBtn.setTypeface(null, Typeface.BOLD);
        downloadBtn.setGravity(Gravity.CENTER);
        GradientDrawable dlBg = new GradientDrawable();
        dlBg.setCornerRadius(dp(12));
        dlBg.setColor(getColor(R.color.primary));
        downloadBtn.setBackground(dlBg);
        downloadBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        box.addView(downloadBtn);


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


        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
        downloadBtn.setOnClickListener(v -> {
            dialog.dismiss();
            downloadAllEpisodes();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

    }

    private void startDownload(EpisodeItem ep) {
        if (!StorageSettingsAndroid.hasDownloadUri(this)) {
            showFolderRequiredDialog();
            return;
        }


        String quality = StorageSettingsAndroid.getPreferredQuality(this);
        String language = ep.language != null ? ep.language : "jpn";
        String hlsUrl = ep.hlsUrl;

        executor.execute(() -> {
            try {
                List<VideoSource> variants = MiruClientAndroid.getQualities(hlsUrl);
                String finalUrl = hlsUrl;
                // Auto-pick preferred quality from Settings — no dialog needed
                for (VideoSource v : variants) {
                    if (v.quality.contains(quality.replace("p", ""))) {
                        finalUrl = v.url;
                        break;
                    }
                }
                queueDownload(ep, finalUrl, quality, language);
            } catch (Exception e) {
                queueDownload(ep, hlsUrl, quality, language);
            }
        });
    }

    private void queueDownload(EpisodeItem ep, String hlsUrl, String quality, String language) {
        // Guard: skip if already queued or downloading — but if the job is stuck
        // (service isn't actually running/processing it), resume it instead of
        // silently doing nothing forever.
        String label = "Episode " + ep.getLabel();
        for (DownloadManager.Job existing : DownloadManager.snapshot()) {
            if (!existing.finished && existing.animeTitle.equals(animeTitle) && existing.episodeTitle.equals(label)) {
                if (!DownloadManager.isRunning()) {
                    // Orphaned/stuck job: the service died or was never (re)started
                    // for it. Kick it off again instead of blocking the retry.
                    List<DownloadManager.Job> jobs = new ArrayList<>();
                    jobs.add(existing);
                    Intent intent = DownloadService.startIntent(this, jobs);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                    handler.post(() -> Toast.makeText(this, "Resuming: " + label, Toast.LENGTH_SHORT).show());
                } else {
                    handler.post(() -> Toast.makeText(this, "Already queued: " + label, Toast.LENGTH_SHORT).show());
                }
                return;
            }
        }

        DownloadManager.Job job = DownloadManager.enqueue(
                animeTitle, label, quality, language, hlsUrl
        );
        adapter.notifyDataSetChanged();

        List<DownloadManager.Job> jobs = new ArrayList<>();
        jobs.add(job);
        Intent intent = DownloadService.startIntent(this, jobs);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        handler.post(() -> Toast.makeText(this, "Queued: " + label, Toast.LENGTH_SHORT).show());
    }

    private void downloadAllEpisodes() {
        enqueueEpisodes(episodes, episodes.size() + " episodes queued");
    }

    /** Queues downloads for a specific subset of episodes (used by multi-select download). */
    private void downloadSelectedEpisodes(List<EpisodeItem> selected) {
        enqueueEpisodes(selected, selected.size() + " episode(s) queued");
    }

    private void enqueueEpisodes(List<EpisodeItem> list, String successMessage) {
        executor.execute(() -> {
            try {
                List<DownloadManager.Job> jobs = new ArrayList<>();
                String prefLang = StorageSettingsAndroid.getPreferredLanguage(this);

                for (EpisodeItem ep : list) {
                    try {
                        String hlsUrl = ep.hlsUrl;
                        String language = ep.language;
                        String langName = ep.langName;

                        if (hlsUrl == null) {
                            List<VideoSource> langs = MiruClientAndroid.getEpisodeLanguages(ep.id);
                            VideoSource selected = null;
                            for (VideoSource vs : langs) {
                                if (vs.language.equals(prefLang)) { selected = vs; break; }
                            }
                            if (selected == null && !langs.isEmpty()) selected = langs.get(0);
                            if (selected == null) continue;

                            hlsUrl = MiruClientAndroid.resolveHlsFromEmbed(selected.url);
                            if (hlsUrl == null) continue;

                            language = selected.language;
                            langName = selected.quality;
                            ep.hlsUrl = hlsUrl;
                            ep.language = language;
                            ep.langName = langName;
                        }

                        DownloadManager.Job job = DownloadManager.enqueue(
                                animeTitle, "Episode " + ep.getLabel(),
                                StorageSettingsAndroid.getPreferredQuality(this),
                                language != null ? language : "jpn", hlsUrl
                        );
                        jobs.add(job);
                    } catch (Exception ignored) {}
                }

                if (!jobs.isEmpty()) {
                    Intent intent = DownloadService.startIntent(this, jobs);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                    handler.post(() -> {
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void enterSelectionMode() {
        adapter.setSelectionMode(true);
        selectCountText.setText("0 selected");
        btnDownloadSelected.setAlpha(0.5f);
        selectBar.setVisibility(View.VISIBLE);
        btnDownloadAll.setVisibility(View.GONE);
        btnEpSelect.setVisibility(View.GONE);
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        selectBar.setVisibility(View.GONE);
        btnDownloadAll.setVisibility(View.VISIBLE);
        btnEpSelect.setVisibility(View.VISIBLE);
    }

    /** Rebuilds the set of already-downloaded episode labels for this anime. */
    private java.util.Set<String> currentDownloadedSet() {
        java.util.Set<String> downloaded = new java.util.HashSet<>();
        for (DownloadRecord e : DownloadEntryStoreAndroid.all(this)) {
            if (animeTitle != null && animeTitle.equals(e.parentName())) {
                String epLabel = e.title.replace("Episode ", "").trim();
                if (!epLabel.isEmpty()) downloaded.add(epLabel);
            }
        }
        return downloaded;
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
