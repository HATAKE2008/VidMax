package com.vidmax.player.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * Recent Play tab content for the Videos home screen (REX RecentlyPlayed
 * behavior adapted to VidMax design): newest first, tap plays via the
 * existing player flow, long-press opens the shared action menu. History
 * itself lives in [com.vidmax.player.data.repository.RecentPlayStore] and
 * is maintained by [LibraryViewModel].
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter) {
      Column(modifier = Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 1100.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
              TextButton(onClick = { viewModel.clearRecentHistory() }) {
                Text(
                    text = "Clear all",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
              }
            }
        LazyColumn(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
              itemsIndexed(items = recentVideos, key = { _, video -> video.path }) { index, video ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = { onPlayVideos(recentVideos, index) },
                                onLongClick = { menuVideo = video })
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Box(
                          modifier =
                              Modifier.size(38.dp)
                                  .clip(CircleShape)
                                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                          contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                          }
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (video.duration > 0) {
                          Text(
                              text = viewModel.formatDuration(video.duration),
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              fontSize = 11.sp)
                        }
                      }
                      IconButton(onClick = { onPlayVideos(recentVideos, index) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                      }
                    }
              }
            }
      }
    }
  }
}
