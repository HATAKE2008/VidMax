package com.vidmax.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope.weight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vidmax.player.data.local.video.VidMaxVideoPlaylistItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlaylistWithCount
import java.io.File

/**
 * Playlists tab content for the Videos home screen (mpvRex-style).
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun VideoPlaylistsContent(
  viewModel: LibraryViewModel,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
) {
  val playlists by viewModel.videoPlaylists.collectAsState()
  val opened by viewModel.openedVideoPlaylist.collectAsState()
  val items by viewModel.openedVideoPlaylistItems.collectAsState()
  var showCreateDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val current = opened

  if (current == null) {
    var playlistQuery by remember { mutableStateOf("") }
    val visiblePlaylists =
        remember(playlists, playlistQuery) {
          if (playlistQuery.isBlank()) playlists
          else playlists.filter { it.playlist.name.contains(playlistQuery, ignoreCase = true) }
        }
    Column(modifier = Modifier.fillMaxSize()) {
      OutlinedTextField(
          value = playlistQuery,
          onValueChange = { playlistQuery = it },
          label = { Text("Search playlists") },
          leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
          trailingIcon = {
            if (playlistQuery.isNotEmpty()) {
              IconButton(onClick = { playlistQuery = "" }) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (playlists.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
              Icon(
                  imageVector = Icons.Filled.QueueMusic,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(64.dp))
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                  text = "No playlists yet",
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.SemiBold)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text =
                      "Long-press videos to select them, then tap the playlist icon to add",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 13.sp,
                  modifier = Modifier.padding(horizontal = 12.dp))
            }
      } else if (visiblePlaylists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
              text = "No playlists match \"$playlistQuery\"",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 14.sp)
        }
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(items = visiblePlaylists, key = { it.playlist.id }) { entry ->
                PlaylistCard(
                    name = entry.playlist.name,
                    count = entry.itemCount,
                    onClick = { viewModel.openVideoPlaylist(entry.playlist.id) })
              }
            }
      }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp)) {
              Icon(imageVector = Icons.Filled.Add, contentDescription = "Create playlist")
            }
      }
    }
  } else {
    PlaylistDetailContent(
        viewModel = viewModel,
        playlistId = current.id,
        playlistName = current.name,
        items = items,
        onBack = { viewModel.closeVideoPlaylist() },
        onRename = { showRenameDialog = true },
        onDelete = { showDeleteConfirm = true },
        onPlayVideos = onPlayVideos)
  }

  if (showCreateDialog) {
    NamePromptDialog(
        title = "New Playlist",
        confirmLabel = "Create",
        onDismiss = { showCreateDialog = false }) { name ->
          viewModel.createVideoPlaylist(name)
          showCreateDialog = false
        }
  }

  if (showRenameDialog && current != null) {
    NamePromptDialog(
        title = "Rename Playlist",
        confirmLabel = "Rename",
        initialText = current.name,
        onDismiss = {
          showRenameDialog = false
          viewModel.closeVideoPlaylist()
          viewModel.openVideoPlaylist(current.id)
        }) { name ->
          viewModel.renameVideoPlaylist(current.id, name)
          showRenameDialog = false
        }
  }

  if (showDeleteConfirm && current != null) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("Delete \"${current.name}\"?", fontWeight = FontWeight.Bold) },
        text = { Text("All videos inside this playlist will be removed from it.") },
        confirmButton = {
          Button(onClick = {
            viewModel.deleteVideoPlaylist(current.id)
            showDeleteConfirm = false
          }) { Text("Delete") }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
        })
  }
}

@Composable
private fun PlaylistCard(name: String, count: Int, onClick: () -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable(onClick = onClick)
              .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center) {
              Icon(
                  imageVector = Icons.Filled.PlaylistAdd,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(26.dp))
            }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = name,
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              modifier = Modifier.basicMarquee(),
              overflow = TextOverflow.Ellipsis)
          Text(
              text = "$count videos",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
      }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PlaylistDetailContent(
  viewModel: LibraryViewModel,
  playlistId: Int,
  playlistName: String,
  items: List<VidMaxVideoPlaylistItem>,
  onBack: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var itemQuery by remember { mutableStateOf("") }
  val visibleItems =
      remember(items, itemQuery) {
        if (itemQuery.isBlank()) items
        else items.filter { it.fileName.contains(itemQuery, ignoreCase = true) }
      }

  fun toVideoItems(list: List<VidMaxVideoPlaylistItem>): List<VideoItem> =
      list.map { item ->
        VideoItem(
            id = item.id.toLong(),
            title = item.fileName,
            path = item.filePath,
            duration = 0L,
            size = 0L,
            width = 0,
            height = 0,
            dateAdded = item.addedAt,
            folderPath = "",
            folderName = "")
      }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary)
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                text = "${items.size} videos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
          }
          if (visibleItems.isNotEmpty()) {
            IconButton(onClick = { onPlayVideos(toVideoItems(visibleItems), 0) }) {
              Icon(
                  imageVector = Icons.Filled.PlayArrow,
                  contentDescription = "Play all",
                  tint = MaterialTheme.colorScheme.primary)
            }
          }
          Box {
            IconButton(onClick = { menuOpen = true }) {
              Icon(
                  imageVector = Icons.Filled.MoreVert,
                  contentDescription = "More",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
              DropdownMenuItem(
                  text = { Text("Rename") },
                  onClick = {
                    menuOpen = false
                    onRename()
                  })
              DropdownMenuItem(
                  text = { Text("Clear videos") },
                  onClick = {
                    menuOpen = false
                    viewModel.clearVideoPlaylist(playlistId)
                  })
              DropdownMenuItem(
                  text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                  onClick = {
                    menuOpen = false
                    onDelete()
                  })
            }
          }
        }

    if (items.size > 1) {
      OutlinedTextField(
          value = itemQuery,
          onValueChange = { itemQuery = it },
          label = { Text("Search in playlist") },
          leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
          trailingIcon = {
            if (itemQuery.isNotEmpty()) {
              IconButton(onClick = { itemQuery = "" }) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear")
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }

    if (items.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "This playlist is empty.\nLong-press videos to add them here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp)
      }
    } else if (visibleItems.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No videos match \"$itemQuery\"",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp)
      }
    } else {
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 130.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items = visibleItems, key = { it.id }) { item ->
              val index = visibleItems.indexOf(item)
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .clip(RoundedCornerShape(12.dp))
                          .background(MaterialTheme.colorScheme.surfaceVariant)
                          .clickable { onPlayVideos(toVideoItems(visibleItems), index) }
                          .padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    // Real video thumbnail, same loading path as the folder view.
                    Box(
                        modifier =
                            Modifier.width(110.dp)
                                .height(62.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)) {
                          GlideImage(
                              model = File(item.filePath),
                              contentDescription = "Thumbnail",
                              contentScale = ContentScale.Crop,
                              modifier = Modifier.fillMaxSize()) { requestBuilder ->
                            requestBuilder
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .override(400)
                          }
                        }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.fileName,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeVideoFromPlaylist(item) }) {
                      Icon(
                          imageVector = Icons.Filled.Delete,
                          contentDescription = "Remove",
                          tint = Color(0xFFB3544F),
                          modifier = Modifier.size(20.dp))
                    }
                  }
            }
          }
    }
  }
}

/** Simple text-input dialog used for creating and renaming playlists. */
@Composable
fun NamePromptDialog(
  title: String,
  confirmLabel: String,
  initialText: String = "",
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialText) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Playlist name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth())
      },
      confirmButton = {
        Button(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
          Text(confirmLabel)
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
