@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vidmax.player.ui.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.ui.theme.VidMaxTheme
import com.vidmax.player.viewmodel.AspectRatioMode
import com.vidmax.player.viewmodel.DarkMode
import com.vidmax.player.viewmodel.LoopMode
import com.vidmax.player.viewmodel.PlayerEngine
import com.vidmax.player.viewmodel.PlayerViewModel
import `is`.xyz.mpv.MPVLib
import java.io.File
import java.util.Locale

class PlayerActivity : ComponentActivity(), MPVLib.EventObserver {

    private val playerViewModel: PlayerViewModel by viewModels()
    private var exoPlayer: ExoPlayer? = null
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    private var pendingPlayIndex: Int = -1
    private var videoPaths: List<String> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: SharedPreferences
    private var isResumePlayback: Boolean = true
    private var audioBoostEnabled: Boolean = false
    private var currentPlayingPath: String = ""

    // MPV হ্যাং হওয়া আটকানোর ফ্ল্যাগ
    private var isTrackChanging: Boolean = false
    private val resetTrackChangeRunnable = Runnable { isTrackChanging = false }

    // 🔥 FIX: MPV এখন lazy initialize হবে — শুধুমাত্র MPV engine ব্যবহারের সময়
    private var mpvInitialized: Boolean = false

    private var subtitlePfd: ParcelFileDescriptor? = null
    private var externalSubUri: Uri? = null

