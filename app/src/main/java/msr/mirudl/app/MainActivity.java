package msr.mirudl.app;

import msr.mirudl.shared.storage.StorageSettingsAndroid;

import msr.mirudl.shared.model.AnimeItem;
import msr.mirudl.shared.model.DownloadRecord;
import msr.mirudl.shared.storage.DownloadEntryStoreAndroid;
import msr.mirudl.shared.network.MiruClientAndroid;
import msr.mirudl.shared.network.UpdateChecker;
import msr.mirudl.shared.network.UpdateCheckerAndroid;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.graphics.PorterDuff;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.splashscreen.SplashScreen;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends BaseActivity {
    // Tabs
    private View tabHome, tabDownloads, tabSettings;
    private int currentTab = 0;
    private boolean suppressTabListener = false;
    private BottomNavigationView bottomNav;
    private View crashCardDot;

    // Home
    private EditText searchInput;
    private RecyclerView animeGrid;
    private ProgressBar loadingBar;
    private TextView emptyText;
    private AnimeGridAdapter gridAdapter;
    private SwipeRefreshLayout swipeRefresh;

    // Downloads
    private RecyclerView downloadsList;
    private TextView emptyTextDl, btnClearFinished;
    private DownloadAdapter downloadAdapter;

    private final Set<String> selectedDownloadKeys = new HashSet<>();
    // Settings
    private TextView folderText;
    private SeekBar parallelBar;
    private TextView parallelValue;
    private SeekBar concurrentBar;
    private TextView concurrentValue;
    private Spinner qualitySpinner, langSpinner;

    // Common
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshDownloads();
            handler.postDelayed(this, 500);
        }
    };

    // Folder picker
    private final ActivityResultLauncher<Intent> folderPicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (Exception ignored) {}
                        StorageSettingsAndroid.setDownloadUri(this, treeUri);
                        updateFolderDisplay();
                        Toast.makeText(this, "Download folder set", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        try {
            onCreateInternal(savedInstanceState);
        } catch (Exception e) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Startup Error")
                    .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .setPositiveButton("Exit", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            CrashLogger.saveCaughtException(this, Thread.currentThread(), e);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current_tab", currentTab);
    }

    private void onCreateInternal(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find tabs
        tabHome = findViewById(R.id.tab_home);
        tabDownloads = findViewById(R.id.tab_downloads);
        tabSettings = findViewById(R.id.tab_settings);

        // Bottom nav
        bottomNav = findViewById(R.id.bottom_nav);

        // Apply bottom inset padding to prevent overlap with the system navigation bar
        // (3-button nav OR gesture nav — height differs per device/OS version).
        // Deliberately does NOT react to the keyboard (IME) anymore: with
        // windowSoftInputMode="adjustNothing" the window never resizes/pans when the
        // keyboard opens, so bottomNav stays physically pinned to the bottom of the
        // screen — the keyboard just overlays on top of it instead of pushing it up.
        View rootLayout = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            int navBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            bottomNav.setPadding(bottomNav.getPaddingLeft(), bottomNav.getPaddingTop(),
                                 bottomNav.getPaddingRight(), navBarBottom);
            // NOTE: root_layout itself must NOT also get this bottom padding —
            // bottomNav is a child of root_layout, so padding both doubles the
            // gap above the system nav bar/buttons.
            return insets;
        });
        // Force an initial dispatch so the listener runs once immediately (some Android 15
        // OEM builds don't auto-dispatch insets the first time a listener is attached).
        ViewCompat.requestApplyInsets(rootLayout);
        bottomNav.setOnItemSelectedListener(item -> {
            if (suppressTabListener) return true;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showTab(0);
                return true;
            } else if (id == R.id.nav_downloads) {
                showTab(1);
                return true;
            } else if (id == R.id.nav_settings) {
                showTab(2);
                return true;
            }
            return false;
        });

        // Tap MiruDL title to open About page
        findViewById(R.id.toolbar_title).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AboutActivity.class));
        });

        // Init Home tab
        initHomeTab();

        // Init Downloads tab
        initDownloadsTab();

        // Init Settings tab
        initSettingsTab();

        // Reflect any unseen crash logs on the settings icon/card
        updateCrashBadge();

        // Silent update check — shows the update dialog if a newer GitHub release exists
        UpdateCheckerAndroid.checkOnStartup(this, info -> {
            refreshUpdateSection(info);
            if (info != null) showUpdateDialog(info);
        });

        // Restore tab from saved state, or start on Home
        int restoreTab = 0;
        if (savedInstanceState != null) {
            restoreTab = savedInstanceState.getInt("current_tab", 0);
        }
        suppressTabListener = true;
        if (restoreTab == 0) bottomNav.setSelectedItemId(R.id.nav_home);
        else if (restoreTab == 1) bottomNav.setSelectedItemId(R.id.nav_downloads);
        else if (restoreTab == 2) bottomNav.setSelectedItemId(R.id.nav_settings);
        suppressTabListener = false;
        showTab(restoreTab);
        if (restoreTab == 0) loadPopular();
    }

    // ============ TAB SWITCHING ============

    private void showTab(int tab) {
        currentTab = tab;
        tabHome.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        tabDownloads.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        tabSettings.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);

        if (tab == 1) refreshDownloads();
        if (tab == 2) updateFolderDisplay();
    }

    // ============ HOME TAB ============

    private void initHomeTab() {
        searchInput = findViewById(R.id.search_input);
        animeGrid = findViewById(R.id.anime_grid);
        loadingBar = findViewById(R.id.loading_bar);
        emptyText = findViewById(R.id.empty_text);


        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.primary_dark);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_variant);
        swipeRefresh.setOnRefreshListener(() -> {
            String q = searchInput.getText().toString().trim();
            if (q.isEmpty()) {
                loadPopular();
            } else {
                searchAnime(q);
            }
        });

        gridAdapter = new AnimeGridAdapter(anime -> {
            // Show detail bottom sheet
            showAnimeDetail(anime);
        });

        animeGrid.setLayoutManager(new GridLayoutManager(this, 2));
        animeGrid.setAdapter(gridAdapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 2) {
                    searchAnime(s.toString());
                } else if (s.length() == 0) {
                    loadPopular();
                }
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && v.getText().length() >= 2) {
                searchAnime(v.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void loadPopular() {
        loadingBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<AnimeItem> results = MiruClientAndroid.browseCurrentlyAiring();
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (results != null && !results.isEmpty()) {
                        gridAdapter.setItems(results);
                    } else {
                        emptyText.setText(R.string.no_results);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    emptyText.setText("Error loading");
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void searchAnime(String query) {
        loadingBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        executor.execute(() -> {
            try {
                List<AnimeItem> results = MiruClientAndroid.search(query);
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    gridAdapter.setItems(results);
                    if (results.isEmpty()) {
                        emptyText.setText(R.string.no_results);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    emptyText.setText("Error: " + e.getMessage());
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showAnimeDetail(AnimeItem anime) {
        // Inflate bottom sheet
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
                .setView(R.layout.bottom_sheet_detail)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        }

        View view = dialog.getWindow() != null ? dialog.getWindow().getDecorView().findViewById(android.R.id.content) : null;
        if (view == null) {
            dialog.dismiss();
            // Fallback: open DetailActivity
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("anime_id", anime.id);
            intent.putExtra("anime_url", anime.url);
            intent.putExtra("anime_title", anime.title);
            intent.putExtra("anime_thumb", anime.thumbnail);
            startActivity(intent);
            return;
        }

        ImageView thumb = view.findViewById(R.id.detail_thumb);
        TextView title = view.findViewById(R.id.detail_title);
        TextView info = view.findViewById(R.id.detail_info);
        TextView desc = view.findViewById(R.id.detail_description);
        View btnEpisodes = view.findViewById(R.id.btn_view_episodes);

        if (anime.thumbnail != null && !anime.thumbnail.isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(anime.thumbnail).centerCrop().into(thumb);
        }
        title.setText(anime.title != null ? anime.title : "Unknown");

        btnEpisodes.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("anime_id", anime.id);
            intent.putExtra("anime_url", anime.url);
            intent.putExtra("anime_title", anime.title);
            intent.putExtra("anime_thumb", anime.thumbnail);
            startActivity(intent);
        });
    }

    // ============ DOWNLOADS TAB ============

    private void initDownloadsTab() {
        downloadsList = findViewById(R.id.downloads_list);
        emptyTextDl = findViewById(R.id.empty_text_dl);
        btnClearFinished = findViewById(R.id.btn_clear_finished);

        btnClearFinished.setOnClickListener(v -> clearFinished());

        downloadAdapter = new DownloadAdapter(this, new DownloadAdapter.OnActionListener() {
            @Override public void onCancel(DownloadManager.Job job) {
                if (job != null) {
                    DownloadManager.cancel(job);
                    // Also notify the service
                    Intent cancelIntent = new Intent(MainActivity.this, DownloadService.class);
                    cancelIntent.setAction("cancel");
                    cancelIntent.putExtra("jobId", job.id);
                    startService(cancelIntent);
                    refreshDownloads();
                }
            }
            @Override public void onClick(DownloadRecord entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                if (dlEntry.uri != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(dlEntry.uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Cannot open file", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override public void onDelete(DownloadRecord entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                showDeleteDownloadDialog(dlEntry);
            }
            @Override public void onSelectionToggle(DownloadRecord entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                String key = dlEntry.key();
                if (selectedDownloadKeys.contains(key)) {
                    selectedDownloadKeys.remove(key);
                } else {
                    selectedDownloadKeys.add(key);
                }
                refreshDownloads();
            }
            @Override public boolean isSelected(DownloadRecord entry) {
                DownloadEntry dlEntry = DownloadEntry.fromRecord(entry);
                return selectedDownloadKeys.contains(dlEntry.key());
            }
            @Override public void onGroupDeleteRequest(String groupName) {
                List<DownloadEntry> groupEntries = new ArrayList<>();
                List<DownloadRecord> all = DownloadEntryStoreAndroid.all(MainActivity.this);
                for (DownloadRecord e : all) {
                    if (groupName.equals(e.parentName())) {
                        groupEntries.add(DownloadEntry.fromRecord(e));
                    }
                }
                showDeleteMultipleDialog(groupEntries);
            }

            @Override public void onGroupDelete(String groupName) {
                // Toggle folder expansion
                downloadAdapter.toggleFolder(groupName);
                refreshDownloads();
            }
        });

        downloadsList.setLayoutManager(new LinearLayoutManager(this));
        downloadsList.setAdapter(downloadAdapter);
    }

    private void refreshDownloads() {
        List<DownloadManager.Job> active = DownloadManager.snapshot();
        List<DownloadRecord> completed = DownloadEntryStoreAndroid.all(this);

        boolean hasActive = false;
        for (DownloadManager.Job j : active) {
            if (!j.finished) { hasActive = true; break; }
        }

        emptyTextDl.setVisibility(
            (!hasActive && (completed == null || completed.isEmpty())) ? View.VISIBLE : View.GONE);

        downloadAdapter.setData(active, completed);
    }

    private void clearFinished() {
        DownloadManager.removeFinished();
        List<DownloadRecord> entries = DownloadEntryStoreAndroid.all(this);
        if (!entries.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear History")
                    .setMessage("Remove all download history?")
                    .setPositiveButton("Clear", (d, w) -> {
                        DownloadEntryStoreAndroid.removeAll(this, entries);
                        refreshDownloads();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        refreshDownloads();
    }

        private void showDeleteDownloadDialog(DownloadEntry dlEntry) {
        int errorColor = getColor(R.color.error);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setColorFilter(errorColor);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0x22F87171);
        icon.setBackground(iconBg);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Delete download?");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.setMargins(dp(14), 0, 0, 0);
        titleRow.addView(title, tLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        String displayName = dlEntry.title != null ? dlEntry.title : "";
        if (displayName.startsWith("Episode ")) displayName = displayName.substring(8);
        TextView epName = new TextView(this);
        epName.setText(displayName);
        epName.setTextColor(getColor(R.color.text_tertiary));
        epName.setTextSize(13);
        epName.setMaxLines(2);
        epName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams enLp = new LinearLayout.LayoutParams(-1, -2);
        enLp.setMargins(dp(58), dp(4), 0, dp(20));
        box.addView(epName, enLp);

        // Buttons
        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("Delete file permanently");
        deleteBtn.setTextColor(getColor(R.color.text_primary));
        deleteBtn.setTextSize(14);
        deleteBtn.setTypeface(null, Typeface.BOLD);
        deleteBtn.setGravity(Gravity.CENTER);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setCornerRadius(dp(12));
        delBg.setColor(errorColor);
        deleteBtn.setBackground(delBg);
        deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, 0));
        box.addView(deleteBtn);

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
        LinearLayout.LayoutParams remLp = new LinearLayout.LayoutParams(-1, -2);
        remLp.setMargins(0, dp(10), 0, 0);
        box.addView(removeBtn, remLp);

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
        LinearLayout.LayoutParams canLp = new LinearLayout.LayoutParams(-1, -2);
        canLp.setMargins(0, dp(10), 0, 0);
        box.addView(cancelBtn, canLp);

        deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();

        // Set listeners after dialog is created
        deleteBtn.setOnClickListener(v -> {
            dlEntry.deleteFileAndRecord(this);
            dialog.dismiss();
            refreshDownloads();
        });
        removeBtn.setOnClickListener(v -> {
            dlEntry.deleteRecordOnly(this);
            dialog.dismiss();
            refreshDownloads();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
    }
    private void showDeleteMultipleDialog(List<DownloadEntry> entries) {
        if (entries.isEmpty()) return;
        String desc = entries.size() == 1 ? entries.get(0).title : entries.size() + " files";
        int errorColor = getColor(R.color.error);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_delete);
        icon.setColorFilter(errorColor);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0x22F87171);
        icon.setBackground(iconBg);
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("Delete " + desc + "?");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(-1, -2);
        tLp.setMargins(dp(14), 0, 0, 0);
        titleRow.addView(title, tLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        // Message
        TextView message = new TextView(this);
        message.setText("Removing from history keeps the file on your device.");
        message.setTextColor(getColor(R.color.text_tertiary));
        message.setTextSize(13);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(-1, -2);
        msgLp.setMargins(dp(58), dp(4), 0, dp(20));
        box.addView(message, msgLp);

        // Delete files button
        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("Delete files permanently");
        deleteBtn.setTextColor(getColor(R.color.text_primary));
        deleteBtn.setTextSize(14);
        deleteBtn.setTypeface(null, Typeface.BOLD);
        deleteBtn.setGravity(Gravity.CENTER);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setCornerRadius(dp(12));
        delBg.setColor(errorColor);
        deleteBtn.setBackground(delBg);
        deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        box.addView(deleteBtn);

        // Remove from history
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

        // Set listeners after dialog initialized
        deleteBtn.setOnClickListener(v -> {
            for (DownloadEntry e : entries) e.deleteFileAndRecord(this);
            selectedDownloadKeys.clear();
            dialog.dismiss();
            refreshDownloads();
        });
        removeBtn.setOnClickListener(v -> {
            for (DownloadEntry e : entries) e.deleteRecordOnly(this);
            selectedDownloadKeys.clear();
            dialog.dismiss();
            refreshDownloads();
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
    }
// ============ SETTINGS TAB ============

    private void showCrashLogsDialog() {
        CrashLogger.markViewed(this);
        updateCrashBadge();
        File[] files = CrashLogger.getCrashFiles(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.setBackground(UiHelper.rounded(getColor(R.color.surface), dp(20)));

        // Title row with icon
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView titleIcon = new ImageView(this);
        titleIcon.setImageResource(R.drawable.ic_bug);
        titleIcon.setColorFilter(getColor(R.color.error));
        titleIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        titleIcon.setBackground(UiHelper.rounded(0x22F44336, dp(12)));
        titleRow.addView(titleIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText("Crash Reports (" + files.length + ")");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.setMargins(dp(12), 0, 0, 0);
        titleRow.addView(title, titleLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        if (files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("No crash logs \u2014 everything running smoothly.");
            empty.setTextColor(getColor(R.color.text_tertiary));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(-1, -2);
            emptyLp.setMargins(0, dp(36), 0, dp(28));
            box.addView(empty, emptyLp);
        } else {
            // Clear All chip
            TextView clearAll = new TextView(this);
            clearAll.setText("Clear All");
            clearAll.setTextColor(getColor(R.color.error));
            clearAll.setTextSize(12);
            clearAll.setTypeface(null, Typeface.BOLD);
            clearAll.setPadding(dp(12), dp(6), dp(12), dp(6));
            clearAll.setBackground(UiHelper.rounded(0x1AF44336, dp(10)));
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, -2);
            clearLp.gravity = Gravity.END;
            clearLp.setMargins(0, dp(14), 0, dp(14));
            clearAll.setLayoutParams(clearLp);
            clearAll.setOnClickListener(v -> {
                CrashLogger.clearAll(this);
                showCrashLogsDialog();
            });
            box.addView(clearAll);

            // Scrollable list (in case of many crash files)
            ScrollView listScroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);

            for (File file : files) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(8), dp(10));
                row.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(12)));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.setMargins(0, 0, 0, dp(8));
                row.setLayoutParams(rowLp);

                String dateStr = new SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
                        .format(new Date(file.lastModified()));
                long sizeKb = file.length() / 1024;

                TextView info = new TextView(this);
                info.setText(dateStr + "  \u2022  " + (sizeKb > 0 ? sizeKb + " KB" : "<1 KB"));
                info.setTextColor(getColor(R.color.text_primary));
                info.setTextSize(12.5f);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(info);

                TextView delBtn = new TextView(this);
                delBtn.setText("Delete");
                delBtn.setTextColor(getColor(R.color.error));
                delBtn.setTextSize(12);
                delBtn.setTypeface(null, Typeface.BOLD);
                delBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
                delBtn.setOnClickListener(v -> {
                    CrashLogger.deleteCrashFile(file);
                    showCrashLogsDialog();
                });
                row.addView(delBtn);

                TextView viewBtn = new TextView(this);
                viewBtn.setText("View");
                viewBtn.setTextColor(getColor(R.color.primary));
                viewBtn.setTextSize(12);
                viewBtn.setTypeface(null, Typeface.BOLD);
                viewBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
                viewBtn.setOnClickListener(v -> showCrashDetailDialog(file));
                row.addView(viewBtn);

                list.addView(row);
            }

            listScroll.addView(list);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, -2);
            scrollLp.height = dp(320);
            box.addView(listScroll, scrollLp);
        }

        // Close button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("Close");
        closeBtn.setTextColor(getColor(R.color.text_primary));
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(12)));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(48));
        closeLp.setMargins(0, dp(16), 0, 0);
        closeBtn.setLayoutParams(closeLp);
        box.addView(closeBtn);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
        closeBtn.setOnClickListener(v -> dialog.dismiss());
    }

    private void showCrashDetailDialog(File file) {
        StringBuilder content = new StringBuilder();
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = r.readLine()) != null) {
                if (content.length() > 5000) {
                    content.append("\n... [TRUNCATED - file too large]");
                    break;
                }
                content.append(line).append("\n");
            }
            r.close();
        } catch (Exception ignored) {}
        String reportText = content.toString();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(UiHelper.rounded(getColor(R.color.surface), dp(20)));

        TextView title = new TextView(this);
        title.setText("Crash Log");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText(file.getName());
        subtitle.setTextColor(getColor(R.color.text_tertiary));
        subtitle.setTextSize(11.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(2), 0, 0);
        box.addView(subtitle, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(14)));
        TextView logText = new TextView(this);
        logText.setText(reportText);
        logText.setTextColor(getColor(R.color.text_secondary));
        logText.setTextSize(10);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setLineSpacing(dp(2), 1f);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(logText);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(360));
        scrollLp.setMargins(0, dp(12), 0, dp(14));
        box.addView(scroll, scrollLp);

        // Copy + Save row
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView copyBtn = new TextView(this);
        copyBtn.setText("Copy");
        copyBtn.setTextColor(getColor(R.color.text_primary));
        copyBtn.setTextSize(13.5f);
        copyBtn.setTypeface(null, Typeface.BOLD);
        copyBtn.setGravity(Gravity.CENTER);
        copyBtn.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(12)));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        copyLp.setMargins(0, 0, dp(6), 0);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Crash report", reportText));
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        actionRow.addView(copyBtn, copyLp);

        TextView saveBtn = new TextView(this);
        saveBtn.setText("Save");
        saveBtn.setTextColor(getColor(R.color.on_primary));
        saveBtn.setTextSize(13.5f);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackground(UiHelper.rounded(getColor(R.color.primary), dp(12)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        saveLp.setMargins(dp(6), 0, 0, 0);
        saveBtn.setOnClickListener(v -> saveCrashReportToDownloads(file, reportText));
        actionRow.addView(saveBtn, saveLp);

        box.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));

        // Close button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("Close");
        closeBtn.setTextColor(getColor(R.color.text_tertiary));
        closeBtn.setTextSize(13);
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(0, dp(12), 0, dp(2));
        box.addView(closeBtn, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();
        closeBtn.setOnClickListener(v -> dialog.dismiss());
    }

    /** Saves a copy of the crash report text file into the device's public Downloads folder. */
    private void saveCrashReportToDownloads(File sourceFile, String reportText) {
        String fileName = sourceFile.getName();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("Could not create file");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) out.write(reportText.getBytes());
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            } else {
                File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists()) downloads.mkdirs();
                File outFile = new File(downloads, fileName);
                try (FileInputStream in = new FileInputStream(sourceFile);
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
            Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String speedLabel(int parallel) {
        if (parallel <= 4) return "Slow";
        if (parallel <= 8) return "Normal";
        if (parallel <= 16) return "Fast";
        if (parallel <= 32) return "Very Fast";
        return "Extreme";
    }

    private void updateParallelTint(int val) {
        int color;
        if (val > 32) {
            color = ContextCompat.getColor(this, R.color.error);
        } else {
            color = ContextCompat.getColor(this, R.color.primary);
        }
        parallelBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        parallelBar.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        parallelValue.setTextColor(color);
    }

    private void showParallelWarning(int val) {
        new AlertDialog.Builder(this)
                .setTitle("High Speed Warning")
                .setMessage("Setting parallel segments to " + val + " may cause:\n\n"
                        + "\u2022 Rate limiting from the server\n"
                        + "\u2022 Increased data usage\n"
                        + "\u2022 Higher battery consumption\n"
                        + "\u2022 Possible download failures on slow connections\n\n"
                        + "Use only if you have a stable, high-speed internet connection.")
                .setPositiveButton("Use Anyway", null)
                .setNegativeButton("Reduce", (d, w) -> {
                    int safe = 32;
                    parallelBar.setProgress(safe);
                    parallelValue.setText(String.valueOf(safe));
                    updateParallelTint(safe);
                    StorageSettingsAndroid.setParallelSegments(MainActivity.this, safe);
                })
                .show();
    }


    private void initSettingsTab() {
        folderText = findViewById(R.id.folder_path);
        findViewById(R.id.btn_select_folder).setOnClickListener(v -> pickFolder());

        parallelBar = findViewById(R.id.parallel_seekbar);
        parallelValue = findViewById(R.id.parallel_value);

        int currentParallel = StorageSettingsAndroid.getParallelSegments(this);
        parallelBar.setProgress(currentParallel);
        parallelValue.setText(String.valueOf(currentParallel));
        updateParallelTint(currentParallel);
        parallelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                int val = Math.max(1, bar.getProgress());
                if (val > 32) {
                    showParallelWarning(val);
                }
            }
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int val = Math.max(1, progress);
                    parallelValue.setText(String.valueOf(val));
                    updateParallelTint(val);
                    StorageSettingsAndroid.setParallelSegments(MainActivity.this, val);
                }
            }
        });

        // Concurrent Downloads (how many episodes download at once, side by side)
        concurrentBar = findViewById(R.id.concurrent_seekbar);
        concurrentValue = findViewById(R.id.concurrent_value);

        int currentConcurrent = StorageSettingsAndroid.getConcurrentDownloads(this);
        concurrentBar.setProgress(Math.max(0, currentConcurrent - 1)); // seekbar is 0-based, value is 1-based
        concurrentValue.setText(String.valueOf(currentConcurrent));

        concurrentBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int val = progress + 1;
                    concurrentValue.setText(String.valueOf(val));
                    StorageSettingsAndroid.setConcurrentDownloads(MainActivity.this, val);
                }
            }
        });

        qualitySpinner = findViewById(R.id.quality_spinner);
        String[] qualities = {"1080p", "720p", "480p", "360p", "Auto"};
        ArrayAdapter<String> qAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_value_chevron, android.R.id.text1, qualities);
        qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qAdapter);
        String prefQ = StorageSettingsAndroid.getPreferredQuality(this);
        for (int i = 0; i < qualities.length; i++) {
            if (qualities[i].equals(prefQ)) { qualitySpinner.setSelection(i); break; }
        }
        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                StorageSettingsAndroid.setPreferredQuality(MainActivity.this, qualities[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        langSpinner = findViewById(R.id.lang_spinner);
        String[] langs = {"jpn", "eng"};
        String[] langLabels = {"Sub (Japanese)", "Dub (English)"};
        ArrayAdapter<String> lAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_value_chevron, android.R.id.text1, langLabels);
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(lAdapter);
        String prefLang = StorageSettingsAndroid.getPreferredLanguage(this);
        for (int i = 0; i < langs.length; i++) {
            if (langs[i].equals(prefLang)) { langSpinner.setSelection(i); break; }
        }
        langSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                StorageSettingsAndroid.setPreferredLanguage(MainActivity.this, langs[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Theme toggle
        androidx.appcompat.widget.SwitchCompat themeSwitch = findViewById(R.id.theme_switch);
        TextView themeLabel = findViewById(R.id.theme_label);
        if (themeSwitch != null && themeLabel != null) {
            boolean isDark = StorageSettingsAndroid.isDarkTheme(this);
            themeSwitch.setChecked(isDark);
            themeLabel.setText(isDark ? R.string.dark_mode_on : R.string.dark_mode_off);
            themeSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                StorageSettingsAndroid.setDarkTheme(MainActivity.this, isChecked);
                // Recreate to apply theme
                recreate();
            });
        }

        // About section -> open About page
        View aboutSection = findViewById(R.id.about_section);
        if (aboutSection != null) {
            aboutSection.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            });
        }

        // Reflect the real installed version here instead of the static
        // "Version 1.0.1" placeholder from strings.xml, so this row never
        // goes stale when the app's versionName changes.
        TextView settingsVersionTv = findViewById(R.id.settings_app_version);
        if (settingsVersionTv != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                settingsVersionTv.setText("Version " + v);
            } catch (Exception ignored) {
                // Keep the strings.xml fallback text if lookup fails for any reason.
            }
        }

        // Developer info section -> open Developer page
        View devInfo = findViewById(R.id.dev_info_section);
        if (devInfo != null) {
            devInfo.setOnClickListener(v -> {
                startActivity(new Intent(MainActivity.this, DeveloperActivity.class));
            });
        }

        // GitHub link in Developer section
        View githubLink = findViewById(R.id.id_github_link);
        if (githubLink != null) {
            githubLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/msrofficial/MiruDL-App")));
                } catch (Exception ignored) {}
            });
        }

        // Crash Reports button
        View crashBtn = findViewById(R.id.btn_crash_reports);
        crashCardDot = findViewById(R.id.crash_card_dot);
        if (crashBtn != null) {
            crashBtn.setOnClickListener(v -> showCrashLogsDialog());
        }

        // Check for Updates button
        View updateSection = findViewById(R.id.update_section);
        View updateProgress = findViewById(R.id.update_progress);
        View updateChevron = findViewById(R.id.update_chevron);
        if (updateSection != null) {
            refreshUpdateSection(null); // shows current version until a check runs
            updateSection.setOnClickListener(v -> {
                updateProgress.setVisibility(View.VISIBLE);
                updateChevron.setVisibility(View.GONE);
                TextView statusTv = findViewById(R.id.update_status_text);
                if (statusTv != null) statusTv.setText("Checking for updates\u2026");
                UpdateCheckerAndroid.checkNow(this, info -> {
                    updateProgress.setVisibility(View.GONE);
                    updateChevron.setVisibility(View.VISIBLE);
                    refreshUpdateSection(info);
                    if (info != null) {
                        showUpdateDialog(info);
                    } else {
                        Toast.makeText(this, "You're using the latest version", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        updateFolderDisplay();
    }

    /** Updates the Settings-tab "Check for Updates" row: version text + the red "update available" dot. */
    private void refreshUpdateSection(UpdateChecker.ReleaseInfo info) {
        TextView statusTv = findViewById(R.id.update_status_text);
        View dot = findViewById(R.id.update_dot);
        if (statusTv == null) return;
        if (info != null) {
            statusTv.setText("Update available \u2022 " + info.tag);
            if (dot != null) dot.setVisibility(View.VISIBLE);
        } else {
            String v = "";
            try {
                v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ignored) {}
            statusTv.setText("Version " + v + " \u2022 Up to date");
            if (dot != null) dot.setVisibility(View.GONE);
        }
    }

    /** Shows the "Update Available" dialog with changelog. Reappears every launch until the user updates. */
    private void showUpdateDialog(UpdateChecker.ReleaseInfo info) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(22), dp(22), dp(18));
        box.setBackground(UiHelper.rounded(getColor(R.color.surface), dp(20)));

        // Icon + title
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_refresh);
        icon.setColorFilter(getColor(R.color.primary));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        icon.setBackground(UiHelper.rounded(0x220EA5E9, dp(12)));
        titleRow.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Update Available");
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16.5f);
        title.setTypeface(null, Typeface.BOLD);
        titleCol.addView(title, new LinearLayout.LayoutParams(-2, -2));

        String currentV = "";
        try {
            currentV = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        TextView versionLine = new TextView(this);
        versionLine.setText(currentV + "  \u2192  " + info.tag);
        versionLine.setTextColor(getColor(R.color.primary));
        versionLine.setTextSize(12.5f);
        versionLine.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(-2, -2);
        verLp.topMargin = dp(2);
        titleCol.addView(versionLine, verLp);

        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleColLp.setMargins(dp(12), 0, 0, 0);
        titleRow.addView(titleCol, titleColLp);
        box.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        // Changelog label
        TextView changelogLabel = new TextView(this);
        changelogLabel.setText("What's new");
        changelogLabel.setTextColor(getColor(R.color.text_tertiary));
        changelogLabel.setTextSize(11.5f);
        changelogLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams clLabelLp = new LinearLayout.LayoutParams(-1, -2);
        clLabelLp.setMargins(0, dp(18), 0, dp(6));
        box.addView(changelogLabel, clLabelLp);

        // Changelog box
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(14)));
        TextView changelogText = new TextView(this);
        String changelog = (info.changelog != null && !info.changelog.trim().isEmpty())
                ? info.changelog.trim() : "No changelog provided for this release.";
        changelogText.setText(changelog);
        changelogText.setTextColor(getColor(R.color.text_secondary));
        changelogText.setTextSize(12.5f);
        changelogText.setLineSpacing(dp(3), 1f);
        changelogText.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.addView(changelogText);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(220));
        scrollLp.bottomMargin = dp(18);
        box.addView(scroll, scrollLp);

        // Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView laterBtn = new TextView(this);
        laterBtn.setText("Not Now");
        laterBtn.setTextColor(getColor(R.color.text_primary));
        laterBtn.setTextSize(13.5f);
        laterBtn.setTypeface(null, Typeface.BOLD);
        laterBtn.setGravity(Gravity.CENTER);
        laterBtn.setBackground(UiHelper.rounded(getColor(R.color.surface_variant), dp(12)));
        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        laterLp.setMargins(0, 0, dp(6), 0);
        btnRow.addView(laterBtn, laterLp);

        TextView updateBtn = new TextView(this);
        updateBtn.setText("Update Now");
        updateBtn.setTextColor(getColor(R.color.on_primary));
        updateBtn.setTextSize(13.5f);
        updateBtn.setTypeface(null, Typeface.BOLD);
        updateBtn.setGravity(Gravity.CENTER);
        updateBtn.setBackground(UiHelper.rounded(getColor(R.color.primary), dp(12)));
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        updateLp.setMargins(dp(6), 0, 0, 0);
        btnRow.addView(updateBtn, updateLp);

        box.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this).setCancelable(true).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
        dialog.show();

        laterBtn.setOnClickListener(v -> dialog.dismiss());
        updateBtn.setOnClickListener(v -> {
            String target = info.apkUrl != null ? info.apkUrl : info.htmlUrl;
            if (target != null) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
                } catch (Exception ignored) {}
            }
            dialog.dismiss();
        });
    }

    private void pickFolder() {
        new AlertDialog.Builder(this)
                .setTitle("Change download folder")
                .setMessage("This will move future downloads to the new folder. Already downloaded files will not be moved.")
                .setPositiveButton("Change", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    folderPicker.launch(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFolderDisplay() {
        Uri uri = StorageSettingsAndroid.getDownloadUri(this);
        if (uri != null) {
            String path = uri.getPath();
            String display = path != null && path.contains(":")
                    ? path.substring(path.indexOf(":") + 1) : path;
            folderText.setText(display != null ? display : uri.toString());
        } else {
            folderText.setText("Not selected");
        }
    }

    // ============ LIFECYCLE ============

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshTask);
        updateCrashBadge();
    }

    /** Shows/hides the red "new crash" indicator on the bottom-nav Settings icon and the crash-reports card. */
    private void updateCrashBadge() {
        boolean hasNew = CrashLogger.hasNewCrash(this);

        if (bottomNav != null) {
            if (hasNew) {
                BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_settings);
                badge.setVisible(true);
                badge.clearNumber();
            } else {
                bottomNav.removeBadge(R.id.nav_settings);
            }
        }

        if (crashCardDot != null) {
            crashCardDot.setVisibility(hasNew ? View.VISIBLE : View.GONE);
        }
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }
}
