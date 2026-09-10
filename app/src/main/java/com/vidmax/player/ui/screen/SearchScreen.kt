package com.vidmax.player.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.data.model.NetworkFile
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.PlaylistWithCount
import java.io.File
import kotlinx.coroutines.delay

enum class SearchScope {
  VIDEOS,
  MUSIC,
  NETWORK,
  PLAYLISTS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    scope: SearchScope,
    viewModel: LibraryViewModel,
    networkFiles: List<NetworkFile> = emptyList(),
    folderPath: String? = null,
    onBack: () -> Unit,
    onPlayVideos: (List<VideoItem>, Int) -> Unit = { _, _ -> },
    onDeleteVideo: (VideoItem) -> Unit = {},
    onPlayAudio: (List<AudioItem>, Int) -> Unit = { _, _ -> },
    onPlayNetworkFile: (NetworkFile) -> Unit = {},
    onOpenNetworkFolder: (NetworkFile) -> Unit = {},
    onOpenPlaylist: (PlaylistWithCount) -> Unit = {},
) {
  val history by viewModel.searchHistory.collectAsState()
  val libraryTick by viewModel.filteredVideos.collectAsState()
  val audioTick by viewModel.filteredAudio.collectAsState()
  val playingPath by viewModel.recentlyPlayedPath.collectAsState()
  val audioPlaying by viewModel.isAudioPlaying.collectAsState()

  var query by rememberSaveable(scope, folderPath) { mutableStateOf("") }
  var visibleQuery by rememberSaveable(scope, folderPath) { mutableStateOf("") }
  var menuVideo by remember { mutableStateOf<VideoItem?>(null) }
  var showClearHistoryConfirm by remember { mutableStateOf(false) }

  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val imeVisible = WindowInsets.isImeVisible

  BackHandler {
    if (imeVisible) keyboard?.hide()
    else onBack()
  }

  LaunchedEffect(Unit) {
      delay(100L)
      runCatching {
          focusRequester.requestFocus()
          keyboard?.show()
      }
  }

  LaunchedEffect(query) {
    delay(150)
    visibleQuery = query
  }

  val context = LocalContext.current
  val prefs = remember {
    context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
  }
  val viewStyleName =
      prefs.getString("home_view_style", HomeViewStyle.LIST.name) ?: HomeViewStyle.LIST.name
  val viewStyle = try {
    HomeViewStyle.valueOf(viewStyleName)
  } catch (e: IllegalArgumentException) {
    HomeViewStyle.LIST
  }
  val gridColumnsPref = prefs.getInt("home_grid_columns", 0)

  val trimmed = visibleQuery.trim()
  val inFolder = scope == SearchScope.VIDEOS && !folderPath.isNullOrEmpty()
  val videoResults = remember(trimmed, libraryTick, scope, folderPath) {
    if (scope == SearchScope.VIDEOS) {
      if (!folderPath.isNullOrEmpty()) viewModel.searchFolderVideos(trimmed, folderPath)
      else viewModel.searchVideos(trimmed)
    } else emptyList()
  }
  val audioResults = remember(trimmed, audioTick, scope) {
    if (scope == SearchScope.MUSIC) viewModel.searchAudio(trimmed) else emptyList()
  }
  val networkResults = remember(trimmed, networkFiles, scope) {
    if (scope == SearchScope.NETWORK && trimmed.isNotEmpty()) {
      networkFiles.filter { it.name.contains(trimmed, ignoreCase = true) }
    } else emptyList()
  }
  val allPlaylists by viewModel.videoPlaylists.collectAsState()
  val playlistResults = remember(trimmed, allPlaylists, scope) {
    if (scope == SearchScope.PLAYLISTS && trimmed.isNotEmpty()) {
      allPlaylists.filter { it.playlist.name.contains(trimmed, ignoreCase = true) }
    } else emptyList()
  }
  val resultCount = when (scope) {
    SearchScope.VIDEOS -> videoResults.size
    SearchScope.MUSIC -> audioResults.size
    SearchScope.NETWORK -> networkResults.size
    SearchScope.PLAYLISTS -> playlistResults.size
  }
  val isTyping = query != visibleQuery

  fun submit(raw: String) {
    val q = raw.trim()
    if (q.isEmpty()) return
    viewModel.addSearchHistory(q)
    query = q
    visibleQuery = q
    keyboard?.hide()
  }

  val hint = when (scope) {
    SearchScope.VIDEOS -> if (inFolder) "Search in folder…" else "Search videos…"
    SearchScope.MUSIC -> "Search songs or artists…"
    SearchScope.NETWORK -> "Search this folder…"
    SearchScope.PLAYLISTS -> "Search playlists…"
  }

  VideoActionMenuHost(
      viewModel = viewModel,
      video = menuVideo,
      onPlay = { video ->
        val index = videoResults.indexOfFirst { it.id == video.id }
        if (index >= 0) onPlayVideos(videoResults, index)
        menuVideo = null
      },
      onDeleteRequest = {
        menuVideo = null
        onDeleteVideo(it)
      },
      onDismiss = { menuVideo = null })

  if (showClearHistoryConfirm) {
    AlertDialog(
        onDismissRequest = { showClearHistoryConfirm = false },
        title = { Text("Clear search history?", fontWeight = FontWeight.Bold) },
        text = { Text("All recent searches will be removed from this device.") },
        confirmButton = {
          TextButton(
              onClick = {
                viewModel.clearSearchHistory()
                showClearHistoryConfirm = false
              }) {
                Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
        })
  }

  Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 1100.dp)
                // .statusBarsPadding() সরানো হয়েছে কারণ প্যারেন্ট Scaffold ইতিমধ্যেই প্যাডিং দিচ্ছে
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp)) {

              // ── Top bar: Sleek compact Search Bar ──
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = 4.dp, bottom = 4.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)) {
                      Icon(
                          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                          Text(
                              text = hint,
                              fontSize = 15.sp,
                              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        },
                        leadingIcon = {
                          Icon(
                              painter = painterResource(id = R.drawable.ic_search),
                              contentDescription = "Search",
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                          if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = ""; visibleQuery = "" },
                                modifier = Modifier.size(32.dp)) {
                              Icon(
                                  imageVector = Icons.Filled.Close,
                                  contentDescription = "Clear search",
                                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                  modifier = Modifier.size(18.dp))
                            }
                          }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .focusRequester(focusRequester))
                  }

              if (isTyping) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(top = 2.dp))
              }
              Spacer(modifier = Modifier.height(4.dp))

              if (trimmed.isEmpty()) {
                if (history.isEmpty()) {
                  // ── Intro empty state ──
                  Column(
                      modifier = Modifier.fillMaxSize().padding(32.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.Center) {
                        Box(
                            modifier = Modifier.size(68.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center) {
                              Icon(
                                  imageVector = Icons.Filled.Search,
                                  contentDescription = null,
                                  tint = MaterialTheme.colorScheme.primary,
                                  modifier = Modifier.size(32.dp))
                            }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search your library",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Find videos, music and folders",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center)
                      }
                } else {
                  // ── History ──
                  Row(
                      modifier = Modifier.fillMaxWidth()
                          .padding(vertical = 4.dp, horizontal = 4.dp),
                      verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recent searches",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { showClearHistoryConfirm = true }) {
                          Text(
                              text = "Clear",
                              color = MaterialTheme.colorScheme.primary,
                              fontSize = 13.sp,
                              fontWeight = FontWeight.Bold)
                        }
                      }
                  LazyColumn(
                      modifier = Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(bottom = 24.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            items = history, key = { _, item -> item }) { _, item ->
                          Row(
                              modifier = Modifier.fillMaxWidth()
                                  .clip(RoundedCornerShape(12.dp))
                                  .background(
                                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                  .clickable { submit(item) }
                                  .padding(horizontal = 14.dp, vertical = 12.dp),
                              verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_search),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.removeSearchHistoryEntry(item) },
                                    modifier = Modifier.size(32.dp)) {
                                      Icon(
                                          imageVector = Icons.Filled.Close,
                                          contentDescription = "Remove search",
                                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                          modifier = Modifier.size(18.dp))
                                    }
                              }
                        }
                      }
                }
              } else if (resultCount == 0) {
                // ── No results ──
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                      Box(
                          modifier = Modifier.size(68.dp)
                              .clip(CircleShape)
                              .background(
                                  MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                          contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp))
                          }
                      Spacer(modifier = Modifier.height(16.dp))
                      Text(
                          text = "No results found",
                          color = MaterialTheme.colorScheme.onBackground,
                          fontSize = 17.sp,
                          fontWeight = FontWeight.Bold,
                          textAlign = TextAlign.Center)
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(
                          text = "Try a different search term.",
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          fontSize = 14.sp,
                          textAlign = TextAlign.Center)
                    }
              } else {
                // ── Results ──
                Text(
                    text = "$resultCount result${if (resultCount == 1) "" else "s"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                when (scope) {
                  SearchScope.VIDEOS -> {
                    when (viewStyle) {
                      HomeViewStyle.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                              itemsIndexed(
                                  items = videoResults,
                                  key = { _, video -> video.id }) { index, video ->
                                PremiumVideoListCard(
                                    video = video,
                                    duration = viewModel.formatDuration(video.duration),
                                    size = viewModel.formatSize(video.size),
                                    resolution =
                                        viewModel.getResolutionLabel(video.width, video.height),
                                    isSelected = false,
                                    onClick = { onPlayVideos(videoResults, index) },
                                    onLongClick = { menuVideo = video })
                              }
                            }
                      }
                      HomeViewStyle.GRID_MEDIUM -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                          val autoColumns = (maxWidth / 170.dp).toInt().coerceIn(2, 12)
                          val gridColumns =
                              if (gridColumnsPref == 0) autoColumns
                              else gridColumnsPref.coerceIn(1, 12)
                          LazyVerticalGrid(
                              columns = GridCells.Fixed(gridColumns),
                              horizontalArrangement = Arrangement.spacedBy(12.dp),
                              verticalArrangement = Arrangement.spacedBy(12.dp),
                              contentPadding = PaddingValues(bottom = 24.dp)) {
                                gridItemsIndexed(
                                    items = videoResults,
                                    key = { _, video -> video.id }) { index, video ->
                                  CustomVideoGridCard(
                                      video = video,
                                      duration = viewModel.formatDuration(video.duration),
                                      isSelected = false,
                                      onClick = { onPlayVideos(videoResults, index) },
                                      onLongClick = { menuVideo = video })
                                }
                              }
                        }
                      }
                      HomeViewStyle.GRID_LARGE -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                              itemsIndexed(
                                  items = videoResults,
                                  key = { _, video -> video.id }) { index, video ->
                                CustomVideoLargeCard(
                                    video = video,
                                    duration = viewModel.formatDuration(video.duration),
                                    size = viewModel.formatSize(video.size),
                                    isSelected = false,
                                    onClick = { onPlayVideos(videoResults, index) },
                                    onLongClick = { menuVideo = video })
                              }
                            }
                      }
                    }
                  }
                  SearchScope.MUSIC -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                          itemsIndexed(
                              items = audioResults,
                              key = { _, audio -> audio.id }) { index, audio ->
                            AudioCard(
                                audio = audio,
                                duration = viewModel.formatDuration(audio.duration),
                                isSelected = false,
                                isPlayingNow = audio.path == playingPath,
                                isAudioPlayingState = audioPlaying,
                                onClick = { onPlayAudio(audioResults, index) })
                          }
                        }
                  }
                  SearchScope.NETWORK -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                          itemsIndexed(
                              items = networkResults,
                              key = { _, file -> file.path }) { _, file ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.5f))
                                    .clickable {
                                      if (file.isDirectory) onOpenNetworkFolder(file)
                                      else onPlayNetworkFile(file)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                  Icon(
                                      imageVector =
                                          if (file.isDirectory) Icons.Filled.Folder
                                          else Icons.Filled.MusicNote,
                                      contentDescription = null,
                                      tint = MaterialTheme.colorScheme.primary,
                                      modifier = Modifier.size(22.dp))
                                  Spacer(modifier = Modifier.width(12.dp))
                                  Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = File(file.path).parent ?: file.path,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                  }
                                }
                          }
                        }
                  }
                  SearchScope.PLAYLISTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                          itemsIndexed(
                              items = playlistResults,
                              key = { _, entry -> entry.playlist.id }) { _, entry ->
                            PlaylistCard(
                                name = entry.playlist.name,
                                count = entry.itemCount,
                                onClick = { onOpenPlaylist(entry) },
                                onLongClick = { onOpenPlaylist(entry) })
                          }
                        }
                  }
                }
              }
            }
      }
}
