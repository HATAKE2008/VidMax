package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import com.vidmax.player.utils.SubtitleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LoopMode {
  NONE,
  ONE,
  ALL
}

enum class AspectRatioMode {
  FIT,
  FILL,
  STRETCH
}

// 🔥 Updated: Dual Engine tracking Enum with MPV instead of VLC
enum class PlayerEngine {
  EXO, // Google's Super Smooth Engine
  MPV  // Advanced Hardware Decoding Engine (previously was VLC)
}

// Magic: New data class to track multiple subtitles
data class SubtitleTrack(val name: String, val subtitles: List<SubtitleItem>)

class PlayerViewModel : ViewModel() {

  // 🔥 New: State for currently active engine
  private val _currentEngine: MutableStateFlow<PlayerEngine> = MutableStateFlow(PlayerEngine.EXO)
  val currentEngine: StateFlow<PlayerEngine> = _currentEngine

  private val _isPlaying: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying

  private val _currentPosition: MutableStateFlow<Long> = MutableStateFlow(0L)
  val currentPosition: StateFlow<Long> = _currentPosition

  private val _duration: MutableStateFlow<Long> = MutableStateFlow(0L)
  val duration: StateFlow<Long> = _duration

  private val _playbackSpeed: MutableStateFlow<Float> = MutableStateFlow(1.0f)
  val playbackSpeed: StateFlow<Float> = _playbackSpeed

  private val _isLocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isLocked: StateFlow<Boolean> = _isLocked

  private val _controlsVisible: MutableStateFlow<Boolean> = MutableStateFlow(true)
  val controlsVisible: StateFlow<Boolean> = _controlsVisible

  private val _loopMode: MutableStateFlow<LoopMode> = MutableStateFlow(LoopMode.NONE)
  val loopMode: StateFlow<LoopMode> = _loopMode

  private val _aspectRatio: MutableStateFlow<AspectRatioMode> =
      MutableStateFlow(AspectRatioMode.FIT)
  val aspectRatio: StateFlow<AspectRatioMode> = _aspectRatio

  private val _videoTitle: MutableStateFlow<String> = MutableStateFlow("")
  val videoTitle: StateFlow<String> = _videoTitle

  private val _currentVideoIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  val currentVideoIndex: StateFlow<Int> = _currentVideoIndex

  private val _totalVideos: MutableStateFlow<Int> = MutableStateFlow(0)
  val totalVideos: StateFlow<Int> = _totalVideos

  // --- Subtitle States (Updated for Multi-Track) ---
  private val _subtitleTracks: MutableStateFlow<List<SubtitleTrack>> = MutableStateFlow(emptyList())
  val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks

  // -1 means subtitles are turned off
  private val _selectedTrackIndex: MutableStateFlow<Int> = MutableStateFlow(-1)
  val selectedTrackIndex: StateFlow<Int> = _selectedTrackIndex

  private val _currentSubtitleText: MutableStateFlow<String> = MutableStateFlow("")
  val currentSubtitleText: StateFlow<String> = _currentSubtitleText

  private val _subtitleSize: MutableStateFlow<Float> = MutableStateFlow(16f)
  val subtitleSize: StateFlow<Float> = _subtitleSize

  private val _subtitleOffsetY: MutableStateFlow<Float> = MutableStateFlow(0f)
  val subtitleOffsetY: StateFlow<Float> = _subtitleOffsetY

  // --- Gesture Indicator States ---
  private val _isGestureOverlayVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val isGestureOverlayVisible: StateFlow<Boolean> = _isGestureOverlayVisible

  private val _gestureIndicatorType: MutableStateFlow<Int> = MutableStateFlow(0)
  val gestureIndicatorType: StateFlow<Int> = _gestureIndicatorType

  private val _gestureIndicatorValue: MutableStateFlow<Float> = MutableStateFlow(0f)
  val gestureIndicatorValue: StateFlow<Float> = _gestureIndicatorValue

  private val _currentVolumePercent: MutableStateFlow<Float> = MutableStateFlow(0f)
  val currentVolumePercent: StateFlow<Float> = _currentVolumePercent

  private val _currentBrightnessPercent: MutableStateFlow<Float> = MutableStateFlow(0f)
  val currentBrightnessPercent: StateFlow<Float> = _currentBrightnessPercent

