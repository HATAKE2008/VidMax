package com.vidmax.player.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R

const val TELEGRAM_CHANNEL_USERNAME: String = "vidmax_opensource"
const val TELEGRAM_CHANNEL_URL: String = "https://t.me/vidmax_opensource"

/**
 * Opens the VidMax Telegram community: prefers the Telegram app via a
 * `tg://` intent, falls back to the web URL in a browser, never crashes.
 */
fun openTelegramCommunity(context: Context) {
  try {
    val appIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("tg://resolve?domain=$TELEGRAM_CHANNEL_USERNAME"))
    val resolved = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.resolveActivity(
            appIntent, PackageManager.ResolveInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(appIntent, 0)
      }
    } catch (e: Exception) {
      null
    }
    if (resolved != null) {
      context.startActivity(appIntent)
      return
    }
  } catch (e: Exception) {
    // Fall through to the browser URL below.
  }
  try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHANNEL_URL)))
  } catch (e: Exception) {
    Toast.makeText(context, "Could not open Telegram", Toast.LENGTH_SHORT).show()
  }
}

private val TELEGRAM_BENEFITS: List<String> = listOf(
    "App updates & new features",
    "Bug reports & fixes",
    "Tips and useful information",
    "Early access to new releases",
    "Share feedback and suggestions",
    "Contact us about VidMax"
)

/**
 * Polished VidMax-styled bottom sheet promoting the Telegram community.
 * Used both for the first-launch invitation and the Home top-bar action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramPromoSheet(
    onJoin: () -> Unit,
    onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
              modifier = Modifier.size(64.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp))
              }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
              text = "Join VidMax on Telegram",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 19.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
              text = "Join our Telegram community to stay updated and help us improve VidMax.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 14.sp,
              lineHeight = 20.sp,
              textAlign = TextAlign.Center)
          Spacer(modifier = Modifier.height(14.dp))
          Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TELEGRAM_BENEFITS.forEach { benefit ->
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = benefit,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp)
                  }
                }
              }
          Spacer(modifier = Modifier.height(14.dp))
          Text(
              text = "Your feedback helps us make VidMax better.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              textAlign = TextAlign.Center)
          Spacer(modifier = Modifier.height(16.dp))
          Button(
              onClick = onJoin,
              modifier = Modifier.fillMaxWidth()) {
                Text(text = "Join Telegram", fontSize = 15.sp, fontWeight = FontWeight.Bold)
              }
          TextButton(
              onClick = onDismiss,
              modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Not now",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
        }
  }
}
