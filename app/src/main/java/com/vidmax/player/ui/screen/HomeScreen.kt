package com.vidmax.player.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vidmax.player.R
import com.vidmax.player.data.model.FolderItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.AddToPlaylistDialog
import com.vidmax.player.ui.components.SortViewOptionsSheet
import com.vidmax.player.ui.components.FolderPickerDialog
import com.vidmax.player.ui.components.MetaChip
import com.vidmax.player.ui.components.DialogCancelButton
import com.vidmax.player.ui.components.DialogConfirmButton
import com.vidmax.player.ui.components.DialogHeaderBadge
import com.vidmax.player.ui.components.SelectionBottomBar
import com.vidmax.player.ui.selection.VideoSelection
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.RenameConsentRequiredException
import com.vidmax.player.viewmodel.SortOrder
import java.io.File

enum class HomeViewStyle {
  LIST,
  GRID_MEDIUM,
  GRID_LARGE
}

// 🔥 UPDATE: Added 2 new modes for the segmented button
enum class HomeContentMode {
  VIDEO,
  FOLDER,
  FAVORITES,
  PLAYLISTS
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    onSettingsClick: () -> Unit
) {
  val context = LocalContext.current
  val prefs: SharedPreferences = remember {
    context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
  }

  val videos by viewModel.filteredVideos.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val hasPermission by viewModel.hasPermission.collectAsState()
  val libraryError by viewModel.libraryError.collectAsState()

  val recentVideoPath by viewModel.recentVideoPath.collectAsState()

  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var showAddToPlaylistDialog by remember { mutableStateOf(false) }
  // Path-keyed selection (REX-inspired): MediaStore ids change on every
  // rescan, so ids silently broke selection across refreshes. Paths are
  // stable, survive recomposition + refresh, and resolve against the
  // current list on every read.
  var selection by remember { mutableStateOf(VideoSelection()) }
  val inSelectionMode = selection.isInSelectionMode
  // Copy/Move destination picker: "copy", "move", or null when closed.
  var folderPickerMode by remember { mutableStateOf<String?>(null) }
  var pickerBusy by remember { mutableStateOf(false) }
  var pickerError by remember { mutableStateOf<String?>(null) }
  var detailsVideo by remember { mutableStateOf<VideoItem?>(null) }
  var topOverflowOpen by remember { mutableStateOf(false) }
  val openedVideoPlaylist by viewModel.openedVideoPlaylist.collectAsState()
  var isVideoSearchOpen by rememberSaveable { mutableStateOf(false) }
  var folderSearchPath by rememberSaveable { mutableStateOf<String?>(null) }
  var isPlaylistSearchOpen by rememberSaveable { mutableStateOf(false) }

  // Resume (continue watching) action — lives in the top bar next to Search
  // so it can never overlap the playlist Create button.
  val resumeLastVideo = {
    var targetIndex = videos.indexOfFirst { it.path == recentVideoPath }
    if (targetIndex == -1 && recentVideoPath.isNotEmpty()) {
      val recentFileName = File(recentVideoPath).name
      targetIndex = videos.indexOfFirst { File(it.path).name == recentFileName }
    }
    if (targetIndex == -1) targetIndex = 0
    onVideoClick(videos, targetIndex)
  }

  var currentViewStyle by remember {
    val savedStyle =
        prefs.getString("home_view_style", HomeViewStyle.LIST.name) ?: HomeViewStyle.LIST.name
    mutableStateOf(HomeViewStyle.valueOf(savedStyle))
  }

  // Scroll states for the Videos list layouts; the Last played bar shows
  // only while the visible list is scrolled back to the very top.
  val videoListState = rememberLazyListState()
  val videoGridState = rememberLazyGridState()
  val videoLargeListState = rememberLazyListState()
  val videoListAtTop by remember {
    derivedStateOf {
      when (currentViewStyle) {
        HomeViewStyle.LIST -> videoListState.firstVisibleItemIndex == 0 &&
            videoListState.firstVisibleItemScrollOffset == 0
        HomeViewStyle.GRID_MEDIUM -> videoGridState.firstVisibleItemIndex == 0 &&
            videoGridState.firstVisibleItemScrollOffset == 0
        HomeViewStyle.GRID_LARGE -> videoLargeListState.firstVisibleItemIndex == 0 &&
            videoLargeListState.firstVisibleItemScrollOffset == 0
      }
    }
  }

  var currentContentMode by remember {
    val savedMode =
        prefs.getString("home_content_mode", HomeContentMode.VIDEO.name)
            ?: HomeContentMode.VIDEO.name
    mutableStateOf(
        try {
          HomeContentMode.valueOf(savedMode)
        } catch (e: IllegalArgumentException) {
          HomeContentMode.VIDEO
        })
  }

  val folders by viewModel.folders.collectAsState()
  val folderVideos by viewModel.folderVideos.collectAsState()
  val currentFolderPath by viewModel.currentFolderPath.collectAsState()
  val isInsideFolder = currentFolderPath.isNotEmpty()

  // Resolved against every visible video list (Videos tab + open folder)
  // so one selection system serves all browsing screens; paths are unique.
  val selectedVideos = remember(selection, videos, folderVideos) {
    (selection.getSelected(videos) + selection.getSelected(folderVideos))
        .distinctBy { it.path }
  }

  val deleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
              Toast.makeText(context, context.getString(R.string.home_toast_selected_deleted), Toast.LENGTH_SHORT)
                  .show()
              selection = selection.clear()
            } else {
              Toast.makeText(context, context.getString(R.string.home_toast_delete_cancelled), Toast.LENGTH_SHORT).show()
            }
          }

  if (showAddToPlaylistDialog) {
    AddToPlaylistDialog(
        viewModel = viewModel,
        videos = selectedVideos,
        onDismiss = {
          showAddToPlaylistDialog = false
          selection = selection.clear()
        })
  }

  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
          DialogHeaderBadge(
              icon = Icons.Rounded.DeleteOutline,
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              contentColor = MaterialTheme.colorScheme.error)
        },
        title = { Text(stringResource(R.string.home_delete_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
          Text(
              stringResource(R.string.home_delete_message, selectedVideos.size))
        },
        confirmButton = {
          DialogConfirmButton(
              label = stringResource(R.string.home_delete_confirm),
              danger = true,
              onClick = {
                showDeleteConfirmDialog = false
                val targets = selectedVideos
                if (targets.isEmpty()) {
                  selection = selection.clear()
                } else if (viewModel.hasFullStorageAccess()) {
                  // All-files access: direct delete, no consent dialog.
                  viewModel.deleteVideos(targets) { result ->
                    result.onSuccess { count ->
                      Toast.makeText(context, context.resources.getQuantityString(R.plurals.home_deleted_count, count, count), Toast.LENGTH_SHORT)
                          .show()
                      selection = selection.clear()
                    }.onFailure {
                      Toast.makeText(context, it.message ?: context.getString(R.string.home_delete_failed), Toast.LENGTH_SHORT)
                          .show()
                    }
                  }
                } else {
                val urisToDelete =
                    targets.mapNotNull { video ->
                      getVideoUriFromPathForMulti(context, video.path)
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && urisToDelete.isNotEmpty()) {
                  val pendingIntent =
                      MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                  deleteLauncher.launch(
                      IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                  var deletedCount = 0
                  targets.forEach { video ->
                    val path = video.path
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                      deletedCount++
                    } else {
                      val uri = getVideoUriFromPathForMulti(context, path)
                      if (uri != null) {
                        val rows = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) deletedCount++
                      }
                    }
                  }
                  Toast.makeText(context, context.resources.getQuantityString(R.plurals.home_deleted_count, deletedCount, deletedCount), Toast.LENGTH_SHORT)
                      .show()
                  selection = selection.clear()
                }
                }
              })
        },
        dismissButton = {
          DialogCancelButton(label = stringResource(R.string.home_cancel), onClick = { showDeleteConfirmDialog = false })
        })
  }

  // Telegram community promo: top-bar icon stays available forever; the
  // first-launch invitation shows only until it has been handled once.
  var showTelegramSheet by remember { mutableStateOf(false) }
  var showTelegramPromo by remember {
    mutableStateOf(!prefs.getBoolean("telegram_promo_dismissed", false))
  }
  fun dismissTelegramPromo() {
    prefs.edit().putBoolean("telegram_promo_dismissed", true).apply()
    showTelegramPromo = false
  }
  if (showTelegramPromo || showTelegramSheet) {
    TelegramPromoSheet(
        onJoin = {
          dismissTelegramPromo()
          showTelegramSheet = false
          openTelegramCommunity(context)
        },
        onDismiss = {
          if (showTelegramPromo) dismissTelegramPromo()
          showTelegramSheet = false
        })
  }

  val sortOrder by viewModel.sortOrder.collectAsState()
  val sortAscending by viewModel.sortAscending.collectAsState()
  val isRefreshing by viewModel.isRefreshing.collectAsState()
  var showSortViewSheet by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
  var renameError by remember { mutableStateOf<String?>(null) }
  var renameBusy by remember { mutableStateOf(false) }
  var pendingRename by remember { mutableStateOf<Pair<VideoItem, String>?>(null) }

  fun succeedRename() {
    renameBusy = false
    renameTarget = null
    renameError = null
    pendingRename = null
    // REX renameSelected: exit selection mode after a successful op.
    selection = selection.clear()
    Toast.makeText(context, context.getString(R.string.home_toast_renamed), Toast.LENGTH_SHORT).show()
  }

  fun failRename(message: String?) {
    renameBusy = false
    renameError = message ?: context.getString(R.string.home_rename_failed)
  }

  val renameWriteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingRename
            pendingRename = null
            if (result.resultCode == Activity.RESULT_OK && pending != null) {
              viewModel.renameVideo(pending.first, pending.second) { retryResult ->
                retryResult.onSuccess { succeedRename() }.onFailure { failRename(it.message) }
              }
            } else {
              failRename(context.getString(R.string.home_rename_cancelled))
            }
          }

  fun handleRenameResult(target: VideoItem, base: String, result: Result<String>) {
    result
        .onSuccess { succeedRename() }
        .onFailure {
          if (it is RenameConsentRequiredException &&
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingRename = target to base
            val pendingIntent =
                MediaStore.createWriteRequest(context.contentResolver, it.uris)
            renameWriteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build())
          } else {
            failRename(it.message)
          }
        }
  }

  var gridColumnsOverride by remember { mutableIntStateOf(prefs.getInt("home_grid_columns", 0)) }

  fun setGridColumns(value: Int) {
    gridColumnsOverride = value
    prefs.edit().putInt("home_grid_columns", value).apply()
  }

  fun performDeleteRequest(video: VideoItem) {
    if (viewModel.hasFullStorageAccess()) {
      // All-files access: direct delete, no "Allow VidMax to delete?" prompt.
      viewModel.deleteVideo(video) { result ->
        result.onSuccess {
          Toast.makeText(context, context.getString(R.string.home_toast_video_deleted), Toast.LENGTH_SHORT).show()
        }.onFailure {
          Toast.makeText(context, it.message ?: context.getString(R.string.home_delete_failed), Toast.LENGTH_SHORT).show()
        }
      }
      return
    }
    val uri = getVideoUriFromPathForMulti(context, video.path)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri != null) {
      val pendingIntent =
          MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
      deleteLauncher.launch(
          IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    } else {
      val deleted =
          if (File(video.path).exists()) File(video.path).delete()
          else
              getVideoUriFromPathForMulti(context, video.path)?.let { u ->
                context.contentResolver.delete(u, null, null) > 0
              } ?: false
      Toast.makeText(
              context,
              if (deleted) context.getString(R.string.home_toast_video_deleted) else context.getString(R.string.home_delete_failed),
              Toast.LENGTH_SHORT)
          .show()
    }
  }

  if (showSortViewSheet) {
    SortViewOptionsSheet(
        sortOrder = sortOrder,
        sortAscending = sortAscending,
        viewStyle = currentViewStyle,
        gridColumns = gridColumnsOverride,
        onSort = { order, ascending -> viewModel.setSort(order, ascending) },
        onViewStyle = { style ->
          currentViewStyle = style
          prefs.edit().putString("home_view_style", style.name).apply()
        },
        onGridColumns = { cols -> setGridColumns(cols) },
        onRefresh = { viewModel.refreshVideos() },
        onDismiss = { showSortViewSheet = false })
  }

  renameTarget?.let { target ->
    RenameVideoDialog(
        currentBaseName =
            File(target.path).nameWithoutExtension.ifEmpty { target.title },
        extension = File(target.path).extension.ifEmpty { "mp4" },
        error = renameError,
        busy = renameBusy,
        onDismiss = {
          if (!renameBusy) {
            renameTarget = null
            renameError = null
          }
        },
        onConfirm = { base ->
          renameBusy = true
          renameError = null
          viewModel.renameVideo(target, base) { result ->
            handleRenameResult(target, base, result)
          }
        })
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier =
            Modifier.align(Alignment.TopCenter)
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                .padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(6.dp))

      if (inSelectionMode) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selection = selection.clear() }) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_close_custom),
                      contentDescription = "Close",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }
                Text(
                    text = stringResource(R.string.home_selection_count, selection.selectedCount, videos.size),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                      // REX playSelected: play the selection as a queue, then exit mode.
                      if (selectedVideos.isNotEmpty()) {
                        onVideoClick(selectedVideos, 0)
                        selection = selection.clear()
                      }
                    }) {
                      Icon(
                          imageVector = Icons.Filled.PlayArrow,
                          contentDescription = "Play selected",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                if (selection.isSingleSelection) {
                  IconButton(
                      onClick = { detailsVideo = selectedVideos.firstOrNull() }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                }
                Box {
                  IconButton(onClick = { topOverflowOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                  }
                  DropdownMenu(
                      expanded = topOverflowOpen,
                      onDismissRequest = { topOverflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_menu_share)) },
                            leadingIcon = {
                              Icon(
                                  painter = painterResource(id = R.drawable.ic_share_custom),
                                  contentDescription = null,
                                  modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                              topOverflowOpen = false
                              val uris =
                                  selectedVideos
                                      .mapNotNull { video ->
                                        getVideoUriFromPathForMulti(context, video.path)
                                      }
                                      .toCollection(ArrayList())
                              if (uris.isNotEmpty()) {
                                val intent =
                                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                      type = "video/*"
                                      putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(intent, context.resources.getQuantityString(R.plurals.home_share_count, uris.size, uris.size)))
                                selection = selection.clear()
                              }
                            })
                        DropdownMenuItem(
                            text = {
                              Text(
                                  if (selection.selectedCount == videos.size) stringResource(R.string.home_menu_deselect_all)
                                  else stringResource(R.string.home_menu_select_all))
                            },
                            leadingIcon = {
                              Icon(
                                  painter = painterResource(id = R.drawable.ic_select_all),
                                  contentDescription = null,
                                  modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                              topOverflowOpen = false
                              selection =
                                  if (selection.selectedCount == videos.size) selection.clear()
                                  else selection.selectAll(videos.map { it.path })
                            })
                      }
                }
              }
            }
      } else {
        // Inside a folder or playlist detail, the screen shows only its own
        // back button + title — hide the home header and category toggle.
        if (!isInsideFolder && openedVideoPlaylist == null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Text(
                  text = when (currentContentMode) {
                      HomeContentMode.VIDEO -> stringResource(R.string.home_title_videos)
                      HomeContentMode.FOLDER -> stringResource(R.string.home_title_folders)
                      HomeContentMode.FAVORITES -> stringResource(R.string.home_title_recent)
                      HomeContentMode.PLAYLISTS -> stringResource(R.string.home_title_playlists)
                  },
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.ExtraBold)

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                      if (currentContentMode == HomeContentMode.PLAYLISTS) {
                        isPlaylistSearchOpen = true
                      } else {
                        folderSearchPath = null
                        isVideoSearchOpen = true
                      }
                    },
                    modifier = Modifier.size(36.dp)) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_search),
                      contentDescription = "Search",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = { showSortViewSheet = true }, modifier = Modifier.size(36.dp)) {
                  Icon(
                      imageVector = Icons.Filled.Tune,
                      contentDescription = "Sort & View Options",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(24.dp))
                }

                IconButton(
                    onClick = { showTelegramSheet = true },
                    modifier = Modifier.size(36.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_telegram),
                          contentDescription = "Join VidMax on Telegram",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }

                IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                  Icon(
                      imageVector = Icons.Filled.Settings,
                      contentDescription = "Settings",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }
              }
            }
        
        // 🔥 UPDATE: 4-Segmented Button Area
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier.fillMaxWidth() // Made it full width to fit 4 items comfortably
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(4.dp)) {
                  
                  val segmentWidth = maxWidth / 4f // 4 options now
                  val indicatorOffset by
                      animateDpAsState(
                          targetValue =
                              when (currentContentMode) {
                                  HomeContentMode.VIDEO -> 0.dp
                                  HomeContentMode.FOLDER -> segmentWidth
                                  HomeContentMode.FAVORITES -> segmentWidth * 2
                                  HomeContentMode.PLAYLISTS -> segmentWidth * 3
                              },
                          animationSpec = spring(
                              dampingRatio = Spring.DampingRatioMediumBouncy,
                              stiffness = Spring.StiffnessLow
                          ),
                          label = "contentModeIndicator")

                  Box(
                      modifier =
                          Modifier.offset(x = indicatorOffset)
                              .width(segmentWidth)
                              .fillMaxHeight()
                              .clip(RoundedCornerShape(50))
                              .background(MaterialTheme.colorScheme.primary))

                  Row(modifier = Modifier.fillMaxSize()) {
                    HomeContentSegment(
                        label = stringResource(R.string.home_segment_video),
                        isActive = currentContentMode == HomeContentMode.VIDEO,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.VIDEO) {
                            currentContentMode = HomeContentMode.VIDEO
                            viewModel.closeFolder()
                            selection = selection.clear()
                            prefs.edit().putString("home_content_mode", HomeContentMode.VIDEO.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(painterResource(id = R.drawable.ic_video_library), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = stringResource(R.string.home_segment_folder),
                        isActive = currentContentMode == HomeContentMode.FOLDER,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.FOLDER) {
                            currentContentMode = HomeContentMode.FOLDER
                            viewModel.closeFolder()
                            selection = selection.clear()
                            prefs.edit().putString("home_content_mode", HomeContentMode.FOLDER.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(painterResource(id = R.drawable.ic_folder), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = stringResource(R.string.home_segment_recent),
                        isActive = currentContentMode == HomeContentMode.FAVORITES,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.FAVORITES) {
                            currentContentMode = HomeContentMode.FAVORITES
                            viewModel.closeFolder()
                            selection = selection.clear()
                            prefs.edit().putString("home_content_mode", HomeContentMode.FAVORITES.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(Icons.Default.History, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = stringResource(R.string.home_segment_playlists),
                        isActive = currentContentMode == HomeContentMode.PLAYLISTS,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.PLAYLISTS) {
                            currentContentMode = HomeContentMode.PLAYLISTS
                            viewModel.closeFolder()
                            selection = selection.clear()
                            prefs.edit().putString("home_content_mode", HomeContentMode.PLAYLISTS.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(painterResource(id = R.drawable.ic_playlist), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                  }
                }
        }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      PullToRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = { viewModel.refreshVideos() },
          modifier = Modifier.fillMaxWidth().weight(1f)) {
        Crossfade(targetState = isLoading, animationSpec = tween(400), label = "loadingAnim") {
            loading ->
        when {
          loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
          !hasPermission -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  text = stringResource(R.string.home_storage_permission),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 15.sp,
                  lineHeight = 22.sp,
                  textAlign = TextAlign.Center)
            }
          }
          videos.isEmpty() && !(currentContentMode == HomeContentMode.FOLDER && folders.isNotEmpty()) -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.padding(24.dp)) {
                Text(
                    text =
                        if (libraryError != null) libraryError!!
                        else if (searchQuery.isNotEmpty()) stringResource(R.string.home_no_videos_match, searchQuery)
                        else stringResource(R.string.home_no_videos_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center)
                if (libraryError != null && hasPermission) {
                  Button(onClick = { viewModel.refreshVideos() }) { Text(stringResource(R.string.home_retry)) }
                }
              }
            }
          }
          else -> {
            Crossfade(
                targetState = currentContentMode,
                animationSpec = tween(400),
                label = "homeContentAnim") { mode ->
                  when (mode) {
                    HomeContentMode.VIDEO -> {
                      // existing video logic...
                      Column(modifier = Modifier.fillMaxSize()) {
                        val lastPlayedVideoIndex =
                            remember(videos, recentVideoPath) {
                              videos.indexOfFirst { it.path == recentVideoPath }
                            }
                        val lastVideo = videos.getOrNull(lastPlayedVideoIndex)
                        if (lastVideo != null) {
                          ContinueWatchingPill(
                              title = lastVideo.title,
                              visible = lastPlayedVideoIndex >= 0 && videoListAtTop,
                              onResume = { resumeLastVideo() },
                              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                        }
                      Crossfade(
                          targetState = currentViewStyle,
                          animationSpec = tween(400),
                          modifier = Modifier.weight(1f),
                          label = "videoViewAnim") { style ->
                            when (style) {
                              HomeViewStyle.LIST -> {
                                LazyColumn(
                                    state = videoListState,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 130.dp)) {
                                      itemsIndexed(
                                          items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selection.isSelected(video.path)
                                        PremiumVideoListCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            size = viewModel.formatSize(video.size),
                                            resolution = viewModel.getResolutionLabel(video.width, video.height),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selection = selection.toggle(video.path)
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = {
                                              // REX handleLongClick: long-press enters
                                              // selection mode instead of opening the menu.
                                              selection = selection.toggle(video.path)
                                            })
                                        }
                                    }
                              }
                              HomeViewStyle.GRID_MEDIUM -> {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                  val autoColumns =
                                      (maxWidth / 170.dp).toInt().coerceIn(2, 12)
                                  val gridColumns =
                                      if (gridColumnsOverride == 0) autoColumns
                                      else gridColumnsOverride.coerceIn(1, 12)
                                  LazyVerticalGrid(
                                      state = videoGridState,
                                      columns = GridCells.Fixed(gridColumns),
                                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                                      verticalArrangement = Arrangement.spacedBy(12.dp),
                                      contentPadding = PaddingValues(bottom = 130.dp)) {
                                        itemsIndexed(
                                            items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selection.isSelected(video.path)
                                        CustomVideoGridCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selection = selection.toggle(video.path)
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = {
                                              selection = selection.toggle(video.path)
                                            })
                                      }
                                    }
                                }
                              }
                              HomeViewStyle.GRID_LARGE -> {
                                LazyColumn(
                                    state = videoLargeListState,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 130.dp)) {
                                      itemsIndexed(
                                          items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selection.isSelected(video.path)
                                        CustomVideoLargeCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            size = viewModel.formatSize(video.size),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selection = selection.toggle(video.path)
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = {
                                              selection = selection.toggle(video.path)
                                            })
                                      }
                                    }
                              }
                            }
                          }
                      }
                    }
                    HomeContentMode.FOLDER -> {
                      // existing folder logic...
                      if (isInsideFolder) {
                        val folderName: String =
                            folders.firstOrNull { it.path == currentFolderPath }?.name ?: stringResource(R.string.home_folder_fallback)
                        Column(modifier = Modifier.fillMaxSize()) {
                          Row(
                              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                              verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selection = selection.clear(); viewModel.closeFolder() }) {
                                  Icon(
                                      imageVector = Icons.Default.ArrowBack,
                                      contentDescription = "Back",
                                      tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                  Text(
                                      text = folderName,
                                      color = MaterialTheme.colorScheme.onBackground,
                                      fontSize = 16.sp,
                                      fontWeight = FontWeight.Bold,
                                      maxLines = 1,
                                      overflow = TextOverflow.Ellipsis)
                                  Text(
                                      text = pluralStringResource(R.plurals.home_videos_count, folderVideos.size, folderVideos.size),
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = {
                                      folderSearchPath = currentFolderPath
                                      isVideoSearchOpen = true
                                    },
                                    modifier = Modifier.size(36.dp)) {
                                  Icon(
                                      painter = painterResource(id = R.drawable.ic_search),
                                      contentDescription = "Search in folder",
                                      tint = MaterialTheme.colorScheme.primary,
                                      modifier = Modifier.size(24.dp))
                                }
                              }

                          val lastPlayedIndex =
                              remember(folderVideos, recentVideoPath) {
                                folderVideos.indexOfFirst { it.path == recentVideoPath }
                              }
                          if (lastPlayedIndex >= 0) {
                            val lastVideo = folderVideos[lastPlayedIndex]
                            ContinueWatchingPill(
                                title = lastVideo.title,
                                visible = true,
                                onResume = { onVideoClick(folderVideos, lastPlayedIndex) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                          }

                          if (folderVideos.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center) {
                              Text(
                                  text = stringResource(R.string.home_folder_empty),
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  fontSize = 15.sp)
                            }
                          } else {
                          Crossfade(
                              targetState = currentViewStyle,
                              animationSpec = tween(400),
                              label = "folderVideoViewAnim") { style ->
                                when (style) {
                                  HomeViewStyle.LIST -> {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 130.dp)) {
                                          itemsIndexed(
                                              items = folderVideos,
                                              key = { _, video -> video.id }) { index, video ->
                                            PremiumVideoListCard(
                                                video = video,
                                                duration = viewModel.formatDuration(video.duration),
                                                size = viewModel.formatSize(video.size),
                                                resolution = viewModel.getResolutionLabel(video.width, video.height),
                                                isSelected = selection.isSelected(video.path),
                                                onClick = {
                                                  if (inSelectionMode) selection = selection.toggle(video.path)
                                                  else onVideoClick(folderVideos, index)
                                                },
                                                onLongClick = { selection = selection.toggle(video.path) })
                                          }
                                        }
                                  }
                                  HomeViewStyle.GRID_MEDIUM -> {
                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                      val autoColumns =
                                          (maxWidth / 170.dp).toInt().coerceIn(2, 12)
                                      val gridColumns =
                                          if (gridColumnsOverride == 0) autoColumns
                                          else gridColumnsOverride.coerceIn(1, 12)
                                      LazyVerticalGrid(
                                          columns = GridCells.Fixed(gridColumns),
                                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                                          verticalArrangement = Arrangement.spacedBy(12.dp),
                                          contentPadding = PaddingValues(bottom = 130.dp)) {
                                            itemsIndexed(
                                                items = folderVideos,
                                                key = { _, video -> video.id }) { index, video ->
                                              CustomVideoGridCard(
                                                  video = video,
                                                  duration = viewModel.formatDuration(video.duration),
                                                  isSelected = selection.isSelected(video.path),
                                                  onClick = {
                                                    if (inSelectionMode) selection = selection.toggle(video.path)
                                                    else onVideoClick(folderVideos, index)
                                                  },
                                                  onLongClick = { selection = selection.toggle(video.path) })
                                            }
                                          }
                                    }
                                  }
                                  HomeViewStyle.GRID_LARGE -> {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(bottom = 130.dp)) {
                                          itemsIndexed(
                                              items = folderVideos,
                                              key = { _, video -> video.id }) { index, video ->
                                            CustomVideoLargeCard(
                                                video = video,
                                                duration = viewModel.formatDuration(video.duration),
                                                size = viewModel.formatSize(video.size),
                                                isSelected = selection.isSelected(video.path),
                                                onClick = {
                                                  if (inSelectionMode) selection = selection.toggle(video.path)
                                                  else onVideoClick(folderVideos, index)
                                                },
                                                onLongClick = { selection = selection.toggle(video.path) })
                                          }
                                        }
                                  }
                                }
                              }
                        }
                        }
                      } else {
                        Crossfade(
                            targetState = currentViewStyle,
                            animationSpec = tween(400),
                            label = "folderViewAnim") { style ->
                              when (style) {
                                HomeViewStyle.LIST -> {
                                  LazyColumn(
                                      verticalArrangement = Arrangement.spacedBy(10.dp),
                                      contentPadding = PaddingValues(bottom = 130.dp)) {
                                        itemsIndexed(
                                            items = folders,
                                            key = { _, folder -> folder.path }) { _, folder ->
                                          HomeFolderListCard(
                                              folder = folder,
                                              onClick = { selection = selection.clear(); viewModel.openFolder(folder.path) })
                                        }
                                      }
                                }
                                HomeViewStyle.GRID_MEDIUM -> {
                                  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val autoColumns =
                                        (maxWidth / 170.dp).toInt().coerceIn(2, 12)
                                    val gridColumns =
                                        if (gridColumnsOverride == 0) autoColumns
                                        else gridColumnsOverride.coerceIn(1, 12)
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(gridColumns),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(bottom = 130.dp)) {
                                          itemsIndexed(
                                              items = folders,
                                              key = { _, folder -> folder.path }) { _, folder ->
                                            HomeFolderGridCard(
                                                folder = folder,
                                                onClick = { selection = selection.clear(); viewModel.openFolder(folder.path) })
                                          }
                                        }
                                  }
                                }
                                HomeViewStyle.GRID_LARGE -> {
                                  LazyColumn(
                                      verticalArrangement = Arrangement.spacedBy(16.dp),
                                      contentPadding = PaddingValues(bottom = 130.dp)) {
                                        itemsIndexed(
                                            items = folders,
                                            key = { _, folder -> folder.path }) { _, folder ->
                                          HomeFolderLargeCard(
                                              folder = folder,
                                              onClick = { selection = selection.clear(); viewModel.openFolder(folder.path) })
                                        }
                                      }
                                }
                              }
                            }
                      }
                    }
                    
                    // Recent Play tab (REX-style): the FAVORITES destination
                    // now shows recently played videos. Favorites data and
                    // the heart toggle stay intact, only the destination changed.
                    HomeContentMode.FAVORITES -> {
                        VideoRecentContent(
                            viewModel = viewModel,
                            selection = selection,
                            onSelectionChange = { selection = it },
                            onPlayVideos = onVideoClick)
                    }
                    HomeContentMode.PLAYLISTS -> {
                        VideoPlaylistsContent(
                            viewModel = viewModel,
                            selection = selection,
                            onSelectionChange = { selection = it },
                            onPlayVideos = onVideoClick,
                            onDeleteRequest = { performDeleteRequest(it) })
                    }
                  }
                }
          }
          }
        }
      }
    }

    // ── REX-style selection overlay: floating bottom action bar ──────────
    // Pure overlay above the untouched bottom navigation; visible only in
    // selection mode (AnimatedVisibility exit plays on clear).
    SelectionBottomBar(
        visible = inSelectionMode,
        isSingleSelection = selection.isSingleSelection,
        onCopyClick = {
          pickerError = null
          folderPickerMode = "copy"
        },
        onMoveClick = {
          pickerError = null
          folderPickerMode = "move"
        },
        onRenameClick = {
          selectedVideos.firstOrNull()?.let {
            renameTarget = it
            renameError = null
          }
        },
        onAddToPlaylistClick = {
          if (selectedVideos.isNotEmpty()) showAddToPlaylistDialog = true
        },
        onDeleteClick = { showDeleteConfirmDialog = true },
        modifier = Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 92.dp))
  }

  BackHandler(enabled = inSelectionMode) {
    selection = selection.clear()
  }

  BackHandler(enabled = isVideoSearchOpen) {
    isVideoSearchOpen = false
    folderSearchPath = null
  }

  // ── Copy/Move destination picker (REX CopyPasteDialog destination role,
  // VidMax folder-list look). Batch ops verify every file, then scan once
  // and refresh; selection clears on success like REX onOperationComplete.
  folderPickerMode?.let { mode ->
    val isCopy = mode == "copy"
    FolderPickerDialog(
        title = if (isCopy) stringResource(R.string.home_copy_to_folder) else stringResource(R.string.home_move_to_folder),
        folders = folders,
        busy = pickerBusy,
        error = pickerError,
        emptyText = stringResource(R.string.home_no_folders),
        onFolderClick = { folder ->
          val targets = selectedVideos
          if (targets.isEmpty()) {
            folderPickerMode = null
            selection = selection.clear()
          } else {
            pickerBusy = true
            pickerError = null
            if (isCopy) {
              viewModel.copyVideosToFolder(targets, folder.path) { result ->
                pickerBusy = false
                result.onSuccess { r ->
                  folderPickerMode = null
                  selection = selection.clear()
                  val skipNote = if (r.skipped > 0) context.getString(R.string.home_copied_skipped_suffix, r.skipped) else ""
                  Toast.makeText(
                          context,
                          context.resources.getQuantityString(R.plurals.home_copied_count, r.newPaths.size, r.newPaths.size, skipNote),
                          Toast.LENGTH_SHORT)
                      .show()
                }.onFailure {
                  pickerError = it.message ?: context.getString(R.string.home_copy_failed)
                }
              }
            } else {
              viewModel.moveVideosToFolder(targets, folder.path) { result ->
                pickerBusy = false
                result.onSuccess { r ->
                  folderPickerMode = null
                  selection = selection.clear()
                  val skipNote = if (r.skipped > 0) context.getString(R.string.home_moved_already_suffix, r.skipped) else ""
                  Toast.makeText(
                          context,
                          context.resources.getQuantityString(R.plurals.home_moved_count, r.newPaths.size, r.newPaths.size, skipNote),
                          Toast.LENGTH_SHORT)
                      .show()
                }.onFailure {
                  pickerError = it.message ?: context.getString(R.string.home_move_failed)
                }
              }
            }
          }
        },
        onDismiss = {
          if (!pickerBusy) {
            folderPickerMode = null
            pickerError = null
          }
        })
  }

  // REX top-bar Info action: rich details for the single selected video.
  detailsVideo?.let { v ->
    VideoDetailsDialog(video = v, onDismiss = { detailsVideo = null })
  }

  if (isVideoSearchOpen) {
    SearchScreen(
        scope = SearchScope.VIDEOS,
        viewModel = viewModel,
        folderPath = folderSearchPath,
        onBack = {
          isVideoSearchOpen = false
          folderSearchPath = null
        },
        onPlayVideos = { videos, index -> onVideoClick(videos, index) },
        onDeleteVideo = { performDeleteRequest(it) })
  }

  if (isPlaylistSearchOpen) {
    SearchScreen(
        scope = SearchScope.PLAYLISTS,
        viewModel = viewModel,
        onBack = { isPlaylistSearchOpen = false },
        onOpenPlaylist = { entry ->
          isPlaylistSearchOpen = false
          viewModel.openVideoPlaylist(entry.playlist.id)
        })
  }

  BackHandler(enabled = isPlaylistSearchOpen) {
    isPlaylistSearchOpen = false
  }
}

