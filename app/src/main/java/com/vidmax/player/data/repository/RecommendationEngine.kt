package com.vidmax.player.data.repository

import com.vidmax.player.data.model.SongItem

/**
 * Lightweight Meld-style recommendation engine — no login, no account.
 *
 * Unlike Meld's Spotify engine (which scores candidates by artist affinity,
 * genre overlap and recency), VidMax works purely from YouTube's public
 * "related" signal combined with the user's own on-device listening profile:
 *
 * 1. Related songs from the seed track (YouTube public API via NewPipe).
 * 2. The user's most-played songs (playCount) as a personal background pool.
 * 3. The user's favorites as a second personal background pool.
 * 4. Diversification: max [MAX_TRACKS_PER_ARTIST] per artist + shuffle, so the
 *    queue never gets monotonous and changes on every play.
 */
object RecommendationEngine {

    const val MAX_TRACKS_PER_ARTIST = 3

    /**
     * Generates a diversified queue of [limit] songs around [seedVideoId].
     *
     * @param related   related songs fetched from YouTube for the seed
     * @param history   most-played songs from the local database
     * @param favorites favorited songs from the local database
     */
    fun buildQueue(
        seedVideoId: String,
        related: List<SongItem>,
        history: List<SongItem>,
        favorites: List<SongItem>,
        limit: Int = 30
    ): List<SongItem> {
        val pool = (related + history + favorites)
            .distinctBy { it.videoId }
            .filter { it.videoId != seedVideoId }
        return diversify(pool, limit)
    }

    /**
     * Diversification: cap tracks per artist, then shuffle for variety so
     * repeated suggestions feel fresh on every load (Meld does the same).
     */
    private fun diversify(pool: List<SongItem>, limit: Int): List<SongItem> {
        val perArtist = pool
            .groupBy { it.artist.lowercase() }
            .mapValues { (_, tracks) -> tracks.take(MAX_TRACKS_PER_ARTIST) }
        return perArtist.values.flatten().shuffled().take(limit)
    }
}
