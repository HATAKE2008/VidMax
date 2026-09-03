package com.vidmax.player.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.data.model.FolderItem
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.data.local.video.VidMaxVideoDatabase
import com.vidmax.player.data.local.video.VidMaxVideoPlaylist
import com.vidmax.player.data.local.video.VidMaxVideoPlaylistItem
import com.vidmax.player.data.repository.AudioRepository
import com.vidmax.player.data.repository.VideoPlaylistRepository
import com.vidmax.player.data.repository.VideoRepository
import com.vidmax.player.service.AudioService
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.AppTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ported from mpvRex's PlaylistViewModel.PlaylistWithCount: a playlist row
 * paired with its item count for list rendering.
 */
data class PlaylistWithCount(
  val playlist: VidMaxVideoPlaylist,
  val itemCount: Int,
)

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

enum class DarkMode {
  Dark,
  Light,
  System
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: VideoRepository = VideoRepository(application.contentResolver)
  private val audioRepository: AudioRepository = AudioRepository(application.contentResolver)
  private val prefs: SharedPreferences =
      application.getSharedPreferences("vidmax_settings", Context.MODE_PRIVATE)

  private val audioManager: AudioManager =
      application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  // Audio Player Engine (ExoPlayer - Media3)
  private var exoPlayer: ExoPlayer? = null
  private var loudnessEnhancer: LoudnessEnhancer? = null

  private var isAudioLoaded: Boolean = false

  // Volume Trackers
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

  // --- Video Playlists (mpvRex-style Room architecture) ---
  private val playlistRepository: VideoPlaylistRepository =
      VideoPlaylistRepository(VidMaxVideoDatabase.getInstance(application).videoPlaylistDao())

  private val _videoPlaylists: MutableStateFlow<List<PlaylistWithCount>> =
      MutableStateFlow(emptyList())
  val videoPlaylists: StateFlow<List<PlaylistWithCount>> = _videoPlaylists.asStateFlow()

  private val _openedVideoPlaylist: MutableStateFlow<VidMaxVideoPlaylist?> =
      MutableStateFlow(null)
  val openedVideoPlaylist: StateFlow<VidMaxVideoPlaylist?> = _openedVideoPlaylist.asStateFlow()

  private val _openedVideoPlaylistItems: MutableStateFlow<List<VidMaxVideoPlaylistItem>> =
      MutableStateFlow(emptyList())
  val openedVideoPlaylistItems: StateFlow<List<VidMaxVideoPlaylistItem>> =
      _openedVideoPlaylistItems.asStateFlow()

  private var openedVideoPlaylistJob: Job? = null

