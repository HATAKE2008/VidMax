package com.vidmax.player.data.spotify.model

/**
 * App-level model for a section shown on the Spotify-powered home / online
 * screen. Mirrors Meld's `SpotifyHomeSection` used by `SpotifyHomeSectionRow`.
 * Backend builds these from Spotify data; the UI renders them.
 */
data class SpotifyHomeSection(
    val title: String,
    val type: SectionType,
    val tracks: List<SpotifyTrack> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
)

enum class SectionType {
    TRACKS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
}

/**
 * Complete home-screen payload consumed by [SpotifyViewModel] and the
 * online screen UI. Populated by the backend repository in one shot so the
 * UI can render sections lazily while images load.
 */
data class SpotifyHomeData(
    val greeting: String? = null,
    val user: SpotifyUser? = null,
    val sections: List<SpotifyHomeSection> = emptyList(),
    val topTracks: List<SpotifyTrack> = emptyList(),
    val topArtists: List<SpotifyArtist> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val newReleases: List<SpotifyAlbum> = emptyList(),
    val homeFeed: List<SpotifyHomeFeedSection> = emptyList(),
    val generatedAt: Long = 0L,
)
