package com.vidmax.player.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.vidmax.player.R
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.ui.components.VidMaxSearchBar
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// --- FAST MEMORY EMBEDDED ART CACHE ENGINE FOR GLIDE ---
private object EmbeddedArtCache {
  private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
  private val cacheSize = maxMemory / 8
  private val memoryCache =
      object : LruCache<String, ByteArray>(cacheSize) {
        override fun sizeOf(key: String, value: ByteArray): Int {
          return value.size / 1024
        }
      }

  fun get(path: String): ByteArray? = memoryCache.get(path)

  fun getOrFetch(context: Context, path: String): ByteArray? {
    if (path.isEmpty()) return null
    get(path)?.let { return it }

    val retriever = MediaMetadataRetriever()
    return try {
      val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
      retriever.setDataSource(context, uri)
      val art = retriever.embeddedPicture

      if (art != null) {
        memoryCache.put(path, art)
      }
      art
    } catch (e: Exception) {
      null
    } finally {
      try {
        retriever.release()
      } catch (e: Exception) {
        // Ignore release errors on older APIs
      }
    }
  }
}

// Playlist Data Model
data class VidMaxPlaylist(val name: String, val paths: List<String>)

fun loadPlaylists(context: Context): List<VidMaxPlaylist> {
  val prefs = context.getSharedPreferences("VidMaxPlaylists", Context.MODE_PRIVATE)
  val jsonString = prefs.getString("playlists_data", "[]") ?: "[]"
  return try {
    val jsonArray = JSONArray(jsonString)
    List(jsonArray.length()) { i ->
      val jsonObj = jsonArray.getJSONObject(i)
      val name = jsonObj.getString("name")
      val pathsArray = jsonObj.getJSONArray("paths")
      val paths = List(pathsArray.length()) { j -> pathsArray.getString(j) }
      VidMaxPlaylist(name, paths)
    }
  } catch (e: Exception) {
    e.printStackTrace()
    emptyList()
  }
}

