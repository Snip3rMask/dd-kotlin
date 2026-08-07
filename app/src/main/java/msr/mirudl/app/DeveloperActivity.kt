package msr.mirudl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle

class DeveloperActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<android.view.View>(R.id.dev_github).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msrofficial"))) } catch (_: Exception) {}
        }

        findViewById<android.view.View>(R.id.dev_telegram_channel).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/msrpatch"))) } catch (_: Exception) {}
        }

        findViewById<android.view.View>(R.id.dev_telegram_group).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/msrpatchchat"))) } catch (_: Exception) {}
        }

        findViewById<android.view.View>(R.id.dev_reddit).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/u/msrsakibur"))) } catch (_: Exception) {}
        }
    }
}
