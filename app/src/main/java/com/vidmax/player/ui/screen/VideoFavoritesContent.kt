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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * Favorites tab content for the Videos home screen. Videos are favorited via
 * the heart action in multi-select mode; persistence mirrors the audio
 * favorites (SharedPreferences path set).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFavoritesContent(
  viewModel: LibraryViewModel,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
  onDeleteRequest: (VideoItem) -> Unit,
) {
  val favorites by viewModel.favoriteVideoPaths.collectAsState()
  val allVideos by viewModel.filteredVideos.collectAsState()

  val favoriteVideos = allVideos.filter { favorites.contains(it.path) }

  var menuVideo by remember { mutableStateOf<VideoItem?>(null) }
  VideoActionMenuHost(
      viewModel = viewModel,
      video = menuVideo,
      onPlay = { video ->
        val index = favoriteVideos.indexOfFirst { it.id == video.id }
        if (index >= 0) onPlayVideos(favoriteVideos, index)
        menuVideo = null
      },
      onDeleteRequest = {
        menuVideo = null
        onDeleteRequest(it)
      },
      onDismiss = { menuVideo = null })

  if (favoriteVideos.isEmpty()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
          Icon(
              imageVector = Icons.Filled.Favorite,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(64.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text(
              text = "No favorite videos yet",
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = "Long-press a video and choose Add to Favorites",
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
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
          itemsIndexed(items = favoriteVideos, key = { _, video -> video.id }) { index, video ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = { onPlayVideos(favoriteVideos, index) },
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
                            imageVector = Icons.Filled.Favorite,
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
                  IconButton(onClick = { viewModel.toggleVideoFavorite(video.path) }) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = "Remove favorite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                  }
                }
          }
        }
    }
  }
}

