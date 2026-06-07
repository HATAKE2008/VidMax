package com.vidmax.player.ui.player

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.R
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MpvTrackInfo(val id: Int, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    currentPath: String,
    audioBoostEnabled: Boolean,
    currentPlaybackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    videoScale: Float,
    onVideoScaleChange: (Float, androidx.compose.ui.geometry.Offset) -> Unit,
    exoPlayer: Player? = null,
    bgPlayEnabled: Boolean,
    onBgPlayToggle: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onBack: () -> Unit,
    onPickSubtitle: () -> Unit,
    modifier: Modifier = Modifier
) {

  val context = LocalContext.current
  val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  val activity = context as? Activity
  val configuration = LocalConfiguration.current
  val coroutineScope = rememberCoroutineScope()

  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val rightSafePadding = if (isLandscape) 88.dp else 16.dp

  val isPlaying by viewModel.isPlaying.collectAsState()
  val currentPosition by viewModel.currentPosition.collectAsState()
  val duration by viewModel.duration.collectAsState()
  val isLocked by viewModel.isLocked.collectAsState()
  val controlsVisible by viewModel.controlsVisible.collectAsState()
  val loopMode by viewModel.loopMode.collectAsState()
  val videoTitle by viewModel.videoTitle.collectAsState()

  val currentEngine by viewModel.currentEngine.collectAsState()

  val isGestureOverlayVisible by viewModel.isGestureOverlayVisible.collectAsState()
  val gestureIndicatorType by viewModel.gestureIndicatorType.collectAsState()
  val gestureIndicatorValue by viewModel.gestureIndicatorValue.collectAsState()
  val currentVolumePercent by viewModel.currentVolumePercent.collectAsState()
  val currentBrightnessPercent by viewModel.currentBrightnessPercent.collectAsState()

  var showSubtitleMenu by remember { mutableStateOf(false) }
  var showAudioMenu by remember { mutableStateOf(false) }

  var showSyncMenu by remember { mutableStateOf(false) }
  var audioDelayMs by remember { mutableLongStateOf(0L) }
  var subtitleDelayMs by remember { mutableLongStateOf(0L) }

  var localBoostEnabled by remember { mutableStateOf(audioBoostEnabled) }
  var sleepTimerMinutes by remember { mutableIntStateOf(0) }
  var showTimerDialog by remember { mutableStateOf(false) }

  var isDragging by remember { mutableStateOf(false) }
  var dragType by remember { mutableStateOf(0) }
  var volumeAccumulator by remember { mutableFloatStateOf(0f) }
  var ignoreDrag by remember { mutableStateOf(false) }

  var dragDirectionDetermined by remember { mutableStateOf(false) }
  var seekAccumulator by remember { mutableFloatStateOf(0f) }
  var targetSeekPosition by remember { mutableLongStateOf(0L) }
  var dragStartOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

  var showDoubleTapRipple by remember { mutableStateOf(0) }

  var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }

  val density = LocalDensity.current
  val deadZonePx = remember(density) { with(density) { 40.dp.toPx() } }
  val bottomDeadZonePx = remember(density) { with(density) { 70.dp.toPx() } }

  var showMoreMenu by remember { mutableStateOf(false) }
  var showPropertiesDialog by remember { mutableStateOf(false) }

  var mpvSubTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
  var currentMpvSubId by remember { mutableStateOf("no") }

  var mpvAudioTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
  var currentMpvAudioId by remember { mutableStateOf("1") }

  LaunchedEffect(sleepTimerMinutes) {
    if (sleepTimerMinutes > 0) {
      delay(sleepTimerMinutes * 60 * 1000L)
      try {
        MPVLib.setPropertyBoolean("pause", true)
      } catch (e: Exception) {}
      exoPlayer?.pause()
      activity?.finish()
    }
  }

  // 🔥 VLC changed to MPV
  LaunchedEffect(showSubtitleMenu) {
    if (showSubtitleMenu && currentEngine == PlayerEngine.MPV) {
      try {
        val tracks = mutableListOf<MpvTrackInfo>()
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until count) {
          val type = MPVLib.getPropertyString("track-list/$i/type")
          if (type == "sub") {
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
            val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
            val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
            val name =
                if (title.isNotEmpty()) title
                else if (lang.isNotEmpty()) lang else "Subtitle Track $id"
            if (id != -1) tracks.add(MpvTrackInfo(id, name))
          }
        }
        mpvSubTracks = tracks
        currentMpvSubId = MPVLib.getPropertyString("sid") ?: "no"
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  // 🔥 VLC changed to MPV
  LaunchedEffect(showAudioMenu) {
    if (showAudioMenu && currentEngine == PlayerEngine.MPV) {
      try {
        val tracks = mutableListOf<MpvTrackInfo>()
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until count) {
          val type = MPVLib.getPropertyString("track-list/$i/type")
          if (type == "audio") {
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: -1
            val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
            val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
            val name =
                if (title.isNotEmpty()) title
                else if (lang.isNotEmpty()) lang else "Audio Track $id"
            if (id != -1) tracks.add(MpvTrackInfo(id, name))
          }
        }
        mpvAudioTracks = tracks
        currentMpvAudioId = MPVLib.getPropertyString("aid") ?: "1"
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  val toggleAudioBoost = {
    localBoostEnabled = !localBoostEnabled
    if (!localBoostEnabled && volumeAccumulator > 100f) {
      volumeAccumulator = 100f
      val maxSystemVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
      val currentSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

      if (currentSystemVol != maxSystemVol) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVol, 0)
      }

      // 🔥 VLC changed to MPV
      if (currentEngine == PlayerEngine.MPV) {
        try {
          MPVLib.setPropertyInt("volume", 100)
        } catch (e: Exception) {}
      } else {
        try {
          loudnessEnhancer?.enabled = false
        } catch (e: Exception) {}
      }
      viewModel.setCurrentVolumePercent(1f)
      viewModel.setGestureIndicator(2, 100f)
    }
  }

  if (showTimerDialog) {
    AlertDialog(
        onDismissRequest = { showTimerDialog = false },
        containerColor = Color(0xFF1E1E1E),
        title = { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
          Column {
            listOf(0, 15, 30, 60, 120).forEach { mins ->
              val text = if (mins == 0) "Off" else "$mins Minutes"
              Row(
                  modifier =
                      Modifier.fillMaxWidth()
                          .clickable {
                            sleepTimerMinutes = mins
                            showTimerDialog = false
                          }
                          .padding(vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint =
                            if (sleepTimerMinutes == mins) MaterialTheme.colorScheme.primary
                            else Color.Transparent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text, color = Color.White, fontSize = 16.sp)
                  }
            }
          }
        },
        confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Close") } })
  }

  if (showPropertiesDialog) {
    val file = File(currentPath)
    val fileSizeMb =
        if (file.exists()) String.format("%.2f MB", file.length() / (1024.0 * 1024.0))
        else "Unknown"

    AlertDialog(
        onDismissRequest = { showPropertiesDialog = false },
        title = { Text("Video Properties", fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Title: $videoTitle", fontSize = 14.sp)
            Text(
                "Path: $currentPath",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Size: $fileSizeMb", fontSize = 14.sp)
            Text(
                "Engine: ${if (currentEngine == PlayerEngine.EXO) "ExoPlayer (Media3)" else "MPV Engine (HW)"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary)
          }
        },
        confirmButton = {
          TextButton(onClick = { showPropertiesDialog = false }) { Text("Close") }
        })
  }

  if (showSyncMenu) {
    LaunchedEffect(showSyncMenu) {
      // 🔥 VLC changed to MPV
      if (currentEngine == PlayerEngine.MPV) {
        try {
          audioDelayMs = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toLong()
          subtitleDelayMs = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000).toLong()
        } catch (e: Exception) {}
      }
    }
    ModalBottomSheet(
        onDismissRequest = { showSyncMenu = false }, containerColor = Color(0xFF1E1E1E)) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    "Speed & Sync",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)

                Column {
                  Text("Playback Speed", color = Color.Gray, fontSize = 14.sp)
                  Spacer(Modifier.height(12.dp))
                  val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween) {
                        speeds.forEach { speed ->
                          val isSelected = currentPlaybackSpeed == speed
                          Box(
                              modifier =
                                  Modifier.clip(RoundedCornerShape(8.dp))
                                      .background(
                                          if (isSelected) MaterialTheme.colorScheme.primary
                                          else Color.DarkGray)
                                      .clickable { onSpeedChange(speed) }
                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    "${speed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight =
                                        if (isSelected) FontWeight.Bold else FontWeight.Normal)
                              }
                        }
                      }
                }

                Divider(color = Color.DarkGray)

                // 🔥 VLC changed to MPV
                if (currentEngine == PlayerEngine.MPV) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically) {
                        Column {
                          Text("Audio Delay", color = Color.White, fontSize = 16.sp)
                          Text(
                              if (audioDelayMs == 0L) "0 ms" else "${audioDelayMs} ms",
                              color = MaterialTheme.colorScheme.primary,
                              fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                          Box(
                              modifier =
                                  Modifier.clip(RoundedCornerShape(8.dp))
                                      .background(Color.DarkGray)
                                      .clickable {
                                        audioDelayMs -= 50
                                        try {
                                          MPVLib.setPropertyDouble(
                                              "audio-delay", audioDelayMs / 1000.0)
                                        } catch (e: Exception) {}
                                      }
                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    "-50ms",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold)
                              }
                          Box(
                              modifier =
                                  Modifier.clip(RoundedCornerShape(8.dp))
                                      .background(Color.DarkGray)
                                      .clickable {
                                        audioDelayMs += 50
                                        try {
                                          MPVLib.setPropertyDouble(
                                              "audio-delay", audioDelayMs / 1000.0)
                                        } catch (e: Exception) {}
                                      }
                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    "+50ms",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold)
                              }
                        }
                      }

                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically) {
                        Column {
                          Text("Subtitle Delay", color = Color.White, fontSize = 16.sp)
                          Text(
                              if (subtitleDelayMs == 0L) "0 ms" else "${subtitleDelayMs} ms",
                              color = MaterialTheme.colorScheme.primary,
                              fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                          Box(
                              modifier =
                                  Modifier.clip(RoundedCornerShape(8.dp))
                                      .background(Color.DarkGray)
                                      .clickable {
                                        subtitleDelayMs -= 50
                                        try {
                                          MPVLib.setPropertyDouble(
                                              "sub-delay", subtitleDelayMs / 1000.0)
                                        } catch (e: Exception) {}
                                      }
                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    "-50ms",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold)
                              }
                          Box(
                              modifier =
                                  Modifier.clip(RoundedCornerShape(8.dp))
                                      .background(Color.DarkGray)
                                      .clickable {
                                        subtitleDelayMs += 50
                                        try {
                                          MPVLib.setPropertyDouble(
                                              "sub-delay", subtitleDelayMs / 1000.0)
                                        } catch (e: Exception) {}
                                      }
                                      .padding(horizontal = 12.dp, vertical = 8.dp),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    "+50ms",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold)
                              }
                        }
                      }
                } else {
                  Text(
                      "Sync delays are automatically handled by ExoPlayer.",
                      color = Color.Gray,
                      fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
              }
        }
  }

  if (showSubtitleMenu) {
    ModalBottomSheet(
        onDismissRequest = { showSubtitleMenu = false }, containerColor = Color(0xFF1E1E1E)) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Subtitles",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable {
                              showSubtitleMenu = false
                              onPickSubtitle()
                            }
                            .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                          painter = painterResource(id = R.drawable.ic_folder),
                          contentDescription = null,
                          tint = Color.White)
                      Spacer(modifier = Modifier.width(16.dp))
                      Text("Open Local Subtitle", color = Color.White, fontSize = 16.sp)
                    }
                Divider(color = Color.DarkGray)

                // 🔥 VLC changed to MPV
                if (currentEngine == PlayerEngine.MPV) {
                  val isOff =
                      currentMpvSubId == "no" ||
                          currentMpvSubId == "false" ||
                          currentMpvSubId == "0"
                  Row(
                      modifier =
                          Modifier.fillMaxWidth()
                              .clickable {
                                try {
                                  MPVLib.setPropertyString("sid", "no")
                                } catch (e: Exception) {}
                                showSubtitleMenu = false
                              }
                              .padding(vertical = 12.dp),
                      verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint =
                                if (isOff) MaterialTheme.colorScheme.primary else Color.Transparent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Off / Disable", color = Color.White, fontSize = 16.sp)
                      }

                  mpvSubTracks.forEach { track ->
                    val isSelected = currentMpvSubId == track.id.toString()
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                  try {
                                    MPVLib.setPropertyInt("sid", track.id)
                                  } catch (e: Exception) {}
                                  showSubtitleMenu = false
                                }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                              imageVector = Icons.Default.Check,
                              contentDescription = null,
                              tint =
                                  if (isSelected) MaterialTheme.colorScheme.primary
                                  else Color.Transparent)
                          Spacer(modifier = Modifier.width(16.dp))
                          Text(track.name, color = Color.White, fontSize = 16.sp)
                        }
                  }
                } else {
                  Text(
                      "Track selection is not available in ExoPlayer. Please switch to MPV (HW) Engine from the menu (⋮) to change subtitles.",
                      color = Color.Gray,
                      fontSize = 14.sp,
                      modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(modifier = Modifier.height(32.dp))
              }
        }
  }

  if (showAudioMenu) {
    ModalBottomSheet(
        onDismissRequest = { showAudioMenu = false }, containerColor = Color(0xFF1E1E1E)) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Audio Tracks",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))

                // 🔥 VLC changed to MPV
                if (currentEngine == PlayerEngine.MPV) {
                  mpvAudioTracks.forEach { track ->
                    val isSelected = currentMpvAudioId == track.id.toString()
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                  try {
                                    MPVLib.setPropertyInt("aid", track.id)
                                  } catch (e: Exception) {}
                                  showAudioMenu = false
                                }
                                .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                              imageVector = Icons.Default.Check,
                              contentDescription = null,
                              tint =
                                  if (isSelected) MaterialTheme.colorScheme.primary
                                  else Color.Transparent)
                          Spacer(modifier = Modifier.width(16.dp))
                          Text(track.name, color = Color.White, fontSize = 16.sp)
                        }
                  }
                } else {
                  Text(
                      "Track selection is not available in ExoPlayer. Please switch to MPV (HW) Engine from the menu (⋮) to change audio tracks.",
                      color = Color.Gray,
                      fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
              }
        }
  }

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .pointerInput(isLocked) {
                detectTransformGestures { _, pan, zoom, _ ->
                  if (!isLocked) onVideoScaleChange(zoom, pan)
                }
              }
              .pointerInput(isLocked) {
                if (!isLocked) {
                  detectTapGestures(
                      onDoubleTap = { offset ->
                        if (offset.y > size.height - bottomDeadZonePx) return@detectTapGestures
                        val centerX = size.width / 2f
                        if (offset.x < centerX) {
                          onSeekBackward()
                          showDoubleTapRipple = -1
                        } else {
                          onSeekForward()
                          showDoubleTapRipple = 1
                        }
                        coroutineScope.launch {
                          delay(600)
                          showDoubleTapRipple = 0
                        }
                      },
                      onTap = { viewModel.setControlsVisible(!controlsVisible) })
                } else {
                  detectTapGestures(onTap = { viewModel.setControlsVisible(true) })
                }
              }
              .pointerInput(isLocked) {
                if (!isLocked) {
                  detectDragGestures(
                      onDragStart = { offset ->
                        if (offset.x < deadZonePx ||
                            offset.x > size.width - deadZonePx ||
                            offset.y > size.height - bottomDeadZonePx) {
                          ignoreDrag = true
                          return@detectDragGestures
                        }
                        ignoreDrag = false
                        isDragging = true
                        dragDirectionDetermined = false
                        dragStartOffset = offset
                        seekAccumulator = 0f
                        targetSeekPosition = currentPosition

                        var mpvVolume = 100
                        // 🔥 VLC changed to MPV
                        if (currentEngine == PlayerEngine.MPV) {
                          try {
                            mpvVolume = MPVLib.getPropertyInt("volume") ?: 100
                          } catch (e: Exception) {}
                        }

                        // 🔥 VLC changed to MPV
                        if (currentEngine == PlayerEngine.MPV && mpvVolume > 100) {
                          volumeAccumulator = mpvVolume.toFloat()
                        } else if (currentEngine == PlayerEngine.EXO &&
                            gestureIndicatorValue > 100f) {
                          volumeAccumulator = gestureIndicatorValue
                        } else {
                          val maxSystemVol =
                              audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                          val currentSystemVol =
                              audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                          volumeAccumulator = (currentSystemVol.toFloat() / maxSystemVol) * 100f
                        }
                      },
                      onDragEnd = {
                        if (dragType == 2 &&
                            volumeAccumulator > 100f &&
                            currentEngine == PlayerEngine.EXO) {
                          if (loudnessEnhancer == null && exoPlayer != null) {
                            try {
                              val sessionId = (exoPlayer as? ExoPlayer)?.audioSessionId ?: 0
                              if (sessionId != 0) {
                                loudnessEnhancer = LoudnessEnhancer(sessionId)
                              }
                            } catch (e: Exception) {}
                          }
                          try {
                            if (loudnessEnhancer?.enabled == false) {
                              loudnessEnhancer?.enabled = true
                            }
                            val currentVolInt = volumeAccumulator.toInt()
                            val boostRatio = (currentVolInt - 100f) / 100f
                            val gainMB = (boostRatio * 10000).toInt()
                            loudnessEnhancer?.setTargetGain(gainMB)
                          } catch (e: Exception) {}
                        }

                        if (dragType == 4) {
                          onSeek(targetSeekPosition)
                          viewModel.setCurrentPosition(targetSeekPosition)
                        }
                        isDragging = false
                        dragType = 0
                        ignoreDrag = false
                        viewModel.hideGestureOverlay()
                      },
                      onDragCancel = {
                        isDragging = false
                        dragType = 0
                        ignoreDrag = false
                        viewModel.hideGestureOverlay()
                      },
                      onDrag = { change, dragAmount ->
                        if (ignoreDrag || videoScale > 1f) return@detectDragGestures
                        change.consume()

                        if (!dragDirectionDetermined) {
                          if (abs(dragAmount.x) > abs(dragAmount.y)) {
                            dragType = 4
                          } else {
                            if (dragStartOffset.x < size.width * 0.5f) {
                              dragType = 1
                            } else {
                              dragType = 2
                            }
                          }
                          dragDirectionDetermined = true
                        }

                        when (dragType) {
                          1 -> {
                            if (activity != null) {
                              val attributes = activity.window.attributes
                              var currentBrightness = attributes.screenBrightness
                              if (currentBrightness < 0) currentBrightness = 0.5f
                              val newBrightness =
                                  (currentBrightness + (-dragAmount.y / size.height * 1.2f))
                                      .coerceIn(0.01f, 1f)
                              attributes.screenBrightness = newBrightness
                              activity.window.attributes = attributes
                              viewModel.setCurrentBrightnessPercent(newBrightness)
                              viewModel.setGestureIndicator(1, newBrightness)
                            }
                          }
                          2 -> {
                            val dragSensitivity = 150f
                            volumeAccumulator += (-dragAmount.y / size.height) * dragSensitivity
                            val maxAllowedVol = if (localBoostEnabled) 200f else 100f
                            volumeAccumulator = volumeAccumulator.coerceIn(0f, maxAllowedVol)

                            val maxSystemVol =
                                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentSystemVol =
                                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                            if (volumeAccumulator <= 100f) {
                              val newSystemVol = ((volumeAccumulator / 100f) * maxSystemVol).toInt()

                              if (currentSystemVol != newSystemVol) {
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC, newSystemVol, 0)
                              }

                              // 🔥 VLC changed to MPV
                              if (currentEngine == PlayerEngine.MPV) {
                                try {
                                  if ((MPVLib.getPropertyInt("volume") ?: 100) != 100)
                                      MPVLib.setPropertyInt("volume", 100)
                                } catch (e: Exception) {}
                              } else {
                                try {
                                  if (loudnessEnhancer?.enabled == true)
                                      loudnessEnhancer?.enabled = false
                                } catch (e: Exception) {}
                              }

                              viewModel.setCurrentVolumePercent(volumeAccumulator / 100f)
                              viewModel.setGestureIndicator(2, volumeAccumulator)
                            } else {
                              if (currentSystemVol != maxSystemVol) {
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC, maxSystemVol, 0)
                              }

                              // 🔥 VLC changed to MPV
                              if (currentEngine == PlayerEngine.MPV) {
                                try {
                                  MPVLib.setPropertyInt("volume", volumeAccumulator.toInt())
                                } catch (e: Exception) {}
                              }

                              viewModel.setCurrentVolumePercent(1f)
                              viewModel.setGestureIndicator(2, volumeAccumulator)
                            }
                          }
                          4 -> {
                            seekAccumulator += dragAmount.x
                            val seekSensitivity = 60000f
                            val msPerPixel = seekSensitivity / size.width
                            targetSeekPosition =
                                (currentPosition + (seekAccumulator * msPerPixel).toLong())
                                    .coerceIn(0L, duration)
                            viewModel.setGestureIndicator(4, targetSeekPosition.toFloat())
                          }
                        }
                      })
                }
              }) {
        if (showDoubleTapRipple != 0) {
          val isLeft = showDoubleTapRipple == -1
          Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(
                    modifier =
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(0.35f)
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        if (isLeft)
                                            listOf(
                                                Color.White.copy(alpha = 0.15f), Color.Transparent)
                                        else
                                            listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.15f)))),
                    contentAlignment = Alignment.Center) {
                      Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (isLeft) R.drawable.ic_skip_previous
                                        else R.drawable.ic_skip_next),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLeft) "-10s" else "+10s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp)
                      }
                    }
              }
        }

        // 🔥 1. Center Seek Indicator
        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 4,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)) {
              Box(
                  modifier =
                      Modifier.clip(RoundedCornerShape(24.dp))
                          .background(Color.Black.copy(alpha = 0.5f))
                          .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                          .padding(vertical = 20.dp, horizontal = 40.dp),
                  contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      val targetMs = gestureIndicatorValue.toLong()
                      Text(
                          "Seek to",
                          color = Color.White,
                          fontSize = 16.sp,
                          fontWeight = FontWeight.SemiBold)
                      Spacer(modifier = Modifier.height(8.dp))
                      Text(
                          formatTimeHelper(targetMs),
                          color = MaterialTheme.colorScheme.primary,
                          fontSize = 28.sp,
                          fontWeight = FontWeight.ExtraBold)
                      Text(
                          "/ ${formatTimeHelper(duration)}",
                          color = Color.White.copy(alpha = 0.7f),
                          fontSize = 14.sp)
                    }
                  }
            }

        // 🔥 2. Left Side Brightness Indicator
        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 1,
            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
              val progress = currentBrightnessPercent
              val percentage = "${(gestureIndicatorValue * 100).toInt()}%"

              Box(
                  modifier =
                      Modifier.height(180.dp)
                          .width(52.dp)
                          .clip(RoundedCornerShape(26.dp))
                          .background(Color.Black.copy(alpha = 0.5f))
                          .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                          .padding(vertical = 16.dp),
                  contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()) {
                          Icon(
                              painter = painterResource(id = R.drawable.ic_brightness),
                              contentDescription = "Brightness",
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(22.dp))
                          Box(
                              modifier =
                                  Modifier.weight(1f)
                                      .padding(vertical = 8.dp)
                                      .width(4.dp)
                                      .clip(CircleShape)
                                      .background(Color.White.copy(alpha = 0.2f)),
                              contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .fillMaxHeight(progress)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary))
                              }
                          Text(
                              text = percentage,
                              color = Color.White,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold)
                        }
                  }
            }

        // 🔥 3. Right Side Volume Indicator
        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 2,
            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)) {
              val progress = (gestureIndicatorValue / 100f).coerceIn(0f, 1f)
              val percentage = "${gestureIndicatorValue.toInt()}%"

              Box(
                  modifier =
                      Modifier.height(180.dp)
                          .width(52.dp)
                          .clip(RoundedCornerShape(26.dp))
                          .background(Color.Black.copy(alpha = 0.5f))
                          .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                          .padding(vertical = 16.dp),
                  contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight()) {
                          Icon(
                              painter = painterResource(id = R.drawable.ic_volume_up),
                              contentDescription = "Volume",
                              tint = MaterialTheme.colorScheme.primary,
                              modifier = Modifier.size(22.dp))
                          Box(
                              modifier =
                                  Modifier.weight(1f)
                                      .padding(vertical = 8.dp)
                                      .width(4.dp)
                                      .clip(CircleShape)
                                      .background(Color.White.copy(alpha = 0.2f)),
                              contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .fillMaxHeight(progress)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary))
                              }
                          Text(
                              text = percentage,
                              color = Color.White,
                              fontSize = 11.sp,
                              fontWeight = FontWeight.Bold)
                        }
                  }
            }

        AnimatedVisibility(
            visible = controlsVisible || isLocked,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()) {
              Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked) {
                  IconButton(
                      onClick = { viewModel.toggleLock() },
                      modifier =
                          Modifier.align(Alignment.CenterStart)
                              .padding(start = 32.dp)
                              .size(48.dp)
                              .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                              .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)) {
                        Crossfade(targetState = isLocked, label = "lockAnim") { locked ->
                          Icon(
                              painterResource(
                                  id = if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open),
                              "Unlock",
                              tint = Color.White)
                        }
                      }
                } else {
                  Row(
                      modifier =
                          Modifier.align(Alignment.TopCenter)
                              .fillMaxWidth()
                              .padding(
                                  top = if (isLandscape) 8.dp else 16.dp,
                                  start = 16.dp,
                                  end = rightSafePadding)
                              .clip(RoundedCornerShape(50))
                              .background(Color.Black.copy(alpha = 0.5f))
                              .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                              .padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                          Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }

                        Text(
                            videoTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                              IconButton(onClick = { showAudioMenu = true }) {
                                Icon(
                                    painterResource(id = R.drawable.ic_music_note),
                                    "Audio Tracks",
                                    tint = Color.White)
                              }
                              IconButton(onClick = { showSubtitleMenu = true }) {
                                Text("CC", color = Color.White, fontWeight = FontWeight.Bold)
                              }
                              Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                  Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier =
                                        Modifier.background(MaterialTheme.colorScheme.surface)) {
                                      DropdownMenuItem(
                                          text = {
                                            Text(
                                                "Engine: ${if (currentEngine == PlayerEngine.EXO) "ExoPlayer" else "MPV (HW)"}",
                                                color = MaterialTheme.colorScheme.onSurface)
                                          },
                                          leadingIcon = {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary)
                                          },
                                          onClick = {
                                            showMoreMenu = false
                                            // 🔥 VLC changed to MPV
                                            val newEngine =
                                                if (currentEngine == PlayerEngine.EXO)
                                                    PlayerEngine.MPV
                                                else PlayerEngine.EXO
                                            viewModel.setPlayerEngine(newEngine)
                                            context
                                                .getSharedPreferences(
                                                    "vidmax_settings", Context.MODE_PRIVATE)
                                                .edit()
                                                .putString("player_engine", newEngine.name)
                                                .apply()

                                            Toast.makeText(
                                                    context,
                                                    "Switched to ${if (newEngine == PlayerEngine.EXO) "ExoPlayer" else "MPV"}. Reloading...",
                                                    Toast.LENGTH_SHORT)
                                                .show()
                                            activity?.recreate()
                                          })

                                      DropdownMenuItem(
                                          text = {
                                            Text(
                                                "Speed & Sync",
                                                color = MaterialTheme.colorScheme.onSurface)
                                          },
                                          leadingIcon = {
                                            Icon(
                                                Icons.Default.Settings,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary)
                                          },
                                          onClick = {
                                            showMoreMenu = false
                                            showSyncMenu = true
                                          })
                                      DropdownMenuItem(
                                          text = {
                                            Text(
                                                "Share",
                                                color = MaterialTheme.colorScheme.onSurface)
                                          },
                                          leadingIcon = {
                                            Icon(
                                                Icons.Default.Share,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary)
                                          },
                                          onClick = {
                                            showMoreMenu = false
                                            val uri = getMediaUriFromPath(context, currentPath)
                                            if (uri != null) {
                                              val shareIntent =
                                                  Intent(Intent.ACTION_SEND).apply {
                                                    type = "video/*"
                                                    putExtra(
                                                        Intent.EXTRA_STREAM,
                                                        uri as android.os.Parcelable)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                  }
                                              context.startActivity(
                                                  Intent.createChooser(shareIntent, "Share Video"))
                                            }
                                          })
                                      DropdownMenuItem(
                                          text = {
                                            Text(
                                                "Properties",
                                                color = MaterialTheme.colorScheme.onSurface)
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
                                    }
                              }
                            }
                      }

                  if (isLandscape) {
                    Column(
                        modifier =
                            Modifier.align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                          SideActionButton(
                              icon = R.drawable.ic_volume_up,
                              text = if (localBoostEnabled) "200%" else "100%",
                              isActive = localBoostEnabled,
                              onClick = toggleAudioBoost)
                          SideActionButton(
                              icon = R.drawable.ic_speed,
                              text = "${currentPlaybackSpeed}x",
                              isActive = currentPlaybackSpeed != 1f,
                              onClick = { showSyncMenu = true })
                          SideActionButton(
                              icon = R.drawable.ic_timer,
                              text =
                                  if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Timer",
                              isActive = sleepTimerMinutes > 0,
                              onClick = { showTimerDialog = true })
                          SideActionButton(
                              icon = R.drawable.ic_headphones,
                              text = "BG Play",
                              isActive = bgPlayEnabled,
                              onClick = { onBgPlayToggle(!bgPlayEnabled) })
                        }
                  } else {
                    Row(
                        modifier =
                            Modifier.align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(top = 76.dp, start = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                          QuickActionButton(
                              icon = R.drawable.ic_volume_up,
                              text = if (localBoostEnabled) "200%" else "100%",
                              isActive = localBoostEnabled,
                              modifier = Modifier.weight(1f),
                              onClick = toggleAudioBoost)
                          QuickActionButton(
                              icon = R.drawable.ic_speed,
                              text = "${currentPlaybackSpeed}x",
                              isActive = currentPlaybackSpeed != 1f,
                              modifier = Modifier.weight(1f),
                              onClick = { showSyncMenu = true })
                          QuickActionButton(
                              icon = R.drawable.ic_timer,
                              text =
                                  if (sleepTimerMinutes > 0) "${sleepTimerMinutes}m" else "Timer",
                              isActive = sleepTimerMinutes > 0,
                              modifier = Modifier.weight(1f),
                              onClick = { showTimerDialog = true })
                          QuickActionButton(
                              icon = R.drawable.ic_headphones,
                              text = "BG Play",
                              isActive = bgPlayEnabled,
                              modifier = Modifier.weight(1f),
                              onClick = { onBgPlayToggle(!bgPlayEnabled) })
                        }
                  }

                  val bottomOuterModifier =
                      if (isLandscape) {
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(
                                1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                      } else {
                        Modifier.fillMaxWidth()
                      }

                  val timelineInnerModifier =
                      if (isLandscape) {
                        Modifier.fillMaxWidth()
                      } else {
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                      }

                  val controlsInnerModifier =
                      if (isLandscape) {
                        Modifier.fillMaxWidth()
                      } else {
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                      }

                  Column(
                      modifier =
                          Modifier.align(Alignment.BottomCenter)
                              .fillMaxWidth()
                              .padding(
                                  bottom = if (isLandscape) 8.dp else 24.dp,
                                  start = if (isLandscape) 16.dp else 12.dp,
                                  end = if (isLandscape) rightSafePadding else 12.dp)
                              .then(bottomOuterModifier),
                      verticalArrangement =
                          Arrangement.spacedBy(if (isLandscape) 4.dp else 12.dp)) {
                        Row(
                            modifier = timelineInnerModifier,
                            verticalAlignment = Alignment.CenterVertically) {
                              var isDraggingSlider by remember { mutableStateOf(false) }
                              var sliderDragValue by remember { mutableStateOf(0f) }
                              val safeDuration = if (duration > 0) duration else 1L
                              val actualProgress =
                                  (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(
                                      0f, 1f)
                              val displayProgress =
                                  if (isDraggingSlider) sliderDragValue else actualProgress
                              val displayPosition =
                                  if (isDraggingSlider) (sliderDragValue * safeDuration).toLong()
                                  else currentPosition

                              val animatedProgress by
                                  animateFloatAsState(
                                      targetValue = displayProgress,
                                      animationSpec =
                                          if (isDraggingSlider) snap()
                                          else tween(100, easing = LinearEasing),
                                      label = "progressAnim")

                              Text(
                                  formatTimeHelper(displayPosition),
                                  color = Color.White,
                                  fontSize = 12.sp,
                                  fontWeight = FontWeight.Medium)

                              BoxWithConstraints(
                                  modifier =
                                      Modifier.weight(1f)
                                          .padding(horizontal = 16.dp)
                                          .height(36.dp)
                                          .pointerInput(safeDuration) {
                                            detectHorizontalDragGestures(
                                                onDragStart = { offset ->
                                                  isDraggingSlider = true
                                                  sliderDragValue =
                                                      (offset.x / size.width.toFloat()).coerceIn(
                                                          0f, 1f)
                                                },
                                                onDragEnd = {
                                                  val targetPos =
                                                      (sliderDragValue * safeDuration).toLong()
                                                  onSeek(targetPos)
                                                  viewModel.setCurrentPosition(targetPos)
                                                  isDraggingSlider = false
                                                },
                                                onDragCancel = { isDraggingSlider = false },
                                                onHorizontalDrag = { change, _ ->
                                                  change.consume()
                                                  sliderDragValue =
                                                      (change.position.x / size.width.toFloat())
                                                          .coerceIn(0f, 1f)
                                                })
                                          }
                                          .pointerInput(safeDuration) {
                                            detectTapGestures(
                                                onTap = { offset ->
                                                  val tapValue =
                                                      (offset.x / size.width.toFloat()).coerceIn(
                                                          0f, 1f)
                                                  val targetPos = (tapValue * safeDuration).toLong()
                                                  onSeek(targetPos)
                                                  viewModel.setCurrentPosition(targetPos)
                                                })
                                          },
                                  contentAlignment = Alignment.CenterStart) {
                                    val thumbX = maxWidth * animatedProgress
                                    val thumbOffset =
                                        (thumbX - 1.5.dp).coerceIn(0.dp, maxWidth - 3.dp)

                                    Box(
                                        modifier =
                                            Modifier.align(Alignment.Center)
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.2f)))
                                    Box(
                                        modifier =
                                            Modifier.align(Alignment.CenterStart)
                                                .width(thumbX)
                                                .height(3.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary))
                                    Box(
                                        modifier =
                                            Modifier.align(Alignment.CenterEnd)
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.5f)))
                                    Box(
                                        modifier =
                                            Modifier.align(Alignment.CenterStart)
                                                .offset(x = thumbOffset)
                                                .width(3.dp)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(Color.White))
                                  }

                              Text(
                                  formatTimeHelper(duration),
                                  color = Color.White,
                                  fontSize = 12.sp,
                                  fontWeight = FontWeight.Medium)
                            }

                        Row(
                            modifier = controlsInnerModifier,
                            verticalAlignment = Alignment.CenterVertically) {
                              Row(
                                  modifier = Modifier.weight(1f),
                                  horizontalArrangement = Arrangement.Start,
                                  verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.toggleLock() }) {
                                      Crossfade(targetState = isLocked, label = "lockIcon") { locked
                                        ->
                                        Icon(
                                            painterResource(
                                                id =
                                                    if (locked) R.drawable.ic_lock
                                                    else R.drawable.ic_lock_open),
                                            "Lock",
                                            tint = Color.White)
                                      }
                                    }
                                    IconButton(
                                        onClick = {
                                          if (activity != null) {
                                            val isLandscapeRotate =
                                                configuration.orientation ==
                                                    Configuration.ORIENTATION_LANDSCAPE
                                            activity.requestedOrientation =
                                                if (isLandscapeRotate)
                                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                                else
                                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                          }
                                        }) {
                                          Icon(
                                              painterResource(id = R.drawable.ic_screen_rotation),
                                              "Rotate",
                                              tint = Color.White)
                                        }
                                  }

                              Row(
                                  horizontalArrangement = Arrangement.Center,
                                  verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onPrevious) {
                                      Icon(
                                          painterResource(id = R.drawable.ic_skip_previous),
                                          "Previous",
                                          tint = Color.White,
                                          modifier = Modifier.size(32.dp))
                                    }
                                    IconButton(onClick = onPlayPause) {
                                      Crossfade(
                                          targetState = isPlaying,
                                          animationSpec = tween(durationMillis = 300),
                                          label = "playPause") { playing ->
                                            Icon(
                                                painterResource(
                                                    id =
                                                        if (playing) R.drawable.ic_pause
                                                        else R.drawable.ic_play),
                                                "Play/Pause",
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp))
                                          }
                                    }
                                    IconButton(onClick = onNext) {
                                      Icon(
                                          painterResource(id = R.drawable.ic_skip_next),
                                          "Next",
                                          tint = Color.White,
                                          modifier = Modifier.size(32.dp))
                                    }
                                  }

                              Row(
                                  modifier = Modifier.weight(1f),
                                  horizontalArrangement = Arrangement.End,
                                  verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.cycleLoopMode() }) {
                                      Crossfade(targetState = loopMode, label = "repeatIcon") { mode
                                        ->
                                        val repeatIconRes =
                                            if (mode == LoopMode.ONE) R.drawable.ic_repeat_one
                                            else R.drawable.ic_repeat
                                        val repeatTint =
                                            if (mode == LoopMode.NONE) Color.White
                                            else MaterialTheme.colorScheme.primary
                                        Icon(
                                            painterResource(id = repeatIconRes),
                                            "Repeat",
                                            tint = repeatTint)
                                      }
                                    }
                                    IconButton(onClick = { viewModel.cycleAspectRatio() }) {
                                      Icon(
                                          painterResource(id = R.drawable.ic_aspect_ratio),
                                          "Aspect Ratio",
                                          tint = Color.White)
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
private fun SideActionButton(icon: Int, text: String, isActive: Boolean, onClick: () -> Unit) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center) {
              Icon(
                  painterResource(id = icon),
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(18.dp))
            }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
      }
}

@Composable
private fun QuickActionButton(
    icon: Int,
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
  Row(
      modifier =
          modifier
              .clip(RoundedCornerShape(12.dp))
              .background(
                  if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                  else Color.Black.copy(alpha = 0.5f))
              .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
              .clickable { onClick() }
              .padding(horizontal = 6.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center) {
        Icon(
            painterResource(id = icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis)
      }
}

fun formatTimeHelper(ms: Long): String {
  if (ms < 0) return "00:00"
  val totalSeconds = ms / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
  else String.format("%02d:%02d", minutes, seconds)
}

fun getMediaUriFromPath(context: Context, path: String): Uri? {
  val cursor =
      context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          arrayOf(MediaStore.Video.Media._ID),
          MediaStore.Video.Media.DATA + "=?",
          arrayOf(path),
          null)
  return cursor?.use {
    if (it.moveToFirst())
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(0))
    else null
  }
}
