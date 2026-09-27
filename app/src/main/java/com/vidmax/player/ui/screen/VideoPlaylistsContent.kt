package com.vidmax.player.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
) {
  val playlists by viewModel.videoPlaylists.collectAsState()
  val context = LocalContext.current
  val opened by viewModel.openedVideoPlaylist.collectAsState()
  val m3uSourceIds by viewModel.m3uSourceIds.collectAsState()
  val items by viewModel.openedVideoPlaylistItems.collectAsState()
  var showCreateDialog by remember { mutableStateOf(false) }
  var showCreateMenu by remember { mutableStateOf(false) }
  var showImportDialog by remember { mutableStateOf(false) }
  var showRenameDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  // Playlist search lives in the unified SearchScreen (PLAYLISTS scope),
  // opened from the top app-bar icon — no duplicate field here and no
  // FocusRequester on this screen.
  val visiblePlaylists = playlists
  var selectedIds by remember { mutableStateOf(setOf<Int>()) }
  val inListSelection = selectedIds.isNotEmpty()
  var renameListTarget by remember { mutableStateOf<PlaylistWithCount?>(null) }
  var showListDeleteConfirm by remember { mutableStateOf(false) }

  BackHandler(enabled = inListSelection && opened == null) {
    selectedIds = emptySet()
  }

  val current = opened

  if (current == null) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                    text = stringResource(R.string.vpl_selected_count, selectedIds.size),
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
                  text = stringResource(R.string.vpl_empty_title),
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.SemiBold)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                  text =
                      stringResource(R.string.vpl_empty_hint),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 13.sp,
                  modifier = Modifier.padding(horizontal = 12.dp))
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
                    isM3u = m3uSourceIds.contains(entry.playlist.id),
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
                    },
                    onPlay = {
                      viewModel.openAndPlayPlaylist(entry.playlist.id) { result ->
                        result
                            .onSuccess { list -> onPlayVideos(list, 0) }
                            .onFailure {
                              Toast.makeText(
                                  context,
                                  it.message ?: context.getString(R.string.vpl_play_failed),
                                  Toast.LENGTH_SHORT).show()
                            }
                      }
                    },
                    onRename = {
                      renameListTarget = PlaylistWithCount(entry.playlist, entry.itemCount)
                    },
                    onDelete = {
                      selectedIds = setOf(entry.playlist.id)
                      showListDeleteConfirm = true
                    })
              }
            }
      }

        if (!inListSelection) {
          FloatingActionButton(
            onClick = { showCreateMenu = true },
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
        isM3u = m3uSourceIds.contains(current.id),
        items = items,
        onBack = { viewModel.closeVideoPlaylist() },
        onRename = { showRenameDialog = true },
        onDelete = { showDeleteConfirm = true },
        onPlayVideos = onPlayVideos,
        onDeleteRequest = onDeleteRequest)
  }

  if (showCreateMenu) {
    AlertDialog(
        onDismissRequest = { showCreateMenu = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { DialogHeaderBadge(icon = Icons.Filled.Add) },
        title = { Text(stringResource(R.string.vpl_create_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CreateMenuRow(
                icon = Icons.Filled.Add,
                title = stringResource(R.string.vpl_create_new_title),
                subtitle = stringResource(R.string.vpl_create_new_subtitle),
                onClick = {
                  showCreateMenu = false
                  showCreateDialog = true
                })
            CreateMenuRow(
                icon = Icons.Filled.Link,
                title = stringResource(R.string.vpl_import_m3u_title),
                subtitle = stringResource(R.string.vpl_import_m3u_subtitle),
                onClick = {
                  showCreateMenu = false
                  showImportDialog = true
                })
          }
        },
        confirmButton = {},
        dismissButton = {
          DialogCancelButton(label = stringResource(R.string.vpl_cancel), onClick = { showCreateMenu = false })
        })
  }

  if (showImportDialog) {
    ImportM3UDialog(
        onDismiss = { showImportDialog = false },
        onImport = { url, onResult ->
          viewModel.importM3UPlaylist(url, onResult)
        })
  }

  if (showCreateDialog) {
    NamePromptDialog(
        title = stringResource(R.string.vpl_new_playlist_title),
        confirmLabel = stringResource(R.string.vpl_create_confirm),
        onDismiss = { showCreateDialog = false }) { name ->
          viewModel.createVideoPlaylist(name)
          showCreateDialog = false
        }
  }

  if (showRenameDialog && current != null) {
    NamePromptDialog(
        title = stringResource(R.string.vpl_rename_title),
        confirmLabel = stringResource(R.string.vpl_rename_confirm),
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
        title = { Text(stringResource(R.string.vpl_delete_title, current.name), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = { Text(stringResource(R.string.vpl_delete_message)) },
        confirmButton = {
          DialogConfirmButton(
              label = stringResource(R.string.vpl_delete_confirm),
              danger = true,
              onClick = {
                viewModel.deleteVideoPlaylist(current.id)
                showDeleteConfirm = false
              })
        },
        dismissButton = {
          DialogCancelButton(label = stringResource(R.string.vpl_cancel), onClick = { showDeleteConfirm = false })
        })
  }

  renameListTarget?.let { target ->
    NamePromptDialog(
        title = stringResource(R.string.vpl_rename_title),
        confirmLabel = stringResource(R.string.vpl_rename_confirm),
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
              if (selectedIds.size == 1) stringResource(R.string.vpl_delete_single_title) else stringResource(R.string.vpl_delete_multi_title, selectedIds.size),
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp)
        },
        text = { Text(stringResource(R.string.vpl_delete_list_message)) },
        confirmButton = {
          DialogConfirmButton(
              label = stringResource(R.string.vpl_delete_confirm),
              danger = true,
              onClick = {
                selectedIds.forEach { viewModel.deleteVideoPlaylist(it) }
                selectedIds = emptySet()
                showListDeleteConfirm = false
              })
        },
        dismissButton = {
          DialogCancelButton(label = stringResource(R.string.vpl_cancel), onClick = { showListDeleteConfirm = false })
        })
  }
}

/**
 * REX-style playlist row: M3 Card with a playlist icon badge, title and
 * metadata chips (count + Local type). Callbacks unchanged.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    name: String,
    count: Int,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    isM3u: Boolean = false,
    onPlay: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
  var overflowOpen by remember { mutableStateOf(false) }
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
                      text = pluralStringResource(R.plurals.vpl_videos_count, count, count),
                      highlighted = true)
                  MetaChip(text = if (isM3u) "M3U" else stringResource(R.string.vpl_type_local))
                }
          }
          IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play playlist",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
          }
          Box {
            IconButton(onClick = { overflowOpen = true }) {
              Icon(
                  imageVector = Icons.Filled.MoreVert,
                  contentDescription = "Playlist options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.vpl_menu_play)) },
                  leadingIcon = {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                  },
                  onClick = {
                    overflowOpen = false
                    onPlay()
                  })
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.vpl_menu_rename)) },
                  leadingIcon = {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                  },
                  onClick = {
                    overflowOpen = false
                    onRename()
                  })
              DropdownMenuItem(
                  text = { Text(stringResource(R.string.vpl_menu_delete), color = MaterialTheme.colorScheme.error) },
                  leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                  },
                  onClick = {
                    overflowOpen = false
                    onDelete()
                  })
            }
          }
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
  isM3u: Boolean,
  items: List<VidMaxVideoPlaylistItem>,
  onBack: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onPlayVideos: (List<VideoItem>, Int) -> Unit,
  onDeleteRequest: (VideoItem) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var itemQuery by remember { mutableStateOf("") }
  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val listState = rememberLazyListState()

  // REX-style reorder mode: drag rows to rearrange, Done commits. Local
  // order previews instantly; every completed drag persists immediately.
  var isReorderMode by remember { mutableStateOf(false) }
  var localOrder by remember { mutableStateOf<List<VidMaxVideoPlaylistItem>?>(null) }
  var draggingId by remember { mutableStateOf<Int?>(null) }
  var dragOffsetYPx by remember { mutableFloatStateOf(0f) }

  BackHandler(enabled = isReorderMode) {
    isReorderMode = false
    localOrder = null
    draggingId = null
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

  fun displayedItems(): List<VidMaxVideoPlaylistItem> {
    val base = localOrder ?: items
    if (itemQuery.isBlank()) return base
    return base.filter { it.fileName.contains(itemQuery, ignoreCase = true) }
  }

  fun enterReorderMode() {
    onSelectionChange(VideoSelection())
    itemQuery = ""
    localOrder = items.toList()
    draggingId = null
    dragOffsetYPx = 0f
    isReorderMode = true
  }

  fun commitOrder() {
    val order = localOrder ?: return
    viewModel.reorderVideoPlaylist(playlistId, order.map { it.id })
  }

  // Videos selected via the shared global system that belong to this
  // playlist (for multi-remove).
  val selectedHere: List<VideoItem> = selection.getSelected(toVideoItems(items))
  val visibleItems =
      remember(items, itemQuery) {
        if (itemQuery.isBlank()) items
        else items.filter { it.fileName.contains(itemQuery, ignoreCase = true) }
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
                    text = pluralStringResource(R.plurals.vpl_videos_count, items.size, items.size),
                    highlighted = true)
              }
            }
            if (isReorderMode) {
              TextButton(onClick = {
                commitOrder()
                isReorderMode = false
                localOrder = null
                draggingId = null
              }) {
                Text(stringResource(R.string.vpl_done), fontWeight = FontWeight.Bold)
              }
            } else {
            if (visibleItems.isNotEmpty()) {
              FilledTonalButton(onClick = { onPlayVideos(toVideoItems(displayedItems()), 0) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.vpl_play_all))
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
                    text = { Text(stringResource(R.string.vpl_menu_rename)) },
                    onClick = {
                      menuOpen = false
                      onRename()
                    })
                if (isM3u) {
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.vpl_refresh_from_url)) },
                      leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null)
                      },
                      onClick = {
                        menuOpen = false
                        viewModel.refreshM3UPlaylist(playlistId) { result ->
                          result
                              .onSuccess { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.vpl_refreshed, count),
                                    Toast.LENGTH_SHORT).show()
                              }
                              .onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: context.getString(R.string.vpl_refresh_failed),
                                    Toast.LENGTH_SHORT).show()
                              }
                        }
                      })
                }
                if (itemQuery.isBlank()) {
                  DropdownMenuItem(
                      text = { Text(stringResource(R.string.vpl_reorder)) },
                      leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = null)
                      },
                      onClick = {
                        menuOpen = false
                        enterReorderMode()
                      })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpl_shuffle)) },
                    leadingIcon = {
                      Icon(
                          imageVector = Icons.Filled.Shuffle,
                          contentDescription = null)
                    },
                    onClick = {
                      menuOpen = false
                      val shuffled = displayedItems().shuffled()
                      if (shuffled.isNotEmpty()) onPlayVideos(toVideoItems(shuffled), 0)
                    })
                if (selectedHere.isNotEmpty()) {
                  DropdownMenuItem(
                      text = {
                        Text(
                            stringResource(R.string.vpl_remove_selected, selectedHere.size),
                            color = MaterialTheme.colorScheme.error)
                      },
                      leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                      },
                      onClick = {
                        menuOpen = false
                        val paths = selectedHere.map { it.path }.toSet()
                        viewModel.removePlaylistItemsByPaths(playlistId, paths) {
                          onSelectionChange(VideoSelection())
                        }
                      })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpl_clear_videos)) },
                    onClick = {
                      menuOpen = false
                      viewModel.clearVideoPlaylist(playlistId)
                    })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vpl_delete_playlist), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                      menuOpen = false
                      onDelete()
                    })
              }
            }
            }
          }
    }

    if (items.size > 1) {
      OutlinedTextField(
          value = itemQuery,
          onValueChange = { itemQuery = it },
          label = { Text(stringResource(R.string.vpl_search_in_playlist)) },
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
            text = stringResource(R.string.vpl_empty_playlist),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp)
      }
    } else if (visibleItems.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.vpl_no_match, itemQuery),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp)
      }
    } else {
      LazyColumn(
          // P4c: cap line length on tablets, same 1100dp pattern as Home.
          state = listState,
          modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxSize().widthIn(max = 1100.dp),
          contentPadding = PaddingValues(bottom = 130.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = displayedItems()
            items(items = shown, key = { it.id }) { item ->
              val index = shown.indexOf(item)
              val itemSelected = selection.isSelected(item.filePath)
              val isDragging = draggingId == item.id
              Card(
                  modifier = Modifier.fillMaxWidth()
                      .zIndex(if (isDragging) 1f else 0f)
                      .graphicsLayer {
                        translationY = if (isDragging) dragOffsetYPx else 0f
                      }
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
                              if (isReorderMode) {
                                // No-op: taps must not start playback mid-reorder.
                              } else if (selection.isInSelectionMode) {
                                onSelectionChange(selection.toggle(item.filePath))
                              } else {
                                onPlayVideos(toVideoItems(shown), index)
                              }
                            },
                            onLongClick = {
                              if (!isReorderMode) {
                                onSelectionChange(selection.toggle(item.filePath))
                              }
                            })
                        .reorderDragModifier(
                            enabled = isReorderMode,
                            itemId = item.id,
                            listState = listState,
                            currentOffsetY = { dragOffsetYPx },
                            onDragStart = {
                              draggingId = item.id
                              dragOffsetYPx = 0f
                              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onMove = { fromId, toIndex ->
                              val current = localOrder ?: items.toList()
                              val fromIndex = current.indexOfFirst { it.id == fromId }
                              if (fromIndex in current.indices) {
                                localOrder = current.toMutableList().apply {
                                  add(toIndex.coerceIn(indices), removeAt(fromIndex))
                                }
                                // Re-anchor the drag offset to the moved row so
                                // further movement stays relative to the finger.
                                dragOffsetYPx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                              }
                            },
                            onDragDelta = { dy -> dragOffsetYPx += dy },
                            onDragEnd = {
                              draggingId = null
                              dragOffsetYPx = 0f
                              commitOrder()
                            },
                            onDragCancel = {
                              draggingId = null
                              dragOffsetYPx = 0f
                            })
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
                      if (isReorderMode) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp))
                      } else {
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
}

/**
 * Long-press-drag reorder modifier for playlist rows (REX drag reorder
 * adapted without extra dependencies): tracks the dragged row against the
 * list layout and reports target indices. The caller owns visual offset,
 * order state and persistence.
 */
private fun Modifier.reorderDragModifier(
    enabled: Boolean,
    itemId: Int,
    listState: LazyListState,
    currentOffsetY: () -> Float,
    onDragStart: () -> Unit,
    onMove: (fromId: Int, toIndex: Int) -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
  if (!enabled) return this
  return this.pointerInput(itemId, listState) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onDragStart() },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragCancel() },
        onDrag = { change, dragAmount ->
          change.consume()
          onDragDelta(dragAmount.y)
          val layout = listState.layoutInfo
          val dragged =
              layout.visibleItemsInfo.firstOrNull { it.key == itemId }
                  ?: return@detectDragGesturesAfterLongPress
          val centerY = dragged.offset + currentOffsetY() + dragged.size / 2f
          val target =
              layout.visibleItemsInfo
                  .filter { it.key != itemId }
                  .firstOrNull { centerY in it.offset.toFloat()..(it.offset + it.size).toFloat() }
                  ?: return@detectDragGesturesAfterLongPress
          onMove(itemId, target.index)
        })
  }
}