// ... [PremiumVideoListCard, CustomVideoGridCard, CustomVideoLargeCard, getVideoUriFromPathForMulti - same as before] ...

/**
 * Modern Material 3 "Continue Watching" pill replacing the old Last Played
 * banner. Spring expand/shrink + fade keeps scroll show/hide fluid instead
 * of snapping.
 */
@Composable
private fun ContinueWatchingPill(
    title: String,
    visible: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
  AnimatedVisibility(
      visible = visible,
      enter = fadeIn(animationSpec = tween(300)) + expandVertically(
          animationSpec = spring(
              dampingRatio = Spring.DampingRatioLowBouncy,
              stiffness = Spring.StiffnessMediumLow)),
      exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(
          animationSpec = spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = Spring.StiffnessMediumLow)),
      modifier = modifier) {
    Surface(
        onClick = onResume,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp))
              }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = stringResource(R.string.home_continue_watching),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium)
              Text(
                  text = title,
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  modifier = Modifier.basicMarquee())
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer) {
              Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_resume),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp))
              }
            }
          }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun PremiumVideoListCard(
    video: VideoItem,
    duration: String,
    size: String,
    resolution: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
  val folderName = File(video.path).parentFile?.name ?: stringResource(R.string.home_unknown_folder)

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .shadow(if (isSelected) 4.dp else 0.dp, RoundedCornerShape(14.dp))
              .clip(RoundedCornerShape(14.dp))
              .background(
                  if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
              .border(
                  width = 1.5.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  shape = RoundedCornerShape(14.dp))
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(width = 110.dp, height = 64.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)) {
              
              GlideImage(
                  model = File(video.path),
                  contentDescription = "Thumbnail",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
              ) { requestBuilder ->
                  requestBuilder
                      .diskCacheStrategy(DiskCacheStrategy.ALL)
                      .override(300) 
              }

              Text(
                  text = duration,
                  color = Color.White,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier =
                      Modifier.align(Alignment.BottomEnd)
                          .padding(4.dp)
                          .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                          .padding(horizontal = 4.dp, vertical = 1.dp))
            }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = video.title,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)

          Spacer(modifier = Modifier.height(6.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                  Text(
                      text = resolution,
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold)
                }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.home_meta_format, size, folderName),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }

        if (isSelected) {
          Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(end = 4.dp).size(20.dp))
        }
      }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun CustomVideoGridCard(
    video: VideoItem,
    duration: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(12.dp))
              .clip(RoundedCornerShape(12.dp))
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .border(
                  1.5.dp,
                  if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  RoundedCornerShape(12.dp)),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column {
          Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.DarkGray)) {
            
            GlideImage(
                model = File(video.path),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) { requestBuilder ->
                requestBuilder
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(400) 
            }

            Text(
                text = duration,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp))

            if (isSelected) {
              Box(
                  modifier =
                      Modifier.fillMaxSize()
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
              Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier =
                      Modifier.align(Alignment.TopEnd)
                          .padding(6.dp)
                          .background(MaterialTheme.colorScheme.primary, CircleShape)
                          .padding(4.dp)
                          .size(16.dp))
            }
          }

          Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = video.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis)
          }
        }
      }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun CustomVideoLargeCard(
    video: VideoItem,
    duration: String,
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
  val folderName = File(video.path).parentFile?.name ?: stringResource(R.string.home_unknown_folder)

  Card(
      modifier =
          Modifier.fillMaxWidth()
              .shadow(if (isSelected) 10.dp else 4.dp, RoundedCornerShape(16.dp))
              .clip(RoundedCornerShape(16.dp))
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .border(
                  2.dp,
                  if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  RoundedCornerShape(16.dp)),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
          Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.DarkGray)) {
            
            GlideImage(
                model = File(video.path),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) { requestBuilder ->
                requestBuilder
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(600) 
            }

            if (isSelected) {
              Box(
                  modifier =
                      Modifier.fillMaxSize()
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
              Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier =
                      Modifier.align(Alignment.TopEnd)
                          .padding(12.dp)
                          .background(MaterialTheme.colorScheme.primary, CircleShape)
                          .padding(4.dp))
            }
          }

          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = video.title,
                      fontWeight = FontWeight.Bold,
                      fontSize = 16.sp,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)) {
                          Text(
                              text = duration,
                              color = MaterialTheme.colorScheme.onPrimaryContainer,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold)
                        }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_meta_format, size, folderName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }

                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center) {
                      Icon(
                          imageVector = Icons.Default.PlayArrow,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(24.dp))
                    }
              }
        }
      }
}

