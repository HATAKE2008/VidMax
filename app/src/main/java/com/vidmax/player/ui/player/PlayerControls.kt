@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vidmax.player.ui.player

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.screen.VideoDetailsDialog
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
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MpvTrackInfo(val id: Int, val name: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel,
    currentPath: String,
    minimalist: Boolean = false,
    audioBoostEnabled: Boolean,
    currentPlaybackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    videoScale: Float,
    onVideoScaleChange: (Float, Offset, Offset?) -> Unit,
    liveZoomScale: MutableFloatState,
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
    isBuffering: Boolean = false
) {

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    // Localized messages hoisted here: takeScreenshot() and toggleEngine run
    // in non-composable callbacks, so they capture these pre-read values.
    val captureUnavailableText = stringResource(R.string.player_capture_unavailable)
    val frameSavedText = stringResource(R.string.player_frame_saved)
    val captureFailedText = stringResource(R.string.player_capture_failed)
    val engineMpvText = stringResource(R.string.player_engine_mpv)
    val engineExoText = stringResource(R.string.player_engine_exo)

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rightSafePadding = 16.dp
    val leftSafePadding = 16.dp

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    // Lock overlay visibility: shown on lock, auto-hides after ~2.8s of
    // inactivity, toggled by taps while locked (hide when visible, reveal
    // + restart countdown when hidden). Unlock restores full controls.
    // Locked taps toggle the shared controls visibility (REX model: the
    // lock UI is purely controlsVisible && isLocked, so show/hide always
    // stays in sync and taps can never strand the user on a hidden overlay).
    // Fresh reads via UpdatedState because this detector coroutine restarts
    // only on key change and would otherwise freeze stale values.
    val lockedTapToggle = rememberUpdatedState {
        viewModel.setControlsVisible(!viewModel.controlsVisible.value)
    }
    val loopMode by viewModel.loopMode.collectAsState()
    val abPointA by viewModel.abRepeatA.collectAsState()
    val abPointB by viewModel.abRepeatB.collectAsState()
    val abLoopEnabled by viewModel.abLoopEnabled.collectAsState()
    val showABPanel by viewModel.showABPanel.collectAsState()
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

    val errorMessage by viewModel.errorMessage.collectAsState()

    // ---- MPVEx preference switches ----
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
        mutableStateOf(settingsPrefs.getBoolean("gesture_horizontal_seek_enabled", true))
    }
    var doubleTapSeekSeconds by remember {
        mutableIntStateOf(settingsPrefs.getInt("double_tap_seek_seconds", 10))
    }
    var reverseDoubleTap by remember {
        mutableStateOf(settingsPrefs.getBoolean("reverse_double_tap", false))
    }
    var seekGestureSensitivity by remember {
        mutableIntStateOf(settingsPrefs.getInt("seek_gesture_sensitivity", 60000))
    }
    var preventSeekbarTap by remember {
        mutableStateOf(settingsPrefs.getBoolean("prevent_seekbar_tap", false))
    }
    var autoHideControls by remember {
        mutableStateOf(settingsPrefs.getBoolean("auto_hide_controls", true))
    }
    var controlsHideDelayMs by remember {
        mutableIntStateOf(settingsPrefs.getInt("controls_hide_delay_ms", 3000))
    }
    var showControlsOnPlay by remember {
        mutableStateOf(settingsPrefs.getBoolean("show_controls_on_play", true))
    }
    var bottomControlsBelowSeekbar by remember {
        mutableStateOf(settingsPrefs.getBoolean("bottom_controls_below_seekbar", false))
    }
    var showDoubleTapIndicator by remember {
        mutableStateOf(settingsPrefs.getBoolean("show_double_tap_indicator", true))
    }
    var mpvVideoSync by remember {
        mutableStateOf(settingsPrefs.getString("mpv_video_sync", "audio") ?: "audio")
    }
    var mpvInterpolation by remember {
        mutableStateOf(settingsPrefs.getBoolean("mpv_interpolation", false))
    }
    var mpvAudioPitchCorrection by remember {
        mutableStateOf(settingsPrefs.getBoolean("mpv_audio_pitch_correction", true))
    }

    val savePrefs: (String, Any) -> Unit = { key, value ->
        settingsPrefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }

    var currentMpvDecoder by remember { mutableStateOf("auto-copy") }

    var audioDelayMs by remember { mutableLongStateOf(0L) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }

    var localBoostEnabled by remember { mutableStateOf(audioBoostEnabled) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showImmersive by remember { mutableStateOf(true) }

    // Gesture States
    var isDragging by remember { mutableStateOf(false) }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }
    var ignoreDrag by remember { mutableStateOf(false) }
    var seekAccumulator by remember { mutableFloatStateOf(0f) }
    var targetSeekPosition by remember { mutableLongStateOf(0L) }
    var volBasePercent by remember { mutableFloatStateOf(-1f) }
    var currentVideoVolumePercent by remember { mutableFloatStateOf(-1f) }
    var brightBase by remember { mutableFloatStateOf(-1f) }
    var brightCurrent by remember { mutableFloatStateOf(-1f) }

    val pointerCount = remember { AtomicInteger(0) }
    var showDoubleTapRipple by remember { mutableIntStateOf(0) }
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    var showZoomMeter by remember { mutableStateOf(false) }

    val currentVideoScale by rememberUpdatedState(videoScale)

    var pinchStartDist by remember { mutableFloatStateOf(0f) }
    var pinchAnchorScale by remember { mutableFloatStateOf(1f) }
    var lastAppliedScale by remember { mutableFloatStateOf(1f) }
    var lastRawTargetScale by remember { mutableFloatStateOf(1f) }
    var pinchPointerIds by remember { mutableStateOf<Pair<PointerId, PointerId>?>(null) }

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
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarkList by remember { mutableStateOf(false) }
    var bookmarkLabel by remember { mutableStateOf("") }
    var bookmarkPosition by remember(currentPath) { mutableLongStateOf(0L) }

    val showSpeedButton = settingsPrefs.getBoolean("show_speed_button", true)
    val showLoopButton = settingsPrefs.getBoolean("show_loop_button", true)
    val showZoomButtons = settingsPrefs.getBoolean("show_zoom_buttons", true)
    val showExtraButtons = settingsPrefs.getBoolean("show_extra_buttons", true)

    var boostPrevSpeed by remember { mutableStateOf<Float?>(null) }
    val isBoosting = boostPrevSpeed != null
    // Release after a real 2x hold must not leak into tap handling below.
    var boostTapLatch by remember { mutableStateOf(false) }
    // Generation guard so only the latest double-tap feedback reset clears.
    var doubleTapFeedbackGen by remember { mutableIntStateOf(0) }

    val bookmarkList =
        remember(currentPath) {
          loadBookmarks(settingsPrefs, currentPath).toMutableStateList()
        }

    fun applyEngineSpeed(speed: Float) {
      viewModel.setPlaybackSpeed(speed)
      if (currentEngine == PlayerEngine.MPV) {
        try {
          MPVLib.setPropertyDouble("speed", speed.toDouble())
        } catch (e: Exception) {}
      } else {
        exoPlayer?.setPlaybackSpeed(speed)
      }
    }

    // REX parity: glide the engine speed in short steps instead of jumping,
    // so engaging/releasing the 2x hold never stutters audio (MPV) or jerks
    // playback (Exo). The ViewModel state flips instantly so the 2x
    // indicator and speed UI stay truthful while the engine catches up.
    fun rampEngineSpeed(from: Float, to: Float) {
      coroutineScope.launch {
        val steps = 5
        repeat(steps) { i ->
          val t = (i + 1).toFloat() / steps
          val v = from + (to - from) * t
          if (currentEngine == PlayerEngine.MPV) {
            try {
              MPVLib.setPropertyDouble("speed", v.toDouble())
            } catch (e: Exception) {}
          } else {
            try {
              exoPlayer?.setPlaybackSpeed(v)
            } catch (e: Exception) {}
          }
          if (i < steps - 1) delay(16)
        }
      }
    }

    fun startSpeedBoost() {
      if (isLocked || boostPrevSpeed != null || !isPlaying) return
      val prev = viewModel.playbackSpeed.value
      boostPrevSpeed = prev
      applyEngineSpeed(2f)
      rampEngineSpeed(prev, 2f)
    }

    fun stopSpeedBoost() {
      val prev = boostPrevSpeed ?: return
      boostPrevSpeed = null
      boostTapLatch = true
      applyEngineSpeed(prev)
      rampEngineSpeed(2f, prev)
    }

    LaunchedEffect(currentPath) {
      boostPrevSpeed = null
      boostTapLatch = false
      doubleTapFeedbackGen++
      showDoubleTapRipple = 0
      viewModel.hideGestureOverlay()
    }

    // A-B repeat: loop inside the same file via absolute seek — no reload,
    // so the restart has no black flash on either engine.
    LaunchedEffect(currentPosition, abPointA, abPointB, abLoopEnabled) {
        val a = abPointA
        val b = abPointB
        if (abLoopEnabled && a != null && b != null && b > a && currentPosition >= b) {
            onSeek(a)
        }
    }

    fun takeScreenshot() {
      showMoreMenu = false
      coroutineScope.launch {
        val mpvShot = if (currentEngine == PlayerEngine.MPV) File(context.cacheDir, "vidmax_shot.png") else null
        if (mpvShot != null) {
          runCatching { MPVLib.command(arrayOf("screenshot-to-file", mpvShot.absolutePath)) }
          delay(900)
          if (!mpvShot.exists()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, captureUnavailableText, Toast.LENGTH_SHORT).show()
            }
            return@launch
          }
        }
        val bitmap: Bitmap? =
            if (mpvShot == null) runCatching { viewModel.exoVideoTextureView?.bitmap }.getOrNull() else null
        val baseName = File(currentPath).nameWithoutExtension.ifEmpty { "video" }
        val ok =
            if (bitmap != null) {
              saveBitmapToGallery(context, bitmap, screenshotFileName(baseName, "jpg"))
            } else if (mpvShot != null) {
              val saved = saveImageFileToGallery(context, mpvShot, screenshotFileName(baseName, "png"), "image/png")
              runCatching { mpvShot.delete() }
              saved
            } else {
              false
            }
        withContext(Dispatchers.Main) {
          Toast.makeText(
                  context,
                  if (ok) frameSavedText else captureFailedText,
                  Toast.LENGTH_SHORT)
              .show()
        }
      }
    }

    val keepRepeatControlsVisible = !isLocked &&
        (showBookmarkDialog || showBookmarkList)
    LaunchedEffect(controlsVisible, isLocked, autoHideControls, controlsHideDelayMs, keepRepeatControlsVisible) {
        if (keepRepeatControlsVisible) {
            viewModel.setControlsVisible(true)
        } else if (isLocked && controlsVisible) {
            // REX model: the locked overlay always auto-hides on a short
            // fixed delay (2s), independent of the user's auto-hide pref.
            delay(2000)
            viewModel.setControlsVisible(false)
        } else if (controlsVisible && autoHideControls && controlsHideDelayMs > 0) {
            delay(controlsHideDelayMs.toLong())
            viewModel.setControlsVisible(false)
        }
    }

    LaunchedEffect(isPlaying, showControlsOnPlay) {
        if (isPlaying && showControlsOnPlay && !isLocked) {
            viewModel.setControlsVisible(true)
        }
    }

    // Permanent screen-on while the player is open: no toggle, the flag
    // is always added and never cleared by a user setting.
    LaunchedEffect(Unit) {
        val act = activity ?: return@LaunchedEffect
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

    // ── Volume boost (200%) helpers ────────────────────────────────────────
    // Lazily creates a LoudnessEnhancer on a guaranteed-valid audio session
    // (Media3 reports AUDIO_SESSION_ID_NOT_SET until audio output initialises,
    // which silently broke boost before).
    val ensureVideoEnhancer: () -> LoudnessEnhancer? = {
        if (loudnessEnhancer != null) {
            loudnessEnhancer
        } else {
            val player = exoPlayer as? ExoPlayer
            var sessionId = try { player?.audioSessionId ?: 0 } catch (e: Exception) { 0 }
            if (sessionId == 0 && player != null) {
                sessionId = audioManager.generateAudioSessionId()
                try { player.setAudioSessionId(sessionId) } catch (e: Exception) {}
            }
            try {
                LoudnessEnhancer(sessionId).also { loudnessEnhancer = it }
            } catch (e: Exception) { null }
        }
    }

    // percent: 0..200 — above 100 uses software gain (Exo) or mpv volume (MPV)
    val applyVideoVolume: (Int) -> Unit = { percent ->
        val clamped = percent.coerceIn(0, 200)
        if (currentEngine == PlayerEngine.MPV) {
            try {
                MPVLib.setPropertyInt("volume-max", 200)
                MPVLib.setPropertyInt("volume", clamped)
            } catch (e: Exception) {}
        } else {
            val maxSystemVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (clamped <= 100) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, (clamped / 100f * maxSystemVol).roundToInt(), 0)
                try { loudnessEnhancer?.enabled = false } catch (e: Exception) {}
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVol, 0)
                val enhancer = ensureVideoEnhancer()
                try {
                    enhancer?.setTargetGain(((clamped - 100) / 100f * 2500f).roundToInt())
                    enhancer?.enabled = true
                } catch (e: Exception) {}
            }
        }
    }

    val toggleAudioBoost = {
        localBoostEnabled = !localBoostEnabled
        if (localBoostEnabled) {
            // Boost ON — force 200%: hardware at max + software gain / mpv volume
            applyVideoVolume(200)
            currentVideoVolumePercent = 200f
            viewModel.setGestureIndicator(2, 2f)
        } else {
            if (volumeAccumulator > 100f) volumeAccumulator = 100f
            applyVideoVolume(100)
            currentVideoVolumePercent = 100f
            viewModel.setCurrentVolumePercent(1f)
            viewModel.setGestureIndicator(2, 1f)
        }
        // Auto-hide the indicator popup shortly after toggling from the button
        coroutineScope.launch {
            delay(1200)
            viewModel.hideGestureOverlay()
        }
        Unit
    }

    // Settings-sheet booster bridge: publishes the local booster state on
    // open and applies sheet toggles through the untouched toggle above.
    val playerVolumeBoost by viewModel.playerVolumeBoost.collectAsState()
    LaunchedEffect(Unit) {
        if (playerVolumeBoost != localBoostEnabled) {
            viewModel.setPlayerVolumeBoost(localBoostEnabled)
        }
    }
    LaunchedEffect(playerVolumeBoost) {
        val target = playerVolumeBoost ?: return@LaunchedEffect
        if (target != localBoostEnabled) toggleAudioBoost()
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
            settingsPrefs.edit().putString("player_engine", engine.name).apply()
            Toast.makeText(
                context,
                if (engine == PlayerEngine.MPV) engineMpvText else engineExoText,
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

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Text(stringResource(R.string.player_zoom_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.player_zoom_label), color = Color.White, fontSize = 14.sp)
                        Text(
                            String.format(Locale.US, "%.2fx", videoScale),
                            color = primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
)
        }

        // ---- Error overlay with retry ----
        errorMessage?.let { message ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            message,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.clearError() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(R.string.player_dismiss))
                            }
                            FilledButton(
                                onClick = {
                                    viewModel.clearError()
                                    onPlayPause()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.player_retry))
                            }
                        }
                    }
                }
            }
        }

        // ---- Floating A-B panel (bottom-right independent overlay) ----
        // Compact content-sized pill floating above the bottom controls. It
        // lives inside the controls-visibility gate, so tap-to-hide and
        // auto-hide hide it together with every other control, and it never
        // pushes, moves or resizes any existing control or the seekbar.
        if (showABPanel && !isLocked) {
            ABLoopPanel(
                pointA = abPointA,
                pointB = abPointB,
                loopEnabled = abLoopEnabled,
                onSetA = { viewModel.setABPointA(currentPosition) },
                onSetB = { viewModel.setABPointB(currentPosition) },
                onClear = {
                    viewModel.clearABRepeat()
                    viewModel.setShowABPanel(false)
                },
                onToggleLoop = {
                    if (abPointA != null && abPointB != null) {
                        viewModel.setABLoopEnabled(!abLoopEnabled)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_ab_set_both),
                            Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 180.dp))
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
            // The locked overlay also hides with the controls; a tap brings
            // it back since gestures stay disabled while locked.
            visible = controlsVisible || keepRepeatControlsVisible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isLocked) {
                    // Scrim behind the top/bottom controls so they stay
                    // readable on bright (white) scenes.
                    Box(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.24f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.55f),
                                    1f to Color.Black.copy(alpha = 0f)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.38f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0f),
                                    1f to Color.Black.copy(alpha = 0.6f)
                                )
                            )
                    )
                }
                if (isLocked) {
                    // ---- Locked state: single-tap Unlock button ----
                    // Visible exactly when the controls overlay is visible
                    // (REX: controlsShown && locked), so it auto-hides and
                    // tap-toggles together with everything else and can never
                    // strand the user. One tap unlocks instantly: the old
                    // slide-to-unlock could never complete because the video
                    // surface claims horizontal drags for seeking.
                    MpvCircleButton(
                        icon = Icons.Default.LockOpen,
                        contentDescription = "Unlock",
                        onClick = {
                            viewModel.toggleLock()
                            viewModel.setControlsVisible(true)
                        },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = leftSafePadding),
                        size = 48.dp
                    )
                } else {
                    // ==================== TOP BAR ====================
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = 24.dp, start = leftSafePadding, end = rightSafePadding)
                            .windowInsetsPadding(WindowInsets.statusBars),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }

                        // Portrait collapses these into the More menu so the
                        // title keeps full width; landscape shows everything.
                        if (isLandscape) {
                        // Engine badge + extra top-bar actions (hidden in minimalist mode)
                        if (!minimalist) {
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
                                    text = { Text(stringResource(R.string.player_menu_engine_exo), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { viewModel.setShowEngineMenu(false); toggleEngine(PlayerEngine.EXO) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_engine_mpv), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { viewModel.setShowEngineMenu(false); toggleEngine(PlayerEngine.MPV) }
                                )
                                if (currentEngine == PlayerEngine.MPV) {
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.player_menu_mpv_decoder), color = MaterialTheme.colorScheme.onSurface) },
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

                        // Settings
                        MpvCircleButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            onClick = { viewModel.setPanelMode(PanelMode.SETTINGS) },
                            size = 42.dp
                        )
                        } // !minimalist
                        } // isLandscape

                        // More menu (always visible, incl. portrait collapsed items)
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
                                    text = { Text(stringResource(R.string.player_speed_sync_title), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Outlined.Speed, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showMoreMenu = false; viewModel.setShowSyncSheet(true) }
                                )
                                // Portrait-collapsed top-bar actions (landscape
                                // keeps them as direct icon buttons).
                                if (!isLandscape) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (currentEngine == PlayerEngine.EXO) stringResource(R.string.player_menu_engine_to_mpv)
                                                else stringResource(R.string.player_menu_engine_to_exo),
                                                color = MaterialTheme.colorScheme.onSurface)
                                        },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showMoreMenu = false
                                            toggleEngine(
                                                if (currentEngine == PlayerEngine.EXO) PlayerEngine.MPV
                                                else PlayerEngine.EXO)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.player_menu_audio_tracks), color = MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.Audiotrack, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setSubtitleAudioTab(SubtitleAudioTab.AUDIO)
                                            viewModel.setPanelMode(PanelMode.SUB_AUDIO)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.player_menu_subtitles), color = MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.Subtitles, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.setSubtitleAudioTab(SubtitleAudioTab.SUBTITLE)
                                            viewModel.setPanelMode(PanelMode.SUB_AUDIO)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.player_menu_settings), color = MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { showMoreMenu = false; viewModel.setPanelMode(PanelMode.SETTINGS) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_add_bookmark), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMoreMenu = false
                                        bookmarkLabel = ""
                                        bookmarkPosition = currentPosition
                                        showBookmarkList = false
                                        showBookmarkDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(pluralStringResource(R.plurals.player_bookmarks_count, bookmarkList.size, bookmarkList.size), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Filled.Bookmarks, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMoreMenu = false
                                        showBookmarkDialog = false
                                        showBookmarkList = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_share), color = MaterialTheme.colorScheme.onSurface) },
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
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.player_share_video_title)))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_menu_details), color = MaterialTheme.colorScheme.onSurface) },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showMoreMenu = false; showDetailsDialog = true }
                                )
                            }
                        }
                    }

                    // ---- AB + Screenshot quick row, directly beneath the
                    // title area, left-aligned under back/title.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ABTextCircleButton(
                            text = "AB",
                            active = showABPanel,
                            onClick = { viewModel.setShowABPanel(!showABPanel) },
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MpvCircleButton(
                            icon = Icons.Filled.PhotoCamera,
                            contentDescription = "Screenshot",
                            onClick = { takeScreenshot() },
                            size = 42.dp
                        )
                    }
                    }

                    // ==================== CENTER TRANSPORT ====================
                    // Hidden while a drag gesture (seek/volume/brightness) is
                    // in progress so it doesn't overlap the gesture overlay.
                    AnimatedVisibility(
                        visible = !isDragging,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!minimalist) {
                            MpvCircleButton(
                                icon = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                onClick = onPrevious,
                                size = 48.dp
                            )
                        }

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

                        if (!minimalist) {
                            MpvCircleButton(
                                icon = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                onClick = onNext,
                                size = 48.dp
                            )
                        }
                    }
                    }

                    // ==================== BOTTOM CONTROLS ====================
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .imePadding()
                            .padding(bottom = 20.dp, start = leftSafePadding, end = rightSafePadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bookmark + A-B pins (REX passes loop points to its
                        // seekbar; VidMax reuses the existing pin markers).
                        val pinFractions =
                            if (duration > 0) {
                                bookmarkList.map { (it.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) } +
                                    listOfNotNull(
                                        abPointA?.let { (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                                        abPointB?.let { (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f) })
                            } else emptyList()
                        if (minimalist) {
                            // Minimalist: lock + rotate stay reachable, everything
                            // else hides; seekbar below keeps seeking accessible.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MpvCircleButton(
                                    icon = if (isLocked) Icons.Default.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = "Lock",
                                    onClick = { viewModel.toggleLock() },
                                    size = 42.dp,
                                    active = isLocked,
                                    hideBackground = false
                                )
                                MpvCircleButton(
                                    icon = Icons.Outlined.ScreenRotation,
                                    contentDescription = "Rotate",
                                    onClick = toggleScreenRotation,
                                    size = 42.dp,
                                    hideBackground = false
                                )
                            }
                            repeatBookmarkPanel()
                            SeekBarRow(
                                currentPosition = currentPosition,
                                duration = duration,
                                whiteSeekbar = false,
                                reduceMotion = false,
                                preventTap = preventSeekbarTap,
                                onSeek = onSeek,
                                onPositionChange = viewModel::setCurrentPosition,
                                pins = pinFractions,
                                onPinClick = { onSeek(it) }
                            )
                        } else if (bottomControlsBelowSeekbar) {
                            repeatBookmarkPanel()
                            SeekBarRow(
                                currentPosition = currentPosition,
                                duration = duration,
                                whiteSeekbar = false,
                                reduceMotion = false,
                                preventTap = preventSeekbarTap,
                                onSeek = onSeek,
                                onPositionChange = viewModel::setCurrentPosition,
                                pins = pinFractions,
                                onPinClick = { onSeek(it) }
                            )
                            BottomControlsScrollRow(
                                isLocked = isLocked,
                                bgPlayEnabled = bgPlayEnabled,
                                currentPlaybackSpeed = currentPlaybackSpeed,
                                loopMode = loopMode,
                                sleepTimerMinutes = sleepTimerMinutes,
                                hideBackground = false,
                                onToggleLock = { viewModel.toggleLock() },
                                onToggleBgPlay = { onBgPlayToggle(!bgPlayEnabled) },
                                onRotate = toggleScreenRotation,
                                onAspect = { viewModel.setShowAspectSheet(true) },
                                onSpeed = { viewModel.setShowSyncSheet(true) },
                                onRepeat = { viewModel.cycleLoopMode() },
                                onTimer = { showTimerDialog = true },
                                onKeepVisible = { viewModel.setControlsVisible(true) },
                                showSpeedButton = showSpeedButton,
                                showLoopButton = showLoopButton,
                                showZoomButtons = showZoomButtons,
                                showExtraButtons = showExtraButtons
                            )
                        } else {
                            BottomControlsScrollRow(
                                isLocked = isLocked,
                                bgPlayEnabled = bgPlayEnabled,
                                currentPlaybackSpeed = currentPlaybackSpeed,
                                loopMode = loopMode,
                                sleepTimerMinutes = sleepTimerMinutes,
                                hideBackground = false,
                                onToggleLock = { viewModel.toggleLock() },
                                onToggleBgPlay = { onBgPlayToggle(!bgPlayEnabled) },
                                onRotate = toggleScreenRotation,
                                onAspect = { viewModel.setShowAspectSheet(true) },
                                onSpeed = { viewModel.setShowSyncSheet(true) },
                                onRepeat = { viewModel.cycleLoopMode() },
                                onTimer = { showTimerDialog = true },
                                onKeepVisible = { viewModel.setControlsVisible(true) },
                                showSpeedButton = showSpeedButton,
                                showLoopButton = showLoopButton,
                                showZoomButtons = showZoomButtons,
                                showExtraButtons = showExtraButtons
                            )
                            repeatBookmarkPanel()
                            SeekBarRow(
                                currentPosition = currentPosition,
                                duration = duration,
                                whiteSeekbar = false,
                                reduceMotion = false,
                                preventTap = preventSeekbarTap,
                                onSeek = onSeek,
                                onPositionChange = viewModel::setCurrentPosition,
                                pins = pinFractions,
                                onPinClick = { onSeek(it) }
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
    currentPlaybackSpeed: Float,
    loopMode: LoopMode,
    sleepTimerMinutes: Int,
    hideBackground: Boolean,
    onToggleLock: () -> Unit,
    onToggleBgPlay: () -> Unit,
    onRotate: () -> Unit,
    onAspect: () -> Unit,
    onSpeed: () -> Unit,
    onRepeat: () -> Unit,
    onTimer: () -> Unit,
    onKeepVisible: () -> Unit,
    showSpeedButton: Boolean = true,
    showLoopButton: Boolean = true,
    showZoomButtons: Boolean = true,
    showExtraButtons: Boolean = true
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
        if (showExtraButtons) {
            MpvCircleButton(
                icon = if (bgPlayEnabled) Icons.Outlined.HeadsetOff else Icons.Outlined.Headset,
                contentDescription = "Background playback",
                onClick = onToggleBgPlay,
                size = 42.dp,
                active = bgPlayEnabled,
                hideBackground = hideBackground
            )
        }
        MpvCircleButton(
            icon = Icons.Outlined.ScreenRotation,
            contentDescription = "Rotate",
            onClick = onRotate,
            size = 42.dp,
            hideBackground = hideBackground
        )
        if (showZoomButtons) {
            MpvCircleButton(
                icon = Icons.Outlined.AspectRatio,
                contentDescription = "Aspect ratio",
                onClick = onAspect,
                size = 42.dp,
                hideBackground = hideBackground
            )
        }
        if (showSpeedButton) {
            MpvCircleButton(
                icon = Icons.Outlined.Speed,
                contentDescription = "Playback speed",
                onClick = onSpeed,
                size = 42.dp,
                active = currentPlaybackSpeed != 1f,
                hideBackground = hideBackground
            )
        }
        if (showLoopButton) {
            MpvCircleButton(
                icon = if (loopMode == LoopMode.ONE) Icons.Default.RepeatOne else Icons.Outlined.Repeat,
                contentDescription = "Repeat",
                onClick = onRepeat,
                size = 42.dp,
                active = loopMode != LoopMode.NONE,
                hideBackground = hideBackground
            )
        }
        if (showExtraButtons) {
            MpvCircleButton(
                icon = Icons.Outlined.Timer,
                contentDescription = "Sleep timer",
                onClick = onTimer,
                size = 42.dp,
                active = sleepTimerMinutes > 0,
                hideBackground = hideBackground
            )
        }
    }
}

// ============================================================
// Seek bar row
// ============================================================
/**
 * Circular "AB" button for the bottom control row: the same translucent
 * circular styling as [MpvCircleButton] (shape, surface, border, press
 * scale), rendering a clear text label instead of an icon.
 */
@Composable
private fun ABTextCircleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    hideBackground: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "abTextButtonScale"
    )

    Surface(
        onClick = if (enabled) onClick else null,
        modifier = modifier.size(size).scale(scale),
        shape = CircleShape,
        color = when {
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            hideBackground -> Color.Transparent
            !enabled -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        contentColor = when {
            active -> MaterialTheme.colorScheme.onPrimary
            !enabled -> Color.White.copy(alpha = 0.38f)
            else -> Color.White
        },
        border = when {
            hideBackground && !active -> null
            !enabled -> BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        },
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    !enabled -> Color.White.copy(alpha = 0.38f)
                    else -> Color.White
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold)
        }
    }
}

