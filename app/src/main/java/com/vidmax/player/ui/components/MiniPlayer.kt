package com.vidmax.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.viewmodel.LibraryViewModel

@Composable
fun MiniPlayer(
    viewModel: LibraryViewModel,
    onExpandPlayer: () -> Unit // এটায় ক্লিক করলে ফুল স্ক্রিন প্লেয়ার ওপেন হবে
) {
  val isPlaying by viewModel.isAudioPlaying.collectAsState()
  val title by viewModel.recentlyPlayedTitle.collectAsState()
  val currentPath by viewModel.recentlyPlayedPath.collectAsState()
  val currentPosition by viewModel.audioPosition.collectAsState()
  val duration by viewModel.audioDuration.collectAsState()

  val safeDuration = if (duration > 0) duration else 1L
  val progress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

  // যদি কোনো গান সিলেক্ট করা না থাকে, তাহলে মিনি প্লেয়ার হাইড থাকবে
  AnimatedVisibility(
      visible = currentPath.isNotEmpty(),
      enter = slideInVertically(initialOffsetY = { it }),
      exit = slideOutVertically(targetOffsetY = { it })) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2C)) // Spotify-এর মতো ডার্ক থিম
                    .clickable { onExpandPlayer() } // ফুল স্ক্রিনে যাওয়ার জন্য
            ) {
              Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    // গানের নাম
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                          text = title.ifEmpty { "VidMax Music" },
                          color = Color.White,
                          fontWeight = FontWeight.Bold,
                          fontSize = 14.sp,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis)
                    }

                    // Play/Pause Buton
                    IconButton(onClick = { viewModel.toggleAudio() }) {
                      Icon(
                          painter =
                              painterResource(
                                  id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                          contentDescription = "Play/Pause",
                          tint = Color.White)
                    }

                    // Next Button
                    IconButton(onClick = { viewModel.playNextAudio() }) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_skip_next),
                          contentDescription = "Next",
                          tint = Color.White)
                    }
                  }

              // নিচে ছোট্ট একটা প্রগ্রেস বার
              LinearProgressIndicator(
                  progress = progress,
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = Color.Transparent,
                  modifier = Modifier.fillMaxWidth().height(2.dp))
            }
      }
}
