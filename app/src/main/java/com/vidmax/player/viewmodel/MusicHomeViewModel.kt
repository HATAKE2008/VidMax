package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidmax.player.data.model.ArtistItem
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

// 🌸 Meld-style "Similar to <seed>" recommendation row
data class SimilarRecommendation(
    val seed: SongItem,
    val items: List<SongItem>
)

data class MusicHomeUiState(
    val categories: List<HomeCategory> = emptyList(),
    val recentlyPlayed: List<SongItem> = emptyList(),
    val favorites: List<SongItem> = emptyList(),
    val quickPicks: List<SongItem> = emptyList(),
    val dailyDiscover: List<SongItem> = emptyList(),
    val keepListening: List<SongItem> = emptyList(),
    val forgottenFavorites: List<SongItem> = emptyList(),
    val similarRecommendations: List<SimilarRecommendation> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
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
    private val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes (fresh suggestions more often)
    private val FORGOTTEN_CUTOFF_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    private data class CategorySpec(
        val title: String,
        val query: String
    )

    private val categorySpecs = listOf(
        CategorySpec("Trending Now", "trending songs 2026"),
        CategorySpec("New Releases", "new hindi songs 2026"),
        CategorySpec("Bengali Hits", "bangla new songs"),
        CategorySpec("Bollywood Hits", "bollywood romantic hits"),
        CategorySpec("Lo-fi Vibes", "lofi chill beats"),
        CategorySpec("Sad Hits", "sad emotional songs"),
        CategorySpec("Happy Vibes", "happy mood songs"),
        CategorySpec("Romantic Hits", "love songs 2026")
    )

    // 🌸 Query variations for randomization — প্রতিবার refresh-এ আলাদা query,
    // তাই একই গান বারবার না এসে নতুন নতুন suggestion আসে।
    private val queryVariants = mapOf(
        "Trending Now" to listOf(
            "trending songs this week",
            "viral music 2026",
            "popular songs right now",
            "trending hindi songs",
            "hit songs 2026"
        ),
        "New Releases" to listOf(
            "new release songs 2026",
            "latest bollywood songs",
            "new music this week",
            "fresh songs 2026",
            "just released songs"
        ),
        "Bengali Hits" to listOf(
            "trending Bengali songs 2026",
            "bangla new songs",
            "bangla hit songs",
            "bengali romantic songs",
            "bangla latest songs"
        ),
        "Bollywood Hits" to listOf(
            "top Bollywood hits 2026",
            "bollywood romantic hits",
            "hindi hit songs",
            "bollywood dance songs",
            "bollywood latest hits"
        ),
        "Lo-fi Vibes" to listOf(
            "lo-fi chill music",
            "lofi beats relax",
            "chill lofi mix",
            "lofi study music",
            "aesthetic lofi songs"
        ),
        "Sad Hits" to listOf(
            "sad songs to cry",
            "emotional songs hindi",
            "heartbreak songs",
            "sad romantic songs",
            "sad music 2026"
        ),
        "Happy Vibes" to listOf(
            "happy feel good songs 2026",
            "upbeat songs",
            "positive vibes music",
            "happy dance songs",
            "feel good hits"
        ),
        "Romantic Hits" to listOf(
            "romantic songs 2026",
            "love songs hindi",
            "romantic melodies",
            "love ballads",
            "romantic hits 2026"
        )
    )

    // 🌸 Artist discovery — CHANNELS search query pool (প্রতিবার refresh-এ বদলায়)
    private val artistQueries = listOf(
        "arijit singh", "atif aslam", "neha kakkar", "shreya ghoshal",
        "kishore kumar", "lata mangeshkar", "arnob", "habib wahid",
        "jubin nautiyal", "darshan raval", "asif akbar", "ahmed humayun",
        "nalam mahbub", "tahsan", "ayub bachchu", "james",
        "rafi", "asha bhosle", "sonu nigam", "kumar sanu"
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
                // 🌸 Fetch with randomized queries — প্রতি ক্যাটাগরিতে random variant +
                // shuffle, তাই প্রতিবার refresh-এ নতুন গান আসে।
                val songLists = categorySpecs.map { spec ->
                    async {
                        val variants = queryVariants[spec.title] ?: listOf(spec.query)
                        val randomQuery = variants.random()
                        repository.getCategorySongs(randomQuery).shuffled().take(30)
                    }
                }.awaitAll()

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

                // 🌸 Meld-style personal sections (Quick Picks, Daily Discover,
                // Keep Listening, Forgotten Favorites, Similar Recommendations)
                loadPersonalSections()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load music suggestions"
                )
            }
        }
    }

    /**
     * Builds the Meld-style personal feed purely from the on-device listening
     * profile + YouTube's public related signal. No login, no account.
     */
    private suspend fun loadPersonalSections() {
        val favorites = repository.getFavoritesSnapshot()
        val recent = repository.getRecentlyPlayedSnapshot(40)
        val mostPlayed = repository.getMostPlayedSongs(40)
        val cutoff = System.currentTimeMillis() - FORGOTTEN_CUTOFF_MS
        val forgotten = repository.getForgottenFavorites(cutoff, 20)

        // 🌸 Quick Picks: Mix of personal + trending for variety
        val quickPicks = run {
            val personal = (favorites + forgotten + recent)
                .distinctBy { it.videoId }
                .shuffled()
                .take(10)

            // Add some trending/category songs for freshness
            val trending = _uiState.value.categories
                .flatMap { it.songs }
                .distinctBy { it.videoId }
                .filter { it !in personal }
                .shuffled()
                .take(10)

            (personal + trending)
                .distinctBy { it.videoId }
                .shuffled()
                .take(20)
        }

        _uiState.value = _uiState.value.copy(
            favorites = favorites,
            quickPicks = quickPicks,
            forgottenFavorites = forgotten.shuffled().take(10)
        )

        // 🌸 Daily Discover: random favorite/most-played seeds -> related song
        val discoverSeeds = (favorites + mostPlayed)
            .distinctBy { it.videoId }
            .shuffled()
            .take(5)
        val discovered = discoverSeeds.mapNotNull { seed ->
            repository.getRelatedSongs(seed.videoId)
                .getOrNull()
                ?.shuffled()
                ?.firstOrNull { it.videoId != seed.videoId }
        }.distinctBy { it.videoId }.take(8)
        _uiState.value = _uiState.value.copy(dailyDiscover = discovered)

        // 🌸 Keep Listening: most played (heavy rotation), shuffled
        val keepListening = mostPlayed.shuffled().take(15)
        _uiState.value = _uiState.value.copy(keepListening = keepListening)

        // 🌸 Similar Recommendations: seeds from most-played -> related rows
        val simSeeds = mostPlayed
            .filter { it.artist.isNotBlank() }
            .shuffled()
            .take(4)
        val similar = simSeeds.mapNotNull { seed ->
            val items = repository.getRelatedSongs(seed.videoId)
                .getOrNull()
                ?.shuffled()
                ?.take(15)
                .orEmpty()
            if (items.isEmpty()) null else SimilarRecommendation(seed = seed, items = items)
        }
        _uiState.value = _uiState.value.copy(similarRecommendations = similar)

        // 🌸 Popular Artists — parallel CHANNELS search, random subset each refresh
        val artistResults = artistQueries.shuffled().take(6).map { query ->
            async { repository.searchChannels(query).getOrDefault(emptyList()) }
        }.awaitAll()
        val artists = artistResults.flatten()
            .distinctBy { it.channelId }
            .filter { it.name.isNotBlank() }
            .take(12)
        _uiState.value = _uiState.value.copy(artists = artists)
    }

    // 🌸 Artist detail → channel-এর latest গান (used by Online screen)
    fun fetchArtistSongs(channelId: String, onSongs: (List<SongItem>) -> Unit) {
        viewModelScope.launch {
            repository.getArtistSongs(channelId)
                .onSuccess { songs -> onSongs(songs) }
                .onFailure { onSongs(emptyList()) }
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
