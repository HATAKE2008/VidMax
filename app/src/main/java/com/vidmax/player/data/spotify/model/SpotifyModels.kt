package com.vidmax.player.data.spotify.model

/**
 * Shared data model contract for the Meld-style Spotify integration.
 * These are plain data classes (no serialization annotations) — the
 * backend client parses Spotify JSON into these using org.json
 * (built into Android), and the UI layer consumes them directly.
 */

data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)

data class SpotifySimpleArtist(
    val id: String? = null,
    val name: String = "",
    val uri: String? = null,
)

data class SpotifySimpleAlbum(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val releaseDate: String? = null,
    val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val uri: String? = null,
)

data class SpotifyExternalIds(
    val isrc: String? = null,
    val ean: String? = null,
    val upc: String? = null,
)

data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val album: SpotifySimpleAlbum? = null,
    val durationMs: Int = 0,
    val explicit: Boolean = false,
    val previewUrl: String? = null,
    val uri: String? = null,
    val popularity: Int? = null,
    val externalIds: SpotifyExternalIds? = null,
) {
    val isrc: String? get() = externalIds?.isrc
    val artistName: String get() = artists.firstOrNull()?.name ?: "Unknown Artist"
}

data class SpotifyArtist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val genres: List<String> = emptyList(),
    val popularity: Int? = null,
    val uri: String? = null,
) {
    fun bestImage(minWidth: Int = 200, maxWidth: Int = 500): String? =
        images.firstOrNull { it.width in minWidth..maxWidth }?.url
            ?: images.firstOrNull()?.url
}

data class SpotifyAlbum(
    val id: String = "",
    val name: String = "",
    val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val images: List<SpotifyImage> = emptyList(),
    val releaseDate: String? = null,
    val totalTracks: Int = 0,
    val uri: String? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
) {
    val artistName: String get() = artists.firstOrNull()?.name ?: "Unknown Artist"
    fun bestImage(minWidth: Int = 200, maxWidth: Int = 500): String? =
        images.firstOrNull { it.width in minWidth..maxWidth }?.url
            ?: images.firstOrNull()?.url
}

data class SpotifyPlaylistOwner(
    val id: String = "",
    val displayName: String? = null,
    val uri: String? = null,
)

data class SpotifyPlaylist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val owner: SpotifyPlaylistOwner? = null,
    val trackCount: Int = 0,
    val uri: String? = null,
    val public: Boolean? = null,
    val collaborative: Boolean = false,
) {
    val ownerName: String get() = owner?.displayName ?: "Spotify"
    fun bestImage(minWidth: Int = 200, maxWidth: Int = 500): String? =
        images.firstOrNull { it.width in minWidth..maxWidth }?.url
            ?: images.firstOrNull()?.url
}

data class SpotifyPlaylistTrack(
    val addedAt: String? = null,
    val track: SpotifyTrack? = null,
    val isLocal: Boolean = false,
    val uid: String? = null,
)

data class SpotifySavedTrack(
    val addedAt: String? = null,
    val track: SpotifyTrack,
)

data class SpotifyUser(
    val id: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val product: String? = null,
    val country: String? = null,
) {
    fun bestImage(minWidth: Int = 200, maxWidth: Int = 500): String? =
        images.firstOrNull { it.width in minWidth..maxWidth }?.url
            ?: images.firstOrNull()?.url
}

data class SpotifyPaging<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 20,
    val offset: Int = 0,
    val next: String? = null,
    val previous: String? = null,
)

data class SpotifySearchResult(
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val playlists: SpotifyPaging<SpotifyPlaylist>? = null,
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
    val artists: SpotifyPaging<SpotifyArtist>? = null,
)

/** Internal web-player access token (from the TOTP endpoint). */
data class SpotifyInternalToken(
    val accessToken: String,
    val accessTokenExpirationTimestampMs: Long,
    val isAnonymous: Boolean = false,
    val clientId: String = "",
)

/**
 * Personalized home feed from the `home` GQL operation — mirrors the
 * open.spotify.com landing page (Daily Mix, Discover Weekly, etc.).
 */
data class SpotifyHomeFeed(
    val greeting: String? = null,
    val sections: List<SpotifyHomeFeedSection> = emptyList(),
)

data class SpotifyHomeFeedSection(
    val sectionUri: String = "",
    val title: String? = null,
    val typename: String = "",
    val totalCount: Int = 0,
    val items: List<SpotifyHomeFeedItem> = emptyList(),
)

sealed class SpotifyHomeFeedItem {
    abstract val uri: String

    data class Playlist(
        override val uri: String,
        val id: String,
        val name: String,
        val description: String? = null,
        val format: String? = null,
        val totalCount: Int = 0,
        val imageUrl: String? = null,
        val extractedColorHex: String? = null,
        val ownerName: String? = null,
        val madeForUsername: String? = null,
    ) : SpotifyHomeFeedItem()

    data class Album(
        override val uri: String,
        val id: String,
        val name: String,
        val albumType: String? = null,
        val artists: List<SpotifySimpleArtist> = emptyList(),
        val imageUrl: String? = null,
    ) : SpotifyHomeFeedItem()

    data class Artist(
        override val uri: String,
        val id: String,
        val name: String,
        val imageUrl: String? = null,
    ) : SpotifyHomeFeedItem()
}

data class NewReleasesResponse(
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
)

data class ArtistTopTracksResponse(
    val tracks: List<SpotifyTrack> = emptyList(),
)
