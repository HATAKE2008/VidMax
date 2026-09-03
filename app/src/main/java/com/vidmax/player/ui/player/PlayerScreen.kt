@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vidmax.player.ui.player

import android.content.Context
import android.util.Log
import android.graphics.SurfaceTexture
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vidmax.player.R
import kotlin.math.max
import com.vidmax.player.viewmodel.AspectRatioMode
import com.vidmax.player.viewmodel.PanelMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib

@Composable
fun PlayerScreen(
    exoPlayer: Player?,
    viewModel: PlayerViewModel,
    currentPath: String,
    audioBoostEnabled: Boolean,
    onMpvLayoutReady: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onPickSubtitle: () -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val prefs = context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)

  var bgPlayEnabled by remember { mutableStateOf(prefs.getBoolean("bg_play_enabled", false)) }
  val minimalistPlayer = remember { prefs.getBoolean("minimalist_player", false) }
  val aspectRatio by viewModel.aspectRatio.collectAsState()
  val currentEngine by viewModel.currentEngine.collectAsState()
  val panelMode by viewModel.panelMode.collectAsState()
  val subAudioTab by viewModel.subtitleAudioTab.collectAsState()
  val panelOpen = panelMode != PanelMode.NONE
  val isLandscape =
      configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

  var videoScale by remember { mutableFloatStateOf(1f) }
  var videoOffsetX by remember { mutableFloatStateOf(0f) }
  var videoOffsetY by remember { mutableFloatStateOf(0f) }
  var currentPlaybackSpeed by remember { mutableFloatStateOf(prefs.getFloat("player_speed", 1f).coerceIn(0.25f, 3f)) }

  // Live zoom layer: written every pinch frame by PlayerControls (no
  // recomposition), kept in sync with the committed scale otherwise.
  val liveZoomScale = remember { mutableFloatStateOf(1f) }
  LaunchedEffect(videoScale) { liveZoomScale.floatValue = videoScale }

  // Real size of the video surface view (px), kept in sync via onSizeChanged on
  // both engine AndroidViews. Used to anchor pinch zoom and to clamp the pan so
  // no one-sided black space can ever appear.
  var viewSizePx by remember { mutableStateOf(IntSize.Zero) }

  // Aspect ratio (width / height) of the currently loaded video. 0 means unknown
  // (not loaded yet), in which case clamping falls back to the view aspect.
  var videoAspectRatio by remember { mutableFloatStateOf(0f) }

  // Max pan offset that keeps the scaled video covering the viewport. Positive
  // only when the scaled content is larger than the view in that dimension;
  // otherwise the offset is forced to 0 (perfectly centered).
  fun clampVideoOffset() {
    val vw = viewSizePx.width.toFloat()
    val vh = viewSizePx.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    val (contentW, contentH) = contentSizePx(vw, vh, videoAspectRatio, aspectRatio)
    val maxX = max(0f, (contentW * videoScale - vw) / 2f)
    val maxY = max(0f, (contentH * videoScale - vh) / 2f)
    videoOffsetX = if (maxX <= 0f) 0f else videoOffsetX.coerceIn(-maxX, maxX)
    videoOffsetY = if (maxY <= 0f) 0f else videoOffsetY.coerceIn(-maxY, maxY)
  }

  // Keep the known video aspect ratio up to date and re-clamp whenever the video,
  // the aspect mode or the engine changes.
  LaunchedEffect(currentPath, aspectRatio, currentEngine) {
    videoAspectRatio =
        when (currentEngine) {
          PlayerEngine.EXO -> {
            val vs = exoPlayer?.videoSize
            if (vs != null && vs.width > 0 && vs.height > 0) {
              vs.width.toFloat() * vs.pixelWidthHeightRatio / vs.height.toFloat()
            } else {
              0f
            }
          }
          PlayerEngine.MPV -> {
            try {
              MPVLib.getPropertyDouble("video-params/aspect")?.toFloat() ?: 0f
            } catch (e: Exception) {
              0f
            }
          }
        }
    clampVideoOffset()
  }

  // Reset zoom / pan whenever a new video is loaded so the video starts
  // cleanly fitted and centered instead of carrying over the previous scale.
  LaunchedEffect(currentPath) {
    videoScale = 1f
    videoOffsetX = 0f
    videoOffsetY = 0f
  }

  // While a right-half panel is open (landscape side-panel layout) the video is
  // shown at scale 1, centered and clean, so the user gets a live unfiltered
  // preview. In portrait the panel is a true overlay, so the video keeps its
  // current zoom/pan untouched.
  LaunchedEffect(panelOpen, isLandscape) {
    if (panelOpen && isLandscape) {
      videoScale = 1f
      videoOffsetX = 0f
      videoOffsetY = 0f
    }
  }

  LaunchedEffect(bgPlayEnabled, currentEngine) {
    if (bgPlayEnabled) {
      if (currentEngine == PlayerEngine.EXO) {
        (exoPlayer as? androidx.media3.exoplayer.ExoPlayer)?.clearVideoSurface()
      } else if (currentEngine == PlayerEngine.MPV) {
        try {
          MPVLib.setPropertyString("vid", "no")
        } catch (e: Exception) {}
      }
    } else {
      if (currentEngine == PlayerEngine.MPV) {
        try {
          MPVLib.setPropertyString("vid", "auto")
        } catch (e: Exception) {}
      }
    }
  }

  LaunchedEffect(aspectRatio, currentEngine) {
    if (currentEngine == PlayerEngine.MPV) {
      MpvScaling.applyAspectMode(aspectRatio)
    }
    if (aspectRatio == AspectRatioMode.FIT) {
      videoScale = 1f
      videoOffsetX = 0f
      videoOffsetY = 0f
      liveZoomScale.floatValue = 1f
    } else {
      clampVideoOffset()
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    if (!bgPlayEnabled) {
      if (currentEngine == PlayerEngine.EXO) {
        AndroidView(
            factory = { ctx: Context ->
              // Inflated from view_player_texture.xml which sets surface_type =
              // "texture_view". A TextureView is drawn in the normal view hierarchy so
              // Compose's graphicsLayer zoom/pan (pinch zoom, zoom sheet) is applied to the
              // rendered video - with the default SurfaceView the zoom never fills the
              // screen. In media3 1.4.x the surface type is only settable via XML.
              (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null) as PlayerView)
                  .apply {
                    player = exoPlayer
                    viewModel.exoSubtitleView = subtitleView
                    viewModel.exoVideoTextureView = videoSurfaceView as? TextureView
                    // Fit the video inside the view, preserving its aspect ratio
                    // and centering it (professional player default).
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                  }
            },
            update = { view: PlayerView ->
              view.player = exoPlayer
              view.resizeMode =
                  when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                  }
            },
            modifier =
                (if (panelOpen && isLandscape) Modifier.fillMaxHeight().fillMaxWidth(0.5f) else Modifier.fillMaxSize())
                    .onSizeChanged { viewSizePx = it; clampVideoOffset() }
                    .graphicsLayer {
                        val s = if (panelOpen && isLandscape) 1f else liveZoomScale.floatValue
                        scaleX = s
                        scaleY = s
                        translationX = if (panelOpen && isLandscape) 0f else videoOffsetX
                        translationY = if (panelOpen && isLandscape) 0f else videoOffsetY
                    })
      } else {
        AndroidView(
            factory = { ctx: Context ->
              FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                // TextureView instead of SurfaceView so Compose's graphicsLayer
                // zoom/pan (pinch zoom, zoom sheet) is applied to the rendered
                // video. A SurfaceView lives in its own window, so graphicsLayer
                // transforms are ignored for it and zoom never fills the screen.
                val textureView =
                    TextureView(ctx).apply {
                      layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                addView(textureView)

                textureView.surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                      override fun onSurfaceTextureAvailable(
                          surface: SurfaceTexture,
                          w: Int,
                          h: Int
                      ) {
                        MPVLib.attachSurface(Surface(surface))
                        try {
                          MPVLib.setPropertyString("vid", "auto")
                          // Push the real surface geometry and aspect mode as early as
                          // possible so the very first frame is fitted and centered instead
                          // of being rendered at a default size (which leaves black space).
                          MpvScaling.applySurfaceSize(w, h)
                          MpvScaling.applyAspectMode(viewModel.aspectRatio.value)
                        } catch (e: Exception) {}
                        onMpvLayoutReady()
                      }

                      override fun onSurfaceTextureSizeChanged(
                          surface: SurfaceTexture,
                          w: Int,
                          h: Int
                      ) {
                        // After the surface is resized (e.g. screen rotation) the previous
                        // buffer gets stretched and uninitialized regions show artifacts;
                        // applyAspectMode forces a repaint of the current frame while
                        // paused, so the video is re-fitted to the new geometry.
                        MpvScaling.applySurfaceSize(w, h)
                        MpvScaling.applyAspectMode(viewModel.aspectRatio.value)
                      }

                      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        MPVLib.detachSurface()
                        return true
                      }

                      override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
              }
            },
            update = { frameLayout -> frameLayout.requestLayout() },
            modifier =
                (if (panelOpen && isLandscape) Modifier.fillMaxHeight().fillMaxWidth(0.5f) else Modifier.fillMaxSize())
                    .onSizeChanged { viewSizePx = it; clampVideoOffset() }
                    .graphicsLayer {
                        val s = if (panelOpen && isLandscape) 1f else liveZoomScale.floatValue
                        scaleX = s
                        scaleY = s
                        translationX = if (panelOpen && isLandscape) 0f else videoOffsetX
                        translationY = if (panelOpen && isLandscape) 0f else videoOffsetY
                    })
      }
    } else {
      Column(
          modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier.size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center) {
                  Icon(
                      painter = painterResource(id = R.drawable.ic_headphones),
                      contentDescription = "Audio Mode",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(60.dp))
                }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Audio Mode Active",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Video rendering is disabled to save battery", color = Color.Gray, fontSize = 14.sp)
          }
    }

    if (panelOpen) {
      when (panelMode) {
        PanelMode.SUB_AUDIO ->
          SubtitleAudioPanel(
              initialTab = subAudioTab,
              currentEngine = currentEngine,
              viewModel = viewModel,
              exoPlayer = exoPlayer,
              onClose = { viewModel.setPanelMode(PanelMode.NONE) },
              onPickSubtitle = onPickSubtitle,
              onOpenSettings = { viewModel.setPanelMode(PanelMode.SETTINGS) },
              onOpenSync = {
                viewModel.setPanelMode(PanelMode.NONE)
                viewModel.setShowSyncSheet(true)
              })
        PanelMode.SETTINGS ->
          PlayerSettingsSheet(
              viewModel = viewModel,
              onDismiss = { viewModel.setPanelMode(PanelMode.NONE) })
        PanelMode.NONE -> {}
      }
    } else {
      PlayerControls(
        viewModel = viewModel,
        currentPath = currentPath,
        minimalist = minimalistPlayer,
        audioBoostEnabled = audioBoostEnabled,
        currentPlaybackSpeed = currentPlaybackSpeed,
        onSpeedChange = { speed ->
          currentPlaybackSpeed = speed
          prefs.edit().putFloat("player_speed", speed).apply()
          viewModel.setPlaybackSpeed(speed)
          if (currentEngine == PlayerEngine.MPV) {
            try {
              MPVLib.setPropertyDouble("speed", speed.toDouble())
            } catch (e: Exception) {}
          } else {
            exoPlayer?.setPlaybackSpeed(speed)
          }
        },
        videoScale = videoScale,
        liveZoomScale = liveZoomScale,
        onVideoScaleChange = { zoom, pan, anchor ->
          var vw = viewSizePx.width.toFloat()
          var vh = viewSizePx.height.toFloat()
          if (vw <= 0f || vh <= 0f) {
            // Safety: view not measured yet; fall back to the window size so the
            // first pinch is still anchored/clamped correctly.
            viewSizePx = with(density) {
              IntSize(
                  configuration.screenWidthDp.dp.toPx().toInt(),
                  configuration.screenHeightDp.dp.toPx().toInt())
            }
            vw = viewSizePx.width.toFloat()
            vh = viewSizePx.height.toFloat()
          }
          if (vw > 0f && vh > 0f) {
            val newScale = (videoScale * zoom).coerceIn(1f, 4f)
            val k = newScale / videoScale
            if (k != 1f) {
              val ax = (anchor?.x ?: vw / 2f) - vw / 2f
              val ay = (anchor?.y ?: vh / 2f) - vh / 2f
              videoOffsetX = ax - (ax - videoOffsetX) * k
              videoOffsetY = ay - (ay - videoOffsetY) * k
            }
            if (pan.getDistance() > 1f) {
              videoOffsetX += pan.x
              videoOffsetY += pan.y
            }
            videoScale = newScale
            clampVideoOffset()
            Log.d("VidMaxGesture", "APPLY scale=$newScale")
          }
        },
        exoPlayer = exoPlayer,
        bgPlayEnabled = bgPlayEnabled,
        onBgPlayToggle = { isEnabled ->
          bgPlayEnabled = isEnabled
          prefs.edit().putBoolean("bg_play_enabled", isEnabled).apply()
        },
        onPlayPause = {
          if (currentEngine == PlayerEngine.MPV) {
            try {
              val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
              MPVLib.setPropertyBoolean("pause", !isPaused)
              // 🔥 FIX: ইনস্ট্যান্ট UI আপডেট করার জন্য জোর করে ভিউমডেলের স্টেট চেঞ্জ করা হলো
              viewModel.setPlaying(isPaused) 
            } catch (e: Exception) {}
          } else {
            if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play()
          }
        },
        onSeek = { position: Long ->
          if (currentEngine == PlayerEngine.MPV) {
            try {
              // 🔥 FIX: ম্যানুয়ালি প্রপার্টি চেঞ্জ করার বদলে ডিরেক্ট MPV command দিয়ে absolute seek
              MPVLib.command(arrayOf("seek", (position / 1000.0).toString(), "absolute"))
            } catch (e: Exception) {}
          } else exoPlayer?.seekTo(position)
        },
        onPrevious = onPrevious,
        onNext = onNext,
        onSeekForward = onSeekForward,
        onSeekBackward = onSeekBackward,
        onBack = onBack,
        modifier = Modifier.fillMaxSize())
    }
  }
}