/**
 * REX-style compact A-B panel: A → X → B → loop toggle in one rounded pill.
 *
 * Adapted from REX Player's floating AB loop panel
 * (ui/player/controls/PlayerControls.kt): the same 40dp circles, 2dp
 * spacing, tertiaryContainer point highlight and bordered translucent
 * container. Only the 4th circle differs — REX opens clip-cut there, while
 * VidMax shows a loop enable/disable toggle (primaryContainer when the
 * loop is armed), since VidMax has no clip export.
 */
@Composable
private fun ABLoopPanel(
    pointA: Long?,
    pointB: Long?,
    loopEnabled: Boolean,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = 40.dp
    val canLoop = pointA != null && pointB != null
    val looping = canLoop && loopEnabled
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.height(buttonSize)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            ABLoopCircle(
                letter = "A",
                timestamp = pointA?.let(::formatTimeHelper),
                highlighted = pointA != null,
                onClick = onSetA)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .size(buttonSize - 4.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear A-B repeat",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp))
                }
            }
            ABLoopCircle(
                letter = "B",
                timestamp = pointB?.let(::formatTimeHelper),
                highlighted = pointB != null,
                onClick = onSetB)
            Surface(
                shape = CircleShape,
                color = if (looping) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (looping) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .size(buttonSize - 4.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleLoop)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Repeat,
                        contentDescription = "Enable or disable A-B loop",
                        tint = if (looping) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Single A/B point circle for [ABLoopPanel]: the letter label is always
 * visible, with the timestamp appended beside it while the point is set.
 * Tapping toggles the point.
 */
@Composable
private fun ABLoopCircle(
    letter: String,
    timestamp: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    buttonSize: Dp = 40.dp
) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
        modifier = Modifier
            .height(buttonSize - 4.dp)
            .widthIn(min = buttonSize - 4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = if (highlighted) 8.dp else 0.dp)) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurface)
                if (timestamp != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SeekBarRow(
    currentPosition: Long,
    duration: Long,
    whiteSeekbar: Boolean,
    reduceMotion: Boolean,
    preventTap: Boolean,
    onSeek: (Long) -> Unit,
    onPositionChange: (Long) -> Unit,
    pins: List<Float> = emptyList(),
    onPinClick: ((Long) -> Unit)? = null
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
                .semantics {
                    progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                        0f, 1f, displayProgress, false
                    )
                    stateDescription = androidx.compose.ui.semantics.StateDescription(
                        contentDescription = "Seek position, ${formatTimeHelper(displayPosition)} of ${formatTimeHelper(safeDuration)}"
                    )
                }
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
            pins.forEach { frac ->
                val f = frac.coerceIn(0f, 1f)
                val pinOffset = (maxWidth * f - 6.dp).coerceIn(0.dp, maxWidth - 12.dp)
                Box(
                    modifier = Modifier.align(Alignment.CenterStart)
                        .offset(x = pinOffset)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD54F))
                        .let { m ->
                            if (onPinClick != null) {
                                m.clickable { onPinClick((f * safeDuration).toLong()) }
                            } else m
                        }
                )
            }
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
    hideBackground: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mpvButtonScale"
    )

    Surface(
        onClick = if (enabled) onClick else null,
        modifier = modifier.size(size).scale(scale),
        shape = CircleShape,
        color = when {
            active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            hideBackground -> Color.Transparent
            !enabled -> Color.White.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        contentColor = when {
            active -> MaterialTheme.colorScheme.onPrimary
            !enabled -> Color.White.copy(alpha = 0.38f)
            else -> tint
        },
        border = when {
            hideBackground && !active -> null
            !enabled -> BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        },
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    !enabled -> Color.White.copy(alpha = 0.38f)
                    else -> tint
                },
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
            stringResource(R.string.player_slide_to_unlock),
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