fun savePlaylists(context: Context, playlists: List<VidMaxPlaylist>) {
  val prefs = context.getSharedPreferences("VidMaxPlaylists", Context.MODE_PRIVATE)
  val jsonArray = JSONArray()
  for (playlist in playlists) {
    val jsonObj = JSONObject()
    jsonObj.put("name", playlist.name)
    val pathsArray = JSONArray()
    for (path in playlist.paths) {
      pathsArray.put(path)
    }
    jsonObj.put("paths", pathsArray)
    jsonArray.put(jsonObj)
  }
  prefs.edit().putString("playlists_data", jsonArray.toString()).apply()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: LibraryViewModel,
    onSettingsClick: () -> Unit,
    onAudioClick: (List<AudioItem>, Int) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMyMix: () -> Unit
) {
  val context = LocalContext.current
  val audioList by viewModel.filteredAudio.collectAsState()
  val searchQuery by viewModel.audioSearchQuery.collectAsState()

  val currentlyPlayingPath by viewModel.recentlyPlayedPath.collectAsState()
  val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
  val favoritePaths by viewModel.favoriteAudioPaths.collectAsState()

  var selectedAudioIds by remember { mutableStateOf(emptySet<Long>()) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  val inSelectionMode = selectedAudioIds.isNotEmpty()
  var isSearchExpanded by remember { mutableStateOf(false) }

  var currentTab by remember { mutableStateOf("Songs") }

  var userPlaylists by remember { mutableStateOf(loadPlaylists(context)) }
  var activePlaylist by remember { mutableStateOf<VidMaxPlaylist?>(null) }
  var showCreatePlaylistDialog by remember { mutableStateOf(false) }
  var showAddToPlaylistDialog by remember { mutableStateOf(false) }
  var newPlaylistName by remember { mutableStateOf("") }
  var playlistToDelete by remember { mutableStateOf<VidMaxPlaylist?>(null) }

  var activeAlbumName by remember { mutableStateOf<String?>(null) }
  val albumsMap = remember(audioList) {
    audioList.groupBy { audio ->
      try {
        File(audio.path).parentFile?.name ?: "Unknown Album"
      } catch (e: Exception) {
        "Unknown Album"
      }
    }
  }

  var activeFolderName by remember { mutableStateOf<String?>(null) }
  val foldersMap = remember(audioList) {
    audioList.groupBy { audio ->
      try {
        File(audio.path).parentFile?.name ?: "Unknown Folder"
      } catch (e: Exception) {
        "Unknown Folder"
      }
    }
  }

  // --- AUTO HIDE TAB BAR ON SCROLL ENGINE ---
  val listState = rememberLazyListState()
  var isTabBarVisible by remember { mutableStateOf(true) }

  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -12f) { // Scroll Down -> Hide
          isTabBarVisible = false
        } else if (available.y > 12f) { // Scroll Up -> Show
          isTabBarVisible = true
        }
        return Offset.Zero
      }
    }
  }

  val displayedList = remember(
      currentTab,
      audioList,
      favoritePaths,
      activePlaylist,
      activeAlbumName,
      activeFolderName,
      albumsMap,
      foldersMap
  ) {
    when {
      currentTab == "Songs" -> audioList
      currentTab == "Favorites" -> audioList.filter { it.path in favoritePaths }
      currentTab == "Playlists" && activePlaylist != null -> {
        val paths = activePlaylist!!.paths
        audioList.filter { it.path in paths }
      }
      currentTab == "Albums" && activeAlbumName != null -> albumsMap[activeAlbumName] ?: emptyList()
      currentTab == "Folders" && activeFolderName != null -> foldersMap[activeFolderName] ?: emptyList()
      else -> emptyList()
    }
  }

  BackHandler(
      enabled = inSelectionMode ||
          isSearchExpanded ||
          activePlaylist != null ||
          activeFolderName != null ||
          activeAlbumName != null
  ) {
    when {
      inSelectionMode -> selectedAudioIds = emptySet()
      isSearchExpanded -> {
        isSearchExpanded = false
        viewModel.setAudioSearchQuery("")
      }
      activePlaylist != null -> activePlaylist = null
      activeFolderName != null -> activeFolderName = null
      activeAlbumName != null -> activeAlbumName = null
    }
  }

  val deleteLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartIntentSenderForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Toast.makeText(context, "Selected audio deleted successfully", Toast.LENGTH_SHORT).show()
      selectedAudioIds = emptySet()
    } else {
      Toast.makeText(context, "Delete Cancelled", Toast.LENGTH_SHORT).show()
    }
  }

  // --- DIALOGS ---
  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = { Text(text = "Delete Audios", fontWeight = FontWeight.Bold) },
        text = {
          Text("Are you sure you want to delete ${selectedAudioIds.size} selected songs? This action cannot be undone.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                val urisToDelete = selectedAudioIds.mapNotNull { id ->
                  displayedList.find { it.id == id }?.path?.let { getAudioUriFromPathForMulti(context, it) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && urisToDelete.isNotEmpty()) {
                  val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                  deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                  var deletedCount = 0
                  selectedAudioIds.forEach { id ->
                    val path = displayedList.find { it.id == id }?.path ?: return@forEach
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                      deletedCount++
                    } else {
                      getAudioUriFromPathForMulti(context, path)?.let { uri ->
                        if (context.contentResolver.delete(uri, null, null) > 0) deletedCount++
                      }
                    }
                  }
                  Toast.makeText(context, "$deletedCount audio(s) deleted", Toast.LENGTH_SHORT).show()
                  selectedAudioIds = emptySet()
                }
              }) {
            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  if (playlistToDelete != null) {
    AlertDialog(
        onDismissRequest = { playlistToDelete = null },
        title = { Text("Delete Playlist", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete '${playlistToDelete?.name}'? This will not delete the actual songs.") },
        confirmButton = {
          TextButton(
              onClick = {
                val updatedPlaylists = userPlaylists.toMutableList().apply { remove(playlistToDelete) }
                userPlaylists = updatedPlaylists
                savePlaylists(context, updatedPlaylists)
                playlistToDelete = null
                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
              }) {
            Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { playlistToDelete = null }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  if (showCreatePlaylistDialog) {
    AlertDialog(
        onDismissRequest = { showCreatePlaylistDialog = false },
        title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
        text = {
          OutlinedTextField(
              value = newPlaylistName,
              onValueChange = { newPlaylistName = it },
              placeholder = { Text("Enter playlist name") },
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  cursorColor = MaterialTheme.colorScheme.primary
              )
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                if (newPlaylistName.isNotBlank()) {
                  val updatedPlaylists = userPlaylists.toMutableList().apply {
                    add(VidMaxPlaylist(newPlaylistName, emptyList()))
                  }
                  userPlaylists = updatedPlaylists
                  savePlaylists(context, updatedPlaylists)
                  newPlaylistName = ""
                  showCreatePlaylistDialog = false
                  Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                }
              }) {
            Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showCreatePlaylistDialog = false }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  if (showAddToPlaylistDialog) {
    AlertDialog(
        onDismissRequest = { showAddToPlaylistDialog = false },
        title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
        text = {
          LazyColumn {
            item {
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        showAddToPlaylistDialog = false
                        showCreatePlaylistDialog = true
                      }
                      .padding(vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = "New",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Create New Playlist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
              }
            }
            itemsIndexed(userPlaylists) { _, playlist ->
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        val pathsToAdd = selectedAudioIds.mapNotNull { id ->
                          audioList.find { it.id == id }?.path
                        }
                        val updatedPaths = (playlist.paths + pathsToAdd).distinct()
                        val updatedPlaylists = userPlaylists.toMutableList()
                        
                        val index = updatedPlaylists.indexOf(playlist)
                        if (index != -1) {
                          updatedPlaylists[index] = VidMaxPlaylist(playlist.name, updatedPaths)
                        }

                        userPlaylists = updatedPlaylists
                        savePlaylists(context, updatedPlaylists)

                        showAddToPlaylistDialog = false
                        selectedAudioIds = emptySet()
                        Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                      }
                      .padding(vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(id = R.drawable.ic_playlist),
                    contentDescription = "Playlist",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(playlist.name, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showAddToPlaylistDialog = false }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  // --- MAIN LAYOUT ---
  Column(
      modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(horizontal = 16.dp)
          .nestedScroll(nestedScrollConnection)) {
    Spacer(modifier = Modifier.height(6.dp))

    if (inSelectionMode) {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
              .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { selectedAudioIds = emptySet() }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_custom),
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
          }
          Text(
              text = "${selectedAudioIds.size} Selected",
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold
          )
        }
        Row {
          IconButton(onClick = { showAddToPlaylistDialog = true }) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add to Playlist",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
          }

          IconButton(
              onClick = {
                selectedAudioIds = if (selectedAudioIds.size == displayedList.size) {
                  emptySet()
                } else {
                  displayedList.map { it.id }.toSet()
                }
              }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_select_all),
                contentDescription = "Select All",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
          }
          IconButton(
              onClick = {
                val uris = selectedAudioIds.mapNotNull { id ->
                  displayedList.find { it.id == id }?.path?.let { getAudioUriFromPathForMulti(context, it) }
                }.toCollection(ArrayList())

                if (uris.isNotEmpty()) {
                  val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  context.startActivity(Intent.createChooser(intent, "Share ${uris.size} Audios"))
                  selectedAudioIds = emptySet()
                }
              }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_share_custom),
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
          }
          IconButton(onClick = { showDeleteConfirmDialog = true }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_custom),
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
          }
        }
      }
    } else if (isSearchExpanded) {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
              isSearchExpanded = false
              viewModel.setAudioSearchQuery("")
            }) {
          Icon(
              imageVector = Icons.Default.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onBackground
          )
        }
        Box(modifier = Modifier.weight(1f)) {
          VidMaxSearchBar(
              query = searchQuery,
              onQueryChange = { viewModel.setAudioSearchQuery(it) },
              placeholder = "Search songs..."
          )
        }
      }
    } else {
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp, top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (currentTab == "Playlists" && activePlaylist != null) {
            IconButton(onClick = { activePlaylist = null }) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
          } else if (currentTab == "Albums" && activeAlbumName != null) {
            IconButton(onClick = { activeAlbumName = null }) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
          } else if (currentTab == "Folders" && activeFolderName != null) {
            IconButton(onClick = { activeFolderName = null }) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
          }

          val titleText = when {
            currentTab == "Playlists" && activePlaylist != null -> activePlaylist!!.name
            currentTab == "Albums" && activeAlbumName != null -> activeAlbumName!!
            currentTab == "Folders" && activeFolderName != null -> activeFolderName!!
            else -> "Music"
          }

          Text(
              text = titleText,
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
          }
          IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
          }
        }
      }
    }

    // 🚀 COMPACT & SLEEK AUTO-HIDING NAVIGATION TAB BAR 🚀
    val tabsList = listOf("Songs", "Folders", "Playlists", "Favorites")
    val selectedTabIndex = tabsList.indexOf(currentTab).coerceAtLeast(0)

    AnimatedVisibility(
        visible = isTabBarVisible,
        enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
        exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
    ) {
      BoxWithConstraints(
          modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
              .height(46.dp)
              .clip(RoundedCornerShape(23.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .padding(3.dp)
      ) {
        val tabWidth = maxWidth / tabsList.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTabIndex,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "indicatorOffset"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
          TabItem(
              title = "Songs",
              isSelected = currentTab == "Songs",
              onClick = {
                currentTab = "Songs"
                activePlaylist = null
                activeAlbumName = null
                activeFolderName = null
              }) { tint, scale ->
            Icon(painterResource(id = R.drawable.ic_music_note), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).scale(scale))
          }

          TabItem(
              title = "Folders",
              isSelected = currentTab == "Folders",
              onClick = {
                currentTab = "Folders"
                activePlaylist = null
                activeAlbumName = null
                activeFolderName = null
              }) { tint, scale ->
            Icon(painterResource(id = R.drawable.ic_folder), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).scale(scale))
          }

          TabItem(
              title = "Playlists",
              isSelected = currentTab == "Playlists",
              onClick = {
                currentTab = "Playlists"
                activePlaylist = null
                activeAlbumName = null
                activeFolderName = null
              }) { tint, scale ->
            Icon(painterResource(id = R.drawable.ic_playlist), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).scale(scale))
          }

          TabItem(
              title = "Favorites",
              isSelected = currentTab == "Favorites",
              onClick = {
                currentTab = "Favorites"
                activePlaylist = null
                activeAlbumName = null
                activeFolderName = null
              }) { tint, scale ->
            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).scale(scale))
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // HEADER TEXT COUNTS
    if ((currentTab != "Playlists" || activePlaylist != null) &&
        (currentTab != "Albums" || activeAlbumName != null) &&
        (currentTab != "Folders" || activeFolderName != null)) {
      Text(
          text = "${displayedList.size} songs",
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
      )
    } else if (currentTab == "Albums" && activeAlbumName == null) {
      Text(
          text = "${albumsMap.size} albums",
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
      )
    } else if (currentTab == "Folders" && activeFolderName == null) {
      Text(
          text = "${foldersMap.size} folders",
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
      )
    }

    // MAIN CONTENT AREA
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 160.dp)) {
        
        if (currentTab == "Albums" && activeAlbumName == null) {
          if (albumsMap.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No Albums found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 16.sp)
              }
            }
          } else {
            itemsIndexed(albumsMap.keys.toList()) { _, albumName ->
              val albumSongs = albumsMap[albumName] ?: emptyList()
              val firstSongPath = albumSongs.firstOrNull()?.path ?: ""

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                      .clickable { activeAlbumName = albumName }
                      .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                AlbumThumbnail(path = firstSongPath)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(albumName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("${albumSongs.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
              }
            }
          }
        } else if (currentTab == "Folders" && activeFolderName == null) {
          if (foldersMap.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No Folders found", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 16.sp)
              }
            }
          } else {
            itemsIndexed(foldersMap.keys.toList()) { _, folderName ->
              val folderSongs = foldersMap[folderName] ?: emptyList()

              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                      .clickable { activeFolderName = folderName }
                      .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                  Icon(painterResource(id = R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(folderName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("${folderSongs.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
              }
            }
          }
        } else if (currentTab == "Playlists" && activePlaylist == null) {
          if (userPlaylists.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No Playlists created yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 16.sp)
              }
            }
          } else {
            itemsIndexed(userPlaylists) { _, playlist ->
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(14.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                      .clickable { activePlaylist = playlist }
                      .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                PlaylistStackedThumbnail(paths = playlist.paths)

                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text("${playlist.paths.size} songs", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                IconButton(onClick = { playlistToDelete = playlist }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        } else {
          // SHOW SONGS LIST
          if (displayedList.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                      currentTab == "Favorites" -> "No songs found in Favorites"
                      activePlaylist != null -> "No songs in this playlist"
                      else -> "No items found in $currentTab"
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
              }
            }
          } else {
            itemsIndexed(items = displayedList, key = { _, item -> item.id }) { index, audio ->
              val isSelected = selectedAudioIds.contains(audio.id)
              val isPlayingNow = currentlyPlayingPath == audio.path

              AudioCard(
                  audio = audio,
                  duration = viewModel.formatDuration(audio.duration),
                  isSelected = isSelected,
                  isPlayingNow = isPlayingNow,
                  isAudioPlayingState = isAudioPlaying,
                  onClick = {
                    if (inSelectionMode) {
                      selectedAudioIds = if (isSelected) selectedAudioIds - audio.id else selectedAudioIds + audio.id
                    } else {
                      onAudioClick(displayedList, index)
                    }
                  },
                  onLongClick = {
                    selectedAudioIds = if (isSelected) selectedAudioIds - audio.id else selectedAudioIds + audio.id
                  }
              )
            }
          }
        }
      }

      // FLOATING ACTION BUTTON
      val fabScale by animateFloatAsState(
          targetValue = if (currentTab == "Playlists" && activePlaylist == null) 1f else 0f,
          animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
          label = "fabScale"
      )

      if (fabScale > 0.01f) {
        FloatingActionButton(
            onClick = { showCreatePlaylistDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp)
                .scale(fabScale)) {
          Icon(Icons.Default.Add, contentDescription = "New Playlist", modifier = Modifier.size(28.dp))
        }
      }
    }
  }
}