fun getVideoUriFromPathForMulti(context: Context, path: String): Uri? {
  val cursor =
      context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          arrayOf(MediaStore.Video.Media._ID),
          MediaStore.Video.Media.DATA + "=?",
          arrayOf(path),
          null)
  return cursor?.use {
    if (it.moveToFirst()) {
      val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
      ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    } else null
  }
}

@Composable
fun HomeContentSegment(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color, Float) -> Unit
) {
  // Same animation design as MusicScreen's TabItem: animated tint plus a
  // low-bouncy spring icon pop when the segment becomes active.
  val contentColor by
      animateColorAsState(
          targetValue =
              if (isActive) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
          animationSpec = tween(200),
          label = "homeSegmentColor")

  val iconScale by animateFloatAsState(
      targetValue = if (isActive) 1.15f else 1.0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
      label = "homeSegmentScale")

  Row(
      modifier =
          modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically) {
        icon(contentColor, iconScale)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
}

@Composable
fun HomeFolderListCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      onClick = onClick,
      modifier = modifier
          .fillMaxWidth()
          .heightIn(min = 72.dp, max = 76.dp),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(
          1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Box(
              modifier =
                  Modifier.size(46.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(
                          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
              }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = folderMetaLabel(folder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp))
          }

          Spacer(modifier = Modifier.width(8.dp))

          Icon(
              imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
              contentDescription = "Open folder",
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(22.dp))
        }
  }
}

