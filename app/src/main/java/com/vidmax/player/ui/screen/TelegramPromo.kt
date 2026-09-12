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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    Toast.makeText(context, context.getString(R.string.tg_open_fail), Toast.LENGTH_SHORT).show()
  }
}

/**
 * Compact, minimal Material 3 card promoting the Telegram community.
 * Used both for the first-launch invitation and the Home top-bar action.
 * Dismisses on outside tap / back press via Dialog defaults.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelegramPromoSheet(
    onJoin: () -> Unit,
    onDismiss: () -> Unit
) {
  val telegramAccent = Color(0xFF229ED9)
  Dialog(onDismissRequest = onDismiss) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .navigationBarsPadding()) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally) {

            // Badge
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(telegramAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = null,
                      tint = telegramAccent,
                      modifier = Modifier.size(20.dp))
                }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.tg_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.tg_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 2,
                textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  FeaturePill(text = stringResource(R.string.tg_pill_updates))
                  FeaturePill(text = stringResource(R.string.tg_pill_requests))
                  FeaturePill(text = stringResource(R.string.tg_pill_bugfix))
                }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onJoin,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = telegramAccent,
                    contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(44.dp)) {
                  Icon(
                      imageVector = Icons.AutoMirrored.Rounded.Send,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(text = stringResource(R.string.tg_open), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            TextButton(onClick = onDismiss) {
              Text(
                  text = stringResource(R.string.tg_not_now),
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
    }
  }
}

@Composable
private fun FeaturePill(text: String) {
  Box(
      modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium)
      }
}
