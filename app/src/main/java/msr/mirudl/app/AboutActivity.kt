package msr.mirudl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<android.view.View>(R.id.btn_back).setOnClickListener { finish() }

        val versionText = findViewById<TextView>(R.id.about_version)
        try {
            val v = packageManager.getPackageInfo(packageName, 0).versionName
            if (v != null) versionText.text = "Version $v"
        } catch (_: Exception) {}

        findViewById<android.view.View>(R.id.about_github).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msrofficial/MiruDL-App")))
            } catch (_: Exception) {}
        }
    }
}
