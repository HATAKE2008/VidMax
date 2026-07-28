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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.VidMaxSearchBar
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File

enum class HomeViewStyle {
  LIST,
  GRID_MEDIUM,
  GRID_LARGE
}

@OptIn(ExperimentalFoundationApi::class)
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

  val recentVideoPath by viewModel.recentVideoPath.collectAsState()

  var selectedVideoIds by remember { mutableStateOf(setOf<Long>()) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var isSearchExpanded by remember { mutableStateOf(false) }
  val inSelectionMode = selectedVideoIds.isNotEmpty()

  var currentViewStyle by remember {
    val savedStyle =
        prefs.getString("home_view_style", HomeViewStyle.LIST.name) ?: HomeViewStyle.LIST.name
    mutableStateOf(HomeViewStyle.valueOf(savedStyle))
  }

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

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_delete_custom),
                      contentDescription = "Delete",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(24.dp))
                }
              }
            }
      } else if (isSearchExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                  onClick = {
                    isSearchExpanded = false
                    viewModel.setSearchQuery("")
                  }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground)
                  }
              Box(modifier = Modifier.weight(1f)) {
                VidMaxSearchBar(
                    query = searchQuery, onQueryChange = { viewModel.setSearchQuery(it) })
              }
            }
      } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Text(
                  text = "Videos",
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.ExtraBold)

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
                  Icon(
                      imageVector = Icons.Filled.Search,
                      contentDescription = "Search",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
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

                IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                  Icon(
                      imageVector = Icons.Filled.Settings,
                      contentDescription = "Settings",
                      tint = MaterialTheme.colorScheme.onBackground,
                      modifier = Modifier.size(24.dp))
                }
              }
            }
      }

      Spacer(modifier = Modifier.height(4.dp))

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
          videos.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  text =
                      if (searchQuery.isNotEmpty()) "No videos match \"$searchQuery\""
                      else "No videos found on this device.",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 15.sp)
            }
          }
          else -> {
            Crossfade(
                targetState = currentViewStyle,
                animationSpec = tween(400),
                label = "videoViewAnim") { style ->
                  when (style) {
                    HomeViewStyle.LIST -> {
                      LazyColumn(
                          verticalArrangement = Arrangement.spacedBy(10.dp),
                          contentPadding = PaddingValues(bottom = 130.dp)) {
                            itemsIndexed(items = videos, key = { _, video -> video.id }) {
                                index,
                                video ->
                              val isSelected = selectedVideoIds.contains(video.id)
                              PremiumVideoListCard(
                                  video = video,
                                  duration = viewModel.formatDuration(video.duration),
                                  size = viewModel.formatSize(video.size),
                                  resolution =
                                      viewModel.getResolutionLabel(video.width, video.height),
                                  isSelected = isSelected,
                                  onClick = {
                                    if (inSelectionMode) {
                                      selectedVideoIds =
                                          if (isSelected) selectedVideoIds - video.id
                                          else selectedVideoIds + video.id
                                    } else {
                                      onVideoClick(videos, index)
                                    }
                                  },
                                  onLongClick = {
                                    selectedVideoIds =
                                        if (isSelected) selectedVideoIds - video.id
                                        else selectedVideoIds + video.id
                                  })
                            }
                          }
                    }
                    HomeViewStyle.GRID_MEDIUM -> {
                      LazyVerticalGrid(
                          columns = GridCells.Fixed(2),
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                          verticalArrangement = Arrangement.spacedBy(12.dp),
                          contentPadding = PaddingValues(bottom = 130.dp)) {
                            itemsIndexed(items = videos, key = { _, video -> video.id }) {
                                index,
                                video ->
                              val isSelected = selectedVideoIds.contains(video.id)
                              CustomVideoGridCard(
                                  video = video,
                                  duration = viewModel.formatDuration(video.duration),
                                  isSelected = isSelected,
                                  onClick = {
                                    if (inSelectionMode) {
                                      selectedVideoIds =
                                          if (isSelected) selectedVideoIds - video.id
                                          else selectedVideoIds + video.id
                                    } else {
                                      onVideoClick(videos, index)
                                    }
                                  },
                                  onLongClick = {
                                    selectedVideoIds =
                                        if (isSelected) selectedVideoIds - video.id
                                        else selectedVideoIds + video.id
                                  })
                            }
                          }
                    }
                    HomeViewStyle.GRID_LARGE -> {
                      LazyColumn(
                          verticalArrangement = Arrangement.spacedBy(16.dp),
                          contentPadding = PaddingValues(bottom = 130.dp)) {
                            itemsIndexed(items = videos, key = { _, video -> video.id }) {
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
                                      selectedVideoIds =
                                          if (isSelected) selectedVideoIds - video.id
                                          else selectedVideoIds + video.id
                                    } else {
                                      onVideoClick(videos, index)
                                    }
                                  },
                                  onLongClick = {
                                    selectedVideoIds =
                                        if (isSelected) selectedVideoIds - video.id
                                        else selectedVideoIds + video.id
                                  })
                            }
                          }
                    }
                  }
                }
          }
        }
      }
    }

    if (!inSelectionMode && videos.isNotEmpty() && searchQuery.isEmpty()) {
      FloatingActionButton(
          onClick = {
            var targetIndex = videos.indexOfFirst { it.path == recentVideoPath }
            if (targetIndex == -1 && recentVideoPath.isNotEmpty()) {
              val recentFileName = File(recentVideoPath).name
              targetIndex = videos.indexOfFirst { File(it.path).name == recentFileName }
            }
            if (targetIndex == -1) targetIndex = 0
            onVideoClick(videos, targetIndex)
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          shape = RoundedCornerShape(16.dp),
          elevation =
              FloatingActionButtonDefaults.elevation(
                  defaultElevation = 6.dp, pressedElevation = 12.dp),
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(end = 20.dp, bottom = 110.dp)
                  .size(56.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Continue Watching",
                modifier = Modifier.size(28.dp))
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
              
              // 🔥 GLIDE MAGIC: Caches video frames permanently on Disk!
              GlideImage(
                  model = File(video.path),
                  contentDescription = "Thumbnail",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
              ) { requestBuilder ->
                  requestBuilder
                      .diskCacheStrategy(DiskCacheStrategy.ALL)
                      .override(300) // Small size for fast list loading
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
            
            // 🔥 GLIDE MAGIC
            GlideImage(
                model = File(video.path),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) { requestBuilder ->
                requestBuilder
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(400) // Medium size
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
            
            // 🔥 GLIDE MAGIC
            GlideImage(
                model = File(video.path),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) { requestBuilder ->
                requestBuilder
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(600) // Large size
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
