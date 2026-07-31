package msr.mirudl.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class DeveloperActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // GitHub link
        findViewById(R.id.dev_github).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/msrofficial")));
            } catch (Exception ignored) {}
        });

        // Telegram Channel
        findViewById(R.id.dev_telegram_channel).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/msrpatch")));
            } catch (Exception ignored) {}
        });

        // Telegram Group
        findViewById(R.id.dev_telegram_group).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://t.me/msrpatchchat")));
            } catch (Exception ignored) {}
        });

        // Reddit
        findViewById(R.id.dev_reddit).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.reddit.com/u/msrsakibur")));
            } catch (Exception ignored) {}
        });
    }
}
