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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
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

class DeveloperActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DeveloperScreen(
                    onBack = { finish() },
                    onGitHub = { openUrl("https://github.com/msrofficial") },
                    onTelegramChannel = { openUrl("https://t.me/msrpatch") },
                    onTelegramGroup = { openUrl("https://t.me/msrpatchchat") },
                    onReddit = { openUrl("https://www.reddit.com/u/msrsakibur") }
                )
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {}
    }
}

@Composable
private fun DeveloperScreen(
    onBack: () -> Unit,
    onGitHub: () -> Unit,
    onTelegramChannel: () -> Unit,
    onTelegramGroup: () -> Unit,
    onReddit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgColor
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DeveloperHeader(onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Avatar
                Image(
                    painter = painterResource(id = R.drawable.dev_avatar),
                    contentDescription = "Developer avatar",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Name
                Text(
                    text = "MD Sakibur Rahman",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )

                // Short name
                Text(
                    text = "in short: MSR Sakibur",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Role
                Text(
                    text = "Independent Devloper, Creator And designer",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                // Divider
                Box(
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .width(48.dp)
                        .height(3.dp)
                        .background(Primary)
                )

                // Bio
                Text(
                    text = "Just an ordinary person with a curious mind,\nalways eager to learn, explore, and discover new things.\nAnd want to help more and more.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Social links card
                SocialLinksCard(
                    onGitHub = onGitHub,
                    onTelegramChannel = onTelegramChannel,
                    onTelegramGroup = onTelegramGroup,
                    onReddit = onReddit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DeveloperHeader(onBack: () -> Unit) {
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
            text = "Developer",
            color = TextPrimary,
            fontSize = 17.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun SocialLinksCard(
    onGitHub: () -> Unit,
    onTelegramChannel: () -> Unit,
    onTelegramGroup: () -> Unit,
    onReddit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(6.dp)
    ) {
        // GitHub
        SocialRow(
            iconRes = R.drawable.ic_terminal,
            label = "GitHub",
            onClick = onGitHub
        )

        HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

        // Telegram Channel
        SocialRow(
            iconRes = R.drawable.ic_send,
            label = "Telegram Channel",
            handle = "@msrpatch",
            onClick = onTelegramChannel
        )

        HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

        // Telegram Group
        SocialRow(
            iconRes = R.drawable.ic_send,
            label = "Telegram Group",
            handle = "@msrpatchchat",
            onClick = onTelegramGroup
        )

        HorizontalDivider(color = DividerColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))

        // Reddit
        SocialRow(
            iconRes = R.drawable.ic_globe,
            label = "Reddit",
            handle = "u/msrsakibur",
            onClick = onReddit
        )
    }
}

@Composable
private fun SocialRow(
    iconRes: Int,
    label: String,
    handle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )

        Text(
            text = label,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )

        if (handle != null) {
            Text(
                text = handle,
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
