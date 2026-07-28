package com.vidmax.player.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vidmax.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoThumbnail(path: String, modifier: Modifier = Modifier) {
  var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(path) {
    bitmap =
        withContext(Dispatchers.IO) {
          try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val frame =
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame
          } catch (e: Exception) {
            null
          }
        }
  }

  Box(
      // CardBg এর বদলে surfaceVariant দেওয়া হলো
      modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center) {
        if (bitmap != null) {
          Image(
              bitmap = bitmap!!.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize())
          Box(
              modifier =
                  Modifier.fillMaxSize()
                      // Overlay এর বদলে ডাইনামিক ব্যাকগ্রাউন্ডের সাথে একটু ট্রান্সপারেন্সি দেওয়া
                      // হলো
                      .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)))
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_play),
            contentDescription = null,
            // Gold এর বদলে থিমের Primary কালার দেওয়া হলো!
            tint =
                MaterialTheme.colorScheme.primary.copy(alpha = if (bitmap != null) 0.9f else 0.4f),
            modifier = Modifier.align(Alignment.Center))
      }
}
