package com.vidmax.player.ui.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.ui.theme.VidMaxTheme
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), MPVLib.EventObserver {

    private val playerViewModel: PlayerViewModel by viewModels()
    private var exoPlayer: ExoPlayer? = null

    private var pendingPlayIndex: Int = -1
    private var videoPaths: List<String> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private var isResumePlayback: Boolean = true
    private var audioBoostEnabled: Boolean = false
    private var currentPlayingPath: String = ""

    private var subtitlePfd: ParcelFileDescriptor? = null

    private val subtitlePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    subtitlePfd?.close()
                    subtitlePfd = contentResolver.openFileDescriptor(uri, "r")
                    val fd = subtitlePfd?.fd
                    if (fd != null) {
                        val fdUri = "fd://$fd"
                        if (playerViewModel.currentEngine.value == PlayerEngine.MPV) {
                            MPVLib.command(arrayOf("sub-add", fdUri))
                            Toast.makeText(this, "Subtitle Added! ✅", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Custom Subtitles require MPV Engine.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error reading subtitle file", Toast.LENGTH_SHORT).show()
                }
            }
        }

    companion object {
        private const val EXTRA_PATHS = "extra_paths"
        private const val EXTRA_INDEX = "extra_index"

        fun start(context: Context, paths: List<String>, startIndex: Int = 0) {
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
            intent.putExtra(EXTRA_INDEX, startIndex)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
        isResumePlayback = prefs.getBoolean("resume_playback", true)
        audioBoostEnabled = prefs.getBoolean("audio_boost", false)
        val isAutoRotate = prefs.getBoolean("auto_rotate", true)

        val savedThemeName = prefs.getString("app_theme", AppTheme.DEFAULT_DARK.name) ?: AppTheme.DEFAULT_DARK.name
        val currentTheme = AppTheme.valueOf(savedThemeName)

        val savedEngineName = prefs.getString("player_engine", PlayerEngine.EXO.name) ?: PlayerEngine.EXO.name
        playerViewModel.setPlayerEngine(PlayerEngine.valueOf(savedEngineName))

        requestedOrientation =
            if (isAutoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val pathsFromIntent = intent.getStringArrayListExtra(EXTRA_PATHS)
        if (pathsFromIntent != null) {
            videoPaths = pathsFromIntent
        }

        val startIndex: Int = intent.getIntExtra(EXTRA_INDEX, 0)
        if (videoPaths.isEmpty()) {
            finish()
            return
        }

        playerViewModel.setTotalVideos(videoPaths.size)

        // MPV Init
        MPVLib.create(this)
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.init()

        MPVLib.addObserver(this)
        MPVLib.observeProperty("time-pos", 5)
        MPVLib.observeProperty("duration", 5)
        MPVLib.observeProperty("pause", 3)

        exoPlayer = ExoPlayer.Builder(this).build()
        setupExoListeners()

        pendingPlayIndex = startIndex

        setContent {
            val currentIndex by playerViewModel.currentVideoIndex.collectAsState()
            val currentPath =
                if (videoPaths.isNotEmpty() && currentIndex < videoPaths.size)
                    videoPaths[currentIndex]
                else ""

            VidMaxTheme(appTheme = currentTheme) {
                PlayerScreen(
                    exoPlayer = exoPlayer,
                    viewModel = playerViewModel,
                    currentPath = currentPath,
                    audioBoostEnabled = audioBoostEnabled,
                    onMpvLayoutReady = {
                        if (pendingPlayIndex != -1) {
                            playVideo(pendingPlayIndex)
                            pendingPlayIndex = -1
                        }
                    },
                    onBack = { finish() },
                    onNext = { playNext() },
                    onPrevious = { playPrevious() },
                    onSeekForward = { seekForward() },
                    onSeekBackward = { seekBackward() },
                    onPickSubtitle = { subtitlePickerLauncher.launch("*/*") }
                )
            }

            LaunchedEffect(Unit) {
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO && pendingPlayIndex != -1) {
                    playVideo(pendingPlayIndex)
                    pendingPlayIndex = -1
                }
            }
        }
    }

    private fun playVideo(index: Int) {
        if (index < 0 || index >= videoPaths.size) return
        saveCurrentPlaybackPosition()

        playerViewModel.setCurrentVideoIndex(index)
        val path = videoPaths[index]
        currentPlayingPath = path

        val name = path.substringAfterLast("/").substringBeforeLast(".")
        playerViewModel.setVideoTitle(name)
        prefs.edit().putString("recent_video_path", path).putString("recent_video_title", name).apply()

        val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
        val startPos = if (isResumePlayback) prefs.getLong("resume_pos_$path", 0L) else 0L

        if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
            MPVLib.command(arrayOf("stop"))
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            if (startPos > 3000L) {
                exoPlayer?.seekTo(startPos)
            }
            exoPlayer?.play()
        } else {
            exoPlayer?.stop()

            if (startPos > 3000L) {
                val startSec = startPos / 1000.0
                MPVLib.setOptionString("start", startSec.toString())
            } else {
                MPVLib.setOptionString("start", "none")
            }

            MPVLib.command(arrayOf("loadfile", uri.toString()))
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    private fun saveCurrentPlaybackPosition() {
        if (isResumePlayback && currentPlayingPath.isNotEmpty()) {
            val currentPos =
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                    exoPlayer?.currentPosition ?: 0L
                } else {
                    try {
                        ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
                    } catch (e: Exception) {
                        0L
                    }
                }
            if (currentPos > 3000L) {
                prefs.edit().putLong("resume_pos_$currentPlayingPath", currentPos).apply()
            }
        }
    }

    private fun playNext() {
        val currentIndex = playerViewModel.currentVideoIndex.value
        if (currentIndex < videoPaths.size - 1) playVideo(currentIndex + 1)
    }

    private fun playPrevious() {
        val currentIndex = playerViewModel.currentVideoIndex.value
        if (currentIndex > 0) playVideo(currentIndex - 1)
    }

    private fun seekForward() {
        if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
            val newPos = (exoPlayer?.currentPosition ?: 0L) + 10_000L
            exoPlayer?.seekTo(newPos)
        } else {
            try {
                val cur = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                MPVLib.setPropertyDouble("time-pos", cur + 10.0)
            } catch (e: Exception) {}
        }
    }

    private fun seekBackward() {
        if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
            val newPosition = (exoPlayer?.currentPosition ?: 0L) - 10_000L
            exoPlayer?.seekTo(if (newPosition < 0) 0L else newPosition)
        } else {
            try {
                val cur = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                val newPos = if (cur - 10.0 < 0) 0.0 else cur - 10.0
                MPVLib.setPropertyDouble("time-pos", newPos)
            } catch (e: Exception) {}
        }
    }

    override fun eventProperty(property: String) {}

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause" && playerViewModel.currentEngine.value == PlayerEngine.MPV) {
            playerViewModel.setPlaying(!value)
        }
    }

    override fun eventProperty(property: String, value: Long) {}

    override fun eventProperty(property: String, value: Double) {
        if (playerViewModel.currentEngine.value == PlayerEngine.MPV) {
            when (property) {
                "time-pos" -> playerViewModel.setCurrentPosition((value * 1000).toLong())
                "duration" -> playerViewModel.setDuration((value * 1000).toLong())
            }
        }
    }

    override fun eventProperty(property: String, value: String) {}

    override fun event(eventId: Int) {
        if (eventId == 7 && playerViewModel.currentEngine.value == PlayerEngine.MPV) {
            playerViewModel.setPlaying(false)
            if (currentPlayingPath.isNotEmpty()) {
                prefs.edit().putLong("resume_pos_$currentPlayingPath", 0L).apply()
            }
            handler.post { handlePlaybackCompleted() }
        }
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (playerViewModel.currentEngine.value == PlayerEngine.EXO &&
                exoPlayer?.isPlaying == true) {
                playerViewModel.setCurrentPosition(exoPlayer?.currentPosition ?: 0L)
                handler.postDelayed(this, 100)
            }
        }
    }

    private fun setupExoListeners() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                    playerViewModel.setPlaying(isPlaying)
                    if (isPlaying) {
                        playerViewModel.setDuration(exoPlayer?.duration ?: 0L)
                        handler.post(progressUpdateRunnable)
                    } else {
                        handler.removeCallbacks(progressUpdateRunnable)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO &&
                    playbackState == Player.STATE_ENDED) {
                    playerViewModel.setPlaying(false)
                    if (currentPlayingPath.isNotEmpty()) {
                        prefs.edit().putLong("resume_pos_$currentPlayingPath", 0L).apply()
                    }
                    handler.post { handlePlaybackCompleted() }
                }
            }
        })
    }

    private fun handlePlaybackCompleted() {
        val loopMode = playerViewModel.loopMode.value
        val currentIndex = playerViewModel.currentVideoIndex.value
        when (loopMode) {
            LoopMode.ONE -> playVideo(currentIndex)
            LoopMode.ALL -> {
                if (currentIndex < videoPaths.size - 1) playNext()
                else playVideo(0)
            }
            else -> {
                if (currentIndex < videoPaths.size - 1) playNext()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentPlaybackPosition()
        val bgPlay = prefs.getBoolean("bg_play_enabled", false)
        if (!bgPlay) {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (e: Exception) {}
            exoPlayer?.pause()
        }
        // ✅ clearVideoSurface() সরানো হয়েছে — Surface হারায় না
    }

    override fun onResume() {
        super.onResume()
        val bgPlay = prefs.getBoolean("bg_play_enabled", false)
        if (playerViewModel.currentEngine.value == PlayerEngine.MPV) {
            try {
                if (!bgPlay) {
                    MPVLib.setPropertyBoolean("pause", false)
                }
            } catch (e: Exception) {}
        } else {
            if (!bgPlay) {
                exoPlayer?.play()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            saveCurrentPlaybackPosition()
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (e: Exception) {}
            MPVLib.command(arrayOf("stop"))
            exoPlayer?.pause()
            exoPlayer?.stop()
        } else {
            try {
                MPVLib.setPropertyBoolean("pause", true)
            } catch (e: Exception) {}
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition()
        handler.removeCallbacksAndMessages(null)
        try {
            subtitlePfd?.close()
        } catch (e: Exception) {}
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
