package com.vidmax.player.ui.screen

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.vidmax.player.R
import com.vidmax.player.ui.components.ArtworkImage
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.MusicPlayerViewModel
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🚀 ALBUM ART CACHE HELPER (ল্যাগ দূর করার মূল সমাধান)
object AlbumArtRetriever {
  private val artCache = object : LruCache<String, ByteArray>(15 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int {
      return value.size
    }
  }

  suspend fun getArtwork(context: Context, path: String): ByteArray? = withContext(Dispatchers.IO) {
    if (path.isEmpty()) return@withContext null

    artCache.get(path)?.let { return@withContext it }

    try {
      val retriever = MediaMetadataRetriever()
      val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
      retriever.setDataSource(context, uri)
      val art = retriever.embeddedPicture
      retriever.release()

      if (art != null) {
        artCache.put(path, art)
      }
      return@withContext art
    } catch (e: Exception) {
      return@withContext null
    }
  }
}

// 🔥 Theme Enum Update
enum class PlayerTheme {
  DEFAULT,
  MODERN,
  WAVY
}

// 🔥 Apple Style Liquid Glass Modifier
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    this.clip(shape)
        .background(
            Brush.linearGradient(
                colors =
                    listOf(
                        Color.White.copy(alpha = 0.15f), 
                        Color.White.copy(alpha = 0.03f) 
                        )))
        .border(
            width = 1.dp,
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.35f), 
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f))),
            shape = shape)

// 🔥 MAIN HUB / CONTROLLER
@Composable
fun MusicPlayerScreen(
    viewModel: LibraryViewModel,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    onBack: () -> Unit
) {
  val context = LocalContext.current

  val sharedPreferences = remember {
    context.getSharedPreferences("PlayerThemePrefs", Context.MODE_PRIVATE)
  }

  var currentTheme by remember {
    val savedTheme = sharedPreferences.getString("SAVED_THEME", PlayerTheme.DEFAULT.name)
    val initialTheme =
        try {
          PlayerTheme.valueOf(savedTheme ?: PlayerTheme.DEFAULT.name)
        } catch (e: Exception) {
          PlayerTheme.DEFAULT
        }
    mutableStateOf(initialTheme)
  }

  val changeAndSaveTheme = { newTheme: PlayerTheme ->
    currentTheme = newTheme
    sharedPreferences.edit().putString("SAVED_THEME", newTheme.name).apply()
  }

  // Smooth transition between themes
  Crossfade(targetState = currentTheme, label = "ThemeSwitcher", animationSpec = tween(500)) { theme
    ->
    when (theme) {
      PlayerTheme.DEFAULT -> {
        DefaultPlayerUI(
            viewModel = viewModel,
            musicPlayerViewModel = musicPlayerViewModel,
            onBack = onBack,
            onThemeChange = changeAndSaveTheme
        )
      }
      PlayerTheme.MODERN -> {
        ModernPlayerScreen(
            viewModel = viewModel, onBack = onBack, onThemeChange = changeAndSaveTheme)
      }
      PlayerTheme.WAVY -> {
        WavyPlayerScreen(
            viewModel = viewModel, onBack = onBack, onThemeChange = changeAndSaveTheme)
      }
    }
  }
}

