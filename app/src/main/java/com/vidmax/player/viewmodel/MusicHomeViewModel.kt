package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeCategory(
    val title: String,
    val songs: List<SongItem>
)

data class MusicHomeUiState(
    val categories: List<HomeCategory> = emptyList(),
    val recentlyPlayed: List<SongItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MusicHomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicHomeUiState())
    val uiState: StateFlow<MusicHomeUiState> = _uiState.asStateFlow()

    private var lastFetchTime = 0L
    private val CACHE_DURATION_MS = 2 * 60 * 60 * 1000L // 2 Hours Cache

    init {
        loadHomeScreenData()
        observeHistory()
    }

    fun loadHomeScreenData(forceRefresh: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (!forceRefresh && _uiState.value.categories.isNotEmpty() && (currentTime - lastFetchTime < CACHE_DURATION_MS)) {
            return // Use cache
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Fetch categories in parallel
                val bengaliDeferred = async { repository.getCategorySongs("trending Bengali songs 2026") }
                val bollywoodDeferred = async { repository.getCategorySongs("top Bollywood hits 2026") }
                val lofiDeferred = async { repository.getCategorySongs("lo-fi chill music") }

                val bengaliSongs = bengaliDeferred.await()
                val bollywoodSongs = bollywoodDeferred.await()
                val lofiSongs = lofiDeferred.await()

                val categoryList = mutableListOf<HomeCategory>()

                if (bengaliSongs.isNotEmpty()) {
                    categoryList.add(HomeCategory("Bengali Hits", bengaliSongs))
                }
                if (bollywoodSongs.isNotEmpty()) {
                    categoryList.add(HomeCategory("Bollywood Hits", bollywoodSongs))
                }
                if (lofiSongs.isNotEmpty()) {
                    categoryList.add(HomeCategory("Lo-fi Vibes", lofiSongs))
                }

                lastFetchTime = currentTime
                _uiState.value = _uiState.value.copy(
                    categories = categoryList,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load music suggestions"
                )
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            repository.getRecentlyPlayed().collectLatest { history ->
                _uiState.value = _uiState.value.copy(recentlyPlayed = history)
                
                // Fetch "For You" (Related songs) based on last played song
                val lastPlayedSong = history.firstOrNull()
                if (lastPlayedSong != null) {
                    fetchForYouCategory(lastPlayedSong)
                }
            }
        }
    }

    private fun fetchForYouCategory(lastSong: SongItem) {
        viewModelScope.launch {
            repository.getRelatedSongs(lastSong.videoId).onSuccess { relatedSongs ->
                if (relatedSongs.isNotEmpty()) {
                    val currentCategories = _uiState.value.categories.filterNot { it.title == "আপনার জন্য" }.toMutableList()
                    currentCategories.add(0, HomeCategory("আপনার জন্য", relatedSongs))
                    _uiState.value = _uiState.value.copy(categories = currentCategories)
                }
            }
        }
    }
}
