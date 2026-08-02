package com.vidmax.player.data.repository

import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.spotify.model.SpotifyHomeData
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.data.spotify.model.SpotifyUser
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level contract for the Meld-style Spotify integration.
 *
 * The backend agent implements this (see [SpotifyRepositoryImpl]) using a port
 * of Meld's unofficial Spotify client:
 *  - WebView login -> sp_dc / sp_key cookies
 *  - TOTP-generated internal access token (no developer Client ID needed)
 *  - GraphQL (api-partner.spotify.com) with REST fallbacks for top tracks/artists
 *
 * The frontend agent consumes ONLY this interface (via [SpotifyViewModel]),
 * so the two can be developed and reviewed independently.
 */
interface SpotifyRepository {

    /** Whether the user is currently logged in with a valid token. */
    val isLoggedIn: StateFlow<Boolean>

    /** The signed-in user's profile, or null when logged out. */
    val currentUser: StateFlow<SpotifyUser?>

    /** True when the stored cookie expired and a re-login is required. */
    val needsReLogin: StateFlow<Boolean>

    /**
     * Completes the Spotify login using the cookies captured from the WebView
     * login screen. Fetches a TOTP access token, stores the session, loads the
     * user profile and returns it on success.
     */
    suspend fun loginWithCookies(spDc: String, spKey: String): Result<SpotifyUser>

    /** Clears the stored session (cookies, token, profile) and logs out. */
    suspend fun logout()

    /**
     * Makes sure a valid access token is set (refreshing via the sp_dc cookie
     * if the current token expired). Returns true when authenticated.
     */
    suspend fun ensureAuthenticated(): Boolean

    /**
     * Fetches everything needed for the Spotify-powered home / online screen:
     * greeting, top tracks, top artists, playlists, new releases and the
     * personalized home feed sections. Results are cached and returned
     * instantly on subsequent calls; background refresh happens only when the
     * cache is stale.
     */
    suspend fun fetchHomeData(forceRefresh: Boolean = false): SpotifyHomeData

    /** Searches Spotify for tracks (used to resolve/recommend songs). */
    suspend fun searchTracks(query: String, limit: Int = 20): Result<List<SpotifyTrack>>

    /**
     * Maps a Spotify track to its YouTube Music equivalent via fuzzy
     * title/artist/duration matching (NewPipe search). The result is a
     * [SongItem] ready for playback by [com.vidmax.player.viewmodel.MusicPlayerViewModel].
     * Matches are cached locally so repeat plays resolve instantly.
     */
    suspend fun resolveToSong(track: SpotifyTrack): Result<SongItem>
}
