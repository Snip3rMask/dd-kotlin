package msr.mirudl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgColor = Color(0xFFF8F9FA)
private val TextPrimary = Color(0xFF202124)
private val TextSecondary = Color(0xFF5F6368)
private val TextTertiary = Color(0xFF80868B)
private val Primary = Color(0xFF0EA5E9)
private val DividerColor = Color(0xFFDADCE0)
private val CardBg = Color(0xFFFFFFFF)

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AboutScreen(
                    version = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                    } catch (_: Exception) { "" },
                    onBack = { finish() },
                    onGitHub = {
                        try {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/msrofficial/MiruDL-App")
                                )
                            )
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onGitHub: () -> Unit
) {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            AboutHeader(onBack = onBack)

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // App icon
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App icon",
                    modifier = Modifier.size(80.dp)
                )

                // App name
                Text(
                    text = "MiruDL",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )

                // Version
                Text(
                    text = "Version $version",
                    color = TextTertiary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .width(48.dp)
                        .height(3.dp)
                        .background(Primary)
                )

                // Description
                Text(
                    text = "MiruDL is a lightweight anime downloader for Android. Browse currently airing anime, search your favorites, and download episodes directly to your device with parallel segment acceleration.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Info card
                InfoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )

                // GitHub link
                GitHubLink(
                    onClick = onGitHub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(BgColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "About",
            color = TextPrimary,
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Spacer to balance the back button
        Box(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(16.dp)
    ) {
        InfoRow(
            iconRes = R.drawable.ic_info,
            label = "Package",
            value = "msr.mirudl.app"
        )

        HorizontalDivider(
            color = DividerColor,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        InfoRow(
            iconRes = R.drawable.ic_hd,
            label = "Supported quality",
            value = "360p / 480p / 720p / 1080p"
        )

        HorizontalDivider(
            color = DividerColor,
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        InfoRow(
            iconRes = R.drawable.ic_star,
            label = "Open source",
            value = "not yet"
        )
    }
}

@Composable
private fun InfoRow(
    iconRes: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )

        Text(
            text = value,
            color = TextTertiary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun GitHubLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_globe),
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "View on GitHub",
            color = Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
