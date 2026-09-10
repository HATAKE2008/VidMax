package com.vidmax.player.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
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
import android.os.SystemClock
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
import com.vidmax.player.data.repository.M3uSourceStore
import com.vidmax.player.data.repository.RecentPlayStore
import com.vidmax.player.data.repository.VideoRepository
import com.vidmax.player.service.AudioService
import com.vidmax.player.ui.theme.AppFonts
import com.vidmax.player.ui.theme.AppTheme
import com.vidmax.player.utils.StorageAccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

/**
 * Thrown by [LibraryViewModel.renameVideo] when the MediaStore rename needs
 * scoped-storage user consent. The UI should fire
 * MediaStore.createWriteRequest([uris]) and retry the rename on success.
 */
class RenameConsentRequiredException(val uris: List<Uri>) :
    Exception("Storage permission needed to rename this file.")

/**
 * Thrown by [LibraryViewModel.moveVideoToFolder] when the file was copied to
 * its new home but deleting the original needs scoped-storage user consent.
 * The UI should fire MediaStore.createDeleteRequest([srcUri]) and then call
 * [LibraryViewModel.completeMoveDelete] on grant.
 */
class MoveDeleteConsentRequired(
    val srcUri: Uri,
    val video: VideoItem,
    val newPath: String,
    val newTitle: String
) : Exception("Storage permission needed to finish moving this file.")

/**
 * Thrown by [LibraryViewModel.moveVideoToFolder] when relocating the
 * MediaStore row needs scoped-storage user consent. The UI should fire
 * MediaStore.createWriteRequest for the video URI and then call
 * [LibraryViewModel.retryMoveAfterWriteConsent] on grant.
 */
