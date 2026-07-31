package msr.mirudl.app;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends BaseActivity {
    private TextView folderText;
    private SeekBar parallelBar;
    private TextView parallelValue;
    private Spinner qualitySpinner, langSpinner;
    private Uri selectedFolderUri;

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

                        StorageSettings.setDownloadUri(this, treeUri);
                        selectedFolderUri = treeUri;
                        updateFolderDisplay();
                        Toast.makeText(this, "Download folder set", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView versionTv = findViewById(R.id.settings_app_version);
        if (versionTv != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                versionTv.setText("Version " + v);
            } catch (Exception ignored) {
                // Keep the strings.xml fallback text if lookup fails for any reason.
            }
        }

        folderText = findViewById(R.id.folder_path);
        findViewById(R.id.btn_select_folder).setOnClickListener(v -> pickFolder());

        // Parallel segments
        parallelBar = findViewById(R.id.parallel_seekbar);
        parallelValue = findViewById(R.id.parallel_value);

        int currentParallel = StorageSettings.getParallelSegments(this);
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
                    StorageSettings.setParallelSegments(SettingsActivity.this, val);
                }
            }
        });

        // Quality spinner
        qualitySpinner = findViewById(R.id.quality_spinner);
        String[] qualities = {"1080p", "720p", "480p", "360p", "Auto"};
        ArrayAdapter<String> qAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_value_chevron, android.R.id.text1, qualities);
        qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qAdapter);
        String prefQ = StorageSettings.getPreferredQuality(this);
        for (int i = 0; i < qualities.length; i++) {
            if (qualities[i].equals(prefQ)) { qualitySpinner.setSelection(i); break; }
        }
        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                StorageSettings.setPreferredQuality(SettingsActivity.this, qualities[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Language spinner
        langSpinner = findViewById(R.id.lang_spinner);
        String[] langs = {"jpn", "eng"};
        String[] langLabels = {"Sub (Japanese)", "Dub (English)"};
        ArrayAdapter<String> lAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_value_chevron, android.R.id.text1, langLabels);
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(lAdapter);
        String prefLang = StorageSettings.getPreferredLanguage(this);
        for (int i = 0; i < langs.length; i++) {
            if (langs[i].equals(prefLang)) { langSpinner.setSelection(i); break; }
        }
        langSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                StorageSettings.setPreferredLanguage(SettingsActivity.this, langs[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // GitHub link
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
        if (crashBtn != null) {
            crashBtn.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                    intent.putExtra("showCrashLogs", true);
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }

        updateFolderDisplay();
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
                    StorageSettings.setParallelSegments(SettingsActivity.this, safe);
                })
                .show();
    }

    private void pickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPicker.launch(intent);
    }

    private void updateFolderDisplay() {
        Uri uri = StorageSettings.getDownloadUri(this);
        if (uri != null) {
            String path = uri.getPath();
            String display = path != null && path.contains(":")
                    ? path.substring(path.indexOf(":") + 1) : path;
            folderText.setText(display != null ? display : uri.toString());
        } else {
            folderText.setText("Not selected");
        }
    }

    private String speedLabel(int parallel) {
        if (parallel <= 4) return "Slow";
        if (parallel <= 8) return "Normal";
        if (parallel <= 16) return "Fast";
        if (parallel <= 32) return "Very Fast";
        return "Extreme";
    }
}
