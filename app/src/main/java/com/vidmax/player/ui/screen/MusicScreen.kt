package com.vidmax.player.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.ui.components.VidMaxSearchBar
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// --- 🚀 FAST MEMORY THUMBNAIL CACHE ENGINE 🚀 ---
private object AudioArtCache {
  private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
  private val cacheSize = maxMemory / 8 // allocate 1/8th of memory
  private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
    override fun sizeOf(key: String, bitmap: Bitmap): Int {
      return bitmap.byteCount / 1024
    }
  }

  fun get(key: String): Bitmap? = memoryCache.get(key)

  fun put(key: String, bitmap: Bitmap) {
    if (get(key) == null) {
      memoryCache.put(key, bitmap)
    }
  }

  fun loadThumbnail(context: Context, path: String, targetDp: Int = 120): Bitmap? {
    if (path.isEmpty()) return null
    val cached = get(path)
    if (cached != null) return cached

    return try {
      val retriever = MediaMetadataRetriever()
      val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
      retriever.setDataSource(context, uri)
      val art = retriever.embeddedPicture
      retriever.release()

      if (art != null) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(art, 0, art.size, options)

        val density = context.resources.displayMetrics.density
        val targetPx = (targetDp * density).toInt()
        options.inSampleSize = calculateInSampleSize(options, targetPx, targetPx)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size, options)
        if (bitmap != null) {
          put(path, bitmap)
        }
        bitmap
      } else null
    } catch (e: Exception) {
      null
    }
  }

  private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
      val halfHeight = height / 2
      val halfWidth = width / 2
      while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
        inSampleSize *= 2
      }
    }
    return inSampleSize
  }
}

// Playlist Data Model
data class VidMaxPlaylist(val name: String, val paths: List<String>)

