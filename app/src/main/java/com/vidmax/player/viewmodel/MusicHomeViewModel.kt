package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val favorites: List<SongItem> = emptyList(),
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

    private data class CategorySpec(
        val title: String,
        val query: String
    )

    private val categorySpecs = listOf(
        CategorySpec("Trending Now", "trending songs this week"),
        CategorySpec("New Releases", "new release songs 2026"),
        CategorySpec("Bengali Hits", "trending Bengali songs 2026"),
        CategorySpec("Bollywood Hits", "top Bollywood hits 2026"),
        CategorySpec("Lo-fi Vibes", "lo-fi chill music"),
        CategorySpec("Sad Hits", "sad songs to cry"),
        CategorySpec("Happy Vibes", "happy feel good songs 2026"),
        CategorySpec("Romantic Hits", "romantic songs 2026")
    )

    init {
        loadHomeScreenData()
        observeHistory()
        observeFavorites()
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
                val songLists = categorySpecs.map { spec -> async { repository.getCategorySongs(spec.query) } }.awaitAll()

                val categoryList = mutableListOf<HomeCategory>()
                categorySpecs.forEachIndexed { index, spec ->
                    if (songLists[index].isNotEmpty()) {
                        categoryList.add(HomeCategory(spec.title, songLists[index]))
                    }
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

    // 🌸 Playlist card → fetch songs for the query (used by Online screen)
    fun fetchPlaylist(query: String, onSongs: (List<SongItem>) -> Unit) {
        viewModelScope.launch {
            onSongs(repository.getCategorySongs(query))
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
                    val currentCategories = _uiState.value.categories.filterNot { it.title == "For You" }.toMutableList()
                    currentCategories.add(0, HomeCategory("For You", relatedSongs))
                    _uiState.value = _uiState.value.copy(categories = currentCategories)
                }
            }
        }
    }

    // 🌸 Live favorites feed — updates whenever a song is favorited/unfavorited
    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavorites().collectLatest { favorites ->
                _uiState.value = _uiState.value.copy(favorites = favorites)
            }
        }
    }
}
