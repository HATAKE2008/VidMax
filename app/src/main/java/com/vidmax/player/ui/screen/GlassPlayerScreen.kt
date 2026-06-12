package com.vidmax.player.ui.screen

import android.app.Activity
import android.app.RecoverableSecurityException
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassPlayerScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onThemeChange: (PlayerTheme) -> Unit
) {
  val context: Context = LocalContext.current
  val audioManager: AudioManager = remember {
    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  val title: String by viewModel.recentlyPlayedTitle.collectAsState()
  val artist: String by viewModel.currentAudioArtist.collectAsState()
  val currentPath: String by viewModel.recentlyPlayedPath.collectAsState()

  val isPlaying: Boolean by viewModel.isAudioPlaying.collectAsState()
  val currentPosition: Long by viewModel.audioPosition.collectAsState()
  val duration: Long by viewModel.audioDuration.collectAsState()

  val isShuffleEnabled: Boolean by viewModel.isShuffleEnabled.collectAsState()
  val repeatMode: LoopMode by viewModel.audioRepeatMode.collectAsState()

  val favoritePaths: Set<String> by viewModel.favoriteAudioPaths.collectAsState()
  val isFavorite: Boolean = favoritePaths.contains(currentPath)
  val isAudioBoosted: Boolean by viewModel.musicBoostEnabled.collectAsState()

  // Menu States
  var showMoreMenu: Boolean by remember { mutableStateOf(false) }
  var showPropertiesDialog: Boolean by remember { mutableStateOf(false) }
  var showDeleteConfirmDialog: Boolean by remember { mutableStateOf(false) }
  var showTimerDialog: Boolean by remember { mutableStateOf(false) }
  val currentTimerMinutes: Int by viewModel.sleepTimerMinutes.collectAsState()

  var albumArtBitmap: Bitmap? by remember { mutableStateOf(null) }
  var isScreenReady: Boolean by remember { mutableStateOf(false) }

  var internalVolumeLevel: Float by remember {
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

  LaunchedEffect(Unit) {
    delay(350)
    isScreenReady = true
  }

  LaunchedEffect(currentPath, isScreenReady) {
    if (isScreenReady && currentPath.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        try {
          val retriever: MediaMetadataRetriever = MediaMetadataRetriever()
          val uri: Uri =
              if (currentPath.startsWith("/")) {
                Uri.fromFile(File(currentPath))
              } else {
                Uri.parse(currentPath)
              }
          retriever.setDataSource(context, uri)
          val art: ByteArray? = retriever.embeddedPicture
          if (art != null) {
            albumArtBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
          } else {
            albumArtBitmap = null
          }
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
        if (file.exists()) String.format("%.2f MB", file.length() / (1024.0 * 1024.0))
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

  // MAIN UI BACKGROUND (Deep Dark)
  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
    Column(modifier = Modifier.fillMaxSize()) {

      // 1. TOP APP BAR
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "NOW PLAYING",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp)

            Box {
              IconButton(onClick = { showMoreMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
              }
              DropdownMenu(
                  expanded = showMoreMenu,
                  onDismissRequest = { showMoreMenu = false },
                  modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    DropdownMenuItem(
                        text = { Text("Properties", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = {
                          Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                          showMoreMenu = false
                          showPropertiesDialog = true
                        })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                          Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                          showMoreMenu = false
                          showDeleteConfirmDialog = true
                        })
                    DropdownMenuItem(
                        text = { Text("Sleep Timer", color = MaterialTheme.colorScheme.onSurface) },
                        leadingIcon = {
                          Icon(
                              painterResource(id = R.drawable.ic_timer),
                              null,
                              tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                          showMoreMenu = false
                          showTimerDialog = true
                        })
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
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
                        text = { Text("Wavy Pastel", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                          showMoreMenu = false
                          onThemeChange(PlayerTheme.WAVY)
                        })
                    DropdownMenuItem(
                        text = { Text("Glass Theme", color = MaterialTheme.colorScheme.primary) },
                        onClick = { showMoreMenu = false })
                  }
            }
          }

      // 2. BREATHING ALBUM ART (Original Square-Rounded Look)
      Box(
          modifier =
              Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
          contentAlignment = Alignment.Center) {
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
                    targetValue = if (isPlaying) 1.03f else 1.0f,
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
                            40.dp,
                            RoundedCornerShape(32.dp),
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary)
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                        .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center) {
                  Crossfade(
                      targetState = albumArtBitmap,
                      animationSpec = tween(400),
                      label = "artFade") { bmp: Bitmap? ->
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
                              tint = Color.White.copy(alpha = 0.1f),
                              modifier = Modifier.size(100.dp))
                        }
                      }
                }
          }

      // 3. THE 3 BOTTOM GLASS CARDS
      Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // CARD 1: VOLUME & FAVORITE ROW
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  // Left Icon: Boost Toggle
                  Box(
                      modifier =
                          Modifier.size(64.dp)
                              .clip(RoundedCornerShape(20.dp))
                              .background(Color.White.copy(alpha = 0.08f))
                              .clickable { viewModel.toggleMusicBoost() },
                      contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_wrench),
                            contentDescription = "Boost",
                            tint =
                                if (isAudioBoosted) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp))
                      }

                  // Volume Bar
                  BoxWithConstraints(
                      modifier =
                          Modifier.weight(1f)
                              .fillMaxHeight()
                              .clip(RoundedCornerShape(20.dp))
                              .background(Color.White.copy(alpha = 0.08f))
                              .pointerInput(isAudioBoosted) {
                                awaitPointerEventScope {
                                  while (true) {
                                    val down = awaitFirstDown()
                                    val maxLimit: Float = if (isAudioBoosted) 2.0f else 1.0f
                                    internalVolumeLevel =
                                        ((down.position.x / size.width.toFloat()).coerceIn(0f, 1f) *
                                            maxLimit)

                                    val maxHardwareVol: Int =
                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    if (internalVolumeLevel <= 1.0f) {
                                      audioManager.setStreamVolume(
                                          AudioManager.STREAM_MUSIC,
                                          (internalVolumeLevel * maxHardwareVol).roundToInt(),
                                          0)
                                      viewModel.setCustomVolume(100)
                                    } else {
                                      audioManager.setStreamVolume(
                                          AudioManager.STREAM_MUSIC, maxHardwareVol, 0)
                                      viewModel.setCustomVolume(
                                          (internalVolumeLevel * 100).roundToInt())
                                    }

                                    do {
                                      val event = awaitPointerEvent()
                                      val change = event.changes.firstOrNull()
                                      if (change != null && change.pressed) {
                                        change.consume()
                                        internalVolumeLevel =
                                            ((change.position.x / size.width.toFloat()).coerceIn(
                                                0f, 1f) * maxLimit)

                                        if (internalVolumeLevel <= 1.0f) {
                                          audioManager.setStreamVolume(
                                              AudioManager.STREAM_MUSIC,
                                              (internalVolumeLevel * maxHardwareVol).roundToInt(),
                                              0)
                                          viewModel.setCustomVolume(100)
                                        } else {
                                          audioManager.setStreamVolume(
                                              AudioManager.STREAM_MUSIC, maxHardwareVol, 0)
                                          viewModel.setCustomVolume(
                                              (internalVolumeLevel * 100).roundToInt())
                                        }
                                      }
                                    } while (change != null && change.pressed)
                                  }
                                }
                              },
                      contentAlignment = Alignment.CenterStart) {
                        val maxLimit: Float = if (isAudioBoosted) 2.0f else 1.0f
                        val displayPercent: Int = (internalVolumeLevel * 100).roundToInt()
                        val barFraction: Float = (internalVolumeLevel / maxLimit).coerceIn(0f, 1f)

                        Box(
                            modifier =
                                Modifier.fillMaxHeight()
                                    .fillMaxWidth(barFraction)
                                    .background(Color.White.copy(alpha = 0.15f)))
                        Text(
                            text = "$displayPercent%",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center))
                      }

                  // Right Icon: Favorite
                  Box(
                      modifier =
                          Modifier.size(64.dp)
                              .clip(RoundedCornerShape(20.dp))
                              .background(Color.White.copy(alpha = 0.08f))
                              .clickable { viewModel.toggleFavorite(currentPath) },
                      contentAlignment = Alignment.Center) {
                        Crossfade(targetState = isFavorite, label = "fav") { fav: Boolean ->
                          Icon(
                              imageVector =
                                  if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                              contentDescription = "Like",
                              tint =
                                  if (fav) MaterialTheme.colorScheme.primary
                                  else Color.White.copy(alpha = 0.7f),
                              modifier = Modifier.size(24.dp))
                        }
                      }
                }

            // CARD 2: SONG INFO & 🔥 WAVY SEEKBAR 🔥
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 20.dp, vertical = 20.dp)) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                              text = title.ifEmpty { "Unknown Song" },
                              color = Color.White,
                              fontSize = 18.sp,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
                          Spacer(modifier = Modifier.height(2.dp))
                          Text(
                              text = artist.ifEmpty { "Unknown Artist" },
                              color = Color.White.copy(alpha = 0.5f),
                              fontSize = 14.sp,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                        }

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
                                  imageVector = Icons.Default.Share,
                                  contentDescription = "Share",
                                  tint = Color.White.copy(alpha = 0.6f),
                                  modifier = Modifier.size(22.dp))
                            }
                      }

                  Spacer(modifier = Modifier.height(16.dp))

                  // 🔥 NEW WAVY SEEKBAR LOGIC 🔥
                  var isDraggingSlider: Boolean by remember { mutableStateOf(false) }
                  var sliderDragValue: Float by remember { mutableFloatStateOf(0f) }
                  val safeDuration: Long = if (duration > 0) duration else 1L
                  val targetProgress: Float =
                      (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                  val coroutineScope = rememberCoroutineScope()

                  val animatedProgress: Float by
                      animateFloatAsState(
                          targetValue = targetProgress,
                          animationSpec =
                              if (isDraggingSlider) snap()
                              else tween(durationMillis = 500, easing = LinearEasing),
                          label = "fluidProgressAnim")

                  val displayProgress: Float =
                      if (isDraggingSlider) sliderDragValue else animatedProgress

                  val animatedWaveHeight: Float by
                      animateFloatAsState(
                          targetValue = if (isDraggingSlider) 35f else 0f,
                          animationSpec =
                              spring(
                                  dampingRatio = Spring.DampingRatioMediumBouncy,
                                  stiffness = Spring.StiffnessLow),
                          label = "waveHeightAnim")

                  val primaryColor = MaterialTheme.colorScheme.primary
                  val density = LocalDensity.current

                  BoxWithConstraints(
                      modifier =
                          Modifier.fillMaxWidth()
                              .padding(
                                  horizontal =
                                      8.dp) // Added padding so it doesn't touch the borders
                              .height(48.dp)
                              .pointerInput(safeDuration) {
                                awaitPointerEventScope {
                                  while (true) {
                                    val down = awaitFirstDown()
                                    isDraggingSlider = true
                                    sliderDragValue =
                                        (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)

                                    do {
                                      val event = awaitPointerEvent()
                                      val change = event.changes.firstOrNull()
                                      if (change != null && change.pressed) {
                                        change.consume()
                                        sliderDragValue =
                                            (change.position.x / size.width.toFloat()).coerceIn(
                                                0f, 1f)
                                      }
                                    } while (change != null && change.pressed)

                                    // Finger released! Now seek the audio.
                                    viewModel.seekAudio((sliderDragValue * safeDuration).toLong())
                                    coroutineScope.launch {
                                      delay(150)
                                      isDraggingSlider = false
                                    }
                                  }
                                }
                              }) {
                        val widthPx = maxWidth.value * density.density
                        val heightPx = maxHeight.value * density.density
                        val baseLineY = heightPx / 2f
                        val waveWidth = 120f

                        val thumbXPx = widthPx * displayProgress

                        // Draw Path
                        Canvas(modifier = Modifier.fillMaxSize()) {
                          val path =
                              Path().apply {
                                moveTo(0f, baseLineY)
                                val startX = thumbXPx - waveWidth
                                val endX = thumbXPx + waveWidth

                                if (startX > 0) lineTo(startX, baseLineY)

                                cubicTo(
                                    thumbXPx - waveWidth / 2,
                                    baseLineY,
                                    thumbXPx - waveWidth / 2,
                                    baseLineY - animatedWaveHeight,
                                    thumbXPx,
                                    baseLineY - animatedWaveHeight)
                                cubicTo(
                                    thumbXPx + waveWidth / 2,
                                    baseLineY - animatedWaveHeight,
                                    thumbXPx + waveWidth / 2,
                                    baseLineY,
                                    endX,
                                    baseLineY)

                                lineTo(size.width, baseLineY)
                              }

                          drawPath(
                              path = path,
                              color = Color.White.copy(alpha = 0.2f),
                              style = Stroke(width = 12f, cap = StrokeCap.Round))

                          clipRect(right = thumbXPx) {
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 12f, cap = StrokeCap.Round))
                          }

                          drawCircle(
                              color = Color.White,
                              radius = 22f,
                              center = Offset(thumbXPx, baseLineY - animatedWaveHeight))
                        }

                        // Floating Timestamp stays perfectly under your finger
                        if (isDraggingSlider) {
                          val displayPos = (sliderDragValue * safeDuration).toLong()
                          val thumbXDp = with(density) { thumbXPx.toDp() }
                          val waveHDp = with(density) { animatedWaveHeight.toDp() }
                          val baseYDp = with(density) { baseLineY.toDp() }

                          Text(
                              text = viewModel.formatDuration(displayPos),
                              color = Color.White,
                              fontSize = 13.sp,
                              fontWeight = FontWeight.Bold,
                              modifier =
                                  Modifier.offset(
                                      x = thumbXDp - 16.dp,
                                      y = baseYDp - waveHDp - 32.dp // Floats just above the thumb
                                      ))
                        }
                      }

                  // Static Text Times Below
                  Row(
                      modifier =
                          Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                      horizontalArrangement = Arrangement.SpaceBetween) {
                        val currentDisplayTime =
                            if (isDraggingSlider) (sliderDragValue * safeDuration).toLong()
                            else currentPosition
                        Text(
                            text = viewModel.formatDuration(currentDisplayTime),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Text(
                            text = viewModel.formatDuration(duration),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                      }
                }

            // CARD 3: PLAYBACK CONTROLS
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        painterResource(id = R.drawable.ic_shuffle),
                        null,
                        tint =
                            if (isShuffleEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp))
                  }
                  IconButton(onClick = { viewModel.playPreviousAudio() }) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_previous),
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp))
                  }

                  Box(
                      modifier =
                          Modifier.size(60.dp)
                              .clip(CircleShape)
                              .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                              .background(Color.White.copy(alpha = 0.05f))
                              .clickable { viewModel.toggleAudio() },
                      contentAlignment = Alignment.Center) {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp))
                      }

                  IconButton(onClick = { viewModel.playNextAudio() }) {
                    Icon(
                        painterResource(id = R.drawable.ic_skip_next),
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp))
                  }
                  IconButton(onClick = { viewModel.toggleRepeat() }) {
                    val iconTint: Color =
                        if (repeatMode == LoopMode.NONE) Color.White.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary
                    val iconRes: Int =
                        if (repeatMode == LoopMode.ONE) R.drawable.ic_repeat_one
                        else R.drawable.ic_repeat
                    Icon(
                        painterResource(id = iconRes),
                        null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp))
                  }
                }
          }
    }
  }
}