fun loadPlaylists(context: Context): List<VidMaxPlaylist> {
  val prefs = context.getSharedPreferences("VidMaxPlaylists", Context.MODE_PRIVATE)
  val jsonString: String = prefs.getString("playlists_data", "[]") ?: "[]"
  val result: MutableList<VidMaxPlaylist> = mutableListOf()
  try {
    val jsonArray = JSONArray(jsonString)
    for (i in 0 until jsonArray.length()) {
      val jsonObj: JSONObject = jsonArray.getJSONObject(i)
      val name: String = jsonObj.getString("name")
      val pathsArray: JSONArray = jsonObj.getJSONArray("paths")
      val paths: MutableList<String> = mutableListOf()
      for (j in 0 until pathsArray.length()) {
        paths.add(pathsArray.getString(j))
      }
      result.add(VidMaxPlaylist(name, paths))
    }
  } catch (e: Exception) {
    e.printStackTrace()
  }
  return result
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
  val context: Context = LocalContext.current
  val audioList: List<AudioItem> by viewModel.filteredAudio.collectAsState()
  val searchQuery: String by viewModel.audioSearchQuery.collectAsState()

  val currentlyPlayingPath: String by viewModel.recentlyPlayedPath.collectAsState()
  val isAudioPlaying: Boolean by viewModel.isAudioPlaying.collectAsState()
  val favoritePaths: Set<String> by viewModel.favoriteAudioPaths.collectAsState()

  var selectedAudioIds: Set<Long> by remember { mutableStateOf(setOf<Long>()) }
  var showDeleteConfirmDialog: Boolean by remember { mutableStateOf(false) }

  val inSelectionMode: Boolean = selectedAudioIds.isNotEmpty()
  var isSearchExpanded: Boolean by remember { mutableStateOf(false) }

  var currentTab: String by remember { mutableStateOf("Songs") }

  var userPlaylists: List<VidMaxPlaylist> by remember { mutableStateOf(loadPlaylists(context)) }
  var activePlaylist: VidMaxPlaylist? by remember { mutableStateOf(null) }
  var showCreatePlaylistDialog: Boolean by remember { mutableStateOf(false) }
  var showAddToPlaylistDialog: Boolean by remember { mutableStateOf(false) }
  var newPlaylistName: String by remember { mutableStateOf("") }
  var playlistToDelete: VidMaxPlaylist? by remember { mutableStateOf(null) }

  var activeAlbumName: String? by remember { mutableStateOf(null) }
  val albumsMap: Map<String, List<AudioItem>> =
      remember(audioList) {
        audioList.groupBy { audio ->
          try {
            File(audio.path).parentFile?.name ?: "Unknown Album"
          } catch (e: Exception) {
            "Unknown Album"
          }
        }
      }

  // FOLDER LOGIC
  var activeFolderName: String? by remember { mutableStateOf(null) }
  val foldersMap: Map<String, List<AudioItem>> =
      remember(audioList) {
        audioList.groupBy { audio ->
          try {
            val parentDir = File(audio.path).parentFile
            if (parentDir != null) {
              parentDir.name
            } else {
              "Unknown Folder"
            }
          } catch (e: Exception) {
            "Unknown Folder"
          }
        }
      }

  val displayedList: List<AudioItem> =
      remember(
          currentTab,
          audioList,
          favoritePaths,
          activePlaylist,
          activeAlbumName,
          activeFolderName,
          albumsMap,
          foldersMap) {
            when {
              currentTab == "Songs" -> audioList
              currentTab == "Favorites" ->
                  audioList.filter { audio: AudioItem -> favoritePaths.contains(audio.path) }
              currentTab == "Playlists" && activePlaylist != null -> {
                val paths: List<String> = activePlaylist!!.paths
                audioList.filter { audio: AudioItem -> paths.contains(audio.path) }
              }
              currentTab == "Albums" && activeAlbumName != null -> {
                albumsMap[activeAlbumName] ?: emptyList()
              }
              currentTab == "Folders" && activeFolderName != null -> {
                foldersMap[activeFolderName] ?: emptyList()
              }
              else -> emptyList()
            }
          }

  // ব্যাক হ্যান্ডলার
  BackHandler(
      enabled =
          inSelectionMode ||
              isSearchExpanded ||
              activePlaylist != null ||
              activeFolderName != null ||
              activeAlbumName != null) {
        if (inSelectionMode) {
          selectedAudioIds = emptySet()
        } else if (isSearchExpanded) {
          isSearchExpanded = false
          viewModel.setAudioSearchQuery("")
        } else if (activePlaylist != null) {
          activePlaylist = null
        } else if (activeFolderName != null) {
          activeFolderName = null
        } else if (activeAlbumName != null) {
          activeAlbumName = null
        }
      }

  val deleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
              Toast.makeText(context, "Selected audio deleted successfully", Toast.LENGTH_SHORT)
                  .show()
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
          Text(
              text =
                  "Are you sure you want to delete ${selectedAudioIds.size} selected songs? This action cannot be undone.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                val urisToDelete: List<Uri> =
                    selectedAudioIds.mapNotNull { id: Long ->
                      val path: String? =
                          displayedList.find { audio: AudioItem -> audio.id == id }?.path
                      if (path != null) getAudioUriFromPathForMulti(context, path) else null
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && urisToDelete.isNotEmpty()) {
                  val pendingIntent =
                      MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                  deleteLauncher.launch(
                      IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                  var deletedCount = 0
                  selectedAudioIds.forEach { id: Long ->
                    val path: String =
                        displayedList.find { audio: AudioItem -> audio.id == id }?.path
                            ?: return@forEach
                    val file: File = File(path)
                    if (file.exists() && file.delete()) {
                      deletedCount++
                    } else {
                      val uri: Uri? = getAudioUriFromPathForMulti(context, path)
                      if (uri != null) {
                        val rows: Int = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) deletedCount++
                      }
                    }
                  }
                  Toast.makeText(context, "$deletedCount audio(s) deleted", Toast.LENGTH_SHORT)
                      .show()
                  selectedAudioIds = emptySet()
                }
              }) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) {
            Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  if (playlistToDelete != null) {
    AlertDialog(
        onDismissRequest = { playlistToDelete = null },
        title = { Text("Delete Playlist", fontWeight = FontWeight.Bold) },
        text = {
          Text(
              "Are you sure you want to delete '${playlistToDelete?.name}'? This will not delete the actual songs.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                val updatedPlaylists = userPlaylists.toMutableList()
                updatedPlaylists.remove(playlistToDelete)
                userPlaylists = updatedPlaylists
                savePlaylists(context, updatedPlaylists)
                playlistToDelete = null
                Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
              }) {
                Text(
                    "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
              onValueChange = { name: String -> newPlaylistName = name },
              placeholder = { Text("Enter playlist name") },
              singleLine = true,
              colors =
                  OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = MaterialTheme.colorScheme.primary,
                      cursorColor = MaterialTheme.colorScheme.primary))
        },
        confirmButton = {
          TextButton(
              onClick = {
                if (newPlaylistName.isNotBlank()) {
                  val newPlaylist = VidMaxPlaylist(newPlaylistName, emptyList())
                  val updatedPlaylists: MutableList<VidMaxPlaylist> = userPlaylists.toMutableList()
                  updatedPlaylists.add(newPlaylist)
                  userPlaylists = updatedPlaylists
                  savePlaylists(context, updatedPlaylists)
                  newPlaylistName = ""
                  showCreatePlaylistDialog = false
                  Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                }
              }) {
                Text(
                    "Create",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
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
                  modifier =
                      Modifier.fillMaxWidth()
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
                        modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Create New Playlist",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                  }
            }
            itemsIndexed(userPlaylists) { _: Int, playlist: VidMaxPlaylist ->
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .clickable {
                            val pathsToAdd: List<String> =
                                selectedAudioIds.mapNotNull { id: Long ->
                                  audioList.find { audio: AudioItem -> audio.id == id }?.path
                                }

                            val updatedPaths: MutableList<String> = playlist.paths.toMutableList()
                            updatedPaths.addAll(pathsToAdd)
                            val uniquePaths: List<String> = updatedPaths.distinct()

                            val newPlaylist = VidMaxPlaylist(playlist.name, uniquePaths)
                            val updatedPlaylists: MutableList<VidMaxPlaylist> =
                                userPlaylists.toMutableList()
                            val index: Int = updatedPlaylists.indexOf(playlist)
                            if (index != -1) {
                              updatedPlaylists[index] = newPlaylist
                            }

                            userPlaylists = updatedPlaylists
                            savePlaylists(context, updatedPlaylists)

                            showAddToPlaylistDialog = false
                            selectedAudioIds = emptySet()
                            Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT)
                                .show()
                          }
                          .padding(vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(id = R.drawable.ic_playlist),
                        contentDescription = "Playlist",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        playlist.name,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface)
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
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(6.dp))

        if (inSelectionMode) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
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
                        modifier = Modifier.size(24.dp))
                  }
                  Text(
                      text = "${selectedAudioIds.size} Selected",
                      color = MaterialTheme.colorScheme.onBackground,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold)
                }
                Row {
                  IconButton(onClick = { showAddToPlaylistDialog = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp))
                  }

                  IconButton(
                      onClick = {
                        selectedAudioIds =
                            if (selectedAudioIds.size == displayedList.size) emptySet()
                            else displayedList.map { audio: AudioItem -> audio.id }.toSet()
                      }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_select_all),
                            contentDescription = "Select All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                  IconButton(
                      onClick = {
                        val uris: ArrayList<Uri> =
                            selectedAudioIds
                                .mapNotNull { id: Long ->
                                  val path: String? =
                                      displayedList
                                          .find { audio: AudioItem -> audio.id == id }
                                          ?.path
                                  if (path != null) getAudioUriFromPathForMulti(context, path)
                                  else null
                                }
                                .toCollection(ArrayList())

                        if (uris.isNotEmpty()) {
                          val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
                          intent.type = "audio/*"
                          intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                          context.startActivity(
                              Intent.createChooser(intent, "Share ${uris.size} Audios"))
                          selectedAudioIds = emptySet()
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
                      viewModel.setAudioSearchQuery("")
                    }) {
                      Icon(
                          imageVector = Icons.Default.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                Box(modifier = Modifier.weight(1f)) {
                  VidMaxSearchBar(
                      query = searchQuery,
                      onQueryChange = { query: String -> viewModel.setAudioSearchQuery(query) },
                      placeholder = "Search songs...")
                }
              }
        } else {
          Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (currentTab == "Playlists" && activePlaylist != null) {
                    IconButton(onClick = { activePlaylist = null }) {
                      Icon(
                          Icons.Default.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                  } else if (currentTab == "Albums" && activeAlbumName != null) {
                    IconButton(onClick = { activeAlbumName = null }) {
                      Icon(
                          Icons.Default.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                  } else if (currentTab == "Folders" && activeFolderName != null) {
                    IconButton(onClick = { activeFolderName = null }) {
                      Icon(
                          Icons.Default.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                  }

                  val titleText =
                      when {
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
                      modifier = Modifier.weight(1f, fill = false))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                      onClick = { isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp))
                      }
                  IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp))
                  }
                }
              }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // TABS
        val tabsList = listOf("Songs", "Folders", "Playlists", "Favorites")
        val selectedTabIndex = tabsList.indexOf(currentTab).coerceAtLeast(0)

        BoxWithConstraints(
            modifier =
                Modifier.fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .padding(6.dp)) {
              val tabWidth = maxWidth / tabsList.size
              val indicatorOffset by
                  animateDpAsState(
                      targetValue = tabWidth * selectedTabIndex,
                      animationSpec =
                          spring(
                              dampingRatio =
                                  Spring.DampingRatioMediumBouncy,
                              stiffness = Spring.StiffnessLow),
                      label = "indicatorOffset")

              Box(
                  modifier =
                      Modifier.offset(x = indicatorOffset)
                          .width(tabWidth)
                          .fillMaxHeight()
                          .clip(RoundedCornerShape(26.dp))
                          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)))

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
                        }) { tint: Color, scale: Float ->
                          Icon(
                              painterResource(id = R.drawable.ic_music_note),
                              contentDescription = null,
                              tint = tint,
                              modifier = Modifier.size(22.dp).scale(scale))
                        }

                    TabItem(
                        title = "Folders",
                        isSelected = currentTab == "Folders",
                        onClick = {
                          currentTab = "Folders"
                          activePlaylist = null
                          activeAlbumName = null
                          activeFolderName = null
                        }) { tint: Color, scale: Float ->
                          Icon(
                              painterResource(id = R.drawable.ic_folder),
                              contentDescription = null,
                              tint = tint,
                              modifier = Modifier.size(22.dp).scale(scale))
                        }

                    TabItem(
                        title = "Playlists",
                        isSelected = currentTab == "Playlists",
                        onClick = {
                          currentTab = "Playlists"
                          activePlaylist = null
                          activeAlbumName = null
                          activeFolderName = null
                        }) { tint: Color, scale: Float ->
                          Icon(
                              painterResource(id = R.drawable.ic_playlist),
                              contentDescription = null,
                              tint = tint,
                              modifier = Modifier.size(22.dp).scale(scale))
                        }

                    TabItem(
                        title = "Favorites",
                        isSelected = currentTab == "Favorites",
                        onClick = {
                          currentTab = "Favorites"
                          activePlaylist = null
                          activeAlbumName = null
                          activeFolderName = null
                        }) { tint: Color, scale: Float ->
                          Icon(
                              Icons.Default.FavoriteBorder,
                              contentDescription = null,
                              tint = tint,
                              modifier = Modifier.size(22.dp).scale(scale))
                        }
                  }
            }

        Spacer(modifier = Modifier.height(16.dp))

        // HEADER TEXT COUNTS
        if ((currentTab != "Playlists" || activePlaylist != null) &&
            (currentTab != "Albums" || activeAlbumName != null) &&
            (currentTab != "Folders" || activeFolderName != null)) {
          Text(
              text = "${displayedList.size} songs",
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp))
        } else if (currentTab == "Albums" && activeAlbumName == null) {
          Text(
              text = "${albumsMap.size} albums",
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp))
        } else if (currentTab == "Folders" && activeFolderName == null) {
          Text(
              text = "${foldersMap.size} folders",
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 8.dp))
        }

        // MAIN CONTENT AREA
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(bottom = 160.dp)
              ) {
                if (currentTab == "Albums" && activeAlbumName == null) {
                  if (albumsMap.isEmpty()) {
                    item {
                      Box(
                          modifier = Modifier.fillMaxWidth().height(200.dp),
                          contentAlignment = Alignment.Center) {
                            Text(
                                "No Albums found",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 16.sp)
                          }
                    }
                  } else {
                    itemsIndexed(albumsMap.keys.toList()) { _: Int, albumName: String ->
                      val albumSongs = albumsMap[albumName] ?: emptyList()
                      val firstSongPath = albumSongs.firstOrNull()?.path ?: ""

                      Row(
                          modifier =
                              Modifier.fillMaxWidth()
                                  .clip(RoundedCornerShape(14.dp))
                                  .background(
                                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                  .clickable { activeAlbumName = albumName }
                                  .padding(12.dp),
                          verticalAlignment = Alignment.CenterVertically) {
                            AlbumThumbnail(path = firstSongPath)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                              Text(
                                  albumName,
                                  fontSize = 18.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onSurface,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis)
                              Spacer(modifier = Modifier.height(4.dp))
                              Text(
                                  "${albumSongs.size} songs",
                                  fontSize = 14.sp,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                          }
                    }
                  }
                } else if (currentTab == "Folders" && activeFolderName == null) {
                  if (foldersMap.isEmpty()) {
                    item {
                      Box(
                          modifier = Modifier.fillMaxWidth().height(200.dp),
                          contentAlignment = Alignment.Center) {
                            Text(
                                "No Folders found",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 16.sp)
                          }
                    }
                  } else {
                    itemsIndexed(foldersMap.keys.toList()) { _: Int, folderName: String ->
                      val folderSongs = foldersMap[folderName] ?: emptyList()

                      Row(
                          modifier =
                              Modifier.fillMaxWidth()
                                  .clip(RoundedCornerShape(14.dp))
                                  .background(
                                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                  .clickable { activeFolderName = folderName }
                                  .padding(12.dp),
                          verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier.size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center) {
                                  Icon(
                                      painterResource(id = R.drawable.ic_folder),
                                      contentDescription = null,
                                      tint = MaterialTheme.colorScheme.primary,
                                      modifier = Modifier.size(28.dp))
                                }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                              Text(
                                  folderName,
                                  fontSize = 18.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onSurface,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis)
                              Spacer(modifier = Modifier.height(4.dp))
                              Text(
                                  "${folderSongs.size} songs",
                                  fontSize = 14.sp,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                          }
                    }
                  }
                } else if (currentTab == "Playlists" && activePlaylist == null) {
                  if (userPlaylists.isEmpty()) {
                    item {
                      Box(
                          modifier = Modifier.fillMaxWidth().height(200.dp),
                          contentAlignment = Alignment.Center) {
                            Text(
                                "No Playlists created yet",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 16.sp)
                          }
                    }
                  } else {
                    itemsIndexed(userPlaylists) { _: Int, playlist: VidMaxPlaylist ->
                      Row(
                          modifier =
                              Modifier.fillMaxWidth()
                                  .clip(RoundedCornerShape(14.dp))
                                  .background(
                                      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                  .clickable { activePlaylist = playlist }
                                  .padding(12.dp),
                          verticalAlignment = Alignment.CenterVertically) {
                            PlaylistStackedThumbnail(paths = playlist.paths)

                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                              Text(
                                  playlist.name,
                                  fontSize = 18.sp,
                                  fontWeight = FontWeight.Bold,
                                  color = MaterialTheme.colorScheme.onSurface)
                              Spacer(modifier = Modifier.height(4.dp))
                              Text(
                                  "${playlist.paths.size} songs",
                                  fontSize = 14.sp,
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }

                            IconButton(onClick = { playlistToDelete = playlist }) {
                              Icon(
                                  Icons.Default.Delete,
                                  contentDescription = "Delete Playlist",
                                  tint = MaterialTheme.colorScheme.error)
                            }
                          }
                    }
                  }
                } else {
                  // SHOW SONGS LIST
                  if (displayedList.isEmpty()) {
                    item {
                      Box(
                          modifier = Modifier.fillMaxWidth().height(300.dp),
                          contentAlignment = Alignment.Center) {
                            Text(
                                text =
                                    if (currentTab == "Favorites") "No songs found in Favorites"
                                    else if (activePlaylist != null) "No songs in this playlist"
                                    else "No items found in $currentTab",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 16.sp)
                          }
                    }
                  } else {
                    itemsIndexed(
                        items = displayedList, key = { _: Int, item: AudioItem -> item.id }) {
                            index: Int,
                            audio: AudioItem ->
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
                                  selectedAudioIds =
                                      if (isSelected) selectedAudioIds - audio.id
                                      else selectedAudioIds + audio.id
                                } else {
                                  onAudioClick(displayedList, index)
                                }
                              },
                              onLongClick = {
                                selectedAudioIds =
                                    if (isSelected) selectedAudioIds - audio.id
                                    else selectedAudioIds + audio.id
                              })
                        }
                  }
                }
              }

          // FLOATING ACTION BUTTON
          val fabScale by
              animateFloatAsState(
                  targetValue = if (currentTab == "Playlists" && activePlaylist == null) 1f else 0f,
                  animationSpec =
                      spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessLow),
                  label = "fabScale")

          if (fabScale > 0.01f) {
            FloatingActionButton(
                onClick = { showCreatePlaylistDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 120.dp)
                        .scale(fabScale)) {
                  Icon(
                      Icons.Default.Add,
                      contentDescription = "New Playlist",
                      modifier = Modifier.size(28.dp))
                }
          }
        }
      }
}