  // Video favorites — persisted the same way as audio favorites.
  private val _favoriteVideoPaths: MutableStateFlow<Set<String>> =
      MutableStateFlow(prefs.getStringSet("favorite_videos", emptySet()) ?: emptySet())
  val favoriteVideoPaths: StateFlow<Set<String>> = _favoriteVideoPaths.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.observeAllPlaylists().collectLatest {
        reloadVideoPlaylistsWithCounts()
      }
    }
  }

  /** Reloads playlists with fresh item counts, newest-updated first. */
  private suspend fun reloadVideoPlaylistsWithCounts() {
    val withCounts = playlistRepository.getAllPlaylists().map { playlist ->
      PlaylistWithCount(playlist, playlistRepository.getPlaylistItemCount(playlist.id))
    }.sortedByDescending { it.playlist.updatedAt }
    _videoPlaylists.value = withCounts
  }

  fun createVideoPlaylist(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.createPlaylist(name.trim())
    }
  }

  fun renameVideoPlaylist(playlistId: Int, newName: String) {
    if (newName.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.getPlaylistById(playlistId)?.let { playlist ->
        playlistRepository.updatePlaylist(playlist.copy(name = newName.trim()))
      }
    }
  }

  fun deleteVideoPlaylist(playlistId: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.getPlaylistById(playlistId)?.let { playlist ->
        playlistRepository.deletePlaylist(playlist)
      }
      if (_openedVideoPlaylist.value?.id == playlistId) closeVideoPlaylist()
    }
  }

  fun addVideoToPlaylist(playlistId: Int, video: VideoItem) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.addItemToPlaylist(playlistId, video.path, video.title)
    }
  }

  /** Creates a new playlist and adds the videos to it (mpvRex AddToPlaylist flow). */
  fun createAndAddToPlaylist(name: String, videos: List<VideoItem>) {
    if (name.isBlank() || videos.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      val playlistId = playlistRepository.createPlaylist(name).toInt()
      playlistRepository.addItemsToPlaylist(playlistId, videos.map { it.path to it.title })
    }
  }

  fun addVideosToPlaylist(playlistId: Int, videos: List<VideoItem>) {
    if (videos.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.addItemsToPlaylist(
          playlistId, videos.map { it.path to it.title })
    }
  }

  fun removeVideoFromPlaylist(item: VidMaxVideoPlaylistItem) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.removeItemFromPlaylist(item)
    }
  }

  fun clearVideoPlaylist(playlistId: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.clearPlaylist(playlistId)
    }
  }

  fun reorderVideoPlaylist(playlistId: Int, newItemOrder: List<Int>) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.reorderPlaylistItems(playlistId, newItemOrder)
    }
  }

  /** Opens a playlist and reactively observes its items until closed. */
  fun openVideoPlaylist(playlistId: Int) {
    viewModelScope.launch(Dispatchers.IO) {
      _openedVideoPlaylist.value = playlistRepository.getPlaylistById(playlistId)
    }
    openedVideoPlaylistJob?.cancel()
    openedVideoPlaylistJob = viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.observePlaylistItems(playlistId).collectLatest {
        _openedVideoPlaylistItems.value = it
      }
    }
  }

  fun closeVideoPlaylist() {
    openedVideoPlaylistJob?.cancel()
    openedVideoPlaylistJob = null
    _openedVideoPlaylist.value = null
    _openedVideoPlaylistItems.value = emptyList()
  }

  /** Records play history for a video played from a playlist (mpvRex parity). */
  fun recordVideoPlayedFromPlaylist(playlistId: Int, filePath: String, positionMs: Long = 0) {
    viewModelScope.launch(Dispatchers.IO) {
      playlistRepository.updatePlayHistory(playlistId, filePath, positionMs)
    }
  }

  fun toggleVideoFavorite(path: String) {
    val currentFavs: MutableSet<String> = _favoriteVideoPaths.value.toMutableSet()
    if (currentFavs.contains(path)) currentFavs.remove(path) else currentFavs.add(path)
    _favoriteVideoPaths.value = currentFavs
    prefs.edit().putStringSet("favorite_videos", currentFavs).apply()
  }

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
  private val _sortOrder: MutableStateFlow<SortOrder> = MutableStateFlow(
      try {
          SortOrder.valueOf(prefs.getString("video_sort_order", SortOrder.DATE.name) ?: SortOrder.DATE.name)
      } catch (e: Exception) {
          SortOrder.DATE
      }
  )
  val sortOrder: StateFlow<SortOrder> = _sortOrder
  private val _sortAscending: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("video_sort_ascending", false))
  val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()
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
  private val _localMode: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("local_mode", false))
  val localMode: StateFlow<Boolean> = _localMode.asStateFlow()
  private val _musicPlayerEnabled: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("music_player_enabled", true))
  val musicPlayerEnabled: StateFlow<Boolean> = _musicPlayerEnabled.asStateFlow()
  private val _minimalistPlayer: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("minimalist_player", false))
  val minimalistPlayer: StateFlow<Boolean> = _minimalistPlayer.asStateFlow()
  private val _pipEnabled: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("pip_enabled", true))
  val pipEnabled: StateFlow<Boolean> = _pipEnabled
  private val _showResolutionBadge: MutableStateFlow<Boolean> =
      MutableStateFlow(prefs.getBoolean("resolution_badge", true))
  val showResolutionBadge: StateFlow<Boolean> = _showResolutionBadge

  // Theme Retrieve
  private val savedThemeName: String =
      prefs.getString("app_theme", AppTheme.Default.name)
          ?: AppTheme.Default.name
  
  private val _appTheme: MutableStateFlow<AppTheme> = MutableStateFlow(
      try {
          AppTheme.valueOf(savedThemeName)
      } catch (e: IllegalArgumentException) {
          AppTheme.Default
      }
  )
  val appTheme: StateFlow<AppTheme> = _appTheme

  private val _darkMode: MutableStateFlow<DarkMode> = MutableStateFlow(
      try {
          DarkMode.valueOf(prefs.getString("dark_mode", DarkMode.System.name) ?: DarkMode.System.name)
      } catch (e: Exception) {
          DarkMode.System
      }
  )
  val darkMode: StateFlow<DarkMode> = _darkMode

  private val _amoledMode: MutableStateFlow<Boolean> = MutableStateFlow(
      prefs.getBoolean("amoled_mode", false)
  )
  val amoledMode: StateFlow<Boolean> = _amoledMode

  // --- App Font (font changer) ---
  private val _appFontId: MutableStateFlow<String> = MutableStateFlow(
      prefs.getString("app_font", AppFonts.SYSTEM_DEFAULT) ?: AppFonts.SYSTEM_DEFAULT
  )
  val appFontId: StateFlow<String> = _appFontId.asStateFlow()

  private val _importedFonts: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
  val importedFonts: StateFlow<List<String>> = _importedFonts.asStateFlow()

  init {
    // Restore previously imported fonts so they appear in Settings on startup.
    refreshImportedFonts()
  }

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

    exoPlayer =
        ExoPlayer.Builder(application).build().apply { skipSilenceEnabled = _skipSilence.value }

    // 🔥 Wire a fixed audio session at creation so the LoudnessEnhancer
    // (volume boost >100%) can always attach — Media3 reports
    // AUDIO_SESSION_ID_NOT_SET (0) until audio output initialises, which
    // silently broke the music boost before.
    try {
      val sessionId: Int = audioManager.generateAudioSessionId()
      exoPlayer?.setAudioSessionId(sessionId)
      loudnessEnhancer = LoudnessEnhancer(sessionId)
    } catch (e: Exception) {
    }

    setupExoPlayerEvents()

    val filter =
        IntentFilter().apply {
          addAction("ACTION_TOGGLE")
          addAction("ACTION_NEXT")
          addAction("ACTION_PREVIOUS")
          addAction("ACTION_STOP")
        }

    androidx.core.content.ContextCompat.registerReceiver(
        application,
        audioReceiver,
        filter,
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  private fun executePlay() {
    exoPlayer?.volume = targetExoVolume
    exoPlayer?.play()
  }

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

  /**
   * Returns the LoudnessEnhancer, creating it on a guaranteed-valid audio
   * session if needed. Returns null when no player exists yet.
   */
  private fun ensureLoudnessEnhancer(): LoudnessEnhancer? {
    if (loudnessEnhancer != null) return loudnessEnhancer
    val player = exoPlayer ?: return null
    var sessionId: Int = try {
      player.audioSessionId
    } catch (e: Exception) {
      0
    }
    if (sessionId == 0) {
      sessionId = audioManager.generateAudioSessionId()
      try {
        player.setAudioSessionId(sessionId)
      } catch (e: Exception) {
        return null
      }
    }
    return try {
      LoudnessEnhancer(sessionId).also { loudnessEnhancer = it }
    } catch (e: Exception) {
      null
    }
  }

  private fun applyCurrentVolume() {
    val isBoosted: Boolean = _musicBoostEnabled.value
    try {
      if (isBoosted) {
        targetExoVolume = 1f
        exoPlayer?.volume = 1f
        ensureLoudnessEnhancer()?.apply {
          setTargetGain(2500)
          enabled = true
        }
      } else {
        loudnessEnhancer?.enabled = false
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

      executePlay() 

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

  fun nextAudio() {
    playNextAudio(false)
  }

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
        executePause() 
      }
    }
  }

  fun toggleAudio() {
    exoPlayer?.let { player ->
      if (player.isPlaying) {
        executePause() 
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
          executePlay() 
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

  private val _libraryError: MutableStateFlow<String?> = MutableStateFlow(null)
  val libraryError: StateFlow<String?> = _libraryError.asStateFlow()

  fun clearLibraryError() {
    _libraryError.value = null
  }

  private var loadVideosJob: Job? = null
  private var refreshJob: Job? = null

  fun loadVideos() {
    if (_isLoading.value) return
    loadVideosJob?.cancel()
    loadVideosJob = viewModelScope.launch {
      _isLoading.value = true
      _libraryError.value = null
      try {
        val videos: List<VideoItem> = withContext(Dispatchers.IO) { repository.getAllVideos() }
        _allVideos.value = videos
        _folders.value = withContext(Dispatchers.Default) { repository.getFolders(videos) }
        applyFilter()
        pruneStaleRecentVideo(videos)
        val openPath = _currentFolderPath.value
        if (openPath.isNotEmpty()) {
          if (_folders.value.any { it.path == openPath }) applyFolderFilter(openPath)
          else _currentFolderPath.value = ""
        }
      } catch (e: SecurityException) {
        _libraryError.value = "Storage permission required to browse videos."
      } catch (e: Exception) {
        _libraryError.value = "Couldn't load videos. Pull to retry."
      } finally {
        _isLoading.value = false
        // P4a-fix: release a pull gesture that arrived mid-load (see refreshVideos).
        // Settling flag only; never starts scan work here.
        _isRefreshing.value = false
      }
    }
  }

  private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

  fun refreshVideos() {
    // Already showing: PullToRefreshBox disables input while refreshing, nothing to acknowledge.
    if (_isRefreshing.value) return
    if (_isLoading.value) {
      // P4a-fix: a pull during initial/permission load hit the old guard and returned
      // without ever setting _isRefreshing. PullToRefreshBox settles its indicator
      // solely off the isRefreshing true->false transition, so the indicator froze
      // mid-pull until the next touch. Acknowledge synchronously; the in-flight
      // load already reloads data (no duplicate scan), and its finally{} releases us.
      _isRefreshing.value = true
      return
    }
    // Synchronous acknowledge: PullToRefreshBox commits to the refresh on release and
    // expects isRefreshing=true in the same frame to drive its settle animation.
    _isRefreshing.value = true
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      _libraryError.value = null
      try {
        val videos: List<VideoItem> = withContext(Dispatchers.IO) { repository.getAllVideos() }
        _allVideos.value = videos
        _folders.value = withContext(Dispatchers.Default) { repository.getFolders(videos) }
        applyFilter()
        pruneStaleRecentVideo(videos)
        val openPath = _currentFolderPath.value
        if (openPath.isNotEmpty()) {
          if (_folders.value.any { it.path == openPath }) applyFolderFilter(openPath)
          else _currentFolderPath.value = ""
        }
      } catch (e: SecurityException) {
        _libraryError.value = "Storage permission required to browse videos."
      } catch (e: Exception) {
        _libraryError.value = "Refresh failed. Pull to retry."
      } finally {
        _isRefreshing.value = false
      }
    }
  }

  /** Hides last-played resume when the file is truly gone (missing from scan + File check). */
  private fun pruneStaleRecentVideo(videos: List<VideoItem>) {
    val recent = _recentVideoPath.value
    if (recent.isEmpty() || videos.isEmpty()) return
    if (videos.any { it.path == recent }) return
    try {
      if (File(recent).exists()) return
    } catch (e: Exception) {
      return
    }
    _recentVideoTitle.value = ""
    _recentVideoPath.value = ""
    try {
      prefs.edit().remove("recent_video_title").remove("recent_video_path").apply()
    } catch (e: Exception) {}
  }

  private fun migrateBookmarkKey(oldPath: String, newPath: String) {
    if (oldPath == newPath) return
    val oldKey = com.vidmax.player.ui.player.bookmarkPrefsKey(oldPath)
    val entries = prefs.getStringSet(oldKey, null) ?: return
    prefs.edit()
        .putStringSet(com.vidmax.player.ui.player.bookmarkPrefsKey(newPath), entries)
        .remove(oldKey)
        .apply()
  }

  fun renameVideo(video: VideoItem, newBaseName: String, onResult: (Result<String>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val base = newBaseName.trim()
        require(base.isNotEmpty()) { "Name cannot be empty" }
        require(base.none { it in "/\\:*?\"<>|" || it.code < 32 }) { "Name contains invalid characters" }
        require(!base.endsWith(".")) { "Name cannot end with a dot" }
        val src = File(video.path)
        require(src.exists()) { "Original file not found" }
        val ext = src.name.substringAfterLast('.', "")
        require(ext.isNotEmpty()) { "File has no extension" }
        val dst = File(src.parent, "$base.$ext")
        if (!dst.absolutePath.equals(src.absolutePath, ignoreCase = true) && dst.exists()) {
          throw IllegalStateException("A file with this name already exists")
        }
        if (!dst.absolutePath.equals(src.absolutePath, ignoreCase = false)) {
          var renamed = false
          runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
            val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, dst.name) }
            if (getApplication<Application>().contentResolver.update(uri, values, null, null) > 0) renamed = true
          }
          if (!renamed) {
            if (!src.renameTo(dst)) throw IllegalStateException("Rename failed")
            MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
          }
        }
        val newPath = dst.absolutePath
        playlistRepository.updatePathReferences(video.path, newPath, dst.nameWithoutExtension)
        migrateBookmarkKey(video.path, newPath)
        withContext(Dispatchers.Main) {
          _allVideos.value = _allVideos.value.map {
            if (it.path == video.path) it.copy(title = dst.nameWithoutExtension, path = newPath) else it
          }
          _folders.value = repository.getFolders(_allVideos.value)
          applyFilter()
          if (_currentFolderPath.value.isNotEmpty()) applyFolderFilter(_currentFolderPath.value)
          val favs = _favoriteVideoPaths.value.toMutableSet()
          if (favs.remove(video.path)) {
            favs.add(newPath)
            _favoriteVideoPaths.value = favs
            prefs.edit().putStringSet("favorite_videos", favs).apply()
          }
          if (_recentVideoPath.value == video.path) {
            setRecentlyPlayedVideo(dst.nameWithoutExtension, newPath)
          }
        }
        newPath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  private fun loadAudio() {
    viewModelScope.launch {
      try {
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
      } catch (e: Exception) {
        _allAudio.value = emptyList()
        applyAudioFilter()
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
    setSort(order, _sortAscending.value)
  }

  fun setSort(order: SortOrder, ascending: Boolean) {
    _sortOrder.value = order
    _sortAscending.value = ascending
    prefs.edit().putString("video_sort_order", order.name).putBoolean("video_sort_ascending", ascending).apply()
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
    val asc = _sortAscending.value
    return when (_sortOrder.value) {
      SortOrder.NAME -> if (asc) videos.sortedBy { it.title.lowercase() } else videos.sortedByDescending { it.title.lowercase() }
      SortOrder.DATE -> if (asc) videos.sortedBy { it.dateAdded } else videos.sortedByDescending { it.dateAdded }
      SortOrder.SIZE -> if (asc) videos.sortedBy { it.size } else videos.sortedByDescending { it.size }
      SortOrder.DURATION -> if (asc) videos.sortedBy { it.duration } else videos.sortedByDescending { it.duration }
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

  fun setLocalMode(enabled: Boolean) {
    _localMode.value = enabled
    prefs.edit().putBoolean("local_mode", enabled).apply()
  }

  fun setMusicPlayerEnabled(enabled: Boolean) {
    _musicPlayerEnabled.value = enabled
    prefs.edit().putBoolean("music_player_enabled", enabled).apply()
  }

  fun setMinimalistPlayer(enabled: Boolean) {
    _minimalistPlayer.value = enabled
    prefs.edit().putBoolean("minimalist_player", enabled).apply()
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

  fun setDarkMode(mode: DarkMode) {
    _darkMode.value = mode
    prefs.edit().putString("dark_mode", mode.name).apply()
  }

  fun setAmoledMode(enabled: Boolean) {
    _amoledMode.value = enabled
    prefs.edit().putBoolean("amoled_mode", enabled).apply()
  }

  fun setAppFont(fontId: String) {
    _appFontId.value = fontId
    prefs.edit().putString("app_font", fontId).apply()
  }

  /** Re-scans the private fonts dir and refreshes [importedFonts]. */
  fun refreshImportedFonts() {
    viewModelScope.launch(Dispatchers.IO) {
      val ids = AppFonts.importedFontFiles(getApplication())
          .map { AppFonts.CUSTOM_PREFIX + it.name }
      _importedFonts.value = ids
    }
  }

  /**
   * Copies a user-selected font (SAF uri) into app storage, auto-selects it
   * and returns the sanitized file name on success.
   */
  fun importCustomFont(uri: Uri): Result<String> {
    val result = AppFonts.importFont(getApplication(), uri)
    result.onSuccess { fileName ->
      refreshImportedFonts()
      setAppFont(AppFonts.CUSTOM_PREFIX + fileName)
    }
    return result
  }

  /** Deletes an imported font; falls back to the system font when it was active. */
  fun deleteCustomFont(fontId: String): Boolean {
    val deleted = AppFonts.deleteImportedFont(getApplication(), fontId)
    if (deleted) {
      if (appFontId.value == fontId) setAppFont(AppFonts.SYSTEM_DEFAULT)
      refreshImportedFonts()
    }
    return deleted
  }

  fun setSkipSilence(enabled: Boolean) {
    _skipSilence.value = enabled
    prefs.edit().putBoolean("skip_silence", enabled).apply()
    exoPlayer?.skipSilenceEnabled = enabled
  }

  fun setCrossfade(enabled: Boolean) {
    _crossfadeEnabled.value = enabled
    prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
  }

  fun setRecentlyPlayedVideo(title: String, path: String) {
    _recentVideoTitle.value = title
    _recentVideoPath.value = path
    prefs.edit().putString("recent_video_title", title).putString("recent_video_path", path).apply()
  }

  // 🔥 FIX: Made this function public so other classes can access it
  fun setRecentlyPlayedMusic(title: String, path: String) {
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
        val boostRatio: Float = (safeVolume - 100f) / 100f
        ensureLoudnessEnhancer()?.apply {
          setTargetGain((boostRatio * 2500).toInt())
          enabled = true
        }
      } catch (e: Exception) {}
    }
  }
}