    private val subtitlePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    if (playerViewModel.currentEngine.value == PlayerEngine.MPV && mpvInitialized) {
                        subtitlePfd?.close()
                        subtitlePfd = contentResolver.openFileDescriptor(uri, "r")
                        val fd = subtitlePfd?.fd
                        if (fd != null) {
                            val fdUri = "fd://$fd"
                            MPVLib.command(arrayOf("sub-add", fdUri))
                            Toast.makeText(this, "Subtitle Added! ✅", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        externalSubUri = uri
                        Toast.makeText(this, "Subtitle Added!", Toast.LENGTH_SHORT).show()
                        handler.post { playVideo(playerViewModel.currentVideoIndex.value) }
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

        // Passing thousands of file paths as an Intent extra can exceed the
        // ~1 MB binder transaction limit (TransactionTooLargeException) and
        // crash the app the instant the player is opened from the full Videos
        // list. Above this size the playlist travels through a process-local
        // holder instead, and only the small index goes through the Intent.
        private const val MAX_PATHS_IN_INTENT = 500

        @Volatile
        var pendingPaths: List<String> = emptyList()

        fun start(context: Context, paths: List<String>, startIndex: Int = 0) {
            val intent = Intent(context, PlayerActivity::class.java)
            if (paths.size <= MAX_PATHS_IN_INTENT) {
                intent.putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
            } else {
                pendingPaths = paths
            }
            intent.putExtra(EXTRA_INDEX, startIndex)
            // 🔥 FIX: Application Context থেকে start করার জন্য
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun takePendingPaths(): List<String> {
            val p = pendingPaths
            pendingPaths = emptyList()
            return p
        }
    }

    // 🔥 FIX: MPV init এখন আলাদা function এ — দরকার হলেই শুধু call হবে
    private fun ensureMpvReady() {
        if (mpvInitialized) return
        try {
            MPVLib.create(this)
            MPVLib.setOptionString("profile", "fast")
            MPVLib.setOptionString("hwdec", "no") // software decode = Unisoc এ safe
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("vd-lavc-fast", "yes")
            MPVLib.setOptionString("keepaspect", "yes")
            MPVLib.setOptionString("panscan", "0")
            MPVLib.setOptionString("video-aspect-override", "no")
            MPVLib.init()

            MPVLib.addObserver(this)
            MPVLib.observeProperty("time-pos", 5)
            MPVLib.observeProperty("duration", 5)
            MPVLib.observeProperty("pause", 3)
            // Fires with true when mpv has no active file — used to detect a
            // loadfile that failed instantly (e.g. a non-direct stream URL).
            MPVLib.observeProperty("idle-active", 3)
            mpvInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
            mpvInitialized = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)
        isResumePlayback = prefs.getBoolean("resume_playback", true)
        audioBoostEnabled = prefs.getBoolean("audio_boost", false)
        val isAutoRotate = prefs.getBoolean("auto_rotate", true)

        val savedEngineName = prefs.getString("player_engine", PlayerEngine.EXO.name) ?: PlayerEngine.EXO.name
        val engineToSet = try {
            if (savedEngineName == "VLC") PlayerEngine.MPV else PlayerEngine.valueOf(savedEngineName)
        } catch (e: Exception) {
            PlayerEngine.EXO
        }

        val intentPaths = intent.getStringArrayListExtra(EXTRA_PATHS)?.takeIf { it.isNotEmpty() }
        if (intentPaths != null) {
            pendingPaths = emptyList()
        }
        val initialPaths: List<String> =
            (intentPaths ?: takePendingPaths()).filter { it.isNotEmpty() }

        // Schemes only mpv can handle (ExoPlayer has no rtsp/rtmp/mms/ftp
        // datasource here): route stream links straight to the MPV engine.
        val firstScheme = initialPaths.firstOrNull()
            ?.let { runCatching { Uri.parse(it).scheme }.getOrNull() }?.lowercase(Locale.US)
        val mpvOnlySchemes = setOf(
            "rtsp", "rtsps", "rtmp", "rtmps", "rtp", "srt",
            "udp", "mms", "mmsh", "tcp", "ftp", "ftps", "smb",
        )
        val resolvedEngine =
            if (firstScheme in mpvOnlySchemes) PlayerEngine.MPV else engineToSet
        playerViewModel.setPlayerEngine(resolvedEngine)

        // Remember the video-control preferences (loop mode, aspect ratio)
        // across player sessions: restore the last saved values as this
        // session's defaults. Changes made in the player are persisted back
        // from the composition below.
        prefs.getString("player_loop_mode", null)?.let { saved ->
            runCatching { playerViewModel.setLoopMode(LoopMode.valueOf(saved)) }
        }
        prefs.getString("player_aspect_ratio", null)?.let { saved ->
            runCatching { playerViewModel.setAspectRatio(AspectRatioMode.valueOf(saved)) }
        }
        playerViewModel.setPlaybackSpeed(prefs.getFloat("player_speed", 1f).coerceIn(0.25f, 3f))

        // 🔥 FIX: MPV শুধু তখনই init হবে যখন engine = MPV
        if (resolvedEngine == PlayerEngine.MPV) {
            ensureMpvReady()
        }

        requestedOrientation =
            if (isAutoRotate) ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        videoPaths = initialPaths

        val rawIndex: Int = intent.getIntExtra(EXTRA_INDEX, 0)
        if (videoPaths.isEmpty()) {
            finish()
            return
        }
        val startIndex: Int = rawIndex.coerceIn(0, videoPaths.size - 1)

        playerViewModel.setTotalVideos(videoPaths.size)

        // 🔥 FIX: decoder fallback enable — hardware decoder fail হলে software ব্যবহার হবে
        // Stream links commonly redirect across protocols (https→http, CDN hops)
        // and some hosts reject the default ExoPlayer user agent, so the HTTP
        // data source allows cross-protocol redirects with a browser UA.
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory)
        )
        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        setupExoListeners()

        handler.post(progressUpdateRunnable)

        pendingPlayIndex = startIndex

        setContent {
            // 🔥 Reactive theme — mirrors the app Settings (theme, dark mode,
            // AMOLED, font) live, so the player and its settings panels always
            // match the rest of the app.
            var themeState by remember { mutableStateOf(readSavedTheme()) }
            var darkModeState by remember { mutableStateOf(readSavedDarkMode()) }
            var amoledState by remember { mutableStateOf(prefs.getBoolean("amoled_mode", false)) }
            var fontIdState by remember {
                mutableStateOf(
                    prefs.getString("app_font", AppFonts.SYSTEM_DEFAULT) ?: AppFonts.SYSTEM_DEFAULT
                )
            }

            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "app_theme" -> themeState = readSavedTheme()
                        "dark_mode" -> darkModeState = readSavedDarkMode()
                        "amoled_mode" -> amoledState = prefs.getBoolean("amoled_mode", false)
                        "app_font" -> fontIdState =
                            prefs.getString("app_font", AppFonts.SYSTEM_DEFAULT) ?: AppFonts.SYSTEM_DEFAULT
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (darkModeState) {
                DarkMode.Dark -> true
                DarkMode.Light -> false
                DarkMode.System -> isSystemDark
            }
            val appFontFamily = remember(fontIdState) {
                AppFonts.resolveFontFamily(this@PlayerActivity, fontIdState)
            }

            val currentIndex by playerViewModel.currentVideoIndex.collectAsState()
            val currentPath =
                if (videoPaths.isNotEmpty() && currentIndex < videoPaths.size)
                    videoPaths[currentIndex]
                else ""

            VidMaxTheme(
                appTheme = themeState,
                useDarkTheme = useDarkTheme,
                amoledMode = amoledState,
                appFontFamily = appFontFamily
            ) {
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

            // Persist the video-control preferences across player sessions
            // and keep the native loop in sync when the loop mode
            // changes mid-playback (no stop/reload, so no black flash).
            LaunchedEffect(Unit) {
                playerViewModel.loopMode.collect { mode ->
                    prefs.edit().putString("player_loop_mode", mode.name).apply()
                    if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                        exoPlayer?.repeatMode =
                            if (mode == LoopMode.ONE) Player.REPEAT_MODE_ONE
                            else Player.REPEAT_MODE_OFF
                    } else if (mpvInitialized) {
                        try {
                            MPVLib.setOptionString("loop-file", if (mode == LoopMode.ONE) "inf" else "no")
                        } catch (e: Exception) {}
                    }
                }
            }
            LaunchedEffect(Unit) {
                playerViewModel.aspectRatio.collect { mode ->
                    prefs.edit().putString("player_aspect_ratio", mode.name).apply()
                }
            }
        }
    }

    private fun readSavedTheme(): AppTheme = try {
        AppTheme.valueOf(prefs.getString("app_theme", AppTheme.Default.name) ?: AppTheme.Default.name)
    } catch (e: Exception) {
        AppTheme.Default
    }

    private fun readSavedDarkMode(): DarkMode = try {
        DarkMode.valueOf(prefs.getString("dark_mode", DarkMode.System.name) ?: DarkMode.System.name)
    } catch (e: Exception) {
        DarkMode.System
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun playVideo(index: Int) {
        if (index < 0 || index >= videoPaths.size) return
        saveCurrentPlaybackPosition()

        handler.removeCallbacks(resetTrackChangeRunnable)
        isTrackChanging = true

        playerViewModel.setCurrentVideoIndex(index)
        playerViewModel.clearABRepeat()
        val path = videoPaths[index]
        currentPlayingPath = path

        val name = runCatching {
            Uri.decode(path.substringAfterLast("/").substringBeforeLast("."))
        }.getOrDefault(path.substringAfterLast("/").substringBeforeLast("."))
        playerViewModel.setVideoTitle(name)
        prefs.edit().putString("recent_video_path", path).putString("recent_video_title", name).apply()

        val uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)
        val startPos = if (isResumePlayback) prefs.getLong("resume_pos_$path", 0L) else 0L

        if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
            if (mpvInitialized) MPVLib.command(arrayOf("stop"))
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            // Loop-one restarts are handled seamlessly by ExoPlayer's own
            // repeat mode, avoiding the black flash of a stop + full reload.
            exoPlayer?.repeatMode =
                if (playerViewModel.loopMode.value == LoopMode.ONE) Player.REPEAT_MODE_ONE
                else Player.REPEAT_MODE_OFF

            val externalSub = externalSubUri
            if (externalSub != null) {
                val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(uri))
                val ext = externalSub.lastPathSegment
                    ?.substringAfterLast('.', "")
                    ?.lowercase(Locale.US) ?: ""
                val mime =
                    when (ext) {
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        "vtt" -> MimeTypes.TEXT_VTT
                        "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                val subSource =
                    SingleSampleMediaSource.Factory(DefaultDataSource.Factory(this))
                        .createMediaSource(
                            MediaItem.SubtitleConfiguration.Builder(externalSub)
                                .setMimeType(mime)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build(),
                            C.TIME_UNSET
                        )
                exoPlayer?.setMediaSource(MergingMediaSource(videoSource, subSource))
            } else {
                exoPlayer?.setMediaItem(MediaItem.fromUri(uri))
            }
            exoPlayer?.prepare()
            if (startPos > 3000L) {
                exoPlayer?.seekTo(startPos)
            }
            exoPlayer?.setPlaybackSpeed(playerViewModel.playbackSpeed.value.coerceIn(0.25f, 3f))
            exoPlayer?.play()
        } else {
            // 🔥 FIX: MPV branch এ এসে তবেই MPV init হবে
            ensureMpvReady()
            if (!mpvInitialized) {
                Toast.makeText(this, "MPV engine failed to start, use EXO engine", Toast.LENGTH_LONG).show()
                return
            }
            exoPlayer?.stop()

            if (startPos > 3000L) {
                val startSec = startPos / 1000.0
                MPVLib.setOptionString("start", startSec.toString())
            } else {
                MPVLib.setOptionString("start", "none")
            }

            // Loop-one restarts inside mpv itself so the video loops
            // seamlessly instead of going through a full reload.
            try {
                MPVLib.setOptionString(
                    "loop-file",
                    if (playerViewModel.loopMode.value == LoopMode.ONE) "inf" else "no"
                )
            } catch (e: Exception) {}

            MPVLib.command(arrayOf("loadfile", uri.toString(), "replace"))
            try {
                MPVLib.setPropertyDouble("speed", playerViewModel.playbackSpeed.value.coerceIn(0.25f, 3f).toDouble())
            } catch (e: Exception) {}
            MPVLib.setPropertyBoolean("pause", false)
            expectMpvPlayback = true
            playerViewModel.setPlaying(true)
        }

        handler.postDelayed(resetTrackChangeRunnable, 2000)
    }