@Composable
fun AlbumThumbnail(path: String) {
  val context = LocalContext.current
  var bitmap by remember(path) { mutableStateOf<Bitmap?>(AudioArtCache.get(path)) }

  LaunchedEffect(path) {
    if (bitmap == null && path.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        val loaded = AudioArtCache.loadThumbnail(context, path, targetDp = 120)
        if (loaded != null) {
          bitmap = loaded
        }
      }
    }
  }

  Box(
      modifier =
          Modifier.size(64.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
      contentAlignment = Alignment.Center) {
        if (bitmap != null) {
          Image(
              bitmap = bitmap!!.asImageBitmap(),
              contentDescription = "Album Art",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize())
        } else {
          Icon(
              painterResource(id = R.drawable.ic_music_note),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp))
        }
      }
}

@Composable
fun PlaylistStackedThumbnail(paths: List<String>) {
  val context = LocalContext.current
  val displayPaths = remember(paths) { paths.take(3) }
  var bitmaps by remember(displayPaths) {
    mutableStateOf<List<Bitmap?>>(displayPaths.map { AudioArtCache.get(it) })
  }

  LaunchedEffect(displayPaths) {
    if (displayPaths.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        val result = displayPaths.map { path ->
          AudioArtCache.loadThumbnail(context, path, targetDp = 100)
        }
        bitmaps = result
      }
    } else {
      bitmaps = emptyList()
    }
  }

  Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
    if (displayPaths.isEmpty()) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center) {
            Icon(
                painterResource(id = R.drawable.ic_playlist),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp))
          }
    } else {
      val hasAnyArt = bitmaps.any { it != null }

      if (!hasAnyArt && bitmaps.isNotEmpty()) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center) {
              Icon(
                  painterResource(id = R.drawable.ic_playlist),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(32.dp))
            }
      } else {
        if (bitmaps.size >= 3 && bitmaps[2] != null) {
          Image(
              bitmap = bitmaps[2]!!.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.size(48.dp)
                      .offset(x = 8.dp, y = (-6).dp)
                      .graphicsLayer { rotationZ = 12f }
                      .clip(RoundedCornerShape(8.dp))
                      .alpha(0.5f))
        }
        if (bitmaps.size >= 2 && bitmaps[1] != null) {
          Image(
              bitmap = bitmaps[1]!!.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.size(52.dp)
                      .offset(x = (-6).dp, y = 2.dp)
                      .graphicsLayer { rotationZ = -10f }
                      .clip(RoundedCornerShape(8.dp))
                      .alpha(0.8f))
        }
        if (bitmaps.isNotEmpty() && bitmaps[0] != null) {
          Image(
              bitmap = bitmaps[0]!!.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.size(56.dp)
                      .shadow(6.dp, RoundedCornerShape(10.dp))
                      .clip(RoundedCornerShape(10.dp)))
        } else if (bitmaps.isNotEmpty()) {
          Box(
              modifier =
                  Modifier.size(56.dp)
                      .shadow(6.dp, RoundedCornerShape(10.dp))
                      .clip(RoundedCornerShape(10.dp))
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(id = R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp))
              }
        }
      }
    }
  }
}

