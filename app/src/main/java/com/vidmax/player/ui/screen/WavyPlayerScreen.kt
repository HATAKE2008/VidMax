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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavyPlayerScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onThemeChange: (PlayerTheme) -> Unit
) {
  val context = LocalContext.current
  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

  // States from ViewModel
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

  // Dialogs & Menus
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPropertiesDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  // Timer States
  var showTimerDialog by remember { mutableStateOf(false) }
  val currentTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
  val isAudioBoosted by viewModel.musicBoostEnabled.collectAsState()

  // QUEUE States
  var showQueueSheet by remember { mutableStateOf(false) }
  val queueList by viewModel.currentQueue.collectAsState()
  val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()

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

  // --- DIALOGS (Delete, Properties, Timer) ---
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
                  val file = File(currentPath)
                  if (file.exists() && file.delete()) {
                    Toast.makeText(context, "Audio Deleted", Toast.LENGTH_SHORT).show()
                    viewModel.pauseAudio()
                    onBack()
                  }
                } catch (e: Exception) {
                  Toast.makeText(context, "Failed to delete.", Toast.LENGTH_SHORT).show()
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

  // Main Layout Box (Handles Gestures)
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
                    viewModel.setCustomVolume(100)
                  } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxHardwareVol, 0)
                    val targetSoftwareVol = (internalVolumeLevel * 100).roundToInt()
                    viewModel.setCustomVolume(targetSoftwareVol)
                  }
                })
          }) {
        // BLURRED BACKGROUND
        Crossfade(targetState = albumArtBitmap, animationSpec = tween(600), label = "bgFade") { bmp
          ->
          if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().blur(radius = 80.dp).graphicsLayer { alpha = 0.12f })
          } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
          }
        }

        // FOREGROUND CONTENT
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
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

                    // Grouping Boost, Share and 3-dot Menu together
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      // 🚀 Sound Boost
                      IconButton(
                          onClick = {
                            viewModel.toggleMusicBoost()
                            val msg =
                                if (!isAudioBoosted) {
                                  "🚀 Software Boost ON: Volume forced to 200%"
                                } else {
                                  "🎵 Boost OFF: Volume back to 100%"
                                }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                          }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_volume_up),
                                contentDescription = "Boost",
                                tint =
                                    if (isAudioBoosted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp))
                          }

                      // 🎵 Share Button
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
                          }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp))
                          }

                      // 3-dot Dropdown Menu
                      Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                          Icon(
                              Icons.Default.MoreVert,
                              "Menu",
                              tint = MaterialTheme.colorScheme.onSurface,
                              modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
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
                                  text = {
                                    Text("Sleep Timer", color = MaterialTheme.colorScheme.onSurface)
                                  },
                                  leadingIcon = {
                                    Icon(
                                        painterResource(id = R.drawable.ic_timer),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp))
                                  },
                                  onClick = {
                                    showMoreMenu = false
                                    showTimerDialog = true
                                  })
                              DropdownMenuItem(
                                  text = {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                  },
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
                                              MaterialTheme.colorScheme.onSurface.copy(
                                                  alpha = 0.12f)))
                              DropdownMenuItem(
                                  text = { Text("Default Theme") },
                                  onClick = {
                                    showMoreMenu = false
                                    onThemeChange(PlayerTheme.DEFAULT)
                                  })
                              DropdownMenuItem(
                                  text = { Text("Modern Circle") },
                                  onClick = {
                                    showMoreMenu = false
                                    onThemeChange(PlayerTheme.MODERN)
                                  })
                              DropdownMenuItem(
                                  text = { Text("Wavy Pastel (Active)") },
                                  onClick = { showMoreMenu = false })
                            }
                      }
                    }
                  }

              Spacer(modifier = Modifier.height(32.dp))

              // --- ALBUM ART (Premium Large 0.95f) ---
              Box(
                  modifier =
                      Modifier.fillMaxWidth(0.95f) // 👈 Large Thumbnail applied
                          .aspectRatio(1f)
                          .shadow(16.dp, RoundedCornerShape(36.dp))
                          .clip(RoundedCornerShape(36.dp))
                          .background(MaterialTheme.colorScheme.surfaceVariant),
                  contentAlignment = Alignment.Center) {
                    Crossfade(targetState = albumArtBitmap, label = "artFade") { bmp ->
                      if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                      } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(100.dp))
                      }
                    }
                  }

              Spacer(modifier = Modifier.height(40.dp))

              // --- TITLE & ARTIST ---
              Text(
                  text = title.ifEmpty { "Unknown Song" },
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 26.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
              Text(
                  text = artist,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                  fontSize = 16.sp,
                  modifier = Modifier.padding(top = 8.dp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis)

              Spacer(modifier = Modifier.height(32.dp))

              // 🔥 WAVY SLIDER
              var isDraggingSlider by remember { mutableStateOf(false) }
              var sliderDragValue by remember { mutableFloatStateOf(0f) }
              val safeDuration = if (duration > 0) duration else 1L
              val targetProgress =
                  (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
              val coroutineScope = rememberCoroutineScope()

              val animatedProgress by
                  animateFloatAsState(
                      targetValue = targetProgress,
                      animationSpec =
                          if (isDraggingSlider) snap()
                          else tween(durationMillis = 500, easing = LinearEasing),
                      label = "wavyProgress")
              val displayProgress = if (isDraggingSlider) sliderDragValue else animatedProgress
              val sliderColor = MaterialTheme.colorScheme.primary

              BoxWithConstraints(
                  modifier =
                      Modifier.fillMaxWidth()
                          .height(40.dp)
                          .pointerInput(safeDuration) {
                            detectTapGestures(
                                onPress = { offset ->
                                  isDraggingSlider = true
                                  sliderDragValue =
                                      (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                  viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
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
                                  sliderDragValue =
                                      (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                  viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
                                  coroutineScope.launch {
                                    delay(200)
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
                    val maxPx = constraints.maxWidth.toFloat()
                    val thumbX = maxPx * displayProgress

                    Canvas(modifier = Modifier.fillMaxSize()) {
                      val waveAmplitude = 10f
                      val waveFrequency = 0.08f
                      val strokeThickness = 12f

                      drawLine(
                          color = sliderColor.copy(alpha = 0.2f),
                          start = Offset(thumbX, center.y),
                          end = Offset(size.width, center.y),
                          strokeWidth = strokeThickness,
                          cap = StrokeCap.Round)

                      val path = Path()
                      path.moveTo(0f, center.y)
                      var currentX = 0f
                      while (currentX < thumbX) {
                        val y =
                            center.y +
                                java.lang.Math.sin((currentX * waveFrequency).toDouble())
                                    .toFloat() * waveAmplitude
                        path.lineTo(currentX, y)
                        currentX += 5f
                      }
                      drawPath(
                          path = path,
                          color = sliderColor,
                          style = Stroke(width = strokeThickness, cap = StrokeCap.Round))

                      val thumbY =
                          center.y +
                              java.lang.Math.sin((thumbX * waveFrequency).toDouble()).toFloat() *
                                  waveAmplitude
                      drawCircle(
                          color = sliderColor,
                          radius = 20f,
                          center = Offset(thumbX.coerceIn(20f, size.width - 20f), thumbY))
                    }
                  }

              Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween) {
                    val displayPos =
                        if (isDraggingSlider) (sliderDragValue * safeDuration).toLong()
                        else currentPosition
                    Text(
                        text = viewModel.formatDuration(displayPos),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                    Text(
                        text = viewModel.formatDuration(duration),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium)
                  }

              Spacer(modifier = Modifier.weight(1f))

              // --- CENTRAL PLAYBACK CONTROLS ---
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier.size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.playPreviousAudio() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painterResource(id = R.drawable.ic_skip_previous),
                              "Previous",
                              tint = MaterialTheme.colorScheme.onSurface,
                              modifier = Modifier.size(28.dp))
                        }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier =
                            Modifier.size(width = 84.dp, height = 80.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { viewModel.toggleAudio() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painterResource(
                                  id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                              "Play/Pause",
                              tint = MaterialTheme.colorScheme.onPrimary,
                              modifier = Modifier.size(36.dp))
                        }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier =
                            Modifier.size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.playNextAudio() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painterResource(id = R.drawable.ic_skip_next),
                              "Next",
                              tint = MaterialTheme.colorScheme.onSurface,
                              modifier = Modifier.size(28.dp))
                        }
                  }

              Spacer(modifier = Modifier.height(40.dp))

              // 🔥 LARGE INDEPENDENT 64DP BOTTOM CIRCLES
              Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically) {
                    // Shuffle Circle
                    Box(
                        modifier =
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.toggleShuffle() },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painterResource(id = R.drawable.ic_shuffle),
                              "Shuffle",
                              tint =
                                  if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.onSurface,
                              modifier = Modifier.size(26.dp))
                        }

                    // Repeat Circle
                    Box(
                        modifier =
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.toggleRepeat() },
                        contentAlignment = Alignment.Center) {
                          val iconRes =
                              when (repeatMode) {
                                LoopMode.ONE -> R.drawable.ic_repeat_one
                                else -> R.drawable.ic_repeat
                              }
                          val iconTint =
                              if (repeatMode == LoopMode.NONE) MaterialTheme.colorScheme.onSurface
                              else MaterialTheme.colorScheme.primary
                          Icon(
                              painterResource(id = iconRes),
                              "Repeat",
                              tint = iconTint,
                              modifier = Modifier.size(26.dp))
                        }

                    // Queue Circle
                    Box(
                        modifier =
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showQueueSheet = true },
                        contentAlignment = Alignment.Center) {
                          Icon(
                              Icons.Default.Menu,
                              "Queue",
                              tint = MaterialTheme.colorScheme.onSurface,
                              modifier = Modifier.size(26.dp))
                        }

                    // Favorite Circle
                    Box(
                        modifier =
                            Modifier.size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.toggleFavorite(currentPath) },
                        contentAlignment = Alignment.Center) {
                          Crossfade(targetState = isFavorite, label = "fav") { fav ->
                            Icon(
                                imageVector =
                                    if (fav) Icons.Default.Favorite
                                    else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (fav) Color.Red else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp))
                          }
                        }
                  }
              Spacer(modifier = Modifier.height(24.dp))
            }

        // 🔥 VOLUME INDICATOR OVERLAY
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) {
              val displayVolPercent = (internalVolumeLevel * 100).roundToInt()
              val maxProgress = if (isAudioBoosted) 2.0f else 1.0f
              Box(
                  modifier =
                      Modifier.clip(RoundedCornerShape(24.dp))
                          .background(Color.Black.copy(alpha = 0.6f))
                          .padding(horizontal = 24.dp, vertical = 12.dp),
                  contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(
                          "Volume: $displayVolPercent%",
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

        // 🔥 QUEUE BOTTOM SHEET
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
                          "Up Next",
                          fontSize = 20.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface)
                      Spacer(modifier = Modifier.height(16.dp))

                      if (queueList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center) {
                              Text(
                                  "Queue list is currently empty.",
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
                                        .clickable { viewModel.playAudioFromList(queueList, index) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                  QueueItemThumbnail(
                                      path = audio.path, isCurrentlyPlaying = isCurrentlyPlaying)
                                  Spacer(modifier = Modifier.width(16.dp))
                                  Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = audio.title,
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
                                        text = audio.artist,
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
