package msr.mirudl.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Show version
        TextView versionText = findViewById(R.id.about_version);
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (v != null) versionText.setText("Version " + v);
        } catch (PackageManager.NameNotFoundException ignored) {}

        // GitHub link
        findViewById(R.id.about_github).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/msrofficial/MiruDL-App")));
            } catch (Exception ignored) {}
        });
    }
}