/** Single row inside the playlist create menu. */
@Composable
private fun CreateMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
  Row(
      modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .clickable(onClick = onClick)
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
              Icon(
                  imageVector = icon,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(22.dp))
            }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = title,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold)
          Text(
              text = subtitle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp)
        }
      }
}

/** M3U/M3U8 URL import dialog with validation, progress and error feedback. */
@Composable
private fun ImportM3UDialog(
    onDismiss: () -> Unit,
    onImport: (String, (Result<Pair<String, Int>>) -> Unit) -> Unit,
) {
  var url by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val context = LocalContext.current

  AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      shape = RoundedCornerShape(28.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      icon = { DialogHeaderBadge(icon = Icons.Filled.Link) },
      title = { Text(stringResource(R.string.vpl_import_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = url,
              onValueChange = {
                url = it
                error = null
              },
              label = { Text(stringResource(R.string.vpl_import_url_label)) },
              placeholder = { Text("https://…") },
              singleLine = true,
              enabled = !busy,
              isError = error != null,
              shape = RoundedCornerShape(16.dp),
              colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  focusedLabelColor = MaterialTheme.colorScheme.primary),
              trailingIcon = {
                if (url.isNotEmpty() && !busy) {
                  IconButton(onClick = { url = "" }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Clear URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              },
              supportingText = {
                Text(
                    text = error ?: stringResource(R.string.vpl_import_hint),
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
              },
              modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        DialogConfirmButton(
            label = if (busy) stringResource(R.string.vpl_importing) else stringResource(R.string.vpl_import_confirm),
            enabled = !busy && url.isNotBlank(),
            onClick = {
              busy = true
              error = null
              onImport(url.trim()) { result ->
                busy = false
                result
                    .onSuccess { (name, count) ->
                      Toast.makeText(
                          context,
                          context.getString(R.string.vpl_imported, name, count),
                          Toast.LENGTH_SHORT).show()
                      onDismiss()
                    }
                    .onFailure {
                      error = it.message ?: context.getString(R.string.vpl_import_failed)
                    }
              }
            })
      },
      dismissButton = {
        DialogCancelButton(label = stringResource(R.string.vpl_cancel), onClick = { if (!busy) onDismiss() })
      })
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
            label = { Text(stringResource(R.string.vpl_name_label)) },
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
      dismissButton = { DialogCancelButton(label = stringResource(R.string.vpl_cancel), onClick = onDismiss) })
}
