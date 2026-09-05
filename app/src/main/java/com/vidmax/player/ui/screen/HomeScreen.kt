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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

  var selectedVideoIds by remember { mutableStateOf(setOf<Long>()) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var showAddToPlaylistDialog by remember { mutableStateOf(false) }
  val openedVideoPlaylist by viewModel.openedVideoPlaylist.collectAsState()
  var isVideoSearchOpen by rememberSaveable { mutableStateOf(false) }
  var folderSearchPath by rememberSaveable { mutableStateOf<String?>(null) }
  val inSelectionMode = selectedVideoIds.isNotEmpty()

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

  val deleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
              Toast.makeText(context, "Selected videos deleted successfully", Toast.LENGTH_SHORT)
                  .show()
              selectedVideoIds = emptySet()
            } else {
              Toast.makeText(context, "Delete Cancelled", Toast.LENGTH_SHORT).show()
            }
          }

  if (showAddToPlaylistDialog) {
    AddToPlaylistDialog(
        viewModel = viewModel,
        videos = videos.filter { selectedVideoIds.contains(it.id) },
        onDismiss = {
          showAddToPlaylistDialog = false
          selectedVideoIds = emptySet()
        })
  }

  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = { Text("Delete Videos", fontWeight = FontWeight.Bold) },
        text = {
          Text(
              "Are you sure you want to delete ${selectedVideoIds.size} selected videos? This action cannot be undone.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                val urisToDelete =
                    selectedVideoIds.mapNotNull { id ->
                      val path = videos.find { it.id == id }?.path ?: return@mapNotNull null
                      getVideoUriFromPathForMulti(context, path)
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && urisToDelete.isNotEmpty()) {
                  val pendingIntent =
                      MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                  deleteLauncher.launch(
                      IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                  var deletedCount = 0
                  selectedVideoIds.forEach { id ->
                    val path = videos.find { it.id == id }?.path ?: return@forEach
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
                  Toast.makeText(context, "$deletedCount video(s) deleted", Toast.LENGTH_SHORT)
                      .show()
                  selectedVideoIds = emptySet()
                }
              }) {
                Text(
                    "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  // Unified long-press video menu (Play, Rename, Share, Favorite,
  // Add to Playlist, Details, Delete) shared by Videos/Search/Folders.
  var menuVideo by remember { mutableStateOf<VideoItem?>(null) }

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
  var showSortMenu by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
  var renameError by remember { mutableStateOf<String?>(null) }
  var renameBusy by remember { mutableStateOf(false) }
  var pendingRename by remember { mutableStateOf<Pair<VideoItem, String>?>(null) }

  fun succeedRename() {
    renameBusy = false
    renameTarget = null
    renameError = null
    pendingRename = null
    Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
  }

  fun failRename(message: String?) {
    renameBusy = false
    renameError = message ?: "Rename failed"
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
              failRename("Rename cancelled")
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
              if (deleted) "Video deleted" else "Delete failed",
              Toast.LENGTH_SHORT)
          .show()
    }
  }

  val playMenuVideo: (VideoItem) -> Unit = { video ->
    val inVideos = videos.indexOfFirst { it.id == video.id }
    if (inVideos >= 0) {
      onVideoClick(videos, inVideos)
    } else {
      val inFolder = folderVideos.indexOfFirst { it.id == video.id }
      if (inFolder >= 0) onVideoClick(folderVideos, inFolder)
      else Toast.makeText(context, "Video not available", Toast.LENGTH_SHORT).show()
    }
    menuVideo = null
  }

  VideoActionMenuHost(
      viewModel = viewModel,
      video = menuVideo,
      onPlay = playMenuVideo,
      onDeleteRequest = { performDeleteRequest(it) },
      onDismiss = { menuVideo = null })

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
                IconButton(onClick = { selectedVideoIds = emptySet() }) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_close_custom),
                      contentDescription = "Close",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }
                Text(
                    text = "${selectedVideoIds.size} Selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
              }
              Row {
                IconButton(
                    onClick = {
                      selectedVideoIds =
                          if (selectedVideoIds.size == videos.size) emptySet()
                          else videos.map { it.id }.toSet()
                    }) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_select_all),
                          contentDescription = "Select All",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                IconButton(
                    onClick = {
                      val uris =
                          selectedVideoIds
                              .mapNotNull { id ->
                                val path =
                                    videos.find { it.id == id }?.path ?: return@mapNotNull null
                                getVideoUriFromPathForMulti(context, path)
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
                            Intent.createChooser(intent, "Share ${uris.size} Videos"))
                        selectedVideoIds = emptySet()
                      }
                    }) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_share_custom),
                          contentDescription = "Share",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                IconButton(
                    onClick = {
                      val selected = videos.filter { selectedVideoIds.contains(it.id) }
                      if (selected.isNotEmpty()) {
                        showAddToPlaylistDialog = true
                      }
                    }) {
                      Icon(
                          imageVector = Icons.Filled.PlaylistAdd,
                          contentDescription = "Add to Playlist",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                IconButton(
                    onClick = {
                      val selected = videos.filter { selectedVideoIds.contains(it.id) }
                      val allFavorite =
                          selected.isNotEmpty() &&
                              selected.all { viewModel.favoriteVideoPaths.value.contains(it.path) }
                      selected.forEach { video ->
                        if (allFavorite == viewModel.favoriteVideoPaths.value.contains(video.path)) {
                          viewModel.toggleVideoFavorite(video.path)
                        }
                      }
                    }) {
                      Icon(
                          imageVector = Icons.Filled.Favorite,
                          contentDescription = "Toggle Favorite",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                if (selectedVideoIds.size == 1) {
                  IconButton(
                      onClick = {
                        videos.find { it.id == selectedVideoIds.first() }?.let {
                          renameTarget = it
                          renameError = null
                        }
                      }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                }
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_delete_custom),
                      contentDescription = "Delete",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(24.dp))
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
                      HomeContentMode.VIDEO -> "Videos"
                      HomeContentMode.FOLDER -> "Folders"
                      HomeContentMode.FAVORITES -> "Favorites"
                      HomeContentMode.PLAYLISTS -> "Playlists"
                  },
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.ExtraBold)

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { folderSearchPath = null; isVideoSearchOpen = true }, modifier = Modifier.size(36.dp)) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_search),
                      contentDescription = "Search",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }

                // Continue-watching button in the top bar (moved here so it
                // can never overlap the playlist Create button).
                if (videos.isNotEmpty()) {
                  IconButton(onClick = resumeLastVideo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Continue Watching",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                  }
                }

                Box {
                  IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp))
                  }
                  DropdownMenu(
                      expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        HomeSortItem(
                            label = "Newest first",
                            checked = sortOrder == SortOrder.DATE && !sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.DATE, false)
                              showSortMenu = false
                            })
                        HomeSortItem(
                            label = "Oldest first",
                            checked = sortOrder == SortOrder.DATE && sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.DATE, true)
                              showSortMenu = false
                            })
                        HomeSortItem(
                            label = "Name A-Z",
                            checked = sortOrder == SortOrder.NAME && sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.NAME, true)
                              showSortMenu = false
                            })
                        HomeSortItem(
                            label = "Name Z-A",
                            checked = sortOrder == SortOrder.NAME && !sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.NAME, false)
                              showSortMenu = false
                            })
                        HomeSortItem(
                            label = "Largest first",
                            checked = sortOrder == SortOrder.SIZE && !sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.SIZE, false)
                              showSortMenu = false
                            })
                        HomeSortItem(
                            label = "Longest first",
                            checked = sortOrder == SortOrder.DURATION && !sortAscending,
                            onClick = {
                              viewModel.setSort(SortOrder.DURATION, false)
                              showSortMenu = false
                            })
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        val columnOptions = listOf(0, 2, 3, 4, 6, 12)
                        columnOptions.forEach { cols ->
                          DropdownMenuItem(
                              text = {
                                Text(
                                    if (cols == 0) "Grid: Auto" else "Grid: $cols columns")
                              },
                              trailingIcon = {
                                if (gridColumnsOverride == cols) {
                                  Icon(
                                      imageVector = Icons.Filled.Check,
                                      contentDescription = null,
                                      tint = MaterialTheme.colorScheme.primary)
                                }
                              },
                              onClick = {
                                setGridColumns(cols)
                                showSortMenu = false
                              })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = {
                              Icon(
                                  imageVector = Icons.Filled.Refresh,
                                  contentDescription = null)
                            },
                            onClick = {
                              viewModel.refreshVideos()
                              showSortMenu = false
                            })
                      }
                }

                IconButton(
                    onClick = {
                      val newStyle =
                          when (currentViewStyle) {
                            HomeViewStyle.LIST -> HomeViewStyle.GRID_MEDIUM
                            HomeViewStyle.GRID_MEDIUM -> HomeViewStyle.GRID_LARGE
                            HomeViewStyle.GRID_LARGE -> HomeViewStyle.LIST
                          }
                      currentViewStyle = newStyle
                      prefs.edit().putString("home_view_style", newStyle.name).apply()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp).size(36.dp)) {
                      Crossfade(targetState = currentViewStyle, label = "iconAnim") { style ->
                        val iconRes =
                            when (style) {
                              HomeViewStyle.LIST -> R.drawable.ic_view_list_custom
                              HomeViewStyle.GRID_MEDIUM -> R.drawable.ic_view_grid_custom
                              HomeViewStyle.GRID_LARGE -> R.drawable.ic_view_list_custom
                            }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = "Change View",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
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
                        label = "Video",
                        isActive = currentContentMode == HomeContentMode.VIDEO,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.VIDEO) {
                            currentContentMode = HomeContentMode.VIDEO
                            viewModel.closeFolder()
                            prefs.edit().putString("home_content_mode", HomeContentMode.VIDEO.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(painterResource(id = R.drawable.ic_video_library), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = "Folder",
                        isActive = currentContentMode == HomeContentMode.FOLDER,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.FOLDER) {
                            currentContentMode = HomeContentMode.FOLDER
                            viewModel.closeFolder()
                            selectedVideoIds = emptySet()
                            prefs.edit().putString("home_content_mode", HomeContentMode.FOLDER.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(painterResource(id = R.drawable.ic_folder), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = "Favs",
                        isActive = currentContentMode == HomeContentMode.FAVORITES,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.FAVORITES) {
                            currentContentMode = HomeContentMode.FAVORITES
                            viewModel.closeFolder()
                            selectedVideoIds = emptySet()
                            prefs.edit().putString("home_content_mode", HomeContentMode.FAVORITES.name).apply()
                          }
                        }) { tint, scale ->
                      Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).scale(scale))
                    }
                    HomeContentSegment(
                        label = "Playlists",
                        isActive = currentContentMode == HomeContentMode.PLAYLISTS,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = {
                          if (currentContentMode != HomeContentMode.PLAYLISTS) {
                            currentContentMode = HomeContentMode.PLAYLISTS
                            viewModel.closeFolder()
                            selectedVideoIds = emptySet()
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
                  text = "Storage permission required\nto browse videos.",
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
                        else if (searchQuery.isNotEmpty()) "No videos match \"$searchQuery\""
                        else "No videos found on this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center)
                if (libraryError != null && hasPermission) {
                  Button(onClick = { viewModel.refreshVideos() }) { Text("Retry") }
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
                      Crossfade(
                          targetState = currentViewStyle,
                          animationSpec = tween(400),
                          label = "videoViewAnim") { style ->
                            when (style) {
                              HomeViewStyle.LIST -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 130.dp)) {
                                      itemsIndexed(
                                          items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selectedVideoIds.contains(video.id)
                                        PremiumVideoListCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            size = viewModel.formatSize(video.size),
                                            resolution = viewModel.getResolutionLabel(video.width, video.height),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = { menuVideo = video })
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
                                            items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selectedVideoIds.contains(video.id)
                                        CustomVideoGridCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = { menuVideo = video })
                                      }
                                    }
                                }
                              }
                              HomeViewStyle.GRID_LARGE -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 130.dp)) {
                                      itemsIndexed(
                                          items = videos, key = { _, video -> video.id }) {
                                          index,
                                          video ->
                                        val isSelected = selectedVideoIds.contains(video.id)
                                        CustomVideoLargeCard(
                                            video = video,
                                            duration = viewModel.formatDuration(video.duration),
                                            size = viewModel.formatSize(video.size),
                                            isSelected = isSelected,
                                            onClick = {
                                              if (inSelectionMode) {
                                                selectedVideoIds = if (isSelected) selectedVideoIds - video.id else selectedVideoIds + video.id
                                              } else {
                                                onVideoClick(videos, index)
                                              }
                                            },
                                            onLongClick = { menuVideo = video })
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
                            folders.firstOrNull { it.path == currentFolderPath }?.name ?: "Folder"
                        Column(modifier = Modifier.fillMaxSize()) {
                          Row(
                              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                              verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.closeFolder() }) {
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
                                      text = "${folderVideos.size} videos",
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
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .clickable { onVideoClick(folderVideos, lastPlayedIndex) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                  Icon(
                                      imageVector = Icons.Filled.PlayArrow,
                                      contentDescription = null,
                                      tint = MaterialTheme.colorScheme.primary,
                                      modifier = Modifier.size(20.dp))
                                  Spacer(modifier = Modifier.width(8.dp))
                                  Text(
                                      text = "Last played: ${lastVideo.title}",
                                      color = MaterialTheme.colorScheme.onSurface,
                                      fontSize = 13.sp,
                                      fontWeight = FontWeight.SemiBold,
                                      maxLines = 1,
                                      overflow = TextOverflow.Ellipsis,
                                      modifier = Modifier.weight(1f))
                                  Text(
                                      text = "Jump",
                                      color = MaterialTheme.colorScheme.primary,
                                      fontSize = 13.sp,
                                      fontWeight = FontWeight.Bold)
                                }
                          }

                          if (folderVideos.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center) {
                              Text(
                                  text = "This folder is empty.",
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
                                                isSelected = false,
                                                onClick = { onVideoClick(folderVideos, index) },
                                                onLongClick = { menuVideo = video })
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
                                                  isSelected = false,
                                                  onClick = { onVideoClick(folderVideos, index) },
                                                  onLongClick = { menuVideo = video })
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
                                                isSelected = false,
                                                onClick = { onVideoClick(folderVideos, index) },
                                                onLongClick = { menuVideo = video })
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
                                              onClick = { viewModel.openFolder(folder.path) })
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
                                                onClick = { viewModel.openFolder(folder.path) })
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
                                              onClick = { viewModel.openFolder(folder.path) })
                                        }
                                      }
                                }
                              }
                            }
                      }
                    }
                    
                    // 🔥 UPDATE: Favorites & Playlists tabs (mpvRex-style)
                    HomeContentMode.FAVORITES -> {
                        VideoFavoritesContent(
                            viewModel = viewModel,
                            onPlayVideos = onVideoClick,
                            onDeleteRequest = { performDeleteRequest(it) })
                    }
                    HomeContentMode.PLAYLISTS -> {
                        VideoPlaylistsContent(
                            viewModel = viewModel,
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
  }

  BackHandler(enabled = isVideoSearchOpen) {
    isVideoSearchOpen = false
    folderSearchPath = null
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
}

// ... [PremiumVideoListCard, CustomVideoGridCard, CustomVideoLargeCard, getVideoUriFromPathForMulti - same as before] ...

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
  val folderName = File(video.path).parentFile?.name ?: "Unknown"

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
                text = "$size  •  $folderName",
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
  val folderName = File(video.path).parentFile?.name ?: "Unknown"

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
                        text = "$size  •  $folderName",
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
private fun HomeSortItem(label: String, checked: Boolean, onClick: () -> Unit) {
  DropdownMenuItem(
      text = { Text(label) },
      trailingIcon = {
        if (checked) {
          Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary)
        }
      },
      onClick = onClick)
}

@Composable
fun HomeFolderListCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
              .clickable(onClick = onClick)
              .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(width = 110.dp, height = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center) {
              Icon(
                  painter = painterResource(id = R.drawable.ic_folder),
                  contentDescription = "Folder Icon",
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(36.dp)
              )
            }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = folder.name,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)

          Spacer(modifier = Modifier.height(6.dp))

          Text(
              text = "${folder.videoCount} videos",
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium)
        }
      }
}

@Composable
fun HomeFolderGridCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      modifier =
          modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column {
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(16f / 9f)
                  .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = "Folder Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp)
                )
          }

          Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = folder.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${folder.videoCount} videos",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
}

@Composable
fun HomeFolderLargeCard(folder: FolderItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      modifier =
          modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(16f / 9f)
                  .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = "Folder Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(72.dp)
                )
          }

          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text = folder.name,
                      fontWeight = FontWeight.Bold,
                      fontSize = 18.sp,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "${folder.videoCount} videos",
                      fontSize = 14.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant)
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