@Composable
fun RowScope.TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color, Float) -> Unit
) {
  val contentColor by
      animateColorAsState(
          targetValue =
              if (isSelected) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          animationSpec = tween(250),
          label = "colorAnim")

  val iconScale by
      animateFloatAsState(
          targetValue = if (isSelected) 1.25f else 1.0f,
          animationSpec =
              spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
          label = "scaleAnim")

  Column(
      modifier =
          Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).clickable {
            onClick()
          },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center) {
        icon(contentColor, iconScale)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
}

@OptIn(ExperimentalFoundationApi::class)
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
  var albumArt by remember(audio.path) { mutableStateOf<Bitmap?>(AudioArtCache.get(audio.path)) }
  val folderName = remember(audio.path) { File(audio.path).parentFile?.name ?: "Unknown" }

  LaunchedEffect(audio.path) {
    if (albumArt == null) {
      withContext(Dispatchers.IO) {
        val loadedBitmap = AudioArtCache.loadThumbnail(context, audio.path, targetDp = 100)
        if (loadedBitmap != null) {
          albumArt = loadedBitmap
        }
      }
    }
  }

  val backgroundColor =
      if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
  val borderColor =
      when {
        isSelected || isPlayingNow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> Color.Transparent
      }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(backgroundColor)
              .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center) {
              if (albumArt != null) {
                Image(
                    bitmap = albumArt!!.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
              } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
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
              color =
                  if (isPlayingNow) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurface,
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
                      text = duration,
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold)
                }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${audio.artist}  •  $folderName",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }

        if (isSelected) {
          Icon(
              imageVector = Icons.Filled.Done,
              contentDescription = "Selected",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(end = 4.dp).size(24.dp))
        } else if (isPlayingNow) {
          Box(
              modifier =
                  Modifier.padding(end = 4.dp)
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (isAudioPlayingState) R.drawable.ic_pause
                                else R.drawable.ic_play),
                    contentDescription = "Playing State",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
              }
        }
      }
}