// 🔥 DEFAULT UI
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun DefaultPlayerUI(
    viewModel: LibraryViewModel,
    musicPlayerViewModel: MusicPlayerViewModel? = null,
    onBack: () -> Unit,
    onThemeChange: (PlayerTheme) -> Unit
) {
  val context = LocalContext.current
  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

  // ---- ONLINE MODE STATE ----
  val onlineState = musicPlayerViewModel?.uiState?.collectAsState()
  val isOnlineMode = musicPlayerViewModel != null && onlineState?.value?.currentSong != null

  // ---- DISPLAY VALUES (conditional: online vs offline) ----
  val offlineTitle by viewModel.recentlyPlayedTitle.collectAsState()
  val offlineArtist by viewModel.currentAudioArtist.collectAsState()
  val offlinePath by viewModel.recentlyPlayedPath.collectAsState()
  val offlineIsPlaying by viewModel.isAudioPlaying.collectAsState()
  val offlinePosition by viewModel.audioPosition.collectAsState()
  val offlineDuration by viewModel.audioDuration.collectAsState()
  val offlineIsShuffle by viewModel.isShuffleEnabled.collectAsState()
  val offlineRepeat by viewModel.audioRepeatMode.collectAsState()
  val offlineFavorites by viewModel.favoriteAudioPaths.collectAsState()
  val offlineQueueList by viewModel.currentQueue.collectAsState()
  val offlineQueueIndex by viewModel.currentQueueIndex.collectAsState()
  val offlineTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
  val offlineIsBoosted by viewModel.musicBoostEnabled.collectAsState()

  val title = if (isOnlineMode) onlineState?.value?.currentSong?.title ?: "" else offlineTitle
  val artist = if (isOnlineMode) onlineState?.value?.currentSong?.artist ?: "" else offlineArtist
  val isPlaying = if (isOnlineMode) onlineState?.value?.isPlaying ?: false else offlineIsPlaying
  val currentPosition = if (isOnlineMode) onlineState?.value?.position ?: 0L else offlinePosition
  val duration = if (isOnlineMode) onlineState?.value?.duration ?: 0L else offlineDuration

  val isShuffleEnabled = if (isOnlineMode) false else offlineIsShuffle
  val repeatMode = if (isOnlineMode) LoopMode.NONE else offlineRepeat

  val favoritePaths = if (isOnlineMode) emptySet<String>() else offlineFavorites
  val isFavorite = if (isOnlineMode) onlineState?.value?.isFavorite ?: false else favoritePaths.contains(offlinePath)

  val currentTimerMinutes = if (isOnlineMode) 0 else offlineTimerMinutes
  val isAudioBoosted = if (isOnlineMode) false else offlineIsBoosted

  // Online queue: list of SongItem; offline: list of AudioItem
  val onlineQueue by remember(isOnlineMode, onlineState?.value?.queue) { mutableStateOf(onlineState?.value?.queue ?: emptyList()) }
  val onlineQueueIdx by remember(isOnlineMode, onlineState?.value?.queueIndex) { mutableStateOf(onlineState?.value?.queueIndex ?: -1) }
  val queueList: List<*> = if (isOnlineMode) onlineQueue else offlineQueueList
  val currentQueueIndex = if (isOnlineMode) onlineQueueIdx else offlineQueueIndex

  // ---- ACTION WRAPPERS ----
  val onTogglePlayPause: () -> Unit = if (isOnlineMode) {
    { musicPlayerViewModel?.togglePlayPause() }
  } else {
    { viewModel.toggleAudio() }
  }
  val onSeekTo: (Long) -> Unit = if (isOnlineMode) {
    { pos -> musicPlayerViewModel?.seekTo(pos) }
  } else {
    { pos -> viewModel.seekAudio(pos) }
  }
  val onNext: () -> Unit = if (isOnlineMode) {
    { musicPlayerViewModel?.playNextOnlineSong() }
  } else {
    { viewModel.playNextAudio() }
  }
  val onPrevious: () -> Unit = if (isOnlineMode) {
    { musicPlayerViewModel?.playPreviousOnlineSong() }
  } else {
    { viewModel.playPreviousAudio() }
  }
  val onToggleShuffle: () -> Unit = if (isOnlineMode) {
    {}
  } else {
    { viewModel.toggleShuffle() }
  }
  val onToggleRepeat: () -> Unit = if (isOnlineMode) {
    {}
  } else {
    { viewModel.toggleRepeat() }
  }
  val onToggleFavorite: () -> Unit = if (isOnlineMode) {
    { musicPlayerViewModel?.toggleFavorite() }
  } else {
    { viewModel.toggleFavorite(offlinePath) }
  }
  val onQueueItemClick: (Any, Int) -> Unit = if (isOnlineMode) {
    { _, idx -> musicPlayerViewModel?.playSongFromQueue(idx) }
  } else {
    { _, idx -> viewModel.playAudioFromList(offlineQueueList as List<com.vidmax.player.data.model.AudioItem>, idx) }
  }
  val formatDurationFn: (Long) -> String = { ms -> viewModel.formatDuration(ms) }

  // Position updater for online mode
  LaunchedEffect(isOnlineMode, isPlaying) {
    if (isOnlineMode && isPlaying) {
      while (true) {
        musicPlayerViewModel?.updatePosition()
        delay(250)
      }
    }
  }

  // Dialog state
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPropertiesDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var showTimerDialog by remember { mutableStateOf(false) }
  var showQueueSheet by remember { mutableStateOf(false) }

  val currentPath = offlinePath
  val onlineCurrentSong = if (isOnlineMode) onlineState?.value?.currentSong else null
  val onlineThumbnailUrl = onlineCurrentSong?.thumbnailUrl
  val onlineVideoId = onlineCurrentSong?.videoId

  // Volume Controller
  var showVolumeIndicator by remember { mutableStateOf(false) }
  var internalVolumeLevel by remember {
    mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
  }

  LaunchedEffect(isAudioBoosted) {
    if (isAudioBoosted && internalVolumeLevel < 2.0f) {
      internalVolumeLevel = 2.0f
      audioManager.setStreamVolume(
          AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
      viewModel.setCustomVolume(200)
    } else if (!isAudioBoosted && internalVolumeLevel > 1.0f) {
      internalVolumeLevel = 1.0f
      viewModel.setCustomVolume(100)
    }
  }

  // 🚀 OPTIMIZED: Fast ByteArray Extraction with Memory Cache
  var artByteArray by remember(currentPath) { mutableStateOf<ByteArray?>(null) }
  var isArtLoaded by remember(currentPath) { mutableStateOf(false) }

  LaunchedEffect(currentPath) {
    if (currentPath.isNotEmpty()) {
      artByteArray = AlbumArtRetriever.getArtwork(context, currentPath)
    } else {
      artByteArray = null
    }
    isArtLoaded = true
  }

  val deleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
              Toast.makeText(context, "Audio Deleted Successfully", Toast.LENGTH_SHORT).show()
              viewModel.pauseAudio()
              onBack()
            }
          }

  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = { Text("Delete Audio", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete '$title'? This action cannot be undone.") },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                try {
                  val uri = getAudioUriFromPath(context, currentPath)
                  val file = File(currentPath)
                  var deleted = false

                  if (file.exists() && file.delete()) deleted = true
                  else if (uri != null)
                      deleted = context.contentResolver.delete(uri, null, null) > 0

                  if (deleted) {
                    Toast.makeText(context, "Audio Deleted", Toast.LENGTH_SHORT).show()
                    viewModel.pauseAudio()
                    onBack()
                  } else {
                    Toast.makeText(context, "Failed to delete.", Toast.LENGTH_SHORT).show()
                  }
                } catch (e: SecurityException) {
                  val uri = getAudioUriFromPath(context, currentPath)
                  if (uri != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                      val pendingIntent =
                          MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                      deleteLauncher.launch(
                          IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                      val recoverableException = e as? RecoverableSecurityException
                      recoverableException?.userAction?.actionIntent?.let { intent ->
                        deleteLauncher.launch(
                            IntentSenderRequest.Builder(intent.intentSender).build())
                      }
                    } else {
                      Toast.makeText(context, "Permission Denied!", Toast.LENGTH_LONG).show()
                    }
                  }
                } catch (e: Exception) {
                  Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

  if (showPropertiesDialog) {
    val file = File(currentPath)
    val fileSizeMb =
        if (file.exists()) String.format(java.util.Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0))
        else "Unknown Size"

    AlertDialog(
        onDismissRequest = { showPropertiesDialog = false },
        title = { Text("Audio Properties", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Title: $title", fontSize = 14.sp)
            Text("Artist: $artist", fontSize = 14.sp)
            Text(
                "Path: $currentPath",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Size: $fileSizeMb", fontSize = 14.sp)
          }
        },
        confirmButton = {
          TextButton(onClick = { showPropertiesDialog = false }) { Text("Close") }
        })
  }

  if (showTimerDialog) {
    AlertDialog(
        onDismissRequest = { showTimerDialog = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
        text = {
          Column {
            listOf(0, 15, 30, 60, 120).forEach { mins ->
              val text = if (mins == 0) "Off" else "$mins Minutes"
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .clickable {
                            viewModel.setSleepTimer(mins)
                            showTimerDialog = false
                          }
                          .padding(vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint =
                            if (currentTimerMinutes == mins) MaterialTheme.colorScheme.primary
                            else Color.Transparent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text, fontSize = 16.sp)
                  }
            }
          }
        },
        confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Close") } })
  }

  Box(
      modifier =
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { showVolumeIndicator = true },
                onDragEnd = { showVolumeIndicator = false },
                onDragCancel = { showVolumeIndicator = false },
                onDrag = { change, dragAmount ->
                  change.consume()
                  val sensitivity = 1.2f
                  val delta = (-dragAmount.y / size.height) * sensitivity
                  val maxLimit = if (isAudioBoosted) 2.0f else 1.0f

                  internalVolumeLevel = (internalVolumeLevel + delta).coerceIn(0f, maxLimit)
                  val maxHardwareVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                  if (internalVolumeLevel <= 1.0f) {
                    val targetHardwareVol = (internalVolumeLevel * maxHardwareVol).roundToInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetHardwareVol, 0)
                    if (!isOnlineMode) viewModel.setCustomVolume(100)
                  } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxHardwareVol, 0)
                    val targetSoftwareVol = (internalVolumeLevel * 100).roundToInt()
                    if (!isOnlineMode) viewModel.setCustomVolume(targetSoftwareVol)
                  }
                })
          }) {

        // 🔥 GLIDE: Blurred Background
        Crossfade(targetState = isArtLoaded, label = "bgFade", animationSpec = tween(600)) { loaded ->
            if (isOnlineMode && onlineThumbnailUrl != null) {
                ArtworkImage(
                    videoId = onlineVideoId,
                    fallbackUrl = onlineThumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 80.dp)
                        .graphicsLayer { alpha = 0.12f },
                    requestBuilder = {
                        it.diskCacheStrategy(DiskCacheStrategy.ALL).override(100)
                    }
                )
            } else if (loaded && artByteArray != null) {
                GlideImage(
                    model = artByteArray,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 80.dp)
                        .graphicsLayer { alpha = 0.12f }
                ) { requestBuilder ->
                    requestBuilder
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .signature(ObjectKey(currentPath + "_bg"))
                        .override(100)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {

          // --- TOP BAR ---
          Row(
              modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                  Icon(
                      Icons.Default.KeyboardArrowDown,
                      "Back",
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(32.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.Center
                      ) {
                          if (isOnlineMode) {
                              Box(
                                  modifier = Modifier
                                      .size(8.dp)
                                      .clip(CircleShape)
                                      .background(Color(0xFF1DB954))
                              )
                              Spacer(modifier = Modifier.width(6.dp))
                          }
                          Text(
                              text = if (isOnlineMode) "Online Stream" else "Now Playing",
                              fontSize = 12.sp,
                              color = if (isOnlineMode) Color(0xFF1DB954).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                              fontWeight = FontWeight.Medium)
                      }
                      Text(
                          text = artist.ifEmpty { "VidMax Player" },
                          fontSize = 14.sp,
                          color = MaterialTheme.colorScheme.onSurface,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis)
                    }

                // 🔥 Menu Button With THEME OPTIONS
                Box {
                  IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp))
                  }
                  DropdownMenu(
                      expanded = showMoreMenu,
                      onDismissRequest = { showMoreMenu = false },
                      modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        DropdownMenuItem(
                            text = {
                              Text("Properties", color = MaterialTheme.colorScheme.onSurface)
                            },
                            leadingIcon = {
                              Icon(
                                  Icons.Default.Info,
                                  null,
                                  tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                              showMoreMenu = false
                              showPropertiesDialog = true
                            })

                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                              Icon(
                                  Icons.Default.Delete,
                                  null,
                                  tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                              showMoreMenu = false
                              showDeleteConfirmDialog = true
                            })
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .height(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
                        // 🔥 THEME SELECTION MENU ITEMS
                        DropdownMenuItem(
                            text = {
                              Text("Default Theme", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                              showMoreMenu = false
                              onThemeChange(PlayerTheme.DEFAULT)
                            })
                        DropdownMenuItem(
                            text = {
                              Text("Modern Circle", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                              showMoreMenu = false
                              onThemeChange(PlayerTheme.MODERN)
                            })
                        DropdownMenuItem(
                            text = {
                              Text("Wavy Pastel", color = MaterialTheme.colorScheme.onSurface)
                            },
                            onClick = {
                              showMoreMenu = false
                              onThemeChange(PlayerTheme.WAVY)
                            })
                      }
                }
              }

          Spacer(modifier = Modifier.weight(0.5f))

          // 🔥 ALBUM ART ANIMATION
          val baseScale by
              animateFloatAsState(
                  targetValue = if (isPlaying) 1.0f else 0.85f,
                  animationSpec =
                      spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessLow),
                  label = "albumBaseScale")

          val infiniteTransition = rememberInfiniteTransition(label = "breathing")
          val breathScale by
              infiniteTransition.animateFloat(
                  initialValue = 1.0f,
                  targetValue = if (isPlaying) 1.02f else 1.0f,
                  animationSpec =
                      infiniteRepeatable(
                          animation = tween(2500, easing = EaseInOutSine),
                          repeatMode = RepeatMode.Reverse),
                  label = "albumBreathScale")

          val finalAlbumScale = if (isPlaying) baseScale * breathScale else baseScale

          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .aspectRatio(1f)
                      .scale(finalAlbumScale)
                      .shadow(
                          32.dp,
                          RoundedCornerShape(24.dp),
                          ambientColor = MaterialTheme.colorScheme.primary,
                          spotColor = MaterialTheme.colorScheme.primary)
                      .clip(RoundedCornerShape(24.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center) {
                
                // 🔥 GLIDE: Main Album Art
                if (isOnlineMode && onlineThumbnailUrl != null) {
                    ArtworkImage(
                        videoId = onlineVideoId,
                        fallbackUrl = onlineThumbnailUrl,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        requestBuilder = {
                            it.diskCacheStrategy(DiskCacheStrategy.ALL).override(500)
                        }
                    )
                } else if (isArtLoaded && artByteArray != null) {
                    GlideImage(
                        model = artByteArray,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    ) { requestBuilder ->
                        requestBuilder
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .signature(ObjectKey(currentPath))
                            .override(500)
                    }
                } else if (isArtLoaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
              }

          Spacer(modifier = Modifier.weight(0.5f))

          // --- SONG INFO & ACTIONS ROW ---
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                  Text(
                      text = title.ifEmpty { "Unknown Song" },
                      color = MaterialTheme.colorScheme.onSurface,
                      fontSize = 24.sp,
                      fontWeight = FontWeight.ExtraBold,
                      maxLines = 1,
                      modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                  Text(
                      text = artist,
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                      fontSize = 16.sp,
                      modifier = Modifier.padding(top = 4.dp),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis)
                }

                Row(
                    modifier =
                        Modifier.liquidGlass(RoundedCornerShape(50))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      // Share Button
                      IconButton(
                          onClick = {
                            val uri = getAudioUriFromPath(context, currentPath)
                            if (uri != null) {
                              val shareIntent =
                                  Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "Listening to $title 🎵")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                  }
                              context.startActivity(
                                  Intent.createChooser(shareIntent, "Share Audio"))
                            } else {
                              Toast.makeText(
                                      context, "Could not share this file", Toast.LENGTH_SHORT)
                                  .show()
                            }
                          },
                          modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Share,
                                "Share",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp))
                          }

                      // Favorite Button
                      IconButton(
                          onClick = { onToggleFavorite() },
                          modifier = Modifier.size(36.dp)) {
                            Crossfade(targetState = isFavorite, label = "fav") { fav ->
                              Icon(
                                  imageVector =
                                      if (fav) Icons.Default.Favorite
                                      else Icons.Default.FavoriteBorder,
                                  contentDescription = "Favorite",
                                  tint =
                                      if (fav) Color.Red
                                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                  modifier = Modifier.size(20.dp))
                            }
                          }
                    }
              }

          Spacer(modifier = Modifier.height(24.dp))

          // 🔥 PREMIUM FLUID SLIDER
          var isDraggingSlider by remember { mutableStateOf(false) }
          var sliderDragValue by remember { mutableFloatStateOf(0f) }
          val safeDuration = if (duration > 0) duration else 1L

          val targetProgress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
          val coroutineScope = rememberCoroutineScope()

          val animatedProgress by
              animateFloatAsState(
                  targetValue = targetProgress,
                  animationSpec =
                      if (isDraggingSlider) snap()
                      else tween(durationMillis = 500, easing = LinearEasing),
                  label = "fluidProgressAnim")

          val displayProgress = if (isDraggingSlider) sliderDragValue else animatedProgress

          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically
          ) {
              // Current Time
              val displayPos = if (isDraggingSlider) (sliderDragValue * safeDuration).toLong() else currentPosition
              Text(
                  text = formatDurationFn(displayPos),
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.width(44.dp)
              )

              // Seekbar Line
              BoxWithConstraints(
                  modifier = Modifier
                      .weight(1f)
                      .height(36.dp)
                      .padding(horizontal = 12.dp)
                      .pointerInput(safeDuration) {
                        detectTapGestures(
                            onPress = { offset ->
                              isDraggingSlider = true
                              sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                              onSeekTo((sliderDragValue * safeDuration).toLong())
                              tryAwaitRelease()
                              coroutineScope.launch {
                                delay(200)
                                isDraggingSlider = false
                              }
                            })
                      }
                      .pointerInput(safeDuration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                              isDraggingSlider = true
                              sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                              onSeekTo((sliderDragValue * safeDuration).toLong())
                              coroutineScope.launch {
                                delay(200)
                                isDraggingSlider = false
                              }
                            },
                            onDragCancel = { isDraggingSlider = false },
                            onDrag = { change, _ ->
                              change.consume()
                              sliderDragValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                              onSeekTo((sliderDragValue * safeDuration).toLong())
                            })
                      }
              ) {
                  val thumbWidth = 4.dp
                  val thumbHeight = 20.dp
                  val trackHeight = 8.dp

                  val thumbX = maxWidth * displayProgress
                  val thumbOffset = (thumbX - (thumbWidth / 2)).coerceIn(0.dp, maxWidth - thumbWidth)

                  // Background Track
                  Box(
                      modifier = Modifier
                          .align(Alignment.Center)
                          .fillMaxWidth()
                          .height(trackHeight)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                  )

                  // Active Track (Colored)
                  Box(
                      modifier = Modifier
                          .align(Alignment.CenterStart)
                          .width(thumbX)
                          .height(trackHeight)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary)
                  )

                  // Vertical Thumb
                  Box(
                      modifier = Modifier
                          .align(Alignment.CenterStart)
                          .offset(x = thumbOffset)
                          .width(thumbWidth)
                          .height(thumbHeight)
                          .clip(RoundedCornerShape(50))
                          .background(MaterialTheme.colorScheme.primary)
                  )
              }

              // Total Duration
              Text(
                  text = formatDurationFn(duration),
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  textAlign = TextAlign.End,
                  modifier = Modifier.width(44.dp)
              )
          }

          Spacer(modifier = Modifier.height(24.dp))

          // 🔥 PLAYBACK CONTROLS
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(horizontal = 8.dp)
                      .height(72.dp)
                      .liquidGlass(RoundedCornerShape(36.dp))
                      .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onToggleShuffle() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_shuffle),
                          contentDescription = "Shuffle",
                          tint =
                              if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                          modifier = Modifier.size(24.dp))
                    }
                IconButton(
                    onClick = { onPrevious() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_skip_previous),
                          contentDescription = "Previous",
                          tint = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(28.dp))
                    }
                
                // 🔥 ANIMATED PLAY/PAUSE BUTTON
                val playPauseRotation by animateFloatAsState(
                    targetValue = if (isPlaying) 180f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "playPauseRotation"
                )

                Box(
                    modifier =
                        Modifier.size(60.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center) {
                      Crossfade(
                          targetState = isPlaying,
                          animationSpec = tween(300),
                          modifier = Modifier.graphicsLayer { rotationZ = playPauseRotation },
                          label = "playPauseFade") { playing ->
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (playing) R.drawable.ic_pause
                                            else R.drawable.ic_play),
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp))
                          }
                    }
                    
                IconButton(
                    onClick = { onNext() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_skip_next),
                          contentDescription = "Next",
                          tint = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(28.dp))
                    }
                IconButton(
                    onClick = { onToggleRepeat() }, modifier = Modifier.size(48.dp)) {
                      val iconRes =
                          when (repeatMode) {
                            LoopMode.ONE -> R.drawable.ic_repeat_one
                            else -> R.drawable.ic_repeat
                          }
                      val iconTint =
                          if (repeatMode == LoopMode.NONE)
                              MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                          else MaterialTheme.colorScheme.primary
                      Icon(
                          painter = painterResource(id = iconRes),
                          contentDescription = "Repeat",
                          tint = iconTint,
                          modifier = Modifier.size(24.dp))
                    }
              }

          Spacer(modifier = Modifier.height(32.dp))

          val queueInteractionSource = remember { MutableInteractionSource() }
          val isQueuePressed by queueInteractionSource.collectIsPressedAsState()
          val queueScale by
              animateFloatAsState(
                  targetValue = if (isQueuePressed) 0.9f else 1.0f,
                  animationSpec =
                      spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessLow),
                  label = "queueScale")

          val timerInteractionSource = remember { MutableInteractionSource() }
          val isTimerPressed by timerInteractionSource.collectIsPressedAsState()
          val timerScale by
              animateFloatAsState(
                  targetValue =
                      if (isTimerPressed) 0.9f else if (currentTimerMinutes > 0) 1.05f else 1.0f,
                  animationSpec =
                      spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessLow),
                  label = "timerScale")

          val boostInteractionSource = remember { MutableInteractionSource() }
          val isBoostPressed by boostInteractionSource.collectIsPressedAsState()
          val boostScale by
              animateFloatAsState(
                  targetValue = if (isBoostPressed) 0.9f else if (isAudioBoosted) 1.05f else 1.0f,
                  animationSpec =
                      spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessLow),
                  label = "boostScale")

          // 🔥 BOTTOM PILLS
          Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically) {
                // Queue Pill
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .height(48.dp)
                            .scale(queueScale)
                            .liquidGlass(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = queueInteractionSource,
                                indication = LocalIndication.current) {
                                  showQueueSheet = true
                                }
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                      Icon(
                          imageVector = Icons.Default.Menu,
                          contentDescription = "Queue",
                          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                          modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                          text = "Queue",
                          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                          fontWeight = FontWeight.Medium,
                          fontSize = 14.sp)
                    }
                Spacer(modifier = Modifier.width(16.dp))

                // Timer Pill
                Box(
                    modifier =
                        Modifier.size(48.dp)
                            .scale(timerScale)
                            .let {
                              if (currentTimerMinutes > 0) {
                                it.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                              } else {
                                it.liquidGlass(CircleShape)
                              }
                            }
                            .clickable(
                                interactionSource = timerInteractionSource,
                                indication = LocalIndication.current) {
                                  showTimerDialog = true
                                },
                    contentAlignment = Alignment.Center) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_timer),
                          contentDescription = "Timer",
                          tint =
                              if (currentTimerMinutes > 0) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                          modifier = Modifier.size(20.dp))
                    }
                Spacer(modifier = Modifier.width(16.dp))

                // Boost Pill
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .height(48.dp)
                            .scale(boostScale)
                            .let {
                              if (isAudioBoosted) {
                                it.clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                              } else {
                                it.liquidGlass(RoundedCornerShape(50))
                              }
                            }
                            .clickable(
                                interactionSource = boostInteractionSource,
                                indication = LocalIndication.current) {
                                  viewModel.toggleMusicBoost()
                                  val isNowBoosted = !isAudioBoosted
                                  val msg =
                                      if (isNowBoosted) {
                                        "🚀 Software Boost ON: Volume forced to 200%"
                                      } else {
                                        "🎵 Boost OFF: Volume back to 100%"
                                      }
                                  Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                      Icon(
                          painterResource(id = R.drawable.ic_volume_up),
                          null,
                          tint =
                              if (isAudioBoosted) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                          modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                          text = if (isAudioBoosted) "200%" else "Boost",
                          color =
                              if (isAudioBoosted) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                          fontWeight = FontWeight.Medium,
                          fontSize = 14.sp)
                    }
              }
        }

        // 🔥 VOLUME INDICATOR (OVERLAY)
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
              val displayVolPercent = (internalVolumeLevel * 100).roundToInt()
              val maxProgress = if (isAudioBoosted) 2.0f else 1.0f
              Box(
                  modifier =
                      Modifier.liquidGlass(RoundedCornerShape(24.dp))
                          .padding(horizontal = 24.dp, vertical = 12.dp),
                  contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(
                          text = "Volume: $displayVolPercent%",
                          color = Color.White,
                          fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(8.dp))
                      LinearProgressIndicator(
                          progress = internalVolumeLevel / maxProgress,
                          color = MaterialTheme.colorScheme.primary,
                          trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                          modifier = Modifier.width(100.dp).height(4.dp))
                    }
                  }
            }

        // QUEUE BOTTOM SHEET
        if (showQueueSheet) {
          ModalBottomSheet(
              onDismissRequest = { showQueueSheet = false },
              containerColor = MaterialTheme.colorScheme.surface,
              contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .fillMaxHeight(0.6f)) {
                      Text(
                          text = "Up Next",
                          fontSize = 20.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface)
                      Spacer(modifier = Modifier.height(16.dp))

                      if (queueList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center) {
                              Text(
                                  text = "Queue list is currently empty.",
                                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                  fontSize = 16.sp)
                            }
                      } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                          itemsIndexed(queueList) { index, audio ->
                            val isCurrentlyPlaying = index == currentQueueIndex

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrentlyPlaying)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            else Color.Transparent)
                                        .clickable {
                                          if (isOnlineMode) {
                                            musicPlayerViewModel?.playSongFromQueue(index)
                                          } else {
                                            viewModel.playAudioFromList(offlineQueueList, index)
                                          }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                  if (isOnlineMode) {
                                    val song = audio as? com.vidmax.player.data.model.SongItem
                                    GlideImage(
                                        model = song?.thumbnailUrl ?: "",
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                  } else {
                                    QueueItemThumbnail(
                                        path = (audio as com.vidmax.player.data.model.AudioItem).path,
                                        isCurrentlyPlaying = isCurrentlyPlaying)
                                  }

                                  Spacer(modifier = Modifier.width(16.dp))
                                  Column(modifier = Modifier.weight(1f)) {
                                    val qTitle = if (isOnlineMode) (audio as com.vidmax.player.data.model.SongItem).title else (audio as com.vidmax.player.data.model.AudioItem).title
                                    val qArtist = if (isOnlineMode) (audio as com.vidmax.player.data.model.SongItem).artist else (audio as com.vidmax.player.data.model.AudioItem).artist
                                    Text(
                                        text = qTitle,
                                        color =
                                            if (isCurrentlyPlaying)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        fontWeight =
                                            if (isCurrentlyPlaying) FontWeight.Bold
                                            else FontWeight.Medium,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = qArtist,
                                        color =
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
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

// 🚀 OPTIMIZED: Fast Queue Thumbnail Component
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun QueueItemThumbnail(path: String, isCurrentlyPlaying: Boolean) {
  val context = LocalContext.current
  var artByteArray by remember(path) { mutableStateOf<ByteArray?>(null) }
  var isArtLoaded by remember(path) { mutableStateOf(false) }

  LaunchedEffect(path) {
    if (path.isNotEmpty()) {
      artByteArray = AlbumArtRetriever.getArtwork(context, path)
    } else {
      artByteArray = null
    }
    isArtLoaded = true
  }

  Box(
      modifier = Modifier.size(48.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center) {
        
        if (isArtLoaded && artByteArray != null) {
            GlideImage(
                model = artByteArray,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) { requestBuilder ->
                requestBuilder
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .signature(ObjectKey(path))
                    .override(100)
            }
        } else if (isArtLoaded) {
            Icon(
                painter = painterResource(
                    id = if (isCurrentlyPlaying) R.drawable.ic_pause else R.drawable.ic_music_note
                ),
                contentDescription = null,
                tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }

        if (isCurrentlyPlaying && artByteArray != null) {
          Box(
              modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(id = R.drawable.ic_pause),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp))
              }
        }
      }
}

fun getAudioUriFromPath(context: Context, path: String): Uri? {
  val cursor =
      context.contentResolver.query(
          MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
          arrayOf(MediaStore.Audio.Media._ID),
          MediaStore.Audio.Media.DATA + "=?",
          arrayOf(path),
          null)
  return cursor?.use {
    if (it.moveToFirst()) {
      val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
      ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    } else null
  }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicPlayerContent(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    val playerState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            while (true) {
                viewModel.updatePosition()
                delay(250)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Online Music",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Box(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shadow(20.dp, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                GlideImage(
                    model = playerState.currentSong?.thumbnailUrl ?: "",
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (playerState.isLoadingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            playerState.currentSong?.let { song ->
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            val safeDuration = if (playerState.duration > 0) playerState.duration else 1L
            val progress =
                (playerState.position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

            var isDraggingSlider by remember { mutableStateOf(false) }
            var sliderDragValue by remember { mutableFloatStateOf(0f) }
            val coroutineScope = rememberCoroutineScope()

            val displayProgress = if (isDraggingSlider) sliderDragValue else progress

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimeMs(if (isDraggingSlider) (sliderDragValue * safeDuration).toLong() else playerState.position),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(44.dp)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 12.dp)
                        .pointerInput(safeDuration) {
                            detectTapGestures(
                                onPress = { offset ->
                                    isDraggingSlider = true
                                    sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    viewModel.seekTo((sliderDragValue * safeDuration).toLong())
                                    tryAwaitRelease()
                                    coroutineScope.launch {
                                        delay(200)
                                        isDraggingSlider = false
                                    }
                                }
                            )
                        }
                        .pointerInput(safeDuration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDraggingSlider = true
                                    sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    viewModel.seekTo((sliderDragValue * safeDuration).toLong())
                                    coroutineScope.launch {
                                        delay(200)
                                        isDraggingSlider = false
                                    }
                                },
                                onDragCancel = { isDraggingSlider = false },
                                onDrag = { change, _ ->
                                    change.consume()
                                    sliderDragValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val thumbWidth = 4.dp
                    val thumbHeight = 20.dp
                    val trackHeight = 8.dp
                    val thumbX = maxWidth * displayProgress
                    val thumbOffset = (thumbX - (thumbWidth / 2)).coerceIn(0.dp, maxWidth - thumbWidth)

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(thumbX)
                            .height(trackHeight)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = thumbOffset)
                            .width(thumbWidth)
                            .height(thumbHeight)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Text(
                    text = formatTimeMs(playerState.duration),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .clickable(enabled = !playerState.isLoadingStream) {
                            viewModel.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (playerState.isLoadingStream) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (playerState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = playerState.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                playerState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimeMs(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