// SLEEK TAB ITEM WITH COMPACT ANIMATION
@Composable
fun RowScope.TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color, Float) -> Unit
) {
  val contentColor by animateColorAsState(
      targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
      animationSpec = tween(200),
      label = "colorAnim"
  )

  val iconScale by animateFloatAsState(
      targetValue = if (isSelected) 1.15f else 1.0f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
      label = "scaleAnim"
  )

  Row(
      modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(20.dp))
          .clickable { onClick() },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically) {
    icon(contentColor, iconScale)
    Spacer(modifier = Modifier.width(5.dp))
    Text(
        text = title,
        color = contentColor,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
  }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AlbumThumbnail(path: String) {
  val context = LocalContext.current
  var artByteArray by remember(path) { mutableStateOf<ByteArray?>(EmbeddedArtCache.get(path)) }
  var isArtLoaded by remember(path) { mutableStateOf(artByteArray != null) }

  LaunchedEffect(path) {
    if (artByteArray == null && path.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        artByteArray = EmbeddedArtCache.getOrFetch(context, path)
        isArtLoaded = true
      }
    }
  }

  Box(
      modifier = Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
      contentAlignment = Alignment.Center) {
    if (isArtLoaded && artByteArray != null) {
      GlideImage(
          model = artByteArray,
          contentDescription = "Album Art",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()) {
        it.diskCacheStrategy(DiskCacheStrategy.ALL).signature(ObjectKey(path)).override(150)
      }
    } else if (isArtLoaded) {
      Icon(painterResource(id = R.drawable.ic_music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
  }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlaylistStackedThumbnail(paths: List<String>) {
  val context = LocalContext.current
  val displayPaths = remember(paths) { paths.take(3) }
  var byteArrays by remember(displayPaths) { mutableStateOf<List<ByteArray?>>(displayPaths.map { EmbeddedArtCache.get(it) }) }
  var isLoaded by remember(displayPaths) { mutableStateOf(byteArrays.any { it != null } || displayPaths.isEmpty()) }

  LaunchedEffect(displayPaths) {
    if (!isLoaded && displayPaths.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        byteArrays = displayPaths.map { EmbeddedArtCache.getOrFetch(context, it) }
        isLoaded = true
      }
    }
  }

  Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
    if (displayPaths.isEmpty()) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center) {
        Icon(painterResource(id = R.drawable.ic_playlist), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
      }
    } else if (isLoaded) {
      if (byteArrays.none { it != null }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center) {
          Icon(painterResource(id = R.drawable.ic_playlist), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        }
      } else {
        if (byteArrays.size >= 3 && byteArrays[2] != null) {
          GlideImage(
              model = byteArrays[2],
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                  .size(48.dp)
                  .offset(x = 8.dp, y = (-6).dp)
                  .graphicsLayer { rotationZ = 12f }
                  .clip(RoundedCornerShape(8.dp))
                  .alpha(0.5f)) {
            it.diskCacheStrategy(DiskCacheStrategy.ALL).signature(ObjectKey(displayPaths[2])).override(100)
          }
        }

        if (byteArrays.size >= 2 && byteArrays[1] != null) {
          GlideImage(
              model = byteArrays[1],
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                  .size(52.dp)
                  .offset(x = (-6).dp, y = 2.dp)
                  .graphicsLayer { rotationZ = -10f }
                  .clip(RoundedCornerShape(8.dp))
                  .alpha(0.8f)) {
            it.diskCacheStrategy(DiskCacheStrategy.ALL).signature(ObjectKey(displayPaths[1])).override(100)
          }
        }

        if (byteArrays.isNotEmpty() && byteArrays[0] != null) {
          GlideImage(
              model = byteArrays[0],
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                  .size(56.dp)
                  .shadow(6.dp, RoundedCornerShape(10.dp))
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant)) {
            it.diskCacheStrategy(DiskCacheStrategy.ALL).signature(ObjectKey(displayPaths[0])).override(150)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun AudioCard(
    audio: AudioItem,
    duration: String,
    isSelected: Boolean = false,
    isPlayingNow: Boolean = false,
    isAudioPlayingState: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
  val context = LocalContext.current
  val folderName = remember(audio.path) { File(audio.path).parentFile?.name ?: "Unknown" }

  var artByteArray by remember(audio.path) { mutableStateOf<ByteArray?>(EmbeddedArtCache.get(audio.path)) }
  var isArtLoaded by remember(audio.path) { mutableStateOf(artByteArray != null) }

  LaunchedEffect(audio.path) {
    if (artByteArray == null) {
      withContext(Dispatchers.IO) {
        artByteArray = EmbeddedArtCache.getOrFetch(context, audio.path)
        isArtLoaded = true
      }
    }
  }

  val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
  val borderColor = if (isSelected || isPlayingNow) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent

  Row(
      modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(backgroundColor)
          .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
          .combinedClickable(onClick = onClick, onLongClick = onLongClick)
          .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center) {
      
      if (isArtLoaded && artByteArray != null) {
        GlideImage(
            model = artByteArray,
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()) {
          it.diskCacheStrategy(DiskCacheStrategy.ALL).signature(ObjectKey(audio.path)).override(150)
        }
      } else if (isArtLoaded) {
        Icon(painter = painterResource(id = R.drawable.ic_music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
      }

      if (isPlayingNow && isAudioPlayingState) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 6.dp),
            contentAlignment = Alignment.BottomCenter) {
          PlayingEqualizerAnim()
        }
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = audio.title,
          color = if (isPlayingNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)) {
          Text(text = duration, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${audio.artist}  •  $folderName",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
      }
    }

    if (isSelected) {
      Icon(imageVector = Icons.Filled.Done, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp).size(24.dp))
    } else if (isPlayingNow) {
      Box(
          modifier = Modifier
              .padding(end = 4.dp)
              .size(32.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(id = if (isAudioPlayingState) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = "Playing State",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}

@Composable
fun PlayingEqualizerAnim() {
  val transition = rememberInfiniteTransition(label = "dj_eq_transition")

  val bar1 by transition.animateFloat(
      initialValue = 4f,
      targetValue = 4f,
      animationSpec = infiniteRepeatable(
          animation = keyframes { durationMillis = 450; 26f at 120 with EaseInOutSine; 14f at 280; 4f at 450 },
          repeatMode = RepeatMode.Restart),
      label = "bar1"
  )

  val bar2 by transition.animateFloat(
      initialValue = 6f,
      targetValue = 6f,
      animationSpec = infiniteRepeatable(
          animation = keyframes { durationMillis = 350; 20f at 90 with EaseInOutSine; 8f at 220; 6f at 350 },
          repeatMode = RepeatMode.Restart),
      label = "bar2"
  )

  val bar3 by transition.animateFloat(
      initialValue = 5f,
      targetValue = 5f,
      animationSpec = infiniteRepeatable(
          animation = keyframes { durationMillis = 500; 28f at 150 with EaseInOutSine; 16f at 320; 5f at 500 },
          repeatMode = RepeatMode.Restart),
      label = "bar3"
  )

  val bar4 by transition.animateFloat(
      initialValue = 4f,
      targetValue = 4f,
      animationSpec = infiniteRepeatable(
          animation = keyframes { durationMillis = 400; 18f at 100 with EaseInOutSine; 10f at 240; 4f at 400 },
          repeatMode = RepeatMode.Restart),
      label = "bar4"
  )

  Row(
      modifier = Modifier.size(24.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.Bottom) {
    Box(modifier = Modifier.width(3.dp).height(bar1.dp).background(Color.White, RoundedCornerShape(2.dp)))
    Box(modifier = Modifier.width(3.dp).height(bar2.dp).background(Color.White, RoundedCornerShape(2.dp)))
    Box(modifier = Modifier.width(3.dp).height(bar3.dp).background(Color.White, RoundedCornerShape(2.dp)))
    Box(modifier = Modifier.width(3.dp).height(bar4.dp).background(Color.White, RoundedCornerShape(2.dp)))
  }
}

fun getAudioUriFromPathForMulti(context: Context, path: String): Uri? {
  return try {
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID),
        MediaStore.Audio.Media.DATA + "=?",
        arrayOf(path),
        null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
      } else null
    }
  } catch (e: Exception) {
    null
  }
}