@Composable
fun PlayingEqualizerAnim() {
  val transition = rememberInfiniteTransition(label = "dj_eq_transition")

  val bar1 by
      transition.animateFloat(
          initialValue = 4f,
          targetValue = 4f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 450
                        26f at 120 with EaseInOutSine
                        14f at 280
                        4f at 450
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar1")

  val bar2 by
      transition.animateFloat(
          initialValue = 6f,
          targetValue = 6f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 350
                        20f at 90 with EaseInOutSine
                        8f at 220
                        6f at 350
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar2")

  val bar3 by
      transition.animateFloat(
          initialValue = 5f,
          targetValue = 5f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 500
                        28f at 150 with EaseInOutSine
                        16f at 320
                        5f at 500
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar3")

  val bar4 by
      transition.animateFloat(
          initialValue = 4f,
          targetValue = 4f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 400
                        18f at 100 with EaseInOutSine
                        10f at 240
                        4f at 400
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar4")

  Row(
      modifier = Modifier.size(24.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.Bottom) {
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar1.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar2.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar3.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar4.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
      }
}

fun getAudioUriFromPathForMulti(context: Context, path: String): Uri? {
  var cursor: android.database.Cursor? = null
  try {
    cursor =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            MediaStore.Audio.Media.DATA + "=?",
            arrayOf(path),
            null)
    if (cursor != null && cursor.moveToFirst()) {
      val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
      return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
    return null
  } catch (e: Exception) {
    return null
  } finally {
    cursor?.close()
  }
}