  // --- Engine Switch Logic ---
  fun setPlayerEngine(engine: PlayerEngine) {
    _currentEngine.value = engine
  }

  // --- Subtitle Logic ---
  fun addSubtitleTrack(name: String, subtitles: List<SubtitleItem>) {
    val currentTracks = _subtitleTracks.value.toMutableList()
    currentTracks.add(SubtitleTrack(name, subtitles))
    _subtitleTracks.value = currentTracks
    // Auto-enable when a new subtitle is added
    _selectedTrackIndex.value = currentTracks.size - 1
    _currentSubtitleText.value = ""
  }

  fun selectSubtitleTrack(index: Int) {
    _selectedTrackIndex.value = index
    _currentSubtitleText.value = ""
    updateSubtitlePosition(_currentPosition.value)
  }

  fun updateSubtitlePosition(currentMs: Long) {
    val trackIndex = _selectedTrackIndex.value
    if (trackIndex >= 0 && trackIndex < _subtitleTracks.value.size) {
      val list = _subtitleTracks.value[trackIndex].subtitles
      var foundText = ""
      for (sub in list) {
        if (currentMs >= sub.startTimeMs && currentMs <= sub.endTimeMs) {
          foundText = sub.text
          break
        }
      }
      if (_currentSubtitleText.value != foundText) {
        _currentSubtitleText.value = foundText
      }
    } else {
      // Hide text if subtitle is off
      if (_currentSubtitleText.value.isNotEmpty()) {
        _currentSubtitleText.value = ""
      }
    }
  }

  fun clearSubtitles() {
    _subtitleTracks.value = emptyList()
    _selectedTrackIndex.value = -1
    _currentSubtitleText.value = ""
  }

  fun setSubtitleSize(size: Float) {
    _subtitleSize.value = size.coerceIn(10f, 40f)
  }

  fun setSubtitleOffset(offsetY: Float) {
    _subtitleOffsetY.value = offsetY
  }

  // --- Other Player Logic ---
  fun setPlaying(playing: Boolean) {
    _isPlaying.value = playing
  }

  fun setCurrentPosition(position: Long) {
    _currentPosition.value = position
    updateSubtitlePosition(position)
  }

  fun setDuration(duration: Long) {
    _duration.value = duration
  }

  fun setPlaybackSpeed(speed: Float) {
    _playbackSpeed.value = speed
  }

  fun toggleLock() {
    _isLocked.value = !_isLocked.value
  }

  fun setControlsVisible(visible: Boolean) {
    _controlsVisible.value = visible
  }

  fun cycleLoopMode() {
    _loopMode.value =
        when (_loopMode.value) {
          LoopMode.NONE -> LoopMode.ONE
          LoopMode.ONE -> LoopMode.ALL
          LoopMode.ALL -> LoopMode.NONE
        }
  }

  fun cycleAspectRatio() {
    _aspectRatio.value =
        when (_aspectRatio.value) {
          AspectRatioMode.FIT -> AspectRatioMode.FILL
          AspectRatioMode.FILL -> AspectRatioMode.STRETCH
          AspectRatioMode.STRETCH -> AspectRatioMode.FIT
        }
  }

  fun setVideoTitle(title: String) {
    _videoTitle.value = title
  }

  fun setCurrentVideoIndex(index: Int) {
    _currentVideoIndex.value = index
  }

  fun setTotalVideos(total: Int) {
    _totalVideos.value = total
  }

  fun setGestureOverlayVisible(visible: Boolean) {
    _isGestureOverlayVisible.value = visible
  }

  fun setGestureIndicator(type: Int, value: Float) {
    _gestureIndicatorType.value = type
    _gestureIndicatorValue.value = value
    _isGestureOverlayVisible.value = true
  }

  fun hideGestureOverlay() {
    _isGestureOverlayVisible.value = false
  }

  fun setCurrentVolumePercent(percent: Float) {
    _currentVolumePercent.value = percent.coerceIn(0f, 1f)
  }

  fun setCurrentBrightnessPercent(percent: Float) {
    _currentBrightnessPercent.value = percent.coerceIn(0f, 1f)
  }
}
