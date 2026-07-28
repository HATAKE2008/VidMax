package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlinePlayerUiState())
    val uiState: StateFlow<OnlinePlayerUiState> = _uiState.asStateFlow()

    // Cache stream URLs to prevent multiple network calls for the same song
    private val streamUrlCache = mutableMapOf<String, String>()

    fun playSong(song: SongItem) {
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            isLoadingStream = true,
            error = null
        )

        viewModelScope.launch {
            // Save song to local Room DB history
            repository.saveToHistory(song)

            // Check if stream URL is already cached
            val cachedUrl = streamUrlCache[song.videoId]
            if (cachedUrl != null) {
                _uiState.value = _uiState.value.copy(
                    resolvedStreamUrl = cachedUrl,
                    isLoadingStream = false,
                    isPlaying = true
                )
                return@launch
            }

            // Extract fresh Stream URL using NewPipe
            repository.getAudioStreamUrl(song.videoId)
                .onSuccess { url ->
                    streamUrlCache[song.videoId] = url
                    _uiState.value = _uiState.value.copy(
                        resolvedStreamUrl = url,
                        isLoadingStream = false,
                        isPlaying = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingStream = false,
                        error = error.localizedMessage ?: "Unable to stream song"
                    )
                }
        }
    }

    fun setPlayingState(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
    }

    fun clearPlayer() {
        _uiState.value = OnlinePlayerUiState()
    }
}