// Computes the on-screen size (px) of the video content inside a view of
// viewW x viewH for the given aspect-ratio mode, preserving the video's real
// aspect ratio (never stretching/distorting):
//  - FIT: content fits inside the view, centered (letterboxed), no cropping.
//  - FILL: content covers the view (cropped symmetrically), aspect preserved.
//  - STRETCH: content fills the whole view (aspect not preserved).
private fun contentSizePx(
    viewW: Float,
    viewH: Float,
    videoAR: Float,
    mode: AspectRatioMode
): Pair<Float, Float> {
  val ar = if (videoAR > 0f) videoAR else viewW / viewH
  return when (mode) {
    AspectRatioMode.STRETCH -> viewW to viewH
    AspectRatioMode.FILL ->
        if (viewW / viewH > ar) viewW to viewW / ar else viewH * ar to viewH
    AspectRatioMode.FIT ->
        if (viewW / viewH > ar) viewH * ar to viewH else viewW to viewW / ar
  }
}

// Shared helper for keeping mpv's video fitted and centered with no unnecessary
// black space. Used by both PlayerScreen (surface callbacks) and PlayerActivity
// (mpv FILE_LOADED / VIDEO_RECONFIG events), so the geometry is always pushed at
// the right moment no matter what triggered the change.
object MpvScaling {
  private var lastWidth = 0
  private var lastHeight = 0

