package com.vidmax.player.ui.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.DialogCancelButton
import com.vidmax.player.ui.components.DialogConfirmButton
import com.vidmax.player.ui.components.DialogHeaderBadge
import com.vidmax.player.ui.selection.VideoSelection
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * Recent Play tab content for the Videos home screen (REX RecentlyPlayed
 * behavior adapted to VidMax design). Rows reuse the exact Videos-tab list
 * card ([PremiumVideoListCard]) so thumbnails and layout match the video
 * screen. History itself lives in
 * [com.vidmax.player.data.repository.RecentPlayStore] and is maintained by
 * [LibraryViewModel].
 */
@Composable
fun VideoRecentContent(
  viewModel: LibraryViewModel,
  selection: VideoSelection,
  onSelectionChange: (VideoSelection) -> Unit,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
) {
  val recentVideos by viewModel.recentVideos.collectAsState()
  var showClearConfirm by remember { mutableStateOf(false) }

  if (showClearConfirm) {
    AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
          DialogHeaderBadge(
              icon = Icons.Rounded.DeleteOutline,
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              contentColor = MaterialTheme.colorScheme.error)
        },
        title = { Text(stringResource(R.string.recent_clear_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = { Text(stringResource(R.string.recent_clear_message)) },
        confirmButton = {
          DialogConfirmButton(
              label = stringResource(R.string.recent_clear_confirm),
              danger = true,
              onClick = {
                viewModel.clearRecentHistory()
                showClearConfirm = false
              })
        },
        dismissButton = {
          DialogCancelButton(label = stringResource(R.string.recent_cancel), onClick = { showClearConfirm = false })
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
              text = stringResource(R.string.recent_empty_title),
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = stringResource(R.string.recent_empty_hint),
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
                  isSelected = selection.isSelected(video.path),
                  onClick = {
                    if (selection.isInSelectionMode) onSelectionChange(selection.toggle(video.path))
                    else onPlayVideos(recentVideos, index)
                  },
                  onLongClick = { onSelectionChange(selection.toggle(video.path)) })
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