class MoveWriteConsentRequired(
    val video: VideoItem,
    val destFolderPath: String,
    val fileName: String
) : Exception("Storage permission needed to move this file.")

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
      M3uSourceStore.removeId(prefs, playlistId)
      _m3uSourceIds.value = M3uSourceStore.allIds(prefs)
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

  /** Removes playlist items by file path (multi-remove for detail selection). */
  fun removePlaylistItemsByPaths(playlistId: Int, paths: Set<String>, onDone: (() -> Unit)? = null) {
    if (paths.isEmpty()) {
      onDone?.let { viewModelScope.launch(Dispatchers.Main) { it() } }
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      val items = playlistRepository.getPlaylistItems(playlistId).filter { paths.contains(it.filePath) }
      if (items.isNotEmpty()) playlistRepository.removeItemsFromPlaylist(items)
      withContext(Dispatchers.Main) { onDone?.invoke() }
    }
  }

  /**
   * Imports an M3U/M3U8 playlist from a URL into a persistent local playlist.
   * Entries keep their titles; unreachable/invalid sources and empty results
   * report clear errors without touching existing playlists.
   */
  fun importM3UPlaylist(rawUrl: String, onResult: (Result<Pair<String, Int>>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
          "Enter a valid http(s) URL"
        }
        val (text, entries) = downloadAndParseM3U(url)
        require(entries.isNotEmpty()) { "No playable entries found" }
        val name = deriveM3UPlaylistName(url, text)
        val playlistId = playlistRepository.createPlaylist(name).toInt()
        playlistRepository.addItemsToPlaylist(playlistId, entries.map { it.first to it.second })
        M3uSourceStore.setUrl(prefs, playlistId, url)
        _m3uSourceIds.value = M3uSourceStore.allIds(prefs)
        name to entries.size
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /** Downloads an M3U document and parses its entries (shared by import/refresh). */
  private fun downloadAndParseM3U(url: String): Pair<String, List<Pair<String, String>>> {
    val text = runCatching {
      val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
      connection.connectTimeout = 15000
      connection.readTimeout = 15000
      connection.setRequestProperty("User-Agent", "VidMax")
      try {
        require(connection.responseCode in 200..299) {
          "Server returned ${connection.responseCode}"
        }
        connection.inputStream.bufferedReader().use { reader ->
          val builder = StringBuilder()
          var total = 0
          while (true) {
            val line = reader.readLine() ?: break
            total += line.length
            if (total > 2_000_000) throw IllegalStateException("Playlist too large")
            builder.appendLine(line)
          }
          builder.toString()
        }
      } finally {
        connection.disconnect()
      }
    }.getOrElse { throw IllegalStateException("Could not download playlist") }
    return text to parseM3UEntries(text)
  }

  /** Parses EXTINF titles + URLs (and bare URL lines) into (url, title). */
  private fun parseM3UEntries(text: String): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    var pendingTitle: String? = null
    text.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty()) return@forEach
      if (line.startsWith("#")) {
        if (line.startsWith("#EXTINF")) {
          pendingTitle = line.substringAfter(",", "").trim().ifEmpty { null }
        }
        return@forEach
      }
      if (line.startsWith("http://") || line.startsWith("https://") || File(line).exists()) {
        val fallback = line.substringAfterLast('/').substringBeforeLast('.').ifEmpty { line }
        entries.add(line to (pendingTitle ?: fallback))
      }
      pendingTitle = null
    }
    return entries.distinctBy { it.first }
  }

  private fun deriveM3UPlaylistName(url: String, text: String): String {
    text.lineSequence()
        .firstOrNull { it.trim().startsWith("#PLAYLIST") }
        ?.substringAfter(":")?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it.take(80) }
    val last = url.substringAfterLast('/').substringBefore('?').trim()
    if (last.isNotEmpty() && last.contains('.')) {
      return last.substringBeforeLast('.').ifEmpty { url }.take(80)
    }
    return runCatching { java.net.URL(url).host }.getOrDefault("Imported playlist").take(80)
  }

  // M3U/M3U8 source registry: which playlists were imported from a URL
  // (badges + refresh-from-URL). Stored outside Room: no migration needed.
  private val _m3uSourceIds: MutableStateFlow<Set<Int>> =
      MutableStateFlow(M3uSourceStore.allIds(prefs))
  val m3uSourceIds: StateFlow<Set<Int>> = _m3uSourceIds.asStateFlow()

  /**
   * Loads a playlist's items as playable [VideoItem]s in saved order for
   * card-level Play actions.
   */
  fun openAndPlayPlaylist(playlistId: Int, onResult: (Result<List<VideoItem>>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val list = playlistRepository.getPlaylistItems(playlistId).map { item ->
          VideoItem(
              id = item.id.toLong(),
              title = item.fileName,
              path = item.filePath,
              duration = 0L,
              size = 0L,
              width = 0,
              height = 0,
              dateAdded = item.addedAt,
              folderPath = "",
              folderName = "")
        }
        require(list.isNotEmpty()) { "Playlist is empty" }
        list
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /**
   * Re-downloads a previously imported M3U playlist and replaces its items
   * (order + titles follow the source). Local playlists are unaffected.
   */
  fun refreshM3UPlaylist(playlistId: Int, onResult: (Result<Int>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val url = M3uSourceStore.getUrl(prefs, playlistId)
            ?: throw IllegalStateException("No source URL saved")
        val (text, entries) = downloadAndParseM3U(url)
        require(entries.isNotEmpty()) { "No playable entries found" }
        playlistRepository.clearPlaylist(playlistId)
        playlistRepository.addItemsToPlaylist(playlistId, entries.map { it.first to it.second })
        entries.size
      }
      withContext(Dispatchers.Main) { onResult(result) }
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

  // Recently-played history (REX model): newest-first entries surviving
  // restarts; joined against the scanned library for display.
  private val _recentHistory: MutableStateFlow<List<RecentPlayStore.RecentEntry>> =
      MutableStateFlow(RecentPlayStore.read(prefs))
  val recentHistory: StateFlow<List<RecentPlayStore.RecentEntry>> = _recentHistory.asStateFlow()

  private val _recentVideos: MutableStateFlow<List<VideoItem>> = MutableStateFlow(emptyList())
  val recentVideos: StateFlow<List<VideoItem>> = _recentVideos.asStateFlow()

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
        refreshRecentVideos()
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
        settleRefreshing()
      }
    }
  }

  private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

  /**
   * Minimum time the refresh spinner stays visible once shown.
   * PullToRefreshBox pins its indicator on release and hides it only on an
   * OBSERVED isRefreshing true->false transition. A scan finishing within the
   * same frame would otherwise leave the indicator pinned until the next
   * touch, so fast refreshes are held briefly to keep the transition
   * observable. Duplicate-scan protection (the _isRefreshing guard in
   * refreshVideos) is unchanged.
   */
  private val minRefreshVisibleMs = 600L
  private var refreshShownAtMs = 0L

  private suspend fun settleRefreshing() {
    val remaining = minRefreshVisibleMs - (SystemClock.uptimeMillis() - refreshShownAtMs)
    if (remaining > 0) {
      withContext(NonCancellable) { delay(remaining) }
    }
    _isRefreshing.value = false
  }

  fun refreshVideos() {
    // Already showing: PullToRefreshBox disables input while refreshing, nothing to acknowledge.
    if (_isRefreshing.value) return
    if (_isLoading.value) {
      // P4a-fix: a pull during initial/permission load hit the old guard and returned
      // without ever setting _isRefreshing. PullToRefreshBox settles its indicator
      // solely off the isRefreshing true->false transition, so the indicator froze
      // mid-pull until the next touch. Acknowledge synchronously; the in-flight
      // load already reloads data (no duplicate scan), and its finally{} releases us.
      refreshShownAtMs = SystemClock.uptimeMillis()
      _isRefreshing.value = true
      return
    }
    // Synchronous acknowledge: PullToRefreshBox commits to the refresh on release and
    // expects isRefreshing=true in the same frame to drive its settle animation.
    refreshShownAtMs = SystemClock.uptimeMillis()
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
        refreshRecentVideos()
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
        settleRefreshing()
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
          if (hasFullStorageAccess()) {
            // All-files access: direct filesystem rename, no consent dialogs.
            // Source is removed only after the destination is verified.
            require(directMoveFile(src, dst)) { "Rename failed" }
            moveSidecars(src, dst)
            syncMediaStoreAfterDirectMove(video.path, dst.absolutePath)
            val newPath = dst.absolutePath
            applyPathChange(video, newPath, dst.nameWithoutExtension)
            return@runCatching newPath
          }
          var renamed = false
          var consentUris: List<Uri>? = null
          runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
            val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, dst.name) }
            if (getApplication<Application>().contentResolver.update(uri, values, null, null) > 0) renamed = true
          }.onFailure { e ->
            // Scoped storage: renaming media we don't own needs user consent via
            // MediaStore.createWriteRequest. Surfacing it lets the UI request
            // consent and retry; falling through to File.renameTo would always fail.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                e is RecoverableSecurityException) {
              consentUris = listOf(
                  ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id))
            }
          }
          if (!renamed) {
            consentUris?.let { throw RenameConsentRequiredException(it) }
            if (!src.renameTo(dst)) throw IllegalStateException("Rename failed")
            MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
          }
        }
        val newPath = dst.absolutePath
        applyPathChange(video, newPath, dst.nameWithoutExtension)
        newPath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /**
   * Shared path-change finalization for rename/move: playlist, bookmark,
   * favorite and last-played references plus library refresh. Runs the state
   * updates on Main; call from any dispatcher.
   */
  suspend fun applyPathChange(video: VideoItem, newPath: String, newTitle: String) {
    playlistRepository.updatePathReferences(video.path, newPath, newTitle)
    migrateBookmarkKey(video.path, newPath)
    // REX onVideoRenamed: recent history follows the file.
    RecentPlayStore.migratePath(prefs, video.path, newPath, newTitle)
    _recentHistory.value = RecentPlayStore.read(prefs)
    withContext(Dispatchers.Main) {
      _allVideos.value = _allVideos.value.mapNotNull {
        if (it.path == video.path) it.copy(title = newTitle, path = newPath)
        else if (it.id == video.id) null
        else it
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
        setRecentlyPlayedVideo(newTitle, newPath)
      }
      refreshRecentVideos()
    }
  }

  private fun videoMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "mpeg", "mpg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "ts" -> "video/mp2t"
      else -> "video/*"
    }
  }

  private fun resolveMoveDestination(destDir: File, fileName: String): File {
    var candidate = File(destDir, fileName)
    if (!candidate.exists()) return candidate
    val base = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "")
    var index = 1
    while (candidate.exists() && index < 1000) {
      val name = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
      candidate = File(destDir, name)
      index++
    }
    return candidate
  }

  fun moveVideoToFolder(video: VideoItem, destFolderPath: String, onResult: (Result<String>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val src = File(video.path)
        require(src.exists()) { "Original file not found" }
        val destDir = File(destFolderPath)
        require(destDir.isDirectory) { "Destination folder not found" }
        require(!destDir.absolutePath.equals(src.parent, ignoreCase = false)) {
          "Already in this folder"
        }
        val dst = resolveMoveDestination(destDir, src.name)
        if (hasFullStorageAccess()) {
          // All-files access: true filesystem move with strict ordering.
          // SOURCE -> verify -> move atomically -> verify destination ->
          // only then drop the source MediaStore row -> refresh library.
          // Never triggers the system delete-consent dialog.
          val srcLen = src.length()
          require(directMoveFile(src, dst)) { "Move failed" }
          require(dst.exists() && (srcLen <= 0L || dst.length() == srcLen)) {
            "Move failed: destination not verified"
          }
          moveSidecars(src, dst)
          syncMediaStoreAfterDirectMove(video.path, dst.absolutePath)
          val newPath = dst.absolutePath
          applyPathChange(video, newPath, dst.nameWithoutExtension)
          return@runCatching newPath
        }
        if (runCatching { src.renameTo(dst) }.getOrDefault(false)) {
          MediaScannerConnection.scanFile(
              getApplication(), arrayOf(dst.absolutePath, src.absolutePath), null, null)
          runCatching {
            val staleUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
            getApplication<Application>().contentResolver.delete(staleUri, null, null)
          }
          val newPath = dst.absolutePath
          applyPathChange(video, newPath, dst.nameWithoutExtension)
          return@runCatching newPath
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
          throw IllegalStateException("Move failed")
        }
        val resolver = getApplication<Application>().contentResolver
        val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        require(destDir.absolutePath.startsWith(externalRoot)) { "Cannot move there" }
        val relativePath = destDir.absolutePath.removePrefix(externalRoot).trim('/') + "/"
        val srcUri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
        val moveValues = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, dst.name)
          put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
        }
        val moveOutcome = runCatching { resolver.update(srcUri, moveValues, null, null) }
        val moveError = moveOutcome.exceptionOrNull()
        if (moveError is RecoverableSecurityException) {
          throw MoveWriteConsentRequired(video, destFolderPath, src.name)
        }
        if (moveOutcome.getOrDefault(0) > 0) {
          val newPath = dst.absolutePath
          applyPathChange(video, newPath, dst.nameWithoutExtension)
          return@runCatching newPath
        }
        val pendingValues = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, dst.name)
          put(MediaStore.Video.Media.MIME_TYPE, videoMimeType(dst.name))
          put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val newUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pendingValues)
            ?: throw IllegalStateException("Move failed")
        try {
          resolver.openInputStream(srcUri)?.use { input ->
            resolver.openOutputStream(newUri)?.use { output -> input.copyTo(output) }
                ?: throw IllegalStateException("Move failed")
          } ?: throw IllegalStateException("Move failed")
          ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }.let { done ->
            resolver.update(newUri, done, null, null)
          }
        } catch (e: Exception) {
          runCatching { resolver.delete(newUri, null, null) }
          throw e
        }
        val deleteOutcome = runCatching { resolver.delete(srcUri, null, null) }
        val deleteError = deleteOutcome.exceptionOrNull()
        if (deleteError is RecoverableSecurityException) {
          throw MoveDeleteConsentRequired(
              srcUri, video, dst.absolutePath, dst.nameWithoutExtension)
        }
        if (deleteOutcome.getOrDefault(0) <= 0) {
          runCatching { resolver.delete(newUri, null, null) }
          throw IllegalStateException("Move failed")
        }
        MediaScannerConnection.scanFile(getApplication(), arrayOf(dst.absolutePath), null, null)
        val newPath = dst.absolutePath
        applyPathChange(video, newPath, dst.nameWithoutExtension)
        newPath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  fun retryMoveAfterWriteConsent(pending: MoveWriteConsentRequired, onResult: (Result<String>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val src = File(pending.video.path)
        require(src.exists()) { "Original file not found" }
        val destDir = File(pending.destFolderPath)
        require(destDir.isDirectory) { "Destination folder not found" }
        val dst = resolveMoveDestination(destDir, pending.fileName)
        val resolver = getApplication<Application>().contentResolver
        val externalRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
        require(destDir.absolutePath.startsWith(externalRoot)) { "Cannot move there" }
        val relativePath = destDir.absolutePath.removePrefix(externalRoot).trim('/') + "/"
        val srcUri = ContentUris.withAppendedId(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, pending.video.id)
        val moveValues = ContentValues().apply {
          put(MediaStore.Video.Media.DISPLAY_NAME, dst.name)
          put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
        }
        val moved = try {
          resolver.update(srcUri, moveValues, null, null) > 0
        } catch (e: RecoverableSecurityException) {
          throw IllegalStateException("Move not permitted")
        }
        require(moved) { "Move failed" }
        val newPath = dst.absolutePath
        applyPathChange(pending.video, newPath, dst.nameWithoutExtension)
        newPath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  fun completeMoveDelete(pending: MoveDeleteConsentRequired, onResult: (Result<String>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        getApplication<Application>().contentResolver.delete(pending.srcUri, null, null)
        MediaScannerConnection.scanFile(
            getApplication(), arrayOf(pending.newPath), null, null)
        applyPathChange(pending.video, pending.newPath, pending.newTitle)
        pending.newPath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  // ── Full storage access (MANAGE_EXTERNAL_STORAGE) ─────────────────────
  //
  // Root cause of the old "Move to folder" bug: without all-files access,
  // every MediaStore delete/update on a file VidMax doesn't own throws
  // RecoverableSecurityException, so the system showed
  // "Allow VidMax to delete this video?" AFTER the destination copy already
  // existed. Denying/cancelling left BOTH files behind (duplicate), and the
  // in-memory-only applyPathChange could not reconcile the two MediaStore
  // rows. The branches below bypass that path entirely when the user grants
  // All files access: one verified filesystem move, then MediaStore sync.

  /** True when VidMax holds All files access (R+) or legacy write grant. */
  fun hasFullStorageAccess(): Boolean {
    return StorageAccess.hasFullStorageAccess(getApplication())
  }

  /**
   * Moves [src] to [dst], preferring an atomic same-volume rename.
   * Cross-volume fallback is copy -> VERIFY -> delete source. The source is
   * never removed unless the destination exists and matches the source size.
   * Returns true only when the destination is verified and the source is gone.
   */
  private fun directMoveFile(src: File, dst: File): Boolean {
    if (src.renameTo(dst)) return dst.exists()
    return runCatching {
      require(src.exists()) { "Original file not found" }
      dst.parentFile?.mkdirs()
      val srcLen = src.length()
      src.inputStream().use { input ->
        dst.outputStream().use { output -> input.copyTo(output) }
      }
      if (!dst.exists()) return false
      if (srcLen > 0 && dst.length() != srcLen) {
        runCatching { dst.delete() }
        return false
      }
      if (!src.delete()) {
        // Source not removed: roll the copy back so no duplicate remains.
        runCatching { dst.delete() }
        return false
      }
      true
    }.getOrDefault(false)
  }

  private val sidecarExtensions = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "lrc")

  /**
   * Moves subtitle/sidecar files sitting next to [src] along with [dst]
   * (e.g. movie.srt follows movie.mp4 on rename AND on folder move).
   * Best effort: never fails the main operation.
   */
  private fun moveSidecars(src: File, dst: File) {
    runCatching {
      val parent = src.parentFile ?: return
      val destDir = dst.parentFile ?: return
      val srcBase = src.nameWithoutExtension
      val dstBase = dst.nameWithoutExtension
      parent.listFiles()?.forEach { sibling ->
        if (!sibling.isFile || sibling == src) return@forEach
        if (sibling.nameWithoutExtension != srcBase) return@forEach
        if (sibling.extension.lowercase() !in sidecarExtensions) return@forEach
        val target = File(destDir, "$dstBase.${sibling.extension}")
        if (target.exists()) return@forEach
        if (!sibling.renameTo(target)) {
          runCatching {
            val len = sibling.length()
            sibling.inputStream().use { input ->
              target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.exists() && target.length() == len) sibling.delete()
            else runCatching { target.delete() }
          }
        }
      }
    }
  }

  /** Best-effort sidecar cleanup after a delete. Never throws. */
  private fun deleteSidecars(file: File) {
    runCatching {
      val parent = file.parentFile ?: return
      val base = file.nameWithoutExtension
      parent.listFiles()?.forEach { sibling ->
        if (!sibling.isFile) return@forEach
        if (sibling.nameWithoutExtension != base) return@forEach
        if (sibling.extension.lowercase() !in sidecarExtensions) return@forEach
        runCatching { sibling.delete() }
      }
    }
  }

  /**
   * Reconciles MediaStore after a verified direct-filesystem move/rename.
   *
   * SAFETY: the stale row is addressed strictly BY OLD PATH and ONLY while
   * no file exists there anymore. Addressing by MediaStore id is banned
   * here: every scan issues a NEW row id, so an in-memory id can be stale
   * (repeat moves, refresh races) and could hit the wrong row — deleting a
   * live file. With the missing-file guard, deleting a live file through
   * this path is impossible by construction.
   *
   * After the scan completes, the library is re-queried so the UI converges
   * to MediaStore truth (fresh row ids, real metadata). Until then the
   * immediate [applyPathChange] keeps the moved video visible, so it never
   * looks "deleted from the phone".
   */
  private fun syncMediaStoreAfterDirectMove(oldPath: String, newPath: String) {
    val app = getApplication<Application>()
    runCatching {
      if (!File(oldPath).exists()) {
        app.contentResolver.delete(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Video.Media.DATA} = ?",
            arrayOf(oldPath)
        )
      }
    }
    runCatching {
      MediaScannerConnection.scanFile(app, arrayOf(newPath), null) { _, _ ->
        refreshVideos()
      }
    }
  }

  /** Drops [paths] from the in-memory library, favorites, recents and playlists. */
  private suspend fun removePathsFromLibrary(paths: Set<String>) {
    if (paths.isEmpty()) return
    runCatching {
      paths.forEach { playlistRepository.removeItemsByPath(it) }
    }
    // REX onVideoDeleted: recent history drops deleted files.
    RecentPlayStore.removePaths(prefs, paths)
    _recentHistory.value = RecentPlayStore.read(prefs)
    withContext(Dispatchers.Main) {
      _allVideos.value = _allVideos.value.filterNot { paths.contains(it.path) }
      _folders.value = repository.getFolders(_allVideos.value)
      applyFilter()
      if (_currentFolderPath.value.isNotEmpty()) applyFolderFilter(_currentFolderPath.value)
      val favs = _favoriteVideoPaths.value.toMutableSet()
      if (favs.removeAll(paths)) {
        _favoriteVideoPaths.value = favs
        prefs.edit().putStringSet("favorite_videos", favs).apply()
      }
      if (paths.contains(_recentVideoPath.value)) {
        _recentVideoTitle.value = ""
        _recentVideoPath.value = ""
        try {
          prefs.edit().remove("recent_video_title").remove("recent_video_path").apply()
        } catch (e: Exception) {}
      }
      refreshRecentVideos()
    }
  }

  /**
   * Deletes a video with full storage access: direct filesystem + MediaStore
   * removal, no system delete-consent dialog. Falls back to a plain attempt
   * (callers may still route RecoverableSecurityException to the consent UI)
   * when all-files access is missing.
   */
  fun deleteVideo(video: VideoItem, onResult: (Result<Unit>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val file = File(video.path)
        if (file.exists() && !file.delete()) {
          // With all-files access this direct delete succeeds; without it
          // the MediaStore row delete below surfaces consent to the caller.
          val uri = ContentUris.withAppendedId(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
          val rows = getApplication<Application>().contentResolver.delete(uri, null, null)
          if (rows <= 0) throw IllegalStateException("Delete failed")
          removePathsFromLibrary(setOf(video.path))
          return@runCatching Unit
        }
        runCatching {
          val uri = ContentUris.withAppendedId(
              MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
          getApplication<Application>().contentResolver.delete(uri, null, null)
        }
        deleteSidecars(file)
        MediaScannerConnection.scanFile(getApplication(), arrayOf(video.path), null, null)
        removePathsFromLibrary(setOf(video.path))
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /** Batch delete used by multi-select; reports how many items were removed. */
  fun deleteVideos(videos: List<VideoItem>, onResult: (Result<Int>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val removedPaths = mutableSetOf<String>()
      val result = runCatching {
        var removed = 0
        val app = getApplication<Application>()
        videos.forEach { video ->
          val ok = runCatching {
            val file = File(video.path)
            if (file.exists() && !file.delete()) {
              val uri = ContentUris.withAppendedId(
                  MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
              require(app.contentResolver.delete(uri, null, null) > 0) { "Delete failed" }
            } else {
              runCatching {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
                app.contentResolver.delete(uri, null, null)
              }
              deleteSidecars(file)
              MediaScannerConnection.scanFile(app, arrayOf(video.path), null, null)
            }
            removedPaths.add(video.path)
            removed++
            true
          }.getOrDefault(false)
          if (!ok) throw IllegalStateException("Could not delete all selected videos")
        }
        removed
      }
      // Always purge whatever was actually removed, even on partial failure,
      // so the library never shows stale entries.
      removePathsFromLibrary(removedPaths)
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /** Creates a folder under [parentPath]. Requires all-files access on R+. */
  fun createFolder(parentPath: String, name: String, onResult: (Result<String>) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        val base = name.trim()
        require(base.isNotEmpty()) { "Name cannot be empty" }
        require(base.none { it in "/\\:*?\"<>|" || it.code < 32 }) {
          "Name contains invalid characters"
        }
        val parent = File(parentPath)
        require(parent.isDirectory || parent.mkdirs()) { "Parent folder not found" }
        if (!hasFullStorageAccess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          throw IllegalStateException("Full storage access required to create folders")
        }
        val dir = File(parent, base)
        require(!dir.exists()) { "A folder with this name already exists" }
        require(dir.mkdirs() && dir.isDirectory) { "Could not create folder" }
        MediaScannerConnection.scanFile(getApplication(), arrayOf(dir.absolutePath), null, null)
        dir.absolutePath
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  // ── Batch copy / move (REX-style selection operations) ────────────────
  //
  // Adapted from REX Player's CopyPasteOps (performCopyOperation /
  // performMoveOperation): validate inputs -> prepare destination -> filter
  // valid sources -> disk-space check -> per-file op with verification ->
  // single media scan + library refresh. Unlike REX's generic engine, the
  // VidMax variants reuse this ViewModel's own MediaStore sync
  // (missing-file-guarded row delete by path), sidecar handling and
  // applyPathChange, so playlists / favorites / bookmarks / last-played stay
  // consistent and no duplicate or ghost entries appear.

  /** Shared result for batch copy/move: verified new paths + skipped count. */
  data class BatchFileResult(val newPaths: List<String>, val skipped: Int)

  private fun prepareBatchDestination(destFolderPath: String): File {
    val destDir = File(destFolderPath)
    require(destDir.isDirectory || destDir.mkdirs()) { "Destination folder not found" }
    require(destDir.canWrite()) { "Destination is not writable" }
    return destDir
  }

  private fun requireBatchFullAccess(action: String) {
    if (!hasFullStorageAccess()) {
      throw IllegalStateException(
          "Full storage access required to $action. Enable All Files Access in Settings.")
    }
  }

  private fun hasEnoughDiskSpace(directory: File, requiredBytes: Long): Boolean {
    return runCatching {
      val stat = android.os.StatFs(directory.absolutePath)
      stat.availableBlocksLong * stat.blockSizeLong >= requiredBytes
    }.getOrDefault(true)
  }

  /**
   * Copies [videos] into [destFolderPath]. The source is never touched.
   * Each copy gets a unique name, is size-verified, and the batch ends with
   * a single media scan + library refresh.
   */
  fun copyVideosToFolder(
      videos: List<VideoItem>,
      destFolderPath: String,
      onResult: (Result<BatchFileResult>) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        require(videos.isNotEmpty()) { "No files to copy" }
        requireBatchFullAccess("copy files")
        val destDir = prepareBatchDestination(destFolderPath)
        val valid = videos.filter {
          File(it.path).exists() && File(it.path).parent != destDir.absolutePath
        }
        val skipped = videos.size - valid.size
        require(valid.isNotEmpty()) { "No valid files to copy" }
        val totalBytes = valid.sumOf { File(it.path).length() }
        require(hasEnoughDiskSpace(destDir, totalBytes)) { "Not enough disk space" }
        val newPaths = mutableListOf<String>()
        valid.forEach { video ->
          val src = File(video.path)
          val dst = resolveMoveDestination(destDir, src.name)
          copyFileVerified(src, dst)
          newPaths.add(dst.absolutePath)
        }
        MediaScannerConnection.scanFile(
            getApplication(), newPaths.toTypedArray(), null) { _, _ -> refreshVideos() }
        BatchFileResult(newPaths, skipped)
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /**
   * Moves [videos] into [destFolderPath] with strict per-file verification
   * (destination must exist and match the source size before the source is
   * dropped). Stale MediaStore rows are removed by old path under the
   * missing-file guard, so a live file can never be deleted and no
   * duplicates appear. Finishes with a single scan + library refresh.
   */
  fun moveVideosToFolder(
      videos: List<VideoItem>,
      destFolderPath: String,
      onResult: (Result<BatchFileResult>) -> Unit
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val result = runCatching {
        require(videos.isNotEmpty()) { "No files to move" }
        requireBatchFullAccess("move files")
        val destDir = prepareBatchDestination(destFolderPath)
        var skipped = 0
        val newPaths = mutableListOf<String>()
        val pathChanges = mutableListOf<Triple<VideoItem, String, String>>()
        videos.forEach { video ->
          val src = File(video.path)
          if (!src.exists()) {
            skipped++
            return@forEach
          }
          if (src.parent == destDir.absolutePath) {
            skipped++
            return@forEach
          }
          val dst = resolveMoveDestination(destDir, src.name)
          val srcLen = src.length()
          require(directMoveFile(src, dst)) { "Move failed: ${src.name}" }
          require(dst.exists() && (srcLen <= 0L || dst.length() == srcLen)) {
            "Move failed: destination not verified"
          }
          moveSidecars(src, dst)
          newPaths.add(dst.absolutePath)
          pathChanges.add(Triple(video, dst.absolutePath, dst.nameWithoutExtension))
        }
        require(newPaths.isNotEmpty()) { "Nothing to move" }
        // Guarded row cleanup per old path (missing-file only, never by id).
        val app = getApplication<Application>()
        pathChanges.forEach { (video, _, _) ->
          runCatching {
            if (!File(video.path).exists()) {
              app.contentResolver.delete(
                  MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                  "${MediaStore.Video.Media.DATA} = ?",
                  arrayOf(video.path))
            }
          }
        }
        pathChanges.forEach { (video, newPath, newTitle) ->
          applyPathChange(video, newPath, newTitle)
        }
        MediaScannerConnection.scanFile(
            app, newPaths.toTypedArray(), null) { _, _ -> refreshVideos() }
        BatchFileResult(newPaths, skipped)
      }
      withContext(Dispatchers.Main) { onResult(result) }
    }
  }

  /**
   * Stream copy with size verification. Cleans up partial copies on error,
   * so a failed copy never leaves a ghost file behind.
   */
  private fun copyFileVerified(src: File, dst: File) {
    require(src.exists()) { "Original file not found: ${src.name}" }
    dst.parentFile?.mkdirs()
    val srcLen = src.length()
    try {
      src.inputStream().use { input ->
        dst.outputStream().use { output -> input.copyTo(output) }
      }
      dst.setLastModified(src.lastModified())
    } catch (e: Exception) {
      runCatching { dst.delete() }
      throw e
    }
    if (!dst.exists() || (srcLen > 0 && dst.length() != srcLen)) {
      runCatching { dst.delete() }
      throw IllegalStateException("Copy verification failed: ${src.name}")
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

  /**
   * Stateless library search over the already-indexed in-memory data (no
   * rescan). Used by the dedicated SearchScreen; the Home/Music inline
   * filters keep working through setSearchQuery/setAudioSearchQuery.
   *
   * Matching is token-based over title + file name (+ folder name for
   * videos) with separators normalized, so every indexed file whose
   * name contains the query is returned regardless of MediaStore TITLE
   * quirks (missing extension, different casing, underscores/dashes).
   */
  fun searchVideos(query: String): List<VideoItem> {
    val tokens: List<String> = tokenizeQuery(query)
    if (tokens.isEmpty()) return emptyList()
    return sortVideos(_allVideos.value.filter { matchesVideo(it, tokens) })
  }

  fun searchAudio(query: String): List<AudioItem> {
    val tokens: List<String> = tokenizeQuery(query)
    if (tokens.isEmpty()) return emptyList()
    return _allAudio.value.filter { matchesAudio(it, tokens) }
  }

  fun searchFolderVideos(query: String, folderPath: String): List<VideoItem> {
    val tokens: List<String> = tokenizeQuery(query)
    if (folderPath.isEmpty()) return searchVideos(query)
    val base: List<VideoItem> = _allVideos.value.filter { it.folderPath == folderPath }
    if (tokens.isEmpty()) return sortVideos(base)
    return sortVideos(base.filter { matchesVideo(it, tokens) })
  }

  private fun tokenizeQuery(query: String): List<String> {
    return normalizeForSearch(query).split(" ").filter { it.isNotEmpty() }
  }

  private fun normalizeForSearch(raw: String): String {
    return raw.lowercase().map { c -> if (c.isLetterOrDigit()) c else ' ' }.joinToString("")
  }

  private fun videoHaystack(video: VideoItem): String {
    val fileName: String = video.path.substringAfterLast('/').substringBeforeLast('.')
    return normalizeForSearch("${video.title} $fileName ${video.folderName}")
  }

  private fun matchesVideo(video: VideoItem, tokens: List<String>): Boolean {
    val haystack: String = videoHaystack(video)
    return tokens.all { haystack.contains(it) }
  }

  private fun matchesAudio(audio: AudioItem, tokens: List<String>): Boolean {
    val fileName: String = audio.path.substringAfterLast('/').substringBeforeLast('.')
    val haystack: String = normalizeForSearch("${audio.title} ${audio.artist} $fileName")
    return tokens.all { haystack.contains(it) }
  }

  // --- Search history (dedicated SearchScreen; plain strings only) ---
  companion object {
    const val SEARCH_HISTORY_KEY: String = "search_history"
    const val SEARCH_HISTORY_MAX: Int = 20
  }

  private val _searchHistory: MutableStateFlow<List<String>> =
      MutableStateFlow(loadSearchHistory())
  val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

  private fun loadSearchHistory(): List<String> {
    return runCatching {
      val raw = prefs.getString(SEARCH_HISTORY_KEY, null) ?: return emptyList()
      val arr = org.json.JSONArray(raw)
      List(arr.length()) { i -> arr.optString(i, "") }
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .take(SEARCH_HISTORY_MAX)
    }.getOrDefault(emptyList())
  }

  private fun persistSearchHistory(history: List<String>) {
    runCatching {
      prefs.edit()
          .putString(SEARCH_HISTORY_KEY, org.json.JSONArray(history).toString())
          .apply()
    }
  }

  /** Saves a query: trims, ignores blanks, dedupes, most-recent first (max 20). */
  fun addSearchHistory(rawQuery: String) {
    val query = rawQuery.trim()
    if (query.isEmpty()) return
    val updated = (listOf(query) + _searchHistory.value.filter { it != query })
        .take(SEARCH_HISTORY_MAX)
    if (updated == _searchHistory.value) return
    _searchHistory.value = updated
    persistSearchHistory(updated)
  }

  fun removeSearchHistoryEntry(query: String) {
    val updated = _searchHistory.value.filter { it != query }
    if (updated == _searchHistory.value) return
    _searchHistory.value = updated
    persistSearchHistory(updated)
  }

  fun clearSearchHistory() {
    if (_searchHistory.value.isEmpty()) return
    _searchHistory.value = emptyList()
    persistSearchHistory(emptyList())
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
    val tokens: List<String> = tokenizeQuery(_searchQuery.value)
    val base: List<VideoItem> =
        if (tokens.isEmpty()) _allVideos.value
        else _allVideos.value.filter { matchesVideo(it, tokens) }
    _filteredVideos.value = sortVideos(base)
  }

  private fun applyAudioFilter() {
    val tokens: List<String> = tokenizeQuery(_audioSearchQuery.value)
    val base: List<AudioItem> =
        if (tokens.isEmpty()) _allAudio.value
        else _allAudio.value.filter { matchesAudio(it, tokens) }
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

  /**
   * P4b — re-reads every managed setting from `vidmax_settings` into the
   * reactive StateFlows after a Settings Import. Parsing mirrors the init
   * defaults above; unknown enum names fall back to the same defaults.
   * Excluded data (favorites, recents, bookmarks, resume positions) is
   * untouched — both here and by the import itself.
   */
  fun reloadSettingsFromDisk() {
    try {
      setSort(
          try {
            SortOrder.valueOf(
                prefs.getString("video_sort_order", SortOrder.DATE.name) ?: SortOrder.DATE.name)
          } catch (e: Exception) {
            SortOrder.DATE
          },
          prefs.getBoolean("video_sort_ascending", false))
    } catch (e: Exception) {}
    try {
      setPlayerEngine(
          try {
            PlayerEngine.valueOf(
                prefs.getString("player_engine", PlayerEngine.EXO.name) ?: PlayerEngine.EXO.name)
          } catch (e: Exception) {
            PlayerEngine.EXO
          })
    } catch (e: Exception) {}
    setAudioBoost(prefs.getBoolean("audio_boost", false))
    setResumePlayback(prefs.getBoolean("resume_playback", true))
    try {
      setDecoderMode(
          try {
            DecoderMode.valueOf(
                prefs.getString("video_decoder", DecoderMode.AUTO.name) ?: DecoderMode.AUTO.name)
          } catch (e: Exception) {
            DecoderMode.AUTO
          })
    } catch (e: Exception) {}
    setAutoRotate(prefs.getBoolean("auto_rotate", true))
    setLocalMode(prefs.getBoolean("local_mode", false))
    setMusicPlayerEnabled(prefs.getBoolean("music_player_enabled", true))
    setMinimalistPlayer(prefs.getBoolean("minimalist_player", false))
    setPipEnabled(prefs.getBoolean("pip_enabled", true))
    setShowResolutionBadge(prefs.getBoolean("resolution_badge", true))
    try {
      setAppTheme(
          try {
            AppTheme.valueOf(
                prefs.getString("app_theme", AppTheme.Default.name) ?: AppTheme.Default.name)
          } catch (e: IllegalArgumentException) {
            AppTheme.Default
          })
    } catch (e: Exception) {}
    try {
      setDarkMode(
          try {
            DarkMode.valueOf(
                prefs.getString("dark_mode", DarkMode.System.name) ?: DarkMode.System.name)
          } catch (e: Exception) {
            DarkMode.System
          })
    } catch (e: Exception) {}
    setAmoledMode(prefs.getBoolean("amoled_mode", false))
    setAppFont(prefs.getString("app_font", AppFonts.SYSTEM_DEFAULT) ?: AppFonts.SYSTEM_DEFAULT)
    setSkipSilence(prefs.getBoolean("skip_silence", false))
    setCrossfade(prefs.getBoolean("crossfade_enabled", true))
  }

  fun setRecentlyPlayedVideo(title: String, path: String) {
    _recentVideoTitle.value = title
    _recentVideoPath.value = path
    prefs.edit().putString("recent_video_title", title).putString("recent_video_path", path).apply()
    // REX recordPlaybackStart: every playback start bumps the entry on top.
    RecentPlayStore.record(prefs, path, title)
    _recentHistory.value = RecentPlayStore.read(prefs)
    refreshRecentVideos()
  }

  /**
   * Rebuilds the display list newest-first, dropping entries whose files
   * are gone (REX auto-remove) and anything missing from the scan.
   */
  private fun refreshRecentVideos() {
    val byPath = _allVideos.value.associateBy { it.path }
    if (RecentPlayStore.pruneMissing(prefs)) {
      _recentHistory.value = RecentPlayStore.read(prefs)
    }
    _recentVideos.value = _recentHistory.value.mapNotNull { byPath[it.path] }
  }

  fun clearRecentHistory() {
    RecentPlayStore.clear(prefs)
    _recentHistory.value = emptyList()
    _recentVideos.value = emptyList()
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
