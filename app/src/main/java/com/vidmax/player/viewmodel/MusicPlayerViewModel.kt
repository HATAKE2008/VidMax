package com.vidmax.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlinePlayerUiState(
    val currentSong: SongItem? = null,
    val isPlaying: Boolean = false,
    val isLoadingStream: Boolean = false,
    val resolvedStreamUrl: String? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnlinePlayerUiState())
    val uiState: StateFlow<OnlinePlayerUiState> = _uiState.asStateFlow()

    private val streamUrlCache = mutableMapOf<String, String>()

    private var exoPlayer: ExoPlayer? = null

    private fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _uiState.value = _uiState.value.copy(
                                duration = duration.coerceAtLeast(0L)
                            )
                        } else if (playbackState == Player.STATE_ENDED) {
                            _uiState.value = _uiState.value.copy(isPlaying = false)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingStream = false,
                            isPlaying = false,
                            error = error.localizedMessage ?: "Playback error"
                        )
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun playSong(song: SongItem) {
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            isLoadingStream = true,
            error = null
        )

        viewModelScope.launch {
            repository.saveToHistory(song)

            val cachedUrl = streamUrlCache[song.videoId]
            if (cachedUrl != null) {
                startPlayback(cachedUrl)
                return@launch
            }

            repository.getAudioStreamUrl(song.videoId)
                .onSuccess { url ->
                    streamUrlCache[song.videoId] = url
                    startPlayback(url)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingStream = false,
                        error = error.localizedMessage ?: "Unable to stream song"
                    )
                }
        }
    }

    private fun startPlayback(url: String) {
        val player = getOrCreatePlayer()
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        _uiState.value = _uiState.value.copy(
            resolvedStreamUrl = url,
            isLoadingStream = false,
            isPlaying = true,
            position = 0L,
            duration = 0L
        )
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _uiState.value = _uiState.value.copy(position = position)
    }

    fun updatePosition() {
        val player = exoPlayer ?: return
        _uiState.value = _uiState.value.copy(position = player.currentPosition)
    }

    fun clearPlayer() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _uiState.value = OnlinePlayerUiState()
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