    private fun saveCurrentPlaybackPosition() {
        if (isResumePlayback && currentPlayingPath.isNotEmpty()) {
            val currentPos =
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                    exoPlayer?.currentPosition ?: 0L
                } else {
                    try {
                        if (mpvInitialized) ((MPVLib.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong() else 0L
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
        } else if (mpvInitialized) {
            MPVLib.command(arrayOf("seek", "10", "relative"))
        }
    }

    private fun seekBackward() {
        if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
            val newPosition = (exoPlayer?.currentPosition ?: 0L) - 10_000L
            exoPlayer?.seekTo(if (newPosition < 0) 0L else newPosition)
        } else if (mpvInitialized) {
            MPVLib.command(arrayOf("seek", "-10", "relative"))
        }
    }

    // Set right before MPV loadfile, cleared when the file actually loads.
    // If idle-active becomes true while this is set, the stream could not be
    // opened (bad/non-direct URL, unreachable host, unsupported protocol).
    @Volatile
    private var expectMpvPlayback: Boolean = false

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Boolean) {
        if (property == "idle-active" && value && expectMpvPlayback) {
            expectMpvPlayback = false
            handler.post {
                playerViewModel.setPlaying(false)
                Toast.makeText(
                    this,
                    "Could not open stream — paste a direct video link (.mp4 / .mkv / .m3u8), not a page URL",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Double) {}
    override fun eventProperty(property: String, value: String) {}

    override fun event(eventId: Int) {
        if (playerViewModel.currentEngine.value != PlayerEngine.MPV || !mpvInitialized) return

        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED,
            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> {
                expectMpvPlayback = false
                val mode = playerViewModel.aspectRatio.value
                handler.post { MpvScaling.reapply(mode) }
            }

            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                if (isTrackChanging) return
                if (playerViewModel.loopMode.value == LoopMode.ONE) return
                playerViewModel.setPlaying(false)
                if (currentPlayingPath.isNotEmpty()) {
                    prefs.edit().putLong("resume_pos_$currentPlayingPath", 0L).apply()
                }
                handler.post { handlePlaybackCompleted() }
            }
        }
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                if (exoPlayer?.isPlaying == true) {
                    playerViewModel.setCurrentPosition(exoPlayer?.currentPosition ?: 0L)
                }
            } else if (playerViewModel.currentEngine.value == PlayerEngine.MPV && mpvInitialized) {
                try {
                    val isPaused = MPVLib.getPropertyBoolean("pause") ?: true
                    playerViewModel.setPlaying(!isPaused)

                    if (!isPaused) {
                        val pos = MPVLib.getPropertyDouble("time-pos") ?: 0.0
                        playerViewModel.setCurrentPosition((pos * 1000).toLong())

                        val dur = MPVLib.getPropertyDouble("duration") ?: 0.0
                        if (dur > 0) playerViewModel.setDuration((dur * 1000).toLong())
                    }
                } catch (e: Exception) {}
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun setupExoListeners() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO) {
                    playerViewModel.setPlaying(isPlaying)
                    if (isPlaying) {
                        playerViewModel.setDuration(exoPlayer?.duration ?: 0L)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playerViewModel.currentEngine.value == PlayerEngine.EXO &&
                    playbackState == Player.STATE_ENDED &&
                    // Seamless loop handled by ExoPlayer itself — a manual
                    // reload here would flash black between restarts.
                    exoPlayer?.repeatMode != Player.REPEAT_MODE_ONE) {
                    playerViewModel.setPlaying(false)
                    if (currentPlayingPath.isNotEmpty()) {
                        prefs.edit().putLong("resume_pos_$currentPlayingPath", 0L).apply()
                    }
                    handler.post { handlePlaybackCompleted() }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VidMaxPlayer", "ExoPlayer error for $currentPlayingPath", error)
                val reason = when (error.errorCode) {
                    2001, 2002 -> "network connection failed — check internet / server address"
                    2003 -> "server sent an unexpected content type"
                    2004 -> "server rejected this link (dead, blocked or needs login)"
                    2005 -> "file not found on the server"
                    2007 -> "plain-HTTP links are blocked by the system"
                    else -> "source could not be read"
                }
                Toast.makeText(
                    this@PlayerActivity,
                    "Playback error ${error.errorCode}: $reason",
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
    }

    private fun handlePlaybackCompleted() {
        val loopMode = playerViewModel.loopMode.value
        val currentIndex = playerViewModel.currentVideoIndex.value
        when (loopMode) {
            LoopMode.ONE -> return
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
            if (mpvInitialized) {
                try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
            }
            exoPlayer?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        val bgPlay = prefs.getBoolean("bg_play_enabled", false)
        if (playerViewModel.currentEngine.value == PlayerEngine.MPV) {
            if (mpvInitialized) {
                try {
                    if (!bgPlay) MPVLib.setPropertyBoolean("pause", false)
                } catch (e: Exception) {}
            }
        } else {
            if (!bgPlay) {
                exoPlayer?.play()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            saveCurrentPlaybackPosition()
            if (mpvInitialized) {
                try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
                MPVLib.command(arrayOf("stop"))
            }
            exoPlayer?.pause()
            exoPlayer?.stop()
        } else {
            if (mpvInitialized) {
                try { MPVLib.setPropertyBoolean("pause", true) } catch (e: Exception) {}
            }
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        saveCurrentPlaybackPosition()
        handler.removeCallbacksAndMessages(null)
        try {
            subtitlePfd?.close()
        } catch (e: Exception) {}
        // 🔥 FIX: শুধুমাত্র init হয়ে থাকলে তবেই MPV destroy হবে
        if (mpvInitialized) {
            MPVLib.removeObserver(this)
            MPVLib.destroy()
        }
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
