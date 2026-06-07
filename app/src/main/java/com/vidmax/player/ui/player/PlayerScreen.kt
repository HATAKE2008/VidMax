package com.vidmax.player.ui.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vidmax.player.R
import com.vidmax.player.viewmodel.AspectRatioMode
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
  val prefs = context.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)

  var bgPlayEnabled by remember { mutableStateOf(prefs.getBoolean("bg_play_enabled", false)) }
  val aspectRatio by viewModel.aspectRatio.collectAsState()
  val currentEngine by viewModel.currentEngine.collectAsState()

  var videoScale by remember { mutableFloatStateOf(1f) }
  var videoOffsetX by remember { mutableFloatStateOf(0f) }
  var videoOffsetY by remember { mutableFloatStateOf(0f) }
  var currentPlaybackSpeed by remember { mutableFloatStateOf(1f) }

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
      try {
        when (aspectRatio) {
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
      } catch (e: Exception) {}
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    if (!bgPlayEnabled) {
      if (currentEngine == PlayerEngine.EXO) {
        AndroidView(
            factory = { ctx: Context ->
              PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
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
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = videoScale,
                        scaleY = videoScale,
                        translationX = videoOffsetX,
                        translationY = videoOffsetY))
      } else {
        AndroidView(
            factory = { ctx: Context ->
              FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                val surfaceView =
                    SurfaceView(ctx).apply {
                      layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                addView(surfaceView)

                surfaceView.holder.addCallback(
                    object : SurfaceHolder.Callback {
                      override fun surfaceCreated(holder: SurfaceHolder) {
                        MPVLib.attachSurface(holder.surface)
                        try {
                          MPVLib.setPropertyString("vid", "auto")
                        } catch (e: Exception) {}
                        onMpvLayoutReady()
                      }

                      override fun surfaceChanged(
                          holder: SurfaceHolder,
                          format: Int,
                          w: Int,
                          h: Int
                      ) {
                        try {
                          MPVLib.setPropertyString("android-surface-size", "${w}x${h}")
                          val currentMode = viewModel.aspectRatio.value
                          if (currentMode == AspectRatioMode.STRETCH) {
                            MPVLib.setPropertyString("keepaspect", "no")
                            MPVLib.setPropertyDouble("panscan", 0.0)
                          } else {
                            MPVLib.setPropertyString("keepaspect", "yes")
                            MPVLib.setPropertyDouble(
                                "panscan", if (currentMode == AspectRatioMode.FILL) 1.0 else 0.0)
                            MPVLib.setPropertyString("video-aspect-override", "no")
                          }
                        } catch (e: Exception) {
                          e.printStackTrace()
                        }
                      }

                      override fun surfaceDestroyed(holder: SurfaceHolder) {
                        MPVLib.detachSurface()
                      }
                    })
              }
            },
            update = { frameLayout -> frameLayout.requestLayout() },
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = videoScale,
                        scaleY = videoScale,
                        translationX = videoOffsetX,
                        translationY = videoOffsetY))
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

    PlayerControls(
        viewModel = viewModel,
        currentPath = currentPath,
        audioBoostEnabled = audioBoostEnabled,
        currentPlaybackSpeed = currentPlaybackSpeed,
        onSpeedChange = { speed ->
          currentPlaybackSpeed = speed
          if (currentEngine == PlayerEngine.MPV) {
            try {
              MPVLib.setPropertyDouble("speed", speed.toDouble())
            } catch (e: Exception) {}
          } else {
            exoPlayer?.setPlaybackSpeed(speed)
          }
        },
        videoScale = videoScale,
        onVideoScaleChange = { zoom, pan ->
          videoScale = (videoScale * zoom).coerceIn(1f, 4f)
          if (videoScale > 1f) {
            videoOffsetX += pan.x * videoScale
            videoOffsetY += pan.y * videoScale
          } else {
            videoOffsetX = 0f
            videoOffsetY = 0f
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
        onPickSubtitle = onPickSubtitle,
        modifier = Modifier.fillMaxSize())
  }
}
