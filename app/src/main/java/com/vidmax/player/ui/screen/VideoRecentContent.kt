package com.vidmax.player.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * Recent Play tab content for the Videos home screen (REX RecentlyPlayed
 * behavior adapted to VidMax design). Rows reuse the exact Videos-tab list
 * card ([PremiumVideoListCard]) so thumbnails and layout match the video
 * screen. History itself lives in
 * [com.vidmax.player.data.repository.RecentPlayStore] and is maintained by
 * [LibraryViewModel].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRecentContent(
  viewModel: LibraryViewModel,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
  onDeleteRequest: (VideoItem) -> Unit,
) {
  val recentVideos by viewModel.recentVideos.collectAsState()
  var menuVideo by remember { mutableStateOf<VideoItem?>(null) }
  var showClearConfirm by remember { mutableStateOf(false) }

  VideoActionMenuHost(
      viewModel = viewModel,
      video = menuVideo,
      onPlay = { video ->
        val index = recentVideos.indexOfFirst { it.path == video.path }
        if (index >= 0) onPlayVideos(recentVideos, index)
        menuVideo = null
      },
      onDeleteRequest = {
        menuVideo = null
        onDeleteRequest(it)
      },
      onDismiss = { menuVideo = null })

  if (showClearConfirm) {
    AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("Clear Recent Play?", fontWeight = FontWeight.Bold) },
        text = { Text("All recently played entries will be removed from this device.") },
        confirmButton = {
          TextButton(
              onClick = {
                viewModel.clearRecentHistory()
                showClearConfirm = false
              }) {
                Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
        })
  }

  if (recentVideos.isEmpty()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
          Icon(
              imageVector = Icons.Filled.History,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(64.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              text = "No recently played videos",
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = "Videos you play will appear here",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 13.sp)
        }
  } else {
    // P4c: cap line length on tablets, same 1100dp pattern as Home.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter) {
      LazyColumn(
          modifier = Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 1100.dp),
          contentPadding = PaddingValues(bottom = 130.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items = recentVideos, key = { _, video -> video.path }) { index, video ->
              PremiumVideoListCard(
                  video = video,
                  duration = viewModel.formatDuration(video.duration),
                  size = viewModel.formatSize(video.size),
                  resolution = viewModel.getResolutionLabel(video.width, video.height),
                  isSelected = false,
                  onClick = { onPlayVideos(recentVideos, index) },
                  onLongClick = { menuVideo = video })
            }
          }
      // Clear-history trash action floating bottom-right above the nav bar.
      IconButton(
          onClick = { showClearConfirm = true },
          modifier = Modifier.align(Alignment.BottomEnd)
              .navigationBarsPadding()
              .padding(end = 12.dp, bottom = 100.dp)
              .size(60.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_custom),
                contentDescription = "Clear Recent Play",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp))
          }
    }
  }
}