  // Tells mpv the pixel dimensions of the video surface so it fits/centers the
  // video correctly. Setting this late or wrong is what causes unnecessary black
  // space around the video.
  fun applySurfaceSize(w: Int, h: Int) {
    if (w <= 0 || h <= 0) return
    lastWidth = w
    lastHeight = h
    try {
      MPVLib.setPropertyString("android-surface-size", "${w}x${h}")
    } catch (e: Exception) {}
  }

  // Applies the current aspect-ratio mode to mpv. FIT keeps the original aspect
  // ratio and letterboxes (video centered, as large as possible). FILL keeps the
  // aspect ratio but zooms/crops to cover the surface. STRETCH ignores the aspect
  // ratio and fills the surface (distorts).
  fun applyAspectMode(mode: AspectRatioMode) {
    try {
      when (mode) {
        AspectRatioMode.FIT -> {
          MPVLib.setPropertyString("keepaspect", "yes")
          MPVLib.setPropertyDouble("panscan", 0.0)
          MPVLib.setPropertyString("video-aspect-override", "no")
        }
        AspectRatioMode.FILL -> {
          MPVLib.setPropertyString("keepaspect", "yes")
          MPVLib.setPropertyDouble("panscan", 1.0)
          MPVLib.setPropertyString("video-aspect-override", "no")
        }
        AspectRatioMode.STRETCH -> {
          MPVLib.setPropertyString("keepaspect", "no")
          MPVLib.setPropertyDouble("panscan", 0.0)
        }
      }
      nudgeRepaintIfPaused()
    } catch (e: Exception) {}
  }

  // Re-pushes the last known surface geometry plus the aspect mode. Called when
  // mpv reconfigures its video output (new file loaded, or rotation/aspect
  // metadata changed) so the new video is fitted/centered right away.
  fun reapply(mode: AspectRatioMode) {
    if (lastWidth > 0 && lastHeight > 0) {
      try {
        MPVLib.setPropertyString("android-surface-size", "${lastWidth}x${lastHeight}")
      } catch (e: Exception) {}
    }
    applyAspectMode(mode)
  }

  // While paused mpv's render loop is idle, so the current frame won't be
  // redrawn at the new size/geometry. A zero-distance exact seek forces a
  // repaint of the current frame without moving the playback position.
  private fun nudgeRepaintIfPaused() {
    try {
      val paused = MPVLib.getPropertyBoolean("pause") ?: false
      if (paused) {
        MPVLib.command(arrayOf("seek", "0", "relative+exact"))
      }
    } catch (e: Exception) {}
  }
}
