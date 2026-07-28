@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MpvTrackInfo(val id: Int, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel,
    currentPath: String,
    audioBoostEnabled: Boolean,
    currentPlaybackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    videoScale: Float,
    onVideoScaleChange: (Float, Offset) -> Unit,
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
    onPickSubtitle: () -> Unit
) {

    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rightSafePadding = 16.dp
    val leftSafePadding = 16.dp

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
    var showEngineMenu by remember { mutableStateOf(false) }
    
    var showDecoderMenu by remember { mutableStateOf(false) }
    var currentMpvDecoder by remember { mutableStateOf("auto-copy") }

    var showSyncMenu by remember { mutableStateOf(false) }
    var audioDelayMs by remember { mutableLongStateOf(0L) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }

    var showZoomMenu by remember { mutableStateOf(false) }

    var localBoostEnabled by remember { mutableStateOf(audioBoostEnabled) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showTimerDialog by remember { mutableStateOf(false) }

    // Gesture States
    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableIntStateOf(0) }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }
    var ignoreDrag by remember { mutableStateOf(false) }
    var dragDirectionDetermined by remember { mutableStateOf(false) }
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var targetSeekPosition by remember { mutableLongStateOf(0L) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }

    val pointerCount = remember { AtomicInteger(0) }
    var showDoubleTapRipple by remember { mutableIntStateOf(0) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var showZoomMeter by remember { mutableStateOf(false) }
    
    LaunchedEffect(videoScale) {
        if (videoScale != 1f) {
            showZoomMeter = true
            delay(1500)
            showZoomMeter = false
        } else {
            showZoomMeter = false
        }
    }

    val density = LocalDensity.current
    val deadZonePx = remember(density) { with(density) { 40.dp.toPx() } }
    val bottomDeadZonePx = remember(density) { with(density) { 120.dp.toPx() } }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    var mpvSubTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
    var currentMpvSubId by remember { mutableStateOf("no") }

    var mpvAudioTracks by remember { mutableStateOf<List<MpvTrackInfo>>(emptyList()) }
    var currentMpvAudioId by remember { mutableStateOf("1") }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay(sleepTimerMinutes * 60 * 1000L)
            try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
            exoPlayer?.pause()
            activity?.finish()
        }
    }

    LaunchedEffect(showDecoderMenu) {
        if (showDecoderMenu && currentEngine == PlayerEngine.MPV) {
            try { currentMpvDecoder = MPVLib.getPropertyString("hwdec") ?: "auto-copy" } catch (e: Exception) {}
        }
    }

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
                        val name = if (title.isNotEmpty()) title else if (lang.isNotEmpty()) lang else "Subtitle Track $id"
                        if (id != -1) tracks.add(MpvTrackInfo(id, name))
                    }
                }
                mpvSubTracks = tracks
                currentMpvSubId = MPVLib.getPropertyString("sid") ?: "no"
            } catch (e: Exception) {}
        }
    }

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
                        val name = if (title.isNotEmpty()) title else if (lang.isNotEmpty()) lang else "Audio Track $id"
                        if (id != -1) tracks.add(MpvTrackInfo(id, name))
                    }
                }
                mpvAudioTracks = tracks
                currentMpvAudioId = MPVLib.getPropertyString("aid") ?: "1"
            } catch (e: Exception) {}
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

            if (currentEngine == PlayerEngine.MPV) {
                try { MPVLib.setPropertyInt("volume", 100) } catch (e: Exception) {}
            } else {
                try { loudnessEnhancer?.enabled = false } catch (e: Exception) {}
            }
            viewModel.setCurrentVolumePercent(1f)
            viewModel.setGestureIndicator(2, 100f)
        }
    }

    // Zoom Bottom Sheet
    if (showZoomMenu) {
        ModalBottomSheet(onDismissRequest = { showZoomMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
            val primaryFaded = primaryColor.copy(alpha = 0.2f)
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(primaryFaded).clickable { 
                            val newZoom = (videoScale - 0.1f).coerceAtLeast(0.1f)
                            onVideoScaleChange(newZoom / videoScale, Offset.Zero)
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("-", color = primaryColor, fontSize = 28.sp, fontWeight = FontWeight.Light) }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                        Text("Video", color = Color.White, fontSize = 14.sp)
                        Text("Zoom", color = Color.White, fontSize = 14.sp)
                        Text(String.format(Locale.US, "%.2fx", videoScale), color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Slider(
                        value = videoScale,
                        onValueChange = { newZoom -> onVideoScaleChange(newZoom / videoScale, Offset.Zero) },
                        valueRange = 0.1f..3.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor, inactiveTrackColor = primaryColor.copy(alpha = 0.3f))
                    )

                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(primaryFaded).clickable { 
                            val newZoom = (videoScale + 0.1f).coerceAtMost(3.0f)
                            onVideoScaleChange(newZoom / videoScale, Offset.Zero)
                        },
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = primaryColor, fontSize = 24.sp, fontWeight = FontWeight.Light) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { showZoomMenu = false }, modifier = Modifier.weight(1f).height(48.dp), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text("Set as default", fontSize = 14.sp)
                    }
                    Button(onClick = { onVideoScaleChange(1f / videoScale, Offset.Zero) }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Text("Reset", color = onPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDecoderMenu) {
        ModalBottomSheet(onDismissRequest = { showDecoderMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hardware Decoder", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                val decoderOptions = listOf(Pair("auto-copy", "Auto (auto-copy)"), Pair("no", "SW (no)"), Pair("mediacodec-copy", "HW (mediacodec-copy)"), Pair("mediacodec", "HW+ (mediacodec)"))
                decoderOptions.forEach { (value, label) ->
                    val isSelected = currentMpvDecoder == value
                    Row(modifier = Modifier.fillMaxWidth().clickable { try { MPVLib.setPropertyString("hwdec", value); currentMpvDecoder = value } catch (e: Exception) {}; showDecoderMenu = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked), contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false }, containerColor = Color(0xFF1E1E1E),
            title = { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0, 15, 30, 60, 120).forEach { mins ->
                        val text = if (mins == 0) "Off" else "$mins Minutes"
                        Row(modifier = Modifier.fillMaxWidth().clickable { sleepTimerMinutes = mins; showTimerDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (sleepTimerMinutes == mins) MaterialTheme.colorScheme.primary else Color.Transparent)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Close") } }
        )
    }

    if (showPropertiesDialog) {
        val file = File(currentPath)
        val fileSizeMb = if (file.exists()) String.format(Locale.US, "%.2f MB", file.length() / (1024.0 * 1024.0)) else "Unknown"
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false },
            title = { Text("Video Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Title: $videoTitle", fontSize = 14.sp)
                    Text("Path: $currentPath", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Size: $fileSizeMb", fontSize = 14.sp)
                    Text("Engine: ${if (currentEngine == PlayerEngine.EXO) "ExoPlayer (Media3)" else "MPV Engine (HW)"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { showPropertiesDialog = false }) { Text("Close") } }
        )
    }

    if (showSyncMenu) {
        LaunchedEffect(showSyncMenu) {
            if (currentEngine == PlayerEngine.MPV) {
                try { audioDelayMs = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toLong(); subtitleDelayMs = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000).toLong() } catch (e: Exception) {}
            }
        }
        ModalBottomSheet(onDismissRequest = { showSyncMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Speed & Sync", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text("Playback Speed", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        speeds.forEach { speed ->
                            val isSelected = currentPlaybackSpeed == speed
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)).clickable { onSpeedChange(speed) }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("${speed}x", color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                if (currentEngine == PlayerEngine.MPV) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Audio Delay", color = Color.White, fontSize = 16.sp); Text(if (audioDelayMs == 0L) "0 ms" else "${audioDelayMs} ms", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { audioDelayMs -= 50; try { MPVLib.setPropertyDouble("audio-delay", audioDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("-50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { audioDelayMs += 50; try { MPVLib.setPropertyDouble("audio-delay", audioDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("+50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Subtitle Delay", color = Color.White, fontSize = 16.sp); Text(if (subtitleDelayMs == 0L) "0 ms" else "${subtitleDelayMs} ms", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { subtitleDelayMs -= 50; try { MPVLib.setPropertyDouble("sub-delay", subtitleDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("-50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { subtitleDelayMs += 50; try { MPVLib.setPropertyDouble("sub-delay", subtitleDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("+50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                } else {
                    Text("Sync delays are automatically handled by ExoPlayer.", color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showSubtitleMenu) {
        ModalBottomSheet(onDismissRequest = { showSubtitleMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subtitles", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { showSubtitleMenu = false; onPickSubtitle() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.ic_folder), contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Open Local Subtitle", color = Color.White, fontSize = 16.sp)
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                if (currentEngine == PlayerEngine.MPV) {
                    val isOff = currentMpvSubId == "no" || currentMpvSubId == "false" || currentMpvSubId == "0"
                    Row(modifier = Modifier.fillMaxWidth().clickable { try { MPVLib.setPropertyString("sid", "no") } catch (e: Exception) {}; showSubtitleMenu = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (isOff) MaterialTheme.colorScheme.primary else Color.Transparent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Off / Disable", color = Color.White, fontSize = 16.sp)
                    }
                    mpvSubTracks.forEach { track ->
                        val isSelected = currentMpvSubId == track.id.toString()
                        Row(modifier = Modifier.fillMaxWidth().clickable { try { MPVLib.setPropertyInt("sid", track.id) } catch (e: Exception) {}; showSubtitleMenu = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(track.name, color = Color.White, fontSize = 16.sp)
                        }
                    }
                } else {
                    Text("Track selection is not available in ExoPlayer. Please switch to MPV (HW) Engine from the menu (⋮) to change subtitles.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAudioMenu) {
        ModalBottomSheet(onDismissRequest = { showAudioMenu = false }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Audio Tracks", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                if (currentEngine == PlayerEngine.MPV) {
                    mpvAudioTracks.forEach { track ->
                        val isSelected = currentMpvAudioId == track.id.toString()
                        Row(modifier = Modifier.fillMaxWidth().clickable { try { MPVLib.setPropertyInt("aid", track.id) } catch (e: Exception) {}; showAudioMenu = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(track.name, color = Color.White, fontSize = 16.sp)
                        }
                    }
                } else {
                    Text("Track selection is not available in ExoPlayer. Please switch to MPV (HW) Engine from the menu (⋮) to change audio tracks.", color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointerCount.set(event.changes.count { it.pressed })
                    }
                }
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    var isDraggingLocal = false
                    var dragAccumulatorX = 0f
                    var dragAccumulatorY = 0f
                    
                    val inDeadZone = down.position.x < deadZonePx || 
                                     down.position.x > size.width - deadZonePx || 
                                     down.position.y > size.height - bottomDeadZonePx

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        if (pressed.size == 1 && videoScale <= 1f && !inDeadZone && pointerCount.get() == 1) {
                            val change = pressed.first()
                            val dragAmount = Offset(
                                change.position.x - change.previousPosition.x,
                                change.position.y - change.previousPosition.y
                            )

                            if (!isDraggingLocal) {
                                dragAccumulatorX += dragAmount.x
                                dragAccumulatorY += dragAmount.y
                                val distance = sqrt(dragAccumulatorX * dragAccumulatorX + dragAccumulatorY * dragAccumulatorY)

                                if (distance > 20f) {
                                    isDraggingLocal = true
                                    isDragging = true
                                    dragDirectionDetermined = false
                                    dragStartOffset = down.position
                                    seekAccumulator = 0f
                                    targetSeekPosition = currentPosition

                                    var mpvVolume = 100
                                    if (currentEngine == PlayerEngine.MPV) {
                                        try { mpvVolume = MPVLib.getPropertyInt("volume") ?: 100 } catch (e: Exception) {}
                                    }

                                    if (currentEngine == PlayerEngine.MPV && mpvVolume > 100) {
                                        volumeAccumulator = mpvVolume.toFloat()
                                    } else if (currentEngine == PlayerEngine.EXO && gestureIndicatorValue > 100f) {
                                        volumeAccumulator = gestureIndicatorValue
                                    } else {
                                        val maxSystemVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val currentSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        volumeAccumulator = (currentSystemVol.toFloat() / maxSystemVol) * 100f
                                    }
                                }
                            }

                            if (isDraggingLocal) {
                                change.consume()

                                if (!dragDirectionDetermined) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y)) dragType = 4 
                                    else if (dragStartOffset.x < size.width * 0.5f) dragType = 1 else dragType = 2
                                    dragDirectionDetermined = true
                                }

                                when (dragType) {
                                    1 -> {
                                        if (activity != null) {
                                            val attributes = activity.window.attributes
                                            var currentBrightness = attributes.screenBrightness
                                            if (currentBrightness < 0) currentBrightness = 0.5f
                                            val newBrightness = (currentBrightness + (-dragAmount.y / size.height * 1.2f)).coerceIn(0.01f, 1f)
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

                                        val maxSystemVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val currentSystemVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                        if (volumeAccumulator <= 100f) {
                                            val newSystemVol = ((volumeAccumulator / 100f) * maxSystemVol).toInt()
                                            if (currentSystemVol != newSystemVol) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newSystemVol, 0)
                                            if (currentEngine == PlayerEngine.MPV) {
                                                try { if ((MPVLib.getPropertyInt("volume") ?: 100) != 100) MPVLib.setPropertyInt("volume", 100) } catch (e: Exception) {}
                                            } else {
                                                try { if (loudnessEnhancer?.enabled == true) loudnessEnhancer?.enabled = false } catch (e: Exception) {}
                                            }
                                            viewModel.setCurrentVolumePercent(volumeAccumulator / 100f)
                                            viewModel.setGestureIndicator(2, volumeAccumulator)
                                        } else {
                                            if (currentSystemVol != maxSystemVol) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVol, 0)
                                            if (currentEngine == PlayerEngine.MPV) {
                                                try { MPVLib.setPropertyInt("volume", volumeAccumulator.toInt()) } catch (e: Exception) {}
                                            }
                                            viewModel.setCurrentVolumePercent(1f)
                                            viewModel.setGestureIndicator(2, volumeAccumulator)
                                        }
                                    }
                                    4 -> {
                                        seekAccumulator += dragAmount.x
                                        val seekSensitivity = 60000f
                                        val msPerPixel = seekSensitivity / size.width
                                        targetSeekPosition = (currentPosition + (seekAccumulator * msPerPixel).toLong()).coerceIn(0L, duration)
                                        viewModel.setGestureIndicator(4, targetSeekPosition.toFloat())
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isDraggingLocal) {
                        if (dragType == 2 && volumeAccumulator > 100f && currentEngine == PlayerEngine.EXO) {
                            if (loudnessEnhancer == null && exoPlayer != null) {
                                try {
                                    val sessionId = (exoPlayer as? ExoPlayer)?.audioSessionId ?: 0
                                    if (sessionId != 0) loudnessEnhancer = LoudnessEnhancer(sessionId)
                                } catch (e: Exception) {}
                            }
                            try {
                                if (loudnessEnhancer?.enabled == false) loudnessEnhancer?.enabled = true
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

                        isDraggingLocal = false
                        isDragging = false
                        dragType = 0
                        ignoreDrag = false
                        viewModel.hideGestureOverlay()
                    }
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
                        onTap = { viewModel.setControlsVisible(!controlsVisible) }
                    )
                } else {
                    detectTapGestures(onTap = { viewModel.setControlsVisible(true) })
                }
            }
    ) {
        
        AnimatedVisibility(
            visible = showZoomMeter,
            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Zoom: ${(videoScale * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showDoubleTapRipple != 0) {
            val isLeft = showDoubleTapRipple == -1
            val amount = if (isLeft) -10 else 10
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.35f).clip(if (isLeft) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (isLeft) {
                            CombiningChevronsAnimation(isRight = false, trigger = showDoubleTapRipple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("- ${abs(amount)}s", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White)
                        } else {
                            Text("+ ${abs(amount)}s", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            CombiningChevronsAnimation(isRight = true, trigger = showDoubleTapRipple)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 4,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)).padding(vertical = 20.dp, horizontal = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val targetMs = gestureIndicatorValue.toLong()
                    Text("Seek to", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(formatTimeHelper(targetMs), color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("/ ${formatTimeHelper(duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 1,
            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            val progress = currentBrightnessPercent
            val percentage = "${(gestureIndicatorValue * 100).toInt()}%"
            Box(
                modifier = Modifier.height(180.dp).width(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp)).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                    Icon(painter = painterResource(id = R.drawable.ic_brightness), contentDescription = "Brightness", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp).width(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(progress).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(text = percentage, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 2,
            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            val progress = (gestureIndicatorValue / 100f).coerceIn(0f, 1f)
            val percentage = "${gestureIndicatorValue.toInt()}%"
            Box(
                modifier = Modifier.height(180.dp).width(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp)).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                    Icon(painter = painterResource(id = R.drawable.ic_volume_up), contentDescription = "Volume", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp).width(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(progress).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(text = percentage, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || isLocked,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked) {
                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = leftSafePadding).size(48.dp).background(Color(0xFF141414).copy(alpha = 0.8f), CircleShape)
                    ) { Crossfade(targetState = isLocked, label = "lockAnim") { locked -> Icon(painterResource(id = if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open), "Unlock", tint = Color.White) } }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 24.dp, start = leftSafePadding, end = rightSafePadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircleIconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp)) }

                        Row(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) { Text(videoTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }

                        Box {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { showEngineMenu = true },
                                contentAlignment = Alignment.Center
                            ) { Text(if (currentEngine == PlayerEngine.EXO) "EXO" else "HW", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            
                            DropdownMenu(expanded = showEngineMenu, onDismissRequest = { showEngineMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                                DropdownMenuItem(text = { Text("Engine: ExoPlayer", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showEngineMenu = false; if (currentEngine != PlayerEngine.EXO) { viewModel.setPlayerEngine(PlayerEngine.EXO); context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).edit().putString("player_engine", PlayerEngine.EXO.name).apply(); Toast.makeText(context, "Switched to ExoPlayer. Reloading...", Toast.LENGTH_SHORT).show(); activity?.recreate() } })
                                DropdownMenuItem(text = { Text("Engine: MPV (HW)", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showEngineMenu = false; if (currentEngine != PlayerEngine.MPV) { viewModel.setPlayerEngine(PlayerEngine.MPV); context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).edit().putString("player_engine", PlayerEngine.MPV.name).apply(); Toast.makeText(context, "Switched to MPV. Reloading...", Toast.LENGTH_SHORT).show(); activity?.recreate() } })
                                if (currentEngine == PlayerEngine.MPV) {
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(text = { Text("MPV Decoder Settings", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showEngineMenu = false; showDecoderMenu = true })
                                }
                            }
                        }

                        CircleIconButton(onClick = { showAudioMenu = true }, drawableRes = R.drawable.ic_music_note)
                        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { showSubtitleMenu = true }, contentAlignment = Alignment.Center) { Text("CC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        
                        Box {
                            CircleIconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White, modifier = Modifier.size(20.dp)) }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                                DropdownMenuItem(text = { Text("Speed & Sync", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showMoreMenu = false; showSyncMenu = true })
                                DropdownMenuItem(text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showMoreMenu = false; val uri = getMediaUriFromPath(context, currentPath); if (uri != null) { val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "video/*"; putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(shareIntent, "Share Video")) } })
                                DropdownMenuItem(text = { Text("Properties", color = MaterialTheme.colorScheme.onSurface) }, leadingIcon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { showMoreMenu = false; showPropertiesDialog = true })
                            }
                        }
                    }

                    // CENTER PLAY/PAUSE CONTROLS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleActionButton(icon = R.drawable.ic_skip_previous, isActive = false, onClick = onPrevious, isLarge = true)
                        
                        // Animated Play/Pause Button
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "buttonScale")
                        val playPauseRotation by animateFloatAsState(targetValue = if (isPlaying) 180f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "playPauseRotation")
                        
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = isPlaying, 
                                animationSpec = tween(durationMillis = 300),
                                modifier = Modifier.graphicsLayer { rotationZ = playPauseRotation },
                                label = "playPause"
                            ) { playing -> 
                                Icon(painterResource(id = if (playing) R.drawable.ic_pause else R.drawable.ic_play), "Play/Pause", tint = Color.White, modifier = Modifier.size(40.dp)) 
                            }
                        }
                        
                        CircleActionButton(icon = R.drawable.ic_skip_next, isActive = false, onClick = onNext, isLarge = true)
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 24.dp, start = leftSafePadding, end = rightSafePadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        var isExtraControlsVisible by remember { mutableStateOf(false) }
                        val scrollState = rememberScrollState()

                        // 🔥 SMART COLLAPSIBLE TOOLBAR (MX PLAYER STYLE)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp), // হালকা লেফট থেকে শুরু
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ১. Lock Button (সবসময় বাইরে থাকবে)
                            CircleActionButton(
                                icon = R.drawable.ic_lock_open, // আনলক অবস্থায় শো করবে
                                isActive = false, 
                                onClick = { viewModel.toggleLock() }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            // ২. Expand / Collapse Arrow Button
                            CircleActionButtonVector(
                                icon = if (isExtraControlsVisible) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
                                isActive = isExtraControlsVisible,
                                onClick = { isExtraControlsVisible = !isExtraControlsVisible }
                            )

                            // ৩. The Hidden Controls (স্লাইড করে বের হবে)
                            AnimatedVisibility(
                                visible = isExtraControlsVisible,
                                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp).horizontalScroll(scrollState),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircleActionButton(icon = R.drawable.ic_headphones, isActive = bgPlayEnabled, onClick = { onBgPlayToggle(!bgPlayEnabled) })
                                    CircleActionButton(icon = R.drawable.ic_screen_rotation, isActive = false, onClick = { if (activity != null) { val isLandscapeRotate = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE; activity.requestedOrientation = if (isLandscapeRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } })
                                    CircleActionButton(icon = R.drawable.ic_zoom, isActive = videoScale != 1f, onClick = { showZoomMenu = true })
                                    CircleActionButton(icon = R.drawable.ic_aspect_ratio, isActive = false, onClick = { viewModel.cycleAspectRatio() })
                                    CircleActionButton(icon = R.drawable.ic_speed, isActive = currentPlaybackSpeed != 1f, onClick = { showSyncMenu = true })
                                    CircleActionButton(icon = if (loopMode == LoopMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat, isActive = loopMode != LoopMode.NONE, onClick = { viewModel.cycleLoopMode() })
                                    CircleActionButton(icon = R.drawable.ic_volume_up, isActive = localBoostEnabled, onClick = toggleAudioBoost)
                                    CircleActionButton(icon = R.drawable.ic_timer, isActive = sleepTimerMinutes > 0, onClick = { showTimerDialog = true })
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            var isDraggingSlider by remember { mutableStateOf(false) }
                            var sliderDragValue by remember { mutableFloatStateOf(0f) }
                            val safeDuration = if (duration > 0) duration else 1L
                            val actualProgress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                            val displayProgress = if (isDraggingSlider) sliderDragValue else actualProgress
                            val displayPosition = if (isDraggingSlider) (sliderDragValue * safeDuration).toLong() else currentPosition
                            val animatedProgress by animateFloatAsState(targetValue = displayProgress, animationSpec = if (isDraggingSlider) snap() else tween(100, easing = LinearEasing), label = "progressAnim")

                            Text(formatTimeHelper(displayPosition), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)

                            val primaryAccentColor = MaterialTheme.colorScheme.primary
                            val primaryTrackBgColor = primaryAccentColor.copy(alpha = 0.3f)

                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .height(36.dp)
                                    .pointerInput(safeDuration) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { offset -> isDraggingSlider = true; sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) },
                                            onDragEnd = { val targetPos = (sliderDragValue * safeDuration).toLong(); onSeek(targetPos); viewModel.setCurrentPosition(targetPos); isDraggingSlider = false },
                                            onDragCancel = { isDraggingSlider = false },
                                            onHorizontalDrag = { change, _ -> change.consume(); sliderDragValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) }
                                        )
                                    }
                                    .pointerInput(safeDuration) { detectTapGestures(onTap = { offset -> val tapValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f); val targetPos = (tapValue * safeDuration).toLong(); onSeek(targetPos); viewModel.setCurrentPosition(targetPos) }) },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val thumbWidth = 4.dp
                                val thumbHeight = 18.dp
                                val trackHeight = 10.dp
                                val thumbCenter = maxWidth * animatedProgress
                                val thumbOffset = (thumbCenter - (thumbWidth / 2)).coerceIn(0.dp, maxWidth - thumbWidth)
                                
                                Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(trackHeight).clip(CircleShape).background(primaryTrackBgColor))
                                val activeTrackWidth = (thumbCenter - 4.dp).coerceAtLeast(0.dp)
                                Box(modifier = Modifier.align(Alignment.CenterStart).width(activeTrackWidth).height(trackHeight).clip(CircleShape).background(primaryAccentColor))
                                Box(modifier = Modifier.align(Alignment.CenterStart).offset(x = thumbOffset).width(thumbWidth).height(thumbHeight).clip(CircleShape).background(primaryAccentColor))
                            }
                            Text(formatTimeHelper(duration), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// 🔥 REFINED BUTTON STYLES WITH SPRING ANIMATIONS
@Composable
private fun CircleIconButton(onClick: () -> Unit, drawableRes: Int? = null, content: @Composable (() -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "iconScale")

    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (content != null) { content() } else if (drawableRes != null) { Icon(painterResource(id = drawableRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun CircleActionButton(icon: Int, isActive: Boolean, onClick: () -> Unit, isLarge: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "actionScale")
    
    val buttonSize = if (isLarge) 56.dp else 46.dp
    val iconSize = if (isLarge) 32.dp else 22.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(id = icon), contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White, modifier = Modifier.size(iconSize))
    }
}

// 🔥 Helper for Vector Icons (For the Expand/Collapse Arrow)
@Composable
private fun CircleActionButtonVector(icon: ImageVector, isActive: Boolean, onClick: () -> Unit, isLarge: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "actionScale")
    
    val buttonSize = if (isLarge) 56.dp else 46.dp
    val iconSize = if (isLarge) 32.dp else 22.dp

    Box(
        modifier = Modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun CombiningChevronsAnimation(isRight: Boolean, trigger: Int, modifier: Modifier = Modifier) {
    val animations = remember { mutableStateListOf<Long>() }
    LaunchedEffect(trigger) { if (trigger != 0) animations.add(System.nanoTime()) }
    Row(modifier = modifier) {
        Box {
             Icon(imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            animations.forEach { animId -> key(animId) { MovingChevron(isRight = isRight, onFinished = { animations.remove(animId) }) } }
        }
    }
}

@Composable
fun MovingChevron(isRight: Boolean, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(targetValue = 1f, animationSpec = tween(250, easing = LinearEasing)); onFinished() }
    val startOffset = if (isRight) -15f else 15f
    val currentOffset = startOffset * (1f - progress.value)
    val alpha = 1f - progress.value
    Icon(imageVector = if (isRight) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp).alpha(alpha).layout { measurable, constraints -> val placeable = measurable.measure(constraints); layout(placeable.width, placeable.height) { placeable.placeRelative(x = currentOffset.dp.roundToPx(), y = 0) } } )
}

fun formatTimeHelper(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds) else String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun getMediaUriFromPath(context: Context, path: String): Uri? {
    val cursor = context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID), MediaStore.Video.Media.DATA + "=?", arrayOf(path), null)
    return cursor?.use { if (it.moveToFirst()) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(0)) else null }
}
