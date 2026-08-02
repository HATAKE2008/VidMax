package com.vidmax.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.repository.SpotifyRepository
import com.vidmax.player.data.spotify.model.SectionType
import com.vidmax.player.data.spotify.model.SpotifyHomeData
import com.vidmax.player.data.spotify.model.SpotifyHomeSection
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.data.spotify.model.SpotifyUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spotify হোম / অনলাইন স্ক্রিনের UiState — লগইন অবস্থা, ইউজার প্রোফাইল,
 * হোম ডেটা এবং রেন্ডার হওয়া সেকশনগুলো একসাথে ধরে রাখে।
 */
data class SpotifyUiState(
    val isLoggedIn: Boolean = false,
    val checkingSession: Boolean = true,
    val user: SpotifyUser? = null,
    val homeData: SpotifyHomeData? = null,
    val sections: List<SpotifyHomeSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginInProgress: Boolean = false,
)

/**
 * Meld-স্টাইল Spotify ইন্টিগ্রেশনের মূল ViewModel।
 *
 * শুধুমাত্র [SpotifyRepository] ইন্টারফেসের উপর নির্ভর করে (backend client
 * সরাসরি ব্যবহার করে না), যাতে ফ্রন্টএন্ড ও ব্যাকএন্ড আলাদা করে ডেভেলপ ও
 * রিভিউ করা যায়। হোম / অনলাইন স্ক্রিন, সেটিংস ও লগইন স্ক্রিন এই ViewModel
 * দিয়েই সব Spotify ডেটা দেখায়।
 */
@HiltViewModel
class SpotifyViewModel @Inject constructor(
    private val repository: SpotifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpotifyUiState())
    val uiState: StateFlow<SpotifyUiState> = _uiState.asStateFlow()

    // লগইন একসাথে একবারই চলবে — ডাবল-ট্যাপ / রেস থেকে বাঁচার জন্য
    private val loginMutex = Mutex()

    init {
        observeLoginState()
        checkSession()
    }

    /**
     * স্টোর করা সেশন আছে কিনা যাচাই করে; থাকলে হোম ডেটা লোড করে।
     * init এ এবং সেশন পরিবর্তনের পর কল হয়।
     */
    fun checkSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(checkingSession = true)
            val authenticated = runCatching { repository.ensureAuthenticated() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                checkingSession = false,
                isLoggedIn = authenticated,
                user = repository.currentUser.value,
                error = null,
            )
            if (authenticated) {
                loadHomeData()
            }
        }
    }

    /**
     * repository.isLoggedIn এ যেকোনো পরিবর্তন (লগইন/লগআউট) ধরার জন্য।
     * লগইন হয়ে গেলে স্বয়ংক্রিয়ভাবে হোম ডেটা রিফ্রেশ হয়।
     */
    private fun observeLoginState() {
        viewModelScope.launch {
            repository.isLoggedIn
                .distinctUntilChanged()
                .collect { loggedIn ->
                    if (loggedIn) {
                        _uiState.value = _uiState.value.copy(
                            isLoggedIn = true,
                            user = repository.currentUser.value,
                            checkingSession = false,
                        )
                        loadHomeData()
                    }
                }
        }
    }

    /**
     * হোম / অনলাইন স্ক্রিনের সব Spotify সেকশন লোড করে।
     * সেকশন খালি থাকলে topTracks / topArtists / newReleases / playlists
     * থেকে ফallback সেকশন বানিয়ে দেওয়া হয়।
     */
    fun loadHomeData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.homeData == null,
                error = null,
            )
            try {
                val data = repository.fetchHomeData(forceRefresh = forceRefresh)
                _uiState.value = _uiState.value.copy(
                    homeData = data,
                    sections = buildSections(data),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Spotify ডেটা লোড করতে ব্যর্থ",
                )
            }
        }
    }

    /**
     * WebView লগইন স্ক্রিন থেকে পাওয়া sp_dc / sp_key কুকি দিয়ে লগইন সম্পন্ন করে।
     * [loginMutex] দিয়ে নিশ্চিত করা হয় লগইন একসাথে মাত্র একবার চলে।
     */
    fun loginWithCookies(spDc: String, spKey: String) {
        viewModelScope.launch {
            loginMutex.withLock {
                _uiState.value = _uiState.value.copy(
                    isLoginInProgress = true,
                    error = null,
                )
                repository.loginWithCookies(spDc, spKey)
                    .onSuccess { user ->
                        _uiState.value = _uiState.value.copy(
                            isLoginInProgress = false,
                            isLoggedIn = true,
                            user = user,
                        )
                        loadHomeData()
                    }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoginInProgress = false,
                            isLoggedIn = false,
                            error = e.localizedMessage ?: "Spotify লগইন ব্যর্থ",
                        )
                    }
            }
        }
    }

    /**
     * Spotify সেশন মুছে লগআউট করে; UiState আবার প্রাথমিক অবস্থায় ফেরে।
     */
    fun logout() {
        viewModelScope.launch {
            loginMutex.withLock {
                repository.logout()
                _uiState.value = SpotifyUiState(checkingSession = false)
            }
        }
    }

    /**
     * একটি Spotify ট্র্যাককে YouTube Music এর [SongItem]-এ রিজলভ করে
     * [onSong] কলব্যাক দিয়ে ফেরত দেয় — UI তখন [MusicPlayerViewModel.playSong]
     * ডাকতে পারে। ব্যর্থ হলে [onError]-এ বার্তা যায়।
     */
    fun resolveAndPlay(track: SpotifyTrack, onSong: (SongItem) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.resolveToSong(track)
                .onSuccess { song -> onSong(song) }
                .onFailure { e ->
                    onError(e.localizedMessage ?: "গান খুঁজে পাওয়া যায়নি")
                }
        }
    }

    /**
     * [SpotifyHomeData] থেকে UI-তে দেখানোর মতো সেকশন লিস্ট তৈরি করে।
     * backend দেওয়া [SpotifyHomeData.sections] থাকলে সেটাই ব্যবহার হয়;
     * না থাকলে আলাদা ফিচার (topTracks ইত্যাদি) থেকে fallback সেকশন তৈরি হয়।
     */
    private fun buildSections(data: SpotifyHomeData): List<SpotifyHomeSection> {
        if (data.sections.isNotEmpty()) return data.sections

        val fallback = mutableListOf<SpotifyHomeSection>()
        if (data.topTracks.isNotEmpty()) {
            fallback.add(
                SpotifyHomeSection(
                    title = "spotify_top_tracks",
                    type = SectionType.TRACKS,
                    tracks = data.topTracks,
                )
            )
        }
        if (data.topArtists.isNotEmpty()) {
            fallback.add(
                SpotifyHomeSection(
                    title = "spotify_top_artists",
                    type = SectionType.ARTISTS,
                    artists = data.topArtists,
                )
            )
        }
        if (data.newReleases.isNotEmpty()) {
            fallback.add(
                SpotifyHomeSection(
                    title = "spotify_new_releases",
                    type = SectionType.ALBUMS,
                    albums = data.newReleases,
                )
            )
        }
        if (data.playlists.isNotEmpty()) {
            fallback.add(
                SpotifyHomeSection(
                    title = "spotify_your_playlists",
                    type = SectionType.PLAYLISTS,
                    playlists = data.playlists,
                )
            )
        }
        return fallback
    }
}
