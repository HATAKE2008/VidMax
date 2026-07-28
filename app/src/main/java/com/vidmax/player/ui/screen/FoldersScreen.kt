package com.vidmax.player.ui.screen

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.FolderItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.FolderCard
import com.vidmax.player.ui.components.VideoCard
import com.vidmax.player.viewmodel.LibraryViewModel

// ভিউ স্টাইল ট্র্যাক করার জন্য Enum
enum class FolderViewStyle {
  LIST,
  GRID_MEDIUM,
  GRID_LARGE
}

@Composable
fun FoldersScreen(viewModel: LibraryViewModel, onVideoClick: (List<VideoItem>, Int) -> Unit) {
  val context = LocalContext.current
  val prefs: SharedPreferences = remember {
    context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
  }

  val folders by viewModel.folders.collectAsState()
  val folderVideos by viewModel.folderVideos.collectAsState()
  val currentFolderPath by viewModel.currentFolderPath.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()

  val isInsideFolder: Boolean = currentFolderPath.isNotEmpty()

  var currentViewStyle by remember {
    val savedStyle =
        prefs.getString("folder_view_style", FolderViewStyle.LIST.name) ?: FolderViewStyle.LIST.name
    mutableStateOf(FolderViewStyle.valueOf(savedStyle))
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (isInsideFolder) {
      val folderName: String =
          folders.firstOrNull { it.path == currentFolderPath }?.name ?: "Folder"

      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.closeFolder() }) {
              Icon(
                  imageVector = Icons.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
              Text(
                  text = folderName,
                  color = MaterialTheme.colorScheme.onBackground,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)
              Text(
                  text = "${folderVideos.size} videos",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.sp)
            }
          }

      LazyColumn(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(items = folderVideos, key = { _: Int, video: VideoItem -> video.id }) {
                index: Int,
                video: VideoItem ->
              VideoCard(
                  video = video,
                  duration = viewModel.formatDuration(video.duration),
                  size = viewModel.formatSize(video.size),
                  resolution = viewModel.getResolutionLabel(video.width, video.height),
                  onClick = { onVideoClick(folderVideos, index) })
            }
          }
    } else {
      Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Column {
                Text(
                    text = "Folders",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Text(
                    text = "${folders.size} folders",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
              }

              // 🔥 ফিক্স: Box আর surfaceVariant ব্যাকগ্রাউন্ড মুছে দিয়ে ক্লিন IconButton বানানো
              // হয়েছে
              IconButton(
                  onClick = {
                    val newStyle =
                        when (currentViewStyle) {
                          FolderViewStyle.LIST -> FolderViewStyle.GRID_MEDIUM
                          FolderViewStyle.GRID_MEDIUM -> FolderViewStyle.GRID_LARGE
                          FolderViewStyle.GRID_LARGE -> FolderViewStyle.LIST
                        }
                    currentViewStyle = newStyle
                    prefs.edit().putString("folder_view_style", newStyle.name).apply()
                  },
                  modifier = Modifier.size(40.dp)) {
                    Crossfade(targetState = currentViewStyle, label = "iconAnim") { style ->
                      val iconRes =
                          when (style) {
                            FolderViewStyle.LIST -> R.drawable.ic_view_list_custom
                            FolderViewStyle.GRID_MEDIUM -> R.drawable.ic_view_grid_custom
                            FolderViewStyle.GRID_LARGE -> R.drawable.ic_view_list_custom
                          }
                      Icon(
                          painter = painterResource(id = iconRes),
                          contentDescription = "Change View",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(24.dp))
                    }
                  }
            }

        when {
          isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
          folders.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  text = "No folders found.",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 15.sp)
            }
          }
          else -> {
            Crossfade(
                targetState = currentViewStyle,
                animationSpec = tween(400),
                label = "folderViewAnim") { style ->
                  when (style) {
                    FolderViewStyle.LIST -> {
                      LazyColumn(
                          verticalArrangement = Arrangement.spacedBy(8.dp),
                          contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(
                                items = folders,
                                key = { _: Int, folder: FolderItem -> folder.path }) {
                                    _: Int,
                                    folder: FolderItem ->
                                  FolderCard(
                                      folder = folder,
                                      totalSize = viewModel.formatSize(folder.totalSize),
                                      onClick = { viewModel.openFolder(folder.path) })
                                }
                          }
                    }
                    FolderViewStyle.GRID_MEDIUM -> {
                      LazyVerticalGrid(
                          columns = GridCells.Fixed(2),
                          horizontalArrangement = Arrangement.spacedBy(12.dp),
                          verticalArrangement = Arrangement.spacedBy(12.dp),
                          contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(
                                items = folders,
                                key = { _: Int, folder: FolderItem -> folder.path }) {
                                    _: Int,
                                    folder: FolderItem ->
                                  CustomFolderGridCard(
                                      folder = folder,
                                      totalSize = viewModel.formatSize(folder.totalSize)) {
                                        viewModel.openFolder(folder.path)
                                      }
                                }
                          }
                    }
                    FolderViewStyle.GRID_LARGE -> {
                      LazyColumn(
                          verticalArrangement = Arrangement.spacedBy(16.dp),
                          contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(
                                items = folders,
                                key = { _: Int, folder: FolderItem -> folder.path }) {
                                    _: Int,
                                    folder: FolderItem ->
                                  CustomFolderLargeCard(
                                      folder = folder,
                                      totalSize = viewModel.formatSize(folder.totalSize)) {
                                        viewModel.openFolder(folder.path)
                                      }
                                }
                          }
                    }
                  }
                }
          }
        }
      }
    }
  }
}

@Composable
fun CustomFolderGridCard(folder: FolderItem, totalSize: String, onClick: () -> Unit) {
  Card(
      modifier =
          Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)).clickable { onClick() },
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                  painter = painterResource(id = R.drawable.ic_folder),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(64.dp))
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                  text = folder.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)
              Text(
                  text = totalSize,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 4.dp))
            }
      }
}

@Composable
fun CustomFolderLargeCard(folder: FolderItem, totalSize: String, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(120.dp)
              .shadow(8.dp, RoundedCornerShape(20.dp))
              .clip(RoundedCornerShape(20.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Box(
                  modifier =
                      Modifier.size(80.dp)
                          .background(
                              MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                              RoundedCornerShape(16.dp)),
                  contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp))
                  }
              Spacer(modifier = Modifier.width(20.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Size: $totalSize",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
      }
}
