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
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.vidmax.player.R
import com.vidmax.player.viewmodel.AspectRatioMode
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.PanelMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import com.vidmax.player.viewmodel.SubtitleAudioTab
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
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
    onVideoScaleChange: (Float, Offset, Offset?) -> Unit,
    exoPlayer: Player? = null,
    bgPlayEnabled: Boolean,
    onBgPlayToggle: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onBack: () -> Unit
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
    val showSyncSheet by viewModel.showSyncSheet.collectAsState()
    val showZoomSheet by viewModel.showZoomSheet.collectAsState()
    val showAspectSheet by viewModel.showAspectSheet.collectAsState()
    val showEngineMenu by viewModel.showEngineMenu.collectAsState()
    val showDecoderMenu by viewModel.showDecoderMenu.collectAsState()

    val isGestureOverlayVisible by viewModel.isGestureOverlayVisible.collectAsState()
    val gestureIndicatorType by viewModel.gestureIndicatorType.collectAsState()
    val gestureIndicatorValue by viewModel.gestureIndicatorValue.collectAsState()
    val currentVolumePercent by viewModel.currentVolumePercent.collectAsState()
    val currentBrightnessPercent by viewModel.currentBrightnessPercent.collectAsState()

    // ---- MPVEx preference switches (stored in vidmax_settings) ----
    val settingsPrefs = context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
    val legacyVerticalGestures = settingsPrefs.getBoolean("gesture_vertical_enabled", true)
    var brightnessGestureEnabled by remember {
        mutableStateOf(settingsPrefs.getBoolean("gesture_brightness_enabled", legacyVerticalGestures))
    }
    var volumeGestureEnabled by remember {
        mutableStateOf(settingsPrefs.getBoolean("gesture_volume_enabled", legacyVerticalGestures))
    }
    val pinchZoomEnabled by remember {
        mutableStateOf(settingsPrefs.getBoolean("pinch_zoom_enabled", true))
    }
    var horizontalSeekEnabled by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("gesture_horizontal_seek_enabled", true))
    }
    var doubleTapSeekSeconds by remember {
        mutableIntStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getInt("double_tap_seek_seconds", 10))
    }
    var reverseDoubleTap by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("reverse_double_tap", false))
    }
    var seekGestureSensitivity by remember {
        mutableIntStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getInt("seek_gesture_sensitivity", 60000))
    }
    var singleTapAction by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getString("single_tap_action", "toggle_controls") ?: "toggle_controls")
    }
    var preventSeekbarTap by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("prevent_seekbar_tap", false))
    }
    var autoHideControls by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("auto_hide_controls", true))
    }
    var controlsHideDelayMs by remember {
        mutableIntStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getInt("controls_hide_delay_ms", 3000))
    }
    var showControlsOnPlay by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("show_controls_on_play", true))
    }
    var bottomControlsBelowSeekbar by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("bottom_controls_below_seekbar", false))
    }
    var ambientMode by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("ambient_mode", false))
    }
    var keepScreenOn by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("keep_screen_on", true))
    }
    var hideButtonBackground by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("hide_button_background", false))
    }
    var reduceMotion by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("reduce_motion", false))
    }
    var whiteSeekbar by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("white_seekbar", false))
    }
    var showDoubleTapIndicator by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("show_double_tap_indicator", true))
    }
    var mpvVideoSync by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getString("mpv_video_sync", "audio") ?: "audio")
    }
    var mpvInterpolation by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("mpv_interpolation", false))
    }
    var mpvAudioPitchCorrection by remember {
        mutableStateOf(context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE).getBoolean("mpv_audio_pitch_correction", true))
    }

    val savePrefs: (String, Any) -> Unit = { key, value ->
        context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
            .edit()
            .apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
            .apply()
    }

    var currentMpvDecoder by remember { mutableStateOf("auto-copy") }

    var audioDelayMs by remember { mutableLongStateOf(0L) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }

    var localBoostEnabled by remember { mutableStateOf(audioBoostEnabled) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showImmersive by remember {
        mutableStateOf(true)
    }

    // Gesture States
    var isDragging by remember { mutableStateOf(false) }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }
    var ignoreDrag by remember { mutableStateOf(false) }
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var targetSeekPosition by remember { mutableLongStateOf(0L) }
    var volBase by remember { mutableIntStateOf(-1) }
    var brightBase by remember { mutableFloatStateOf(-1f) }
    var brightCurrent by remember { mutableFloatStateOf(-1f) }

    val pointerCount = remember { AtomicInteger(0) }
    var showDoubleTapRipple by remember { mutableIntStateOf(0) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var showZoomMeter by remember { mutableStateOf(false) }

    val currentVideoScale by rememberUpdatedState(videoScale)

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
    val bottomDeadZonePx = remember(density) { with(density) { 120.dp.toPx() } }

    var showMoreMenu by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, isLocked, autoHideControls, controlsHideDelayMs) {
        if (controlsVisible && !isLocked && autoHideControls && controlsHideDelayMs > 0) {
            delay(controlsHideDelayMs.toLong())
            viewModel.setControlsVisible(false)
        }
    }

    LaunchedEffect(isPlaying, showControlsOnPlay) {
        if (isPlaying && showControlsOnPlay && !isLocked) {
            viewModel.setControlsVisible(true)
        }
    }

    LaunchedEffect(ambientMode, keepScreenOn) {
        val act = activity ?: return@LaunchedEffect
        act.window.setDimAmount(if (ambientMode) 0.85f else 0f)
        if (keepScreenOn) {
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(currentEngine, mpvVideoSync, mpvInterpolation, mpvAudioPitchCorrection) {
        if (currentEngine == PlayerEngine.MPV) {
            try {
                MPVLib.setPropertyString("video-sync", mpvVideoSync)
                MPVLib.setPropertyBoolean("interpolation", mpvInterpolation)
                MPVLib.setPropertyBoolean("audio-pitch-correction", mpvAudioPitchCorrection)
            } catch (e: Exception) {}
        }
    }

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

    val toggleImmersive = {
        val activityRef = activity
        if (activityRef != null) {
            showImmersive = !showImmersive
            val controller = WindowInsetsControllerCompat(activityRef.window, activityRef.window.decorView)
            if (showImmersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            savePrefs("immersive_fullscreen", showImmersive)
        }
    }

    val toggleScreenRotation = {
        if (activity != null) {
            val isLandscapeNow = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            activity.requestedOrientation =
                if (isLandscapeNow) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    val toggleEngine = { engine: PlayerEngine ->
        if (currentEngine != engine) {
            viewModel.setPlayerEngine(engine)
            context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
                .edit().putString("player_engine", engine.name).apply()
            Toast.makeText(
                context,
                if (engine == PlayerEngine.MPV) "Switched to MPV. Reloading..." else "Switched to ExoPlayer. Reloading...",
                Toast.LENGTH_SHORT
            ).show()
            activity?.recreate()
        }
    }

    // ============================================================
    // Zoom bottom sheet
    // ============================================================
    if (showZoomSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.setShowZoomSheet(false) }, containerColor = Color(0xFF1E1E1E)) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
            val primaryFaded = primaryColor.copy(alpha = 0.2f)

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Text("Video Zoom", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MpvCircleButton(
                        icon = Icons.Outlined.ZoomOut,
                        contentDescription = "Zoom out",
                        onClick = {
                            val newZoom = (videoScale - 0.1f).coerceAtLeast(1f)
                            onVideoScaleChange(newZoom / videoScale, Offset.Zero, null)
                        },
                        size = 44.dp
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                        Text("Zoom", color = Color.White, fontSize = 14.sp)
                        Text(
                            String.format(Locale.US, "%.2fx", videoScale),
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = videoScale,
                        onValueChange = { newZoom -> onVideoScaleChange(newZoom / videoScale, Offset.Zero, null) },
                        valueRange = 1f..4f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = primaryColor.copy(alpha = 0.3f)
                        )
                    )

                    MpvCircleButton(
                        icon = Icons.Outlined.ZoomIn,
                        contentDescription = "Zoom in",
                        onClick = {
                            val newZoom = (videoScale + 0.1f).coerceAtMost(4f)
                            onVideoScaleChange(newZoom / videoScale, Offset.Zero, null)
                        },
                        size = 44.dp
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.setShowZoomSheet(false) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Set as default", fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onVideoScaleChange(1f / videoScale, Offset.Zero, null) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Reset", color = onPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ============================================================
    // Aspect ratio bottom sheet
    // ============================================================
    if (showAspectSheet) {
        val aspect by viewModel.aspectRatio.collectAsState()
        ModalBottomSheet(onDismissRequest = { viewModel.setShowAspectSheet(false) }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aspect Ratio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                listOf(
                    Triple(AspectRatioMode.FIT, "Fit", Icons.Outlined.FitScreen),
                    Triple(AspectRatioMode.FILL, "Crop / Fill", Icons.Outlined.AspectRatio),
                    Triple(AspectRatioMode.STRETCH, "Stretch", Icons.Outlined.Fullscreen)
                ).forEach { (mode, label, icon) ->
                    val isSelected = aspect == mode
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { viewModel.setAspectRatio(mode); viewModel.setShowAspectSheet(false) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ============================================================
    // Decoder bottom sheet
    // ============================================================
    if (showDecoderMenu) {
        ModalBottomSheet(onDismissRequest = { viewModel.setShowDecoderMenu(false) }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hardware Decoder", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                val decoderOptions = listOf(
                    Pair("auto-copy", "Auto (auto-copy)"),
                    Pair("no", "SW (no)"),
                    Pair("mediacodec-copy", "HW (mediacodec-copy)"),
                    Pair("mediacodec", "HW+ (mediacodec)")
                )
                decoderOptions.forEach { (value, label) ->
                    val isSelected = currentMpvDecoder == value
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            try { MPVLib.setPropertyString("hwdec", value); currentMpvDecoder = value } catch (e: Exception) {}
                            viewModel.setShowDecoderMenu(false)
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // ============================================================
    // Sleep timer dialog
    // ============================================================
    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false }, containerColor = Color(0xFF1E1E1E),
            title = { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0, 15, 30, 60, 120).forEach { mins ->
                        val text = if (mins == 0) "Off" else "$mins Minutes"
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { sleepTimerMinutes = mins; showTimerDialog = false }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (sleepTimerMinutes == mins) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimerDialog = false }) { Text("Close") } }
        )
    }

    // ============================================================
    // Video properties dialog
    // ============================================================
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
                    Text(
                        "Engine: ${if (currentEngine == PlayerEngine.EXO) "ExoPlayer (Media3)" else "MPV Engine (HW)"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPropertiesDialog = false }) { Text("Close") } }
        )
    }

    // ============================================================
    // Speed & Sync bottom sheet
    // ============================================================
    if (showSyncSheet) {
        LaunchedEffect(showSyncSheet) {
            if (currentEngine == PlayerEngine.MPV) {
                try {
                    audioDelayMs = ((MPVLib.getPropertyDouble("audio-delay") ?: 0.0) * 1000).toLong()
                    subtitleDelayMs = ((MPVLib.getPropertyDouble("sub-delay") ?: 0.0) * 1000).toLong()
                } catch (e: Exception) {}
            }
        }
        ModalBottomSheet(onDismissRequest = { viewModel.setShowSyncSheet(false) }, containerColor = Color(0xFF1E1E1E)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Speed & Sync", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text("Playback Speed", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        speeds.forEach { speed ->
                            val isSelected = currentPlaybackSpeed == speed
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f))
                                    .clickable { onSpeedChange(speed) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${speed}x",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                if (currentEngine == PlayerEngine.MPV) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Audio Delay", color = Color.White, fontSize = 16.sp)
                            Text(if (audioDelayMs == 0L) "0 ms" else "${audioDelayMs} ms", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { audioDelayMs -= 50; try { MPVLib.setPropertyDouble("audio-delay", audioDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("-50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { audioDelayMs += 50; try { MPVLib.setPropertyDouble("audio-delay", audioDelayMs / 1000.0) } catch (e: Exception) {} }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text("+50ms", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Subtitle Delay", color = Color.White, fontSize = 16.sp)
                            Text(if (subtitleDelayMs == 0L) "0 ms" else "${subtitleDelayMs} ms", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
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

    // ============================================================
    // Main overlay layout
    // ============================================================
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            pointerCount.set(event.changes.count { it.pressed })
                        }
                    }
                }
                .pointerInput(isLocked, pinchZoomEnabled, currentVideoScale) {
                    if (isLocked) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isDraggingLocal = false
                        var accX = 0f
                        var accY = 0f
                        var dragType = 0
                        var pinchActive = false
                        var lastDist = 0f
                        var lastCentroid = Offset.Zero

                        var lastAppliedBrightness = -1f
                        var lastAppliedVolume = -1
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            // TWO FINGERS: pinch zoom + pan
                            if (pressed.size >= 2 && pinchZoomEnabled) {
                                isDraggingLocal = false
                                dragType = 0
                                val p1 = pressed[0]
                                val p2 = pressed[1]
                                val dist = (p1.position - p2.position).getDistance()
                                val centroid = (p1.position + p2.position) / 2f
                                if (pinchActive && lastDist > 0f) {
                                    val zoomChange = dist / lastDist
                                    val panChange = centroid - lastCentroid
                                    onVideoScaleChange(zoomChange, panChange, centroid)
                                } else {
                                    pinchActive = true
                                }
                                lastDist = dist
                                lastCentroid = centroid
                                p1.consume()
                                p2.consume()
                            }
                            // ONE FINGER
                            else if (pressed.size == 1) {
                                val change = pressed.first()
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y

                                if (currentVideoScale > 1f) {
                                    // zoomed: one finger pans the video
                                    onVideoScaleChange(1f, Offset(dx, dy), change.position)
                                    change.consume()
                                } else {
                                    if (!isDraggingLocal) {
                                        accX += dx
                                        accY += dy
                                        // Increased Touch Slop to prevent accidental gesture activation
                                        if (sqrt(accX * accX + accY * accY) > 40f) {
                                            isDraggingLocal = true
                                            dragType = if (abs(accX) > abs(accY)) {
                                                // horizontal seek, only outside bottom dead zone
                                                if (down.position.y < size.height - bottomDeadZonePx) 4 else 0
                                            } else if (down.position.x < size.width / 2f) 1 // left = brightness
                                            else 2 // right = volume

                                            isDragging = true
                                            seekAccumulator = 0f
                                            targetSeekPosition = currentPosition

                                            volBase = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            volumeAccumulator = 0f
                                            lastAppliedVolume = volBase

                                            brightBase = activity?.window?.attributes?.screenBrightness ?: -1f
                                            if (brightBase < 0f) {
                                                brightBase = Settings.System.getFloat(
                                                    context.contentResolver,
                                                    Settings.System.SCREEN_BRIGHTNESS,
                                                    255f
                                                ) / 255f
                                            }
                                            brightCurrent = brightBase
                                            lastAppliedBrightness = brightBase
                                        }
                                    }
                                    if (isDraggingLocal && dragType != 0) {
                                        change.consume()
                                        when (dragType) {
                                            1 -> {
                                                if (brightnessGestureEnabled) {
                                                    if (activity != null) {
                                                        // Half screen height (0.5f) drag for Full brightness range
                                                        brightCurrent = (brightCurrent - dy / (size.height * 0.5f)).coerceIn(0f, 1f)
                                                        
                                                        // Debounce OS Window attribute updates to prevent Stuttering
                                                        if (abs(brightCurrent - lastAppliedBrightness) > 0.02f) {
                                                            activity.window.attributes = activity.window.attributes.apply {
                                                                screenBrightness = brightCurrent
                                                            }
                                                            lastAppliedBrightness = brightCurrent
                                                        }
                                                        viewModel.setCurrentBrightnessPercent(brightCurrent)
                                                        viewModel.setGestureIndicator(1, brightCurrent)
                                                    }
                                                }
                                            }
                                            2 -> {
                                                if (volumeGestureEnabled) {
                                                    volumeAccumulator += dy
                                                    // Half screen height (0.5f) drag for Full volume range
                                                    val delta = (-volumeAccumulator / (size.height * 0.5f) * maxVol).roundToInt()
                                                    val newVol = (volBase + delta).coerceIn(0, maxVol)
                                                    
                                                    // Only update stream when integer value changes
                                                    if (newVol != lastAppliedVolume) {
                                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                                        lastAppliedVolume = newVol
                                                    }
                                                    viewModel.setGestureIndicator(2, newVol.toFloat() / maxVol)
                                                }
                                            }
                                            4 -> {
                                                if (horizontalSeekEnabled) {
                                                    seekAccumulator += dx
                                                    val seekSensitivity = seekGestureSensitivity.toFloat()
                                                    val msPerPixel = seekSensitivity / size.width
                                                    targetSeekPosition = (currentPosition + (seekAccumulator * msPerPixel).toLong()).coerceIn(0L, duration)
                                                    viewModel.setGestureIndicator(4, targetSeekPosition.toFloat())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDraggingLocal) {
                            if (dragType == 4 && horizontalSeekEnabled) {
                                onSeek(targetSeekPosition)
                                viewModel.setCurrentPosition(targetSeekPosition)
                            }

                            isDraggingLocal = false
                            isDragging = false
                            dragType = 0
                            ignoreDrag = false
                            viewModel.hideGestureOverlay()
                        }
                        pinchActive = false
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                if (offset.y > size.height - bottomDeadZonePx) return@detectTapGestures
                                val third = size.width / 3f
                                val seekMs = doubleTapSeekSeconds * 1000L
                                val isLeftSide = offset.x < third
                                val isRightSide = offset.x > third * 2f
                                if (!isLeftSide && !isRightSide) {
                                    onPlayPause()
                                    return@detectTapGestures
                                }
                                val seekBackward = if (reverseDoubleTap) isRightSide else isLeftSide
                                val target = if (seekBackward) {
                                    (currentPosition - seekMs).coerceAtLeast(0L)
                                } else {
                                    (currentPosition + seekMs).coerceAtMost(duration)
                                }
                                onSeek(target)
                                viewModel.setCurrentPosition(target)
                                if (showDoubleTapIndicator) {
                                    showDoubleTapRipple = if (seekBackward) -1 else 1
                                    coroutineScope.launch {
                                        delay(600)
                                        showDoubleTapRipple = 0
                                    }
                                }
                            },
                            onTap = {
                                when (singleTapAction) {
                                    "play_pause" -> onPlayPause()
                                    else -> viewModel.setControlsVisible(!controlsVisible)
                                }
                            }
                        )
                    } else {
                        detectTapGestures(onTap = { viewModel.setControlsVisible(true) })
                    }
                }
        )

        // ---- Zoom % meter ----
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

        // ---- Double-tap seek ripple ----
        if (showDoubleTapRipple != 0) {
            val isLeft = showDoubleTapRipple == -1
            val amount = if (isLeft) -doubleTapSeekSeconds else doubleTapSeekSeconds
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.35f)
                        .clip(if (isLeft) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                        .background(Color.White.copy(alpha = 0.2f)),
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

        // ---- Seek gesture overlay ----
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

        // ---- Brightness gesture overlay ----
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
                    Icon(imageVector = Icons.Outlined.BrightnessHigh, contentDescription = "Brightness", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp).width(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(progress).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(text = percentage, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- Volume gesture overlay ----
        AnimatedVisibility(
            visible = isGestureOverlayVisible && !isLocked && gestureIndicatorType == 2,
            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            val progress = gestureIndicatorValue.coerceIn(0f, 1f)
            val percentage = "${(gestureIndicatorValue * 100).toInt()}%"
            Box(
                modifier = Modifier.height(180.dp).width(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp)).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                    Icon(imageVector = Icons.Outlined.VolumeUp, contentDescription = "Volume", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp).width(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(progress).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Text(text = percentage, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ============================================================
        // MPVEx-style controls (shown when not locked)
        // ============================================================
        AnimatedVisibility(
            visible = controlsVisible || isLocked,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked) {
                    // ---- Locked state: lock button + slide to unlock ----
                    MpvCircleButton(
                        icon = Icons.Default.Lock,
                        contentDescription = "Unlock",
                        onClick = { viewModel.setControlsVisible(true) },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = leftSafePadding),
                        size = 48.dp
                    )
                    MpvSlideToUnlock(
                        onUnlock = { viewModel.toggleLock() },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                    )
                } else {
                    // ==================== TOP BAR ====================
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 24.dp, start = leftSafePadding, end = rightSafePadding)
                            .windowInsetsPadding(WindowInsets.statusBars),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MpvCircleButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                            size = 42.dp
                        )

                        Row(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                videoTitle.ifBlank { File(currentPath).nameWithoutExtension },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Engine badge
                        Box {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { viewModel.setShowEngineMenu(true) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (currentEngine == PlayerEngine.EXO) "EXO" else "HW", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = showEngineMenu,
                                onDismissRequest = { viewModel.setShowEngineMenu(false) },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Engine: ExoPlayer", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { viewModel.setShowEngineMenu(false); toggleEngine(PlayerEngine.EXO) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Engine: MPV (HW)", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { viewModel.setShowEngineMenu(false); toggleEngine(PlayerEngine.MPV) }
                                )
                                if (currentEngine == PlayerEngine.MPV) {
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(
                                        text = { Text("MPV Decoder Settings", color = MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { viewModel.setShowEngineMenu(false); viewModel.setShowDecoderMenu(true) }
                                    )
                                }
                            }
                        }

                        MpvCircleButton(
                            icon = Icons.Outlined.Audiotrack,
                            contentDescription = "Audio tracks",
                            onClick = {
                                viewModel.setSubtitleAudioTab(SubtitleAudioTab.AUDIO)
                                viewModel.setPanelMode(PanelMode.SUB_AUDIO)
                            },
                            size = 42.dp
                        )

                        MpvCircleButton(
                            icon = Icons.Outlined.Subtitles,
                            contentDescription = "Subtitles",
                            onClick = {
                                viewModel.setSubtitleAudioTab(SubtitleAudioTab.SUBTITLE)
                                viewModel.setPanelMode(PanelMode.SUB_AUDIO)
                            },
                            size = 42.dp
                        )

                        // Settings (direct call, not inside a dropdown)
                        MpvCircleButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            onClick = { viewModel.setPanelMode(PanelMode.SETTINGS) },
                            size = 42.dp
                        )

                        // More menu
                        Box {
                            MpvCircleButton(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = "More",
                                onClick = { showMoreMenu = true },
                                size = 42.dp
                            )
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Speed & Sync", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showMoreMenu = false; viewModel.setShowSyncSheet(true) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMoreMenu = false
                                        val uri = getMediaUriFromPath(context, currentPath)
                                        if (uri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/*"
                                                putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Properties", color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showMoreMenu = false; showPropertiesDialog = true }
                                )
                            }
                        }
                    }

                    // ==================== CENTER TRANSPORT ====================
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MpvCircleButton(
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            onClick = onPrevious,
                            size = 48.dp
                        )

                        // Animated play / pause
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.85f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "playPauseScale"
                        )
                        val playPauseRotation by animateFloatAsState(
                            targetValue = if (isPlaying) 180f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "playPauseRotation"
                        )
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current
                                ) { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = isPlaying,
                                animationSpec = tween(durationMillis = 300),
                                modifier = Modifier.graphicsLayer { rotationZ = playPauseRotation },
                                label = "playPause"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        MpvCircleButton(
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            onClick = onNext,
                            size = 48.dp
                        )
                    }

                    // ==================== BOTTOM CONTROLS ====================
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 20.dp, start = leftSafePadding, end = rightSafePadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (bottomControlsBelowSeekbar) {
                            SeekBarRow(
                                currentPosition = currentPosition,
                                duration = duration,
                                whiteSeekbar = whiteSeekbar,
                                reduceMotion = reduceMotion,
                                preventTap = preventSeekbarTap,
                                onSeek = onSeek,
                                onPositionChange = viewModel::setCurrentPosition
                            )
                            BottomControlsScrollRow(
                                isLocked = isLocked,
                                bgPlayEnabled = bgPlayEnabled,
                                videoScale = videoScale,
                                currentPlaybackSpeed = currentPlaybackSpeed,
                                loopMode = loopMode,
                                localBoostEnabled = localBoostEnabled,
                                sleepTimerMinutes = sleepTimerMinutes,
                                showImmersive = showImmersive,
                                hideBackground = hideButtonBackground,
                                onToggleLock = { viewModel.toggleLock() },
                                onToggleBgPlay = { onBgPlayToggle(!bgPlayEnabled) },
                                onRotate = toggleScreenRotation,
                                onZoom = { viewModel.setShowZoomSheet(true) },
                                onAspect = { viewModel.setShowAspectSheet(true) },
                                onSpeed = { viewModel.setShowSyncSheet(true) },
                                onRepeat = { viewModel.cycleLoopMode() },
                                onBoost = toggleAudioBoost,
                                onTimer = { showTimerDialog = true },
                                onImmersive = toggleImmersive,
                                onKeepVisible = { viewModel.setControlsVisible(true) }
                            )
                        } else {
                            BottomControlsScrollRow(
                                isLocked = isLocked,
                                bgPlayEnabled = bgPlayEnabled,
                                videoScale = videoScale,
                                currentPlaybackSpeed = currentPlaybackSpeed,
                                loopMode = loopMode,
                                localBoostEnabled = localBoostEnabled,
                                sleepTimerMinutes = sleepTimerMinutes,
                                showImmersive = showImmersive,
                                hideBackground = hideButtonBackground,
                                onToggleLock = { viewModel.toggleLock() },
                                onToggleBgPlay = { onBgPlayToggle(!bgPlayEnabled) },
                                onRotate = toggleScreenRotation,
                                onZoom = { viewModel.setShowZoomSheet(true) },
                                onAspect = { viewModel.setShowAspectSheet(true) },
                                onSpeed = { viewModel.setShowSyncSheet(true) },
                                onRepeat = { viewModel.cycleLoopMode() },
                                onBoost = toggleAudioBoost,
                                onTimer = { showTimerDialog = true },
                                onImmersive = toggleImmersive,
                                onKeepVisible = { viewModel.setControlsVisible(true) }
                            )
                            SeekBarRow(
                                currentPosition = currentPosition,
                                duration = duration,
                                whiteSeekbar = whiteSeekbar,
                                reduceMotion = reduceMotion,
                                preventTap = preventSeekbarTap,
                                onSeek = onSeek,
                                onPositionChange = viewModel::setCurrentPosition
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// Scrollable bottom button row (MPVEx-style)
// ============================================================
@Composable
private fun BottomControlsScrollRow(
    isLocked: Boolean,
    bgPlayEnabled: Boolean,
    videoScale: Float,
    currentPlaybackSpeed: Float,
    loopMode: LoopMode,
    localBoostEnabled: Boolean,
    sleepTimerMinutes: Int,
    showImmersive: Boolean,
    hideBackground: Boolean,
    onToggleLock: () -> Unit,
    onToggleBgPlay: () -> Unit,
    onRotate: () -> Unit,
    onZoom: () -> Unit,
    onAspect: () -> Unit,
    onSpeed: () -> Unit,
    onRepeat: () -> Unit,
    onBoost: () -> Unit,
    onTimer: () -> Unit,
    onImmersive: () -> Unit,
    onKeepVisible: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            while (scrollState.isScrollInProgress) {
                onKeepVisible()
                delay(1000)
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MpvCircleButton(
            icon = if (isLocked) Icons.Default.Lock else Icons.Outlined.LockOpen,
            contentDescription = "Lock",
            onClick = onToggleLock,
            size = 42.dp,
            active = isLocked,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = if (bgPlayEnabled) Icons.Outlined.HeadsetOff else Icons.Outlined.Headset,
            contentDescription = "Background playback",
            onClick = onToggleBgPlay,
            size = 42.dp,
            active = bgPlayEnabled,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.ScreenRotation,
            contentDescription = "Rotate",
            onClick = onRotate,
            size = 42.dp,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.ZoomIn,
            contentDescription = "Zoom",
            onClick = onZoom,
            size = 42.dp,
            active = videoScale != 1f,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.AspectRatio,
            contentDescription = "Aspect ratio",
            onClick = onAspect,
            size = 42.dp,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.Speed,
            contentDescription = "Playback speed",
            onClick = onSpeed,
            size = 42.dp,
            active = currentPlaybackSpeed != 1f,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = if (loopMode == LoopMode.ONE) Icons.Default.RepeatOne else Icons.Outlined.Repeat,
            contentDescription = "Repeat",
            onClick = onRepeat,
            size = 42.dp,
            active = loopMode != LoopMode.NONE,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.VolumeUp,
            contentDescription = "Volume boost",
            onClick = onBoost,
            size = 42.dp,
            active = localBoostEnabled,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = Icons.Outlined.Timer,
            contentDescription = "Sleep timer",
            onClick = onTimer,
            size = 42.dp,
            active = sleepTimerMinutes > 0,
            hideBackground = hideBackground
        )
        MpvCircleButton(
            icon = if (showImmersive) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
            contentDescription = "Fullscreen",
            onClick = onImmersive,
            size = 42.dp,
            hideBackground = hideBackground
        )
    }
}

// ============================================================
// Seek bar row
// ============================================================
@Composable
private fun SeekBarRow(
    currentPosition: Long,
    duration: Long,
    whiteSeekbar: Boolean,
    reduceMotion: Boolean,
    preventTap: Boolean,
    onSeek: (Long) -> Unit,
    onPositionChange: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var isDraggingSlider by remember { mutableStateOf(false) }
        var sliderDragValue by remember { mutableFloatStateOf(0f) }
        val safeDuration = if (duration > 0) duration else 1L
        val actualProgress = (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        val displayProgress = if (isDraggingSlider) sliderDragValue else actualProgress
        val displayPosition = if (isDraggingSlider) (sliderDragValue * safeDuration).toLong() else currentPosition
        val animatedProgress by animateFloatAsState(
            targetValue = displayProgress,
            animationSpec = when {
                reduceMotion -> snap()
                isDraggingSlider -> snap()
                else -> tween(100, easing = LinearEasing)
            },
            label = "seekProgress"
        )

        Text(
            formatTimeHelper(displayPosition),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        val primaryAccentColor = if (whiteSeekbar) Color.White else MaterialTheme.colorScheme.primary
        val primaryTrackBgColor = primaryAccentColor.copy(alpha = 0.3f)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(36.dp)
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDraggingSlider = true
                            sliderDragValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetPos = (sliderDragValue * safeDuration).toLong()
                            onSeek(targetPos)
                            onPositionChange(targetPos)
                            isDraggingSlider = false
                        },
                        onDragCancel = { isDraggingSlider = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            sliderDragValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(safeDuration, preventTap) {
                    if (preventTap) return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        val tapValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val targetPos = (tapValue * safeDuration).toLong()
                        onSeek(targetPos)
                        onPositionChange(targetPos)
                    })
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbWidth = 4.dp
            val thumbHeight = 18.dp
            val trackHeight = 8.dp
            val thumbCenter = maxWidth * animatedProgress
            val thumbOffset = (thumbCenter - (thumbWidth / 2)).coerceIn(0.dp, maxWidth - thumbWidth)

            Box(
                modifier = Modifier.align(Alignment.Center)
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(primaryTrackBgColor)
            )
            val activeTrackWidth = (thumbCenter - 4.dp).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .width(activeTrackWidth)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(primaryAccentColor)
            )
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .width(thumbWidth)
                    .height(thumbHeight)
                    .clip(CircleShape)
                    .background(primaryAccentColor)
            )
        }
        Text(
            formatTimeHelper(duration),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================
// MPVEx-style translucent circular button
// ============================================================
@Composable
fun MpvCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    active: Boolean = false,
    tint: Color = Color.White,
    hideBackground: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mpvButtonScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.size(size).scale(scale),
        shape = CircleShape,
        color = when {
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            hideBackground -> Color.Transparent
            else -> Color.White.copy(alpha = 0.12f)
        },
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else tint,
        border = if (hideBackground && !active) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else tint,
                modifier = Modifier.padding(size * 0.22f)
            )
        }
    }
}

// ============================================================
// MPVEx-style slide to unlock
// ============================================================
@Composable
private fun MpvSlideToUnlock(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(0.dp) }
    var unlocked by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxSlide = maxWidth - 48.dp
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (unlocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
                .pointerInput(maxSlide) {
                    detectHorizontalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            if (offset > maxSlide / 2f) {
                                unlocked = true
                                onUnlock()
                            } else {
                                offset = 0.dp
                            }
                        },
                        onDragCancel = { offset = 0.dp },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offset = (offset + with(density) { dragAmount.toDp() }).coerceIn(0.dp, maxSlide)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = "Slide to unlock",
                tint = if (unlocked) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            "Slide to unlock",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ============================================================
// Chevron seek animations
// ============================================================
@Composable
fun CombiningChevronsAnimation(isRight: Boolean, trigger: Int, modifier: Modifier = Modifier) {
    val animations = remember { mutableStateListOf<Long>() }
    LaunchedEffect(trigger) { if (trigger != 0) animations.add(System.nanoTime()) }
    Row(modifier = modifier) {
        Box {
            Icon(
                imageVector = if (isRight) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            animations.forEach { animId ->
                key(animId) { MovingChevron(isRight = isRight, onFinished = { animations.remove(animId) }) }
            }
        }
    }
}

@Composable
fun MovingChevron(isRight: Boolean, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        progress.animateTo(targetValue = 1f, animationSpec = tween(250, easing = LinearEasing))
        onFinished()
    }
    val startOffset = if (isRight) -15f else 15f
    val currentOffset = startOffset * (1f - progress.value)
    val alpha = 1f - progress.value
    Icon(
        imageVector = if (isRight) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(48.dp).alpha(alpha).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val offsetPx = with(density) { currentOffset.dp.roundToPx() }
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(x = offsetPx, y = 0)
            }
        }
    )
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
    val cursor = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media._ID),
        MediaStore.Video.Media.DATA + "=?",
        arrayOf(path),
        null
    )
    return cursor?.use { if (it.moveToFirst()) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.getLong(0)) else null }
}
