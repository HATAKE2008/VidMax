package com.vidmax.player.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.LoopMode
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ModernPlayerScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onThemeChange: (PlayerTheme) -> Unit
) {
  val context = LocalContext.current
  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

  val title by viewModel.recentlyPlayedTitle.collectAsState()
  val artist by viewModel.currentAudioArtist.collectAsState()
  val currentPath by viewModel.recentlyPlayedPath.collectAsState()
  val isPlaying by viewModel.isAudioPlaying.collectAsState()
  val currentPosition by viewModel.audioPosition.collectAsState()
  val duration by viewModel.audioDuration.collectAsState()
  val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
  val repeatMode by viewModel.audioRepeatMode.collectAsState()
  val favoritePaths by viewModel.favoriteAudioPaths.collectAsState()
  val isFavorite = favoritePaths.contains(currentPath)

  // Dialogs & States
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPropertiesDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }
  var showTimerDialog by remember { mutableStateOf(false) }
  val currentTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
  val isAudioBoosted by viewModel.musicBoostEnabled.collectAsState()
  var showQueueSheet by remember { mutableStateOf(false) }
  val queueList by viewModel.currentQueue.collectAsState()
  val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()

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

  var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isScreenReady by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    delay(350)
    isScreenReady = true
  }

  LaunchedEffect(currentPath, isScreenReady) {
    if (isScreenReady && currentPath.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        try {
          val retriever = MediaMetadataRetriever()
          val uri =
              if (currentPath.startsWith("/")) Uri.fromFile(File(currentPath))
              else Uri.parse(currentPath)
          retriever.setDataSource(context, uri)
          val art = retriever.embeddedPicture
          albumArtBitmap =
              if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
          retriever.release()
        } catch (e: Exception) {
          albumArtBitmap = null
        }
      }
    }
  }

  // --- FAKE WAVEFORM GENERATOR ---
  val barCount = 45
  val barHeights = remember(currentPath) { List(barCount) { (0.2f + Random.nextFloat() * 0.8f) } }

  // DIALOGS
  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = { Text("Delete Audio", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete '$title'?") },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                try {
                  if (File(currentPath).delete()) {
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    viewModel.pauseAudio()
                    onBack()
                  }
                } catch (e: Exception) {
                  Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }
              }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
              }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
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
        title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
        text = {
          Column {
            listOf(0, 15, 30, 60, 120).forEach { mins ->
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
                        Icons.Default.Check,
                        null,
                        tint =
                            if (currentTimerMinutes == mins) MaterialTheme.colorScheme.primary
                            else Color.Transparent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (mins == 0) "Off" else "$mins Minutes", fontSize = 16.sp)
                  }
            }
          }
        },
        confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Close") } })
  }

  // MAIN LAYOUT
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.Black) // Dark base for Modern Theme
              .pointerInput(Unit) {
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
                      val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                      if (internalVolumeLevel <= 1.0f) {
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (internalVolumeLevel * maxVol).roundToInt(),
                            0)
                        viewModel.setCustomVolume(100)
                      } else {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                        viewModel.setCustomVolume((internalVolumeLevel * 100).roundToInt())
                      }
                    })
              }) {
        // 🔥 HEAVILY BLURRED BACKGROUND
        Crossfade(targetState = albumArtBitmap, animationSpec = tween(800), label = "bgFade") { bmp
          ->
          if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().blur(radius = 120.dp).graphicsLayer { alpha = 0.4f })
          } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)))
          }
        }

        // Gradient Overlay for better text visibility
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

        // FOREGROUND
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
              // --- TOP BAR ---
              Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    // Circle Back Button
                    Box(
                        modifier =
                            Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { onBack() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              Icons.Default.KeyboardArrowLeft,
                              "Back",
                              tint = Color.White,
                              modifier = Modifier.size(32.dp))
                        }

                    Text(
                        "Now Playing",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      // Circle Favorite Button
                      Box(
                          modifier =
                              Modifier.size(48.dp)
                                  .clip(CircleShape)
                                  .background(Color.White.copy(alpha = 0.2f))
                                  .clickable { viewModel.toggleFavorite(currentPath) },
                          contentAlignment = Alignment.Center) {
                            Crossfade(targetState = isFavorite, label = "fav") { fav ->
                              Icon(
                                  if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                  null,
                                  tint = if (fav) Color.Red else Color.White,
                                  modifier = Modifier.size(24.dp))
                            }
                          }

                      Spacer(modifier = Modifier.width(12.dp))

                      // Menu
                      Box {
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { showMoreMenu = true },
                            contentAlignment = Alignment.Center) {
                              Icon(
                                  Icons.Default.MoreVert,
                                  "Menu",
                                  tint = Color.White,
                                  modifier = Modifier.size(24.dp))
                            }
                        DropdownMenu(
                            expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                              DropdownMenuItem(
                                  text = { Text("Properties") },
                                  onClick = {
                                    showMoreMenu = false
                                    showPropertiesDialog = true
                                  })
                              DropdownMenuItem(
                                  text = { Text("Sleep Timer") },
                                  onClick = {
                                    showMoreMenu = false
                                    showTimerDialog = true
                                  })
                              DropdownMenuItem(
                                  text = {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
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
                                              MaterialTheme.colorScheme.onSurface.copy(
                                                  alpha = 0.12f)))
                              DropdownMenuItem(
                                  text = { Text("Default Theme") },
                                  onClick = {
                                    showMoreMenu = false
                                    onThemeChange(PlayerTheme.DEFAULT)
                                  })
                              DropdownMenuItem(
                                  text = { Text("Modern Circle (Active)") },
                                  onClick = { showMoreMenu = false })
                              DropdownMenuItem(
                                  text = { Text("Wavy Pastel") },
                                  onClick = {
                                    showMoreMenu = false
                                    onThemeChange(PlayerTheme.WAVY)
                                  })
                            }
                      }
                    }
                  }

              Spacer(modifier = Modifier.height(48.dp))

              // --- ALBUM ART (Perfect Circle) ---
              Box(
                  modifier =
                      Modifier.fillMaxWidth(0.85f)
                          .aspectRatio(1f)
                          .shadow(32.dp, CircleShape)
                          .clip(CircleShape)
                          .background(Color.DarkGray),
                  contentAlignment = Alignment.Center) {
                    Crossfade(targetState = albumArtBitmap, label = "artFade") { bmp ->
                      if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                      } else {
                        Icon(
                            painterResource(id = R.drawable.ic_music_note),
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(120.dp))
                      }
                    }
                  }

              Spacer(modifier = Modifier.height(48.dp))

              // --- TITLE & ARTIST ---
              Text(
                  text = title.ifEmpty { "Unknown Song" },
                  color = Color.White,
                  fontSize = 28.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  modifier = Modifier.basicMarquee())
              Text(
                  text = artist,
                  color = Color.White.copy(alpha = 0.7f),
                  fontSize = 16.sp,
                  modifier = Modifier.padding(top = 8.dp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)

              Spacer(modifier = Modifier.height(32.dp))

              // 🔥 WAVEFORM VISUALIZER PROGRESS BAR
              var isDraggingSlider by remember { mutableStateOf(false) }
              var sliderDragValue by remember { mutableFloatStateOf(0f) }
              val safeDuration = if (duration > 0) duration else 1L
              val targetProgress =
                  (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
              val coroutineScope = rememberCoroutineScope()
              val animatedProgress by
                  animateFloatAsState(
                      if (isDraggingSlider) sliderDragValue else targetProgress, label = "progress")

              BoxWithConstraints(
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(48.dp)
                          .pointerInput(safeDuration) {
                            detectTapGestures(
                                onPress = { offset ->
                                  isDraggingSlider = true
                                  sliderDragValue =
                                      (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                  viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
                                  tryAwaitRelease()
                                  coroutineScope.launch {
                                    delay(100)
                                    isDraggingSlider = false
                                  }
                                })
                          }
                          .pointerInput(safeDuration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                  isDraggingSlider = true
                                  sliderDragValue =
                                      (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                  viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
                                  coroutineScope.launch {
                                    delay(100)
                                    isDraggingSlider = false
                                  }
                                },
                                onDragCancel = { isDraggingSlider = false },
                                onDrag = { change, _ ->
                                  change.consume()
                                  sliderDragValue =
                                      (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                  viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
                                })
                          },
                  contentAlignment = Alignment.Center) {
                    val thumbX = constraints.maxWidth.toFloat() * animatedProgress

                    Canvas(modifier = Modifier.fillMaxSize()) {
                      val gap = 8f
                      val totalGapWidth = gap * (barCount - 1)
                      val barWidth = (size.width - totalGapWidth) / barCount

                      for (i in 0 until barCount) {
                        val xOffset = i * (barWidth + gap)
                        val isActive = xOffset <= thumbX
                        val color = if (isActive) Color.White else Color.White.copy(alpha = 0.2f)
                        val barHeight = size.height * barHeights[i]

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(xOffset, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2, barWidth / 2))
                      }
                    }
                  }

              // TIMERS
              Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween) {
                    val displayPos =
                        if (isDraggingSlider) (sliderDragValue * safeDuration).toLong()
                        else currentPosition
                    Text(
                        viewModel.formatDuration(displayPos),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp)
                    Text(
                        viewModel.formatDuration(duration),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp)
                  }

              Spacer(modifier = Modifier.weight(1f))

              // --- PLAYBACK CONTROLS ---
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                          Icon(
                              painterResource(id = R.drawable.ic_shuffle),
                              null,
                              tint =
                                  if (isShuffleEnabled) Color.White
                                  else Color.White.copy(alpha = 0.4f),
                              modifier = Modifier.size(24.dp))
                        }

                    IconButton(
                        onClick = { viewModel.playPreviousAudio() },
                        modifier = Modifier.size(56.dp)) {
                          Icon(
                              painterResource(id = R.drawable.ic_skip_previous),
                              null,
                              tint = Color.White,
                              modifier = Modifier.size(36.dp))
                        }

                    // Play Button (Large White Circle)
                    Box(
                        modifier =
                            Modifier.size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { viewModel.toggleAudio() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painterResource(
                                  if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                              null,
                              tint = Color.Black,
                              modifier = Modifier.size(40.dp))
                        }

                    IconButton(
                        onClick = { viewModel.playNextAudio() }, modifier = Modifier.size(56.dp)) {
                          Icon(
                              painterResource(id = R.drawable.ic_skip_next),
                              null,
                              tint = Color.White,
                              modifier = Modifier.size(36.dp))
                        }

                    IconButton(
                        onClick = { viewModel.toggleRepeat() }, modifier = Modifier.size(48.dp)) {
                          val iconRes =
                              when (repeatMode) {
                                LoopMode.ONE -> R.drawable.ic_repeat_one
                                else -> R.drawable.ic_repeat
                              }
                          val iconTint =
                              if (repeatMode == LoopMode.NONE) Color.White.copy(alpha = 0.4f)
                              else Color.White
                          Icon(
                              painterResource(iconRes),
                              null,
                              tint = iconTint,
                              modifier = Modifier.size(24.dp))
                        }
                  }

              Spacer(modifier = Modifier.height(24.dp))

              // --- BOTTOM EXTRA ACTIONS (Boost, Share, Queue) ---
              Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                  verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                          viewModel.toggleMusicBoost()
                          Toast.makeText(
                                  context,
                                  if (!isAudioBoosted) "Boost ON" else "Boost OFF",
                                  Toast.LENGTH_SHORT)
                              .show()
                        }) {
                          Icon(
                              painterResource(R.drawable.ic_volume_up),
                              null,
                              tint =
                                  if (isAudioBoosted) MaterialTheme.colorScheme.primary
                                  else Color.White.copy(alpha = 0.6f))
                        }
                    IconButton(onClick = { showQueueSheet = true }) {
                      Icon(Icons.Default.Menu, null, tint = Color.White.copy(alpha = 0.6f))
                    }
                    IconButton(
                        onClick = {
                          val uri = getAudioUriFromPath(context, currentPath)
                          if (uri != null) {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                      type = "audio/*"
                                      putExtra(Intent.EXTRA_STREAM, uri)
                                    },
                                    "Share Audio"))
                          }
                        }) {
                          Icon(Icons.Default.Share, null, tint = Color.White.copy(alpha = 0.6f))
                        }
                  }
              Spacer(modifier = Modifier.height(16.dp))
            }

        // VOLUME & QUEUE SHEET (Same as before, adapted to theme)
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
              Box(
                  modifier =
                      Modifier.clip(RoundedCornerShape(24.dp))
                          .background(Color.Black.copy(alpha = 0.7f))
                          .padding(horizontal = 24.dp, vertical = 12.dp),
                  contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(
                          "Volume: ${(internalVolumeLevel * 100).roundToInt()}%",
                          color = Color.White,
                          fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(8.dp))
                      LinearProgressIndicator(
                          progress = internalVolumeLevel / (if (isAudioBoosted) 2.0f else 1.0f),
                          color = Color.White,
                          trackColor = Color.White.copy(alpha = 0.3f),
                          modifier = Modifier.width(100.dp).height(4.dp))
                    }
                  }
            }

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
                      Text("Up Next", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(16.dp))
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
                                      .clickable { viewModel.playAudioFromList(queueList, index) }
                                      .padding(vertical = 12.dp, horizontal = 8.dp),
                              verticalAlignment = Alignment.CenterVertically) {
                                QueueItemThumbnail(
                                    path = audio.path, isCurrentlyPlaying = isCurrentlyPlaying)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                  Text(
                                      audio.title,
                                      color =
                                          if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.onSurface,
                                      fontWeight =
                                          if (isCurrentlyPlaying) FontWeight.Bold
                                          else FontWeight.Medium,
                                      maxLines = 1,
                                      overflow = TextOverflow.Ellipsis)
                                  Text(
                                      audio.artist,
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
