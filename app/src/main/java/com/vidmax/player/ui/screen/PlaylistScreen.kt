package com.vidmax.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.viewmodel.LibraryViewModel

@Composable
fun PlaylistScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onAudioClick: (List<AudioItem>, Int) -> Unit
) {
  val title by viewModel.openedPlaylistTitle.collectAsState()
  val audioList by viewModel.openedPlaylistAudio.collectAsState()

  // 🔥 ম্যাজিক: প্লেলিস্টেও কারেন্ট প্লেয়িং গানের হাইলাইট
  val currentlyPlayingPath by viewModel.recentlyPlayedPath.collectAsState()
  val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
              }
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Text(
                    text = "${audioList.size} songs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp)
              }
            }

        if (audioList.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No songs found in $title",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp)
          }
        } else {
          LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(items = audioList, key = { _, item -> item.id }) { index, audio ->
                  val isPlayingNow = currentlyPlayingPath == audio.path

                  AudioCard(
                      audio = audio,
                      duration = viewModel.formatDuration(audio.duration),
                      isPlayingNow = isPlayingNow,
                      isAudioPlayingState = isAudioPlaying,
                      onClick = { onAudioClick(audioList, index) })
                }
              }
        }
      }
}