@Composable
private fun folderMetaLabel(folder: FolderItem): String {
  val count = pluralStringResource(R.plurals.home_videos_count, folder.videoCount, folder.videoCount)
  val size = formatCompactSize(folder.totalSize)
  return if (size.isNotEmpty()) stringResource(R.string.home_meta_format, count, size) else count
}

private fun formatCompactSize(bytes: Long): String {
  if (bytes <= 0) return ""
  return when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%d KB".format(bytes / 1_024)
    else -> "$bytes B"
  }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeFolderGridCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(
          1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))) {
        Column {
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(16f / 9f)
                  .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
              contentAlignment = Alignment.Center) {
                if (folder.firstVideoPath.isNotEmpty()) {
                  GlideImage(
                      model = File(folder.firstVideoPath),
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()) { requestBuilder ->
                        requestBuilder
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(400)
                      }
                } else {
                  Box(
                      modifier =
                          Modifier.size(52.dp)
                              .clip(RoundedCornerShape(14.dp))
                              .background(
                                  MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                      }
                }
                if (folder.firstVideoPath.isNotEmpty()) {
                  Surface(
                      shape = RoundedCornerShape(50),
                      color = Color.Black.copy(alpha = 0.55f),
                      modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                          imageVector = Icons.Rounded.Folder,
                          contentDescription = null,
                          tint = Color.White,
                          modifier = Modifier.size(14.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                          text = "${folder.videoCount}",
                          color = Color.White,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }

          Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folderMetaLabel(folder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }
      }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeFolderLargeCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(
          1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))) {
        Column(modifier = Modifier.padding(12.dp)) {
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(16f / 9f)
                  .clip(RoundedCornerShape(14.dp))
                  .background(
                      Brush.verticalGradient(
                          0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                          1f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))),
              contentAlignment = Alignment.Center) {
                if (folder.firstVideoPath.isNotEmpty()) {
                  GlideImage(
                      model = File(folder.firstVideoPath),
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()) { requestBuilder ->
                        requestBuilder
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(600)
                      }
                  Box(
                      modifier = Modifier.fillMaxSize().background(
                          Brush.verticalGradient(
                              0f to Color.Transparent,
                              1f to Color.Black.copy(alpha = 0.45f))))
                } else {
                  Box(
                      modifier =
                          Modifier.size(64.dp)
                              .clip(RoundedCornerShape(16.dp))
                              .background(
                                  MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FolderCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp))
                      }
                }
              }

          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = folder.name,
                      style = MaterialTheme.typography.titleMedium,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                  Spacer(modifier = Modifier.height(6.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val countLabel =
                        pluralStringResource(R.plurals.home_videos_count, folder.videoCount, folder.videoCount)
                    MetaChip(text = countLabel, highlighted = true)
                    val sizeLabel = formatCompactSize(folder.totalSize)
                    if (sizeLabel.isNotEmpty()) MetaChip(text = sizeLabel)
                  }
                }

                Spacer(modifier = Modifier.width(12.dp))

                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(48.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary)) {
                  Icon(
                      imageVector = Icons.Default.PlayArrow,
                      contentDescription = "Open folder",
                      modifier = Modifier.size(24.dp))
                }
              }
        }
      }
}

