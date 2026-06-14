package com.vidmax.player.ui.screen

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 🔥 Theme Enum Update (Removed GLASS)
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
fun MusicPlayerScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
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
        DefaultPlayerUI(viewModel = viewModel, onBack = onBack, onThemeChange = changeAndSaveTheme)
      }
      PlayerTheme.MODERN -> {
        ModernPlayerScreen(
            viewModel = viewModel, onBack = onBack, onThemeChange = changeAndSaveTheme)
      }
      PlayerTheme.WAVY -> {
        WavyPlayerScreen(viewModel = viewModel, onBack = onBack, onThemeChange = changeAndSaveTheme)
      }
    }
  }
}

// 🔥 DEFAULT UI
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DefaultPlayerUI(
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

  // Dialogs & Menus
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPropertiesDialog by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog by remember { mutableStateOf(false) }

  // Timer & Boost States
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
                    viewModel.setCustomVolume(100)
                  } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxHardwareVol, 0)
                    val targetSoftwareVol = (internalVolumeLevel * 100).roundToInt()
                    viewModel.setCustomVolume(targetSoftwareVol)
                  }
                })
          }) {

        // Background Album Art Crossfade
        Crossfade(targetState = albumArtBitmap, animationSpec = tween(600), label = "bgFade") { bmp
          ->
          if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize().blur(radius = 80.dp).graphicsLayer {
                      alpha = 0.12f // Slightly dimmed for better contrast
                    })
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
                      Text(
                          text = "Now Playing",
                          fontSize = 12.sp,
                          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                          fontWeight = FontWeight.Medium)
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
                Crossfade(
                    targetState = albumArtBitmap, animationSpec = tween(400), label = "artFade") {
                        bmp ->
                      if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                      } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center) {
                              Icon(
                                  painter = painterResource(id = R.drawable.ic_music_note),
                                  contentDescription = null,
                                  tint =
                                      MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                  modifier = Modifier.size(100.dp))
                            }
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
                          onClick = { viewModel.toggleFavorite(currentPath) },
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

          BoxWithConstraints(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(36.dp)
                      .pointerInput(safeDuration) {
                        detectTapGestures(
                            onPress = { offset ->
                              isDraggingSlider = true
                              sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
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
                              sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
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
                      }) {
                val thumbX = maxWidth * displayProgress
                // Adjust thumb offset to stay inside bounds perfectly
                val thumbOffset = (thumbX - 3.dp).coerceIn(0.dp, maxWidth - 6.dp)

                // Background Track
                Box(
                    modifier =
                        Modifier.align(Alignment.Center)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))

                // Active Track (Colored)
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .width(thumbX)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))

                // Thumb
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .offset(x = thumbOffset)
                            .width(6.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary))
              }

          Row(
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_shuffle),
                          contentDescription = "Shuffle",
                          tint =
                              if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                          modifier = Modifier.size(24.dp))
                    }
                IconButton(
                    onClick = { viewModel.playPreviousAudio() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_skip_previous),
                          contentDescription = "Previous",
                          tint = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(28.dp))
                    }
                Box(
                    modifier =
                        Modifier.size(60.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { viewModel.toggleAudio() },
                    contentAlignment = Alignment.Center) {
                      Crossfade(
                          targetState = isPlaying,
                          animationSpec = tween(300),
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
                    onClick = { viewModel.playNextAudio() }, modifier = Modifier.size(48.dp)) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_skip_next),
                          contentDescription = "Next",
                          tint = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(28.dp))
                    }
                IconButton(
                    onClick = { viewModel.toggleRepeat() }, modifier = Modifier.size(48.dp)) {
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

@Composable
fun QueueItemThumbnail(path: String, isCurrentlyPlaying: Boolean) {
  var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
  val context = LocalContext.current

  LaunchedEffect(path) {
    withContext(Dispatchers.IO) {
      try {
        val mmr = MediaMetadataRetriever()
        val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
        mmr.setDataSource(context, uri)
        val pic = mmr.embeddedPicture
        if (pic != null) {
          val bmp = BitmapFactory.decodeByteArray(pic, 0, pic.size)
          bitmap = bmp.asImageBitmap()
        }
        mmr.release()
      } catch (e: Exception) {
        bitmap = null
      }
    }
  }

  Box(
      modifier =
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center) {
        if (bitmap != null) {
          Image(
              bitmap = bitmap!!,
              contentDescription = "Album Art",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize())
          if (isCurrentlyPlaying) {
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
        } else {
          Icon(
              painter =
                  painterResource(
                      id =
                          if (isCurrentlyPlaying) R.drawable.ic_pause
                          else R.drawable.ic_music_note),
              contentDescription = null,
              tint =
                  if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
              modifier = Modifier.size(24.dp))
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
