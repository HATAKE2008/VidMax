package com.vidmax.player.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.data.model.FolderItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.data.repository.AudioRepository
import com.vidmax.player.data.repository.VideoRepository
import com.vidmax.player.service.AudioService
import com.vidmax.player.ui.theme.AppTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
  NAME,
  DATE,
  SIZE,
  DURATION
}

enum class DecoderMode {
  AUTO,
  HARDWARE,
  SOFTWARE
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: VideoRepository = VideoRepository(application.contentResolver)
  private val audioRepository: AudioRepository = AudioRepository(application.contentResolver)
  private val prefs: SharedPreferences =
      application.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)

  // Audio Player Engine (ExoPlayer - Media3)
  private var exoPlayer: ExoPlayer? = null
  private var loudnessEnhancer: LoudnessEnhancer? = null

  private var isAudioLoaded: Boolean = false

  // 🔥 Volume Trackers
  private var targetExoVolume: Float = 1.0f

  private val _isAudioPlaying: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying

  private val _audioPosition: MutableStateFlow<Long> = MutableStateFlow(0L)
  val audioPosition: StateFlow<Long> = _audioPosition
  private val _audioDuration: MutableStateFlow<Long> = MutableStateFlow(0L)
  val audioDuration: StateFlow<Long> = _audioDuration

  private var audioProgressJob: Job? = null
  private val _currentAudioArtist: MutableStateFlow<String> = MutableStateFlow("Unknown Artist")
  val currentAudioArtist: StateFlow<String> = _currentAudioArtist

  private var currentAudioList: MutableList<AudioItem> = mutableListOf()
  private var currentAudioIndex: Int = -1

  private val _currentQueue: MutableStateFlow<List<AudioItem>> = MutableStateFlow(emptyList())
  val currentQueue: StateFlow<List<AudioItem>> = _currentQueue

  private val _currentQueueIndex: MutableStateFlow<Int> = MutableStateFlow(-1)
  val currentQueueIndex: StateFlow<Int> = _currentQueueIndex

  private val _isShuffleEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled

  private val _audioRepeatMode: MutableStateFlow<LoopMode> = MutableStateFlow(LoopMode.NONE)
  val audioRepeatMode: StateFlow<LoopMode> = _audioRepeatMode

  private val _favoriteAudioPaths: MutableStateFlow<Set<String>> =
      MutableStateFlow(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
  val favoriteAudioPaths: StateFlow<Set<String>> = _favoriteAudioPaths

  private val _openedPlaylistTitle: MutableStateFlow<String> = MutableStateFlow("")
  val openedPlaylistTitle: StateFlow<String> = _openedPlaylistTitle
  private val _openedPlaylistAudio: MutableStateFlow<List<AudioItem>> =
      MutableStateFlow(emptyList())
  val openedPlaylistAudio: StateFlow<List<AudioItem>> = _openedPlaylistAudio

  // --- Common States ---
  private val _allVideos: MutableStateFlow<List<VideoItem>> = MutableStateFlow(emptyList())
  private val _folders: MutableStateFlow<List<FolderItem>> = MutableStateFlow(emptyList())
  val folders: StateFlow<List<FolderItem>> = _folders
  private val _filteredVideos: MutableStateFlow<List<VideoItem>> = MutableStateFlow(emptyList())
  val filteredVideos: StateFlow<List<VideoItem>> = _filteredVideos
  private val _folderVideos: MutableStateFlow<List<VideoItem>> = MutableStateFlow(emptyList())
  val folderVideos: StateFlow<List<VideoItem>> = _folderVideos
  private val _searchQuery: MutableStateFlow<String> = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery
  private val _sortOrder: MutableStateFlow<SortOrder> = MutableStateFlow(SortOrder.DATE)
  val sortOrder: StateFlow<SortOrder> = _sortOrder
  private val _currentFolderPath: MutableStateFlow<String> = MutableStateFlow("")
  val currentFolderPath: StateFlow<String> = _currentFolderPath

  private val _allAudio: MutableStateFlow<List<AudioItem>> = MutableStateFlow(emptyList())
  private val _filteredAudio: MutableStateFlow<List<AudioItem>> = MutableStateFlow(emptyList())
  val filteredAudio: StateFlow<List<AudioItem>> = _filteredAudio
  private val _audioSearchQuery: MutableStateFlow<String> = MutableStateFlow("")
  val audioSearchQuery: StateFlow<String> = _audioSearchQuery

  private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading
  private val _hasPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val hasPermission: StateFlow<Boolean> = _hasPermission

  // --- Advanced Player States ---
  private val _playerEngine: MutableStateFlow<PlayerEngine> =
      MutableStateFlow(
          PlayerEngine.valueOf(
              prefs.getString("player_engine", PlayerEngine.EXO.name) ?: PlayerEngine.EXO.name))
  val playerEngine: StateFlow<PlayerEngine> = _playerEngine

  private val _audioBoost: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("audio_boost", false))
  val audioBoost: StateFlow<Boolean> = _audioBoost

  private val _resumePlayback: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("resume_playback", true))
  val resumePlayback: StateFlow<Boolean> = _resumePlayback

  private val _decoderMode: MutableStateFlow<DecoderMode> =
      MutableStateFlow(
          DecoderMode.valueOf(
              prefs.getString("video_decoder", DecoderMode.AUTO.name) ?: DecoderMode.AUTO.name))
  val decoderMode: StateFlow<DecoderMode> = _decoderMode

  private val _autoRotate: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("auto_rotate", true))
  val autoRotate: StateFlow<Boolean> = _autoRotate
  private val _pipEnabled: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("pip_enabled", true))
  val pipEnabled: StateFlow<Boolean> = _pipEnabled
  private val _showResolutionBadge: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("resolution_badge", true))
  val showResolutionBadge: StateFlow<Boolean> = _showResolutionBadge

  // 🔥 Default Theme Changed to CINEMATIC_CRIMSON
  private val savedThemeName: String =
      prefs.getString("app_theme", AppTheme.CINEMATIC_CRIMSON.name)
          ?: AppTheme.CINEMATIC_CRIMSON.name
  private val _appTheme: MutableStateFlow<AppTheme> =
      MutableStateFlow(AppTheme.valueOf(savedThemeName))
  val appTheme: StateFlow<AppTheme> = _appTheme

  // 🔥 NEW Premium Feature States
  private val _skipSilence: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("skip_silence", false))
  val skipSilence: StateFlow<Boolean> = _skipSilence

  private val _crossfadeEnabled: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("crossfade_enabled", true))
  val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled

  // --- Memory States ---
  private val _recentlyPlayedTitle: MutableStateFlow<String> =
      MutableStateFlow(prefs.getString("recent_music_title", "") ?: "")
  val recentlyPlayedTitle: StateFlow<String> = _recentlyPlayedTitle
  private val _recentlyPlayedPath: MutableStateFlow<String> =
      MutableStateFlow(prefs.getString("recent_music_path", "") ?: "")
  val recentlyPlayedPath: StateFlow<String> = _recentlyPlayedPath

  private val _recentVideoTitle: MutableStateFlow<String> =
      MutableStateFlow(prefs.getString("recent_video_title", "") ?: "")
  val recentVideoTitle: StateFlow<String> = _recentVideoTitle
  private val _recentVideoPath: MutableStateFlow<String> =
      MutableStateFlow(prefs.getString("recent_video_path", "") ?: "")
  val recentVideoPath: StateFlow<String> = _recentVideoPath

  private val _isMiniPlayerVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible

  private val _musicBoostEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val musicBoostEnabled: StateFlow<Boolean> = _musicBoostEnabled

  private val _sleepTimerMinutes: MutableStateFlow<Int> = MutableStateFlow(0)
  val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes
  private var sleepTimerJob: Job? = null

  private val prefListener: SharedPreferences.OnSharedPreferenceChangeListener =
      SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "recent_video_path" || key == "recent_video_title") {
          _recentVideoTitle.value = sharedPreferences.getString("recent_video_title", "") ?: ""
          _recentVideoPath.value = sharedPreferences.getString("recent_video_path", "") ?: ""
        }
      }

  private val audioReceiver: BroadcastReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          when (intent?.action) {
            "ACTION_TOGGLE" -> toggleAudio()
            "ACTION_NEXT" -> playNextAudio()
            "ACTION_PREVIOUS" -> playPreviousAudio()
            "ACTION_STOP" -> stopAudioCompletely()
          }
        }
      }

  init {
    prefs.registerOnSharedPreferenceChangeListener(prefListener)

    // Initialize ExoPlayer with Skip Silence Feature
    exoPlayer =
        ExoPlayer.Builder(application).build().apply { skipSilenceEnabled = _skipSilence.value }
    setupExoPlayerEvents()

    val filter =
        IntentFilter().apply {
          addAction("ACTION_TOGGLE")
          addAction("ACTION_NEXT")
          addAction("ACTION_PREVIOUS")
          addAction("ACTION_STOP")
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      application.registerReceiver(audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      application.registerReceiver(audioReceiver, filter)
    }
  }

  // 🔥 Instant Play (Fade-In Removed)
  private fun executePlay() {
    exoPlayer?.volume = targetExoVolume
    exoPlayer?.play()
  }

  // 🔥 Instant Pause (Fade-Out Removed to prevent Delay)
  private fun executePause() {
    exoPlayer?.pause()
    audioProgressJob?.cancel()
    updateNotification(_recentlyPlayedTitle.value, _currentAudioArtist.value, false)
  }

  private fun setupExoPlayerEvents() {
    exoPlayer?.addListener(
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
              viewModelScope.launch(Dispatchers.Main) {
                if (_audioRepeatMode.value == LoopMode.ONE) {
                  if (currentAudioList.isNotEmpty() &&
                      currentAudioIndex in currentAudioList.indices) {
                    val audio: AudioItem = currentAudioList[currentAudioIndex]
                    playAudioInternal(audio.title, audio.artist, audio.path)
                  }
                } else {
                  playNextAudio(isAutoPlay = true)
                }
              }
            } else if (playbackState == Player.STATE_READY) {
              _audioDuration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
              applyCurrentVolume()
            }
          }

          override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isAudioPlaying.value = isPlaying
            if (isPlaying) {
              _isMiniPlayerVisible.value = true
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            viewModelScope.launch(Dispatchers.Main) {
              _isAudioPlaying.value = false
              playNextAudio(isAutoPlay = true)
            }
          }
        })
  }

  private fun applyCurrentVolume() {
    val isBoosted: Boolean = _musicBoostEnabled.value
    try {
      if (loudnessEnhancer == null && exoPlayer != null) {
        val sessionId: Int = exoPlayer?.audioSessionId ?: 0
        if (sessionId != 0) {
          loudnessEnhancer = LoudnessEnhancer(sessionId)
        }
      }
      if (isBoosted) {
        targetExoVolume = 1f
        exoPlayer?.volume = 1f
        loudnessEnhancer?.setTargetGain(2500) // Safe Max Boost
        loudnessEnhancer?.enabled = true
      } else {
        loudnessEnhancer?.enabled = false
        // targetExoVolume is managed by setCustomVolume
        exoPlayer?.volume = targetExoVolume
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun updateNotification(title: String, artist: String, isPlaying: Boolean) {
    val intent =
        Intent(getApplication(), AudioService::class.java).apply {
          action = "UPDATE_NOTIFICATION"
          putExtra("TITLE", title)
          putExtra("ARTIST", artist)
          putExtra("IS_PLAYING", isPlaying)
          putExtra("FILE_PATH", _recentlyPlayedPath.value)
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getApplication<Application>().startForegroundService(intent)
    } else {
      getApplication<Application>().startService(intent)
    }
  }

  fun toggleFavorite(path: String) {
    val currentFavs: MutableSet<String> = _favoriteAudioPaths.value.toMutableSet()
    if (currentFavs.contains(path)) currentFavs.remove(path) else currentFavs.add(path)
    _favoriteAudioPaths.value = currentFavs
    prefs.edit().putStringSet("favorites", currentFavs).apply()
  }

  fun openFavorites() {
    _openedPlaylistTitle.value = "Favorites"
    _openedPlaylistAudio.value =
        _allAudio.value.filter { _favoriteAudioPaths.value.contains(it.path) }
  }

  fun openMyMix() {
    _openedPlaylistTitle.value = "My Mix"
    _openedPlaylistAudio.value = _allAudio.value.shuffled().take(20)
  }

  fun closePlaylist() {
    _openedPlaylistTitle.value = ""
    _openedPlaylistAudio.value = emptyList()
  }

  fun playAudioFromList(list: List<AudioItem>, index: Int) {
    if (list.isEmpty() || index < 0 || index >= list.size) return
    currentAudioList.clear()
    currentAudioList.addAll(list)
    _currentQueue.value = currentAudioList.toList()
    currentAudioIndex = index
    _currentQueueIndex.value = index
    val audio: AudioItem = list[index]
    playAudioInternal(audio.title, audio.artist, audio.path)
  }

  private fun playAudioInternal(title: String, artist: String, path: String) {
    try {
      val uri: Uri = if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

      exoPlayer?.stop()
      exoPlayer?.clearMediaItems()
      exoPlayer?.setMediaItem(MediaItem.fromUri(uri))
      exoPlayer?.prepare()

      executePlay() // 🔥 Play INSTANTLY

      isAudioLoaded = true
      _currentAudioArtist.value = artist

      setRecentlyPlayedMusic(title, path)
      startAudioProgress()
      updateNotification(title, artist, true)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun playNextAudio(isAutoPlay: Boolean = false) {
    if (currentAudioList.isEmpty()) return
    if (_isShuffleEnabled.value) {
      currentAudioIndex = currentAudioList.indices.random()
    } else {
      currentAudioIndex++
      if (currentAudioIndex >= currentAudioList.size) {
        if (_audioRepeatMode.value == LoopMode.ALL || !isAutoPlay) {
          currentAudioIndex = 0
        } else {
          currentAudioIndex = currentAudioList.size - 1
          _currentQueueIndex.value = currentAudioIndex
          executePause()
          return
        }
      }
    }
    _currentQueueIndex.value = currentAudioIndex
    val audio: AudioItem = currentAudioList[currentAudioIndex]
    playAudioInternal(audio.title, audio.artist, audio.path)
  }

  fun playPreviousAudio() {
    if (currentAudioList.isEmpty()) return
    if (_audioPosition.value > 3000) {
      seekAudio(0)
      executePlay()
      updateNotification(_recentlyPlayedTitle.value, _currentAudioArtist.value, true)
      return
    }
    if (_isShuffleEnabled.value) {
      currentAudioIndex = currentAudioList.indices.random()
    } else {
      currentAudioIndex--
      if (currentAudioIndex < 0) currentAudioIndex = currentAudioList.size - 1
    }
    _currentQueueIndex.value = currentAudioIndex
    val audio: AudioItem = currentAudioList[currentAudioIndex]
    playAudioInternal(audio.title, audio.artist, audio.path)
  }

  // 🔥 MainScreen এর সাথে মেলানোর জন্য যোগ করা হলো
  fun nextAudio() {
    playNextAudio(false)
  }

  // 🔥 MainScreen এর সাথে মেলানোর জন্য যোগ করা হলো
  fun previousAudio() {
    playPreviousAudio()
  }

  fun toggleShuffle() {
    _isShuffleEnabled.value = !_isShuffleEnabled.value
  }

  fun toggleRepeat() {
    _audioRepeatMode.value =
        when (_audioRepeatMode.value) {
          LoopMode.NONE -> LoopMode.ALL
          LoopMode.ALL -> LoopMode.ONE
          LoopMode.ONE -> LoopMode.NONE
        }
  }

  fun pauseAudio() {
    exoPlayer?.let { player ->
      if (player.isPlaying) {
        executePause() // 🔥 Pause INSTANTLY
      }
    }
  }

  fun toggleAudio() {
    exoPlayer?.let { player ->
      if (player.isPlaying) {
        executePause() // 🔥 Pause INSTANTLY
      } else {
        if (!isAudioLoaded && _recentlyPlayedPath.value.isNotEmpty()) {
          if (currentAudioIndex != -1 && currentAudioList.isNotEmpty()) {
            val audio: AudioItem = currentAudioList[currentAudioIndex]
            playAudioInternal(audio.title, audio.artist, audio.path)
          } else {
            playAudioInternal(
                _recentlyPlayedTitle.value, _currentAudioArtist.value, _recentlyPlayedPath.value)
          }
        } else {
          executePlay() // 🔥 Play INSTANTLY
          startAudioProgress()
          updateNotification(_recentlyPlayedTitle.value, _currentAudioArtist.value, true)
        }
      }
    }
  }

  fun seekAudio(position: Long) {
    exoPlayer?.seekTo(position)
    _audioPosition.value = position
  }

  private fun stopAudioCompletely() {
    pauseAudio()
    _isMiniPlayerVisible.value = false
    val intent =
        Intent(getApplication(), AudioService::class.java).apply { action = "STOP_SERVICE" }
    getApplication<Application>().startService(intent)
  }

  private fun startAudioProgress() {
    audioProgressJob?.cancel()
    audioProgressJob =
        viewModelScope.launch {
          while (isActive) {
            exoPlayer?.let { player ->
              if (player.isPlaying) _audioPosition.value = player.currentPosition
            }
            delay(500)
          }
        }
  }

  fun toggleMusicBoost() {
    val isBoosted: Boolean = !_musicBoostEnabled.value
    _musicBoostEnabled.value = isBoosted
    applyCurrentVolume()
  }

  fun setSleepTimer(minutes: Int) {
    _sleepTimerMinutes.value = minutes
    sleepTimerJob?.cancel()
    if (minutes > 0) {
      sleepTimerJob =
          viewModelScope.launch {
            delay(minutes * 60 * 1000L)
            stopAudioCompletely()
            _sleepTimerMinutes.value = 0
          }
    }
  }

  fun setMiniPlayerVisible(visible: Boolean) {
    _isMiniPlayerVisible.value = visible
  }

  override fun onCleared() {
    super.onCleared()
    prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    audioProgressJob?.cancel()
    sleepTimerJob?.cancel()

    try {
      getApplication<Application>().unregisterReceiver(audioReceiver)
    } catch (e: Exception) {}

    try {
      _isMiniPlayerVisible.value = false
      val intent =
          Intent(getApplication(), AudioService::class.java).apply { action = "STOP_SERVICE" }
      getApplication<Application>().startService(intent)

      exoPlayer?.release()
      loudnessEnhancer?.release()
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun setPermissionGranted(granted: Boolean) {
    _hasPermission.value = granted
    if (granted) {
      loadVideos()
      loadAudio()
    }
  }

  fun loadVideos() {
    viewModelScope.launch {
      _isLoading.value = true
      val videos: List<VideoItem> = withContext(Dispatchers.IO) { repository.getAllVideos() }
      _allVideos.value = videos
      _folders.value = repository.getFolders(videos)
      applyFilter()
      _isLoading.value = false
    }
  }

  private fun loadAudio() {
    viewModelScope.launch {
      val audio: List<AudioItem> = withContext(Dispatchers.IO) { audioRepository.getAllAudio() }
      _allAudio.value = audio
      applyAudioFilter()

      if (currentAudioList.isEmpty() && _recentlyPlayedPath.value.isNotEmpty()) {
        val idx: Int = audio.indexOfFirst { it.path == _recentlyPlayedPath.value }
        if (idx != -1) {
          currentAudioList.addAll(audio)
          _currentQueue.value = currentAudioList.toList()
          currentAudioIndex = idx
          _currentQueueIndex.value = idx
          _currentAudioArtist.value = audio[idx].artist
        }
      }
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
    applyFilter()
  }

  fun setAudioSearchQuery(query: String) {
    _audioSearchQuery.value = query
    applyAudioFilter()
  }

  fun setSortOrder(order: SortOrder) {
    _sortOrder.value = order
    applyFilter()
    if (_currentFolderPath.value.isNotEmpty()) applyFolderFilter(_currentFolderPath.value)
  }

  fun openFolder(folderPath: String) {
    _currentFolderPath.value = folderPath
    applyFolderFilter(folderPath)
  }

  fun closeFolder() {
    _currentFolderPath.value = ""
  }

  private fun applyFilter() {
    val query: String = _searchQuery.value.lowercase()
    val base: List<VideoItem> =
        if (query.isEmpty()) _allVideos.value
        else _allVideos.value.filter { it.title.lowercase().contains(query) }
    _filteredVideos.value = sortVideos(base)
  }

  private fun applyAudioFilter() {
    val query: String = _audioSearchQuery.value.lowercase()
    val base: List<AudioItem> =
        if (query.isEmpty()) _allAudio.value
        else
            _allAudio.value.filter {
              it.title.lowercase().contains(query) || it.artist.lowercase().contains(query)
            }
    _filteredAudio.value = base
  }

  private fun applyFolderFilter(folderPath: String) {
    val base: List<VideoItem> = _allVideos.value.filter { it.folderPath == folderPath }
    _folderVideos.value = sortVideos(base)
  }

  private fun sortVideos(videos: List<VideoItem>): List<VideoItem> {
    return when (_sortOrder.value) {
      SortOrder.NAME -> videos.sortedBy { it.title.lowercase() }
      SortOrder.DATE -> videos.sortedByDescending { it.dateAdded }
      SortOrder.SIZE -> videos.sortedByDescending { it.size }
      SortOrder.DURATION -> videos.sortedByDescending { it.duration }
    }
  }

  fun formatDuration(ms: Long): String = repository.formatDuration(ms)

  fun formatSize(bytes: Long): String = repository.formatSize(bytes)

  fun getResolutionLabel(width: Int, height: Int): String =
      repository.getResolutionLabel(width, height)

  fun setPlayerEngine(engine: PlayerEngine) {
    _playerEngine.value = engine
    prefs.edit().putString("player_engine", engine.name).apply()
  }

  fun setAudioBoost(enabled: Boolean) {
    _audioBoost.value = enabled
    prefs.edit().putBoolean("audio_boost", enabled).apply()
  }

  fun setResumePlayback(enabled: Boolean) {
    _resumePlayback.value = enabled
    prefs.edit().putBoolean("resume_playback", enabled).apply()
  }

  fun setDecoderMode(mode: DecoderMode) {
    _decoderMode.value = mode
    prefs.edit().putString("video_decoder", mode.name).apply()
  }

  fun setAutoRotate(enabled: Boolean) {
    _autoRotate.value = enabled
    prefs.edit().putBoolean("auto_rotate", enabled).apply()
  }

  fun setPipEnabled(enabled: Boolean) {
    _pipEnabled.value = enabled
    prefs.edit().putBoolean("pip_enabled", enabled).apply()
  }

  fun setShowResolutionBadge(enabled: Boolean) {
    _showResolutionBadge.value = enabled
    prefs.edit().putBoolean("resolution_badge", enabled).apply()
  }

  fun setAppTheme(theme: AppTheme) {
    _appTheme.value = theme
    prefs.edit().putString("app_theme", theme.name).apply()
  }

  fun setSkipSilence(enabled: Boolean) {
    _skipSilence.value = enabled
    prefs.edit().putBoolean("skip_silence", enabled).apply()
    exoPlayer?.skipSilenceEnabled = enabled
  }

  fun setCrossfade(enabled: Boolean) {
    _crossfadeEnabled.value = enabled
    // Settings state will save, but fade effect is now natively bypassed in logic
    prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
  }

  fun setRecentlyPlayedVideo(title: String, path: String) {
    _recentVideoTitle.value = title
    _recentVideoPath.value = path
    prefs.edit().putString("recent_video_title", title).putString("recent_video_path", path).apply()
  }

  private fun setRecentlyPlayedMusic(title: String, path: String) {
    _recentlyPlayedTitle.value = title
    _recentlyPlayedPath.value = path
    prefs.edit().putString("recent_music_title", title).putString("recent_music_path", path).apply()
  }

  fun setCustomVolume(volume: Int) {
    val safeVolume: Int = volume.coerceIn(0, 200)
    if (safeVolume <= 100) {
      targetExoVolume = safeVolume / 100f
      exoPlayer?.volume = targetExoVolume
      try {
        loudnessEnhancer?.enabled = false
      } catch (e: Exception) {}
    } else {
      targetExoVolume = 1f
      exoPlayer?.volume = 1f
      try {
        if (loudnessEnhancer == null && exoPlayer != null) {
          val sessionId: Int = exoPlayer?.audioSessionId ?: 0
          if (sessionId != 0) loudnessEnhancer = LoudnessEnhancer(sessionId)
        }
        loudnessEnhancer?.enabled = true
        val boostRatio: Float = (safeVolume - 100f) / 100f
        loudnessEnhancer?.setTargetGain((boostRatio * 2500).toInt())
      } catch (e: Exception) {}
    }
  }
}
