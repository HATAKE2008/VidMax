package com.vidmax.player.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.vidmax.player.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vidmax.player.data.local.video.VidMaxVideoPlaylistItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.DialogCancelButton
import com.vidmax.player.ui.components.DialogConfirmButton
import com.vidmax.player.ui.components.DialogHeaderBadge
import com.vidmax.player.ui.components.MetaChip
import com.vidmax.player.ui.selection.VideoSelection
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlaylistWithCount
import java.io.File

/**
 * Playlists tab content for the Videos home screen (REX PlaylistScreen
 * patterns adapted to VidMax): top-bar-driven search, playlist
 * multi-selection with rename/delete, create flow and detail navigation.
 * Video add/remove and repository logic are untouched.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaylistsContent(
  viewModel: LibraryViewModel,
  selection: VideoSelection,
  onSelectionChange: (VideoSelection) -> Unit,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
  onDeleteRequest: (VideoItem) -> Unit,
  searchRequestTick: Int = 0,
) {
  val playlists by viewModel.videoPlaylists.collectAsState()
  val opened by viewModel.openedVideoPlaylist.collectAsState()
  val items by viewModel.openedVideoPlaylistItems.collectAsState()
  var showCreateDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  // REX-style search: opened from the top app-bar icon, autofocused, X exits.
  var searching by rememberSaveable { mutableStateOf(false) }
  var playlistQuery by rememberSaveable { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  LaunchedEffect(searchRequestTick) {
    if (searchRequestTick > 0) {
      searching = true
      playlistQuery = ""
    }
  }
  LaunchedEffect(searching) {
    if (searching) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  // REX-style playlist multi-selection (stable int ids): rename when single,
  // delete when any selected. Video selection lives in the shared global
  // system; this covers playlist rows only.
  var selectedIds by remember { mutableStateOf(setOf<Int>()) }
  val inListSelection = selectedIds.isNotEmpty()
  var renameListTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
  var showListDeleteConfirm by remember { mutableStateOf(false) }

  BackHandler(enabled = (inListSelection || searching) && opened == null) {
    when {
      searching -> {
        searching = false
        playlistQuery = ""
      }
      inListSelection -> selectedIds = emptySet()
    }
  }

  val current = opened

  if (current == null) {
    val visiblePlaylists =
        remember(playlists, playlistQuery) {
          if (playlistQuery.isBlank()) playlists
          else playlists.filter { it.playlist.name.contains(playlistQuery, ignoreCase = true) }
        }
    Column(modifier = Modifier.fillMaxSize()) {
      if (searching) {
        SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                  query = playlistQuery,
                  onQueryChange = { playlistQuery = it },
                  onSearch = {},
                  expanded = false,
                  onExpandedChange = {},
                  placeholder = { Text("Search playlists") },
                  leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null)
                  },
                  trailingIcon = {
                    IconButton(
                        onClick = {
                          searching = false
                          playlistQuery = ""
                        }) {
                      Icon(
                          imageVector = Icons.Filled.Close,
                          contentDescription = "Close search")
                    }
                  },
                  modifier = Modifier.focusRequester(focusRequester))
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp) {}
      }
      if (inListSelection) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedIds = emptySet() }) {
                  Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Clear selection",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }
                Text(
                    text = "${selectedIds.size} Selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedIds.size == 1) {
                  IconButton(
                      onClick = {
                        visiblePlaylists.firstOrNull { it.playlist.id == selectedIds.first() }
                            ?.let { renameListTarget = it }
                      }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename playlist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                }
                IconButton(onClick = { showListDeleteConfirm = true }) {
                  Icon(
                      imageVector = Icons.Filled.Delete,
                      contentDescription = "Delete playlists",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(24.dp))
                }
              }
            }
      }
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
                      "Long-press a video and choose Add to Playlist",
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
            // P4c: cap line length on tablets, same 1100dp pattern as Home.
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1100.dp),
            contentPadding = PaddingValues(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(items = visiblePlaylists, key = { it.playlist.id }) { entry ->
                PlaylistCard(
                    name = entry.playlist.name,
                    count = entry.itemCount,
                    isSelected = selectedIds.contains(entry.playlist.id),
                    onClick = {
                      if (inListSelection) {
                        selectedIds =
                            if (selectedIds.contains(entry.playlist.id)) selectedIds - entry.playlist.id
                            else selectedIds + entry.playlist.id
                      } else {
                        viewModel.openVideoPlaylist(entry.playlist.id)
                      }
                    },
                    onLongClick = {
                      selectedIds =
                          if (selectedIds.contains(entry.playlist.id)) selectedIds - entry.playlist.id
                          else selectedIds + entry.playlist.id
                    })
              }
            }
      }

        if (!inListSelection && !searching) {
          FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 148.dp)
                    .size(56.dp)) {
              Icon(imageVector = Icons.Filled.Add, contentDescription = "Create playlist")
            }
      }
    }
    }
  } else {
    PlaylistDetailContent(
        viewModel = viewModel,
        selection = selection,
        onSelectionChange = onSelectionChange,
        playlistId = current.id,
        playlistName = current.name,
        items = items,
        onBack = { viewModel.closeVideoPlaylist() },
        onRename = { showRenameDialog = true },
        onDelete = { showDeleteConfirm = true },
        onPlayVideos = onPlayVideos,
        onDeleteRequest = onDeleteRequest)
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
        icon = Icons.Rounded.DriveFileRenameOutline,
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
          DialogHeaderBadge(
              icon = Icons.Rounded.DeleteOutline,
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              contentColor = MaterialTheme.colorScheme.error)
        },
        title = { Text("Delete \"${current.name}\"?", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = { Text("All videos inside this playlist will be removed from it.") },
        confirmButton = {
          DialogConfirmButton(
              label = "Delete",
              danger = true,
              onClick = {
                viewModel.deleteVideoPlaylist(current.id)
                showDeleteConfirm = false
              })
        },
        dismissButton = {
          DialogCancelButton(label = "Cancel", onClick = { showDeleteConfirm = false })
        })
  }

  renameListTarget?.let { target ->
    NamePromptDialog(
        title = "Rename Playlist",
        confirmLabel = "Rename",
        initialText = target.playlist.name,
        icon = Icons.Rounded.DriveFileRenameOutline,
        onDismiss = { renameListTarget = null },
        onConfirm = { name ->
          viewModel.renameVideoPlaylist(target.playlist.id, name)
          renameListTarget = null
          selectedIds = emptySet()
        })
  }

  if (showListDeleteConfirm && selectedIds.isNotEmpty()) {
    AlertDialog(
        onDismissRequest = { showListDeleteConfirm = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
          DialogHeaderBadge(
              icon = Icons.Rounded.DeleteOutline,
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              contentColor = MaterialTheme.colorScheme.error)
        },
        title = {
          Text(
              if (selectedIds.size == 1) "Delete this playlist?" else "Delete ${selectedIds.size} playlists?",
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp)
        },
        text = { Text("Videos stay in your library; only the playlists are removed.") },
        confirmButton = {
          DialogConfirmButton(
              label = "Delete",
              danger = true,
              onClick = {
                selectedIds.forEach { viewModel.deleteVideoPlaylist(it) }
                selectedIds = emptySet()
                showListDeleteConfirm = false
              })
        },
        dismissButton = {
          DialogCancelButton(label = "Cancel", onClick = { showListDeleteConfirm = false })
        })
  }
}

/**
 * REX-style playlist row: M3 Card with a playlist icon badge, title and
 * metadata chips (count + Local type). Callbacks unchanged.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    name: String,
    count: Int,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
  Card(
      modifier = Modifier.fillMaxWidth()
          .border(
              width = 1.5.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary
              else Color.Transparent,
              shape = RoundedCornerShape(12.dp)),
      colors = CardDefaults.cardColors(
          containerColor = if (isSelected)
              MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
          else MaterialTheme.colorScheme.surfaceContainer)) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Box(
              modifier =
                  Modifier.size(56.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                    modifier = Modifier.size(30.dp))
              }
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
                overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  MetaChip(
                      text = if (count == 1) "1 video" else "$count videos",
                      highlighted = true)
                  MetaChip(text = "Local")
                }
          }
          Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Open",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp))
        }
  }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailContent(
  viewModel: LibraryViewModel,
  selection: VideoSelection,
  onSelectionChange: (VideoSelection) -> Unit,
  playlistId: Int,
  playlistName: String,
  items: List<VidMaxVideoPlaylistItem>,
  onBack: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
  onDeleteRequest: (VideoItem) -> Unit,
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

  // Long-press drives the shared selection system; all actions live in the
  // global top/bottom bars, so no local menu host is needed here.

  Column(modifier = Modifier.fillMaxSize()) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
              Icon(
                  imageVector = Icons.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = MaterialTheme.colorScheme.primary)
            }
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                      modifier = Modifier.size(26.dp))
                }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = playlistName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)
              Spacer(modifier = Modifier.height(4.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaChip(
                    text = if (items.size == 1) "1 video" else "${items.size} videos",
                    highlighted = true)
              }
            }
            if (visibleItems.isNotEmpty()) {
              FilledTonalButton(onClick = { onPlayVideos(toVideoItems(visibleItems), 0) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play all")
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
    }

    if (items.size > 1) {
      OutlinedTextField(
          value = itemQuery,
          onValueChange = { itemQuery = it },
          label = { Text("Search in playlist") },
          leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_search), contentDescription = null) },
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
          // P4c: cap line length on tablets, same 1100dp pattern as Home.
          modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxSize().widthIn(max = 1100.dp),
          contentPadding = PaddingValues(bottom = 130.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items = visibleItems, key = { it.id }) { item ->
              val index = visibleItems.indexOf(item)
              val itemSelected = selection.isSelected(item.filePath)
              Card(
                  modifier = Modifier.fillMaxWidth()
                      .border(
                          width = 1.5.dp,
                          color = if (itemSelected) MaterialTheme.colorScheme.primary
                          else Color.Transparent,
                          shape = RoundedCornerShape(12.dp)),
                  colors = CardDefaults.cardColors(
                      containerColor = if (itemSelected)
                          MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                      else MaterialTheme.colorScheme.surfaceContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                              if (selection.isInSelectionMode) {
                                onSelectionChange(selection.toggle(item.filePath))
                              } else {
                                onPlayVideos(toVideoItems(visibleItems), index)
                              }
                            },
                            onLongClick = { onSelectionChange(selection.toggle(item.filePath)) })
                        .padding(10.dp),
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
                          color = MaterialTheme.colorScheme.onSurface,
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
}

/** Simple text-input dialog used for creating and renaming playlists. */
@Composable
fun NamePromptDialog(
  title: String,
  confirmLabel: String,
  initialText: String = "",
  icon: ImageVector = Icons.Rounded.AddCircleOutline,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialText) }
  AlertDialog(
      onDismissRequest = onDismiss,
      shape = RoundedCornerShape(28.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      icon = { DialogHeaderBadge(icon = icon) },
      title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
      text = {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Playlist name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary),
            trailingIcon = {
              if (text.isNotEmpty()) {
                IconButton(onClick = { text = "" }) {
                  Icon(
                      imageVector = Icons.Rounded.Clear,
                      contentDescription = "Clear name",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            },
            modifier = Modifier.fillMaxWidth())
      },
      confirmButton = {
        DialogConfirmButton(
            label = confirmLabel,
            enabled = text.isNotBlank(),
            onClick = { onConfirm(text.trim()) })
      },
      dismissButton = { DialogCancelButton(label = "Cancel", onClick = onDismiss) })
}
