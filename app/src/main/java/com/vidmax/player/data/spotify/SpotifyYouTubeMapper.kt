package com.vidmax.player.data.spotify

import android.util.LruCache
import android.util.Log
import com.vidmax.player.data.local.SpotifyMatchDao
import com.vidmax.player.data.local.SpotifyMatchEntity
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.repository.MusicRepository
import com.vidmax.player.data.spotify.model.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles the matching of Spotify tracks to YouTube equivalents using NewPipe
 * search (MusicRepository.searchSongs) + fuzzy matching on title, artist and
 * duration. Successful matches are cached in Room ([SpotifyMatchDao]) and an
 * in-memory LRU cache to avoid repeated DB queries / searches.
 */
class SpotifyYouTubeMapper(
    private val musicRepository: MusicRepository,
    private val spotifyMatchDao: SpotifyMatchDao,
) {

    private data class CachedMatch(
        val youtubeId: String,
        val title: String,
        val artist: String,
        val isManualOverride: Boolean = false,
    )

    /**
     * Maps a Spotify track to a [SongItem] ready for playback by searching
     * YouTube (NewPipe) and fuzzy-matching the best result.
     *
     * Resolution order: in-memory cache → Room DB → NewPipe search.
     * Returns null if no suitable match is found.
     */
    suspend fun mapToSongItem(track: SpotifyTrack): SongItem? = withContext(Dispatchers.IO) {
        // 1. In-memory LRU cache (zero I/O)
        memoryCache[track.id]?.let { mem ->
            Log.d(TAG, "Spotify match memory hit: ${track.name} -> ${mem.youtubeId}")
            return@withContext buildSongItem(mem.youtubeId, track, mem.title, mem.artist)
        }

        // 2. Room DB cache
        val cached = spotifyMatchDao.getSpotifyMatch(track.id)
        if (cached != null) {
            Log.d(TAG, "Spotify match cache hit: ${track.name} -> ${cached.youtubeId} (manual=${cached.isManualOverride})")
            memoryCache.put(track.id, CachedMatch(cached.youtubeId, cached.title, cached.artist, cached.isManualOverride))
            return@withContext buildSongItem(cached.youtubeId, track, cached.title, cached.artist)
        }

        // 3. NewPipe search + fuzzy match
        val query = SpotifyMapper.buildSearchQuery(track)
        Log.d(TAG, "Searching YouTube for Spotify track: $query")

        val searchResult = musicRepository.searchSongs(query).getOrNull() ?: return@withContext null
        val bestMatch = findBestMatch(track, searchResult)

        if (bestMatch != null) {
            spotifyMatchDao.upsertSpotifyMatch(
                SpotifyMatchEntity(
                    spotifyId = track.id,
                    youtubeId = bestMatch.youtubeId,
                    title = bestMatch.title,
                    artist = bestMatch.artist,
                    matchScore = bestMatch.score,
                )
            )
            memoryCache.put(track.id, CachedMatch(bestMatch.youtubeId, bestMatch.title, bestMatch.artist))
            Log.d(TAG, "Spotify match found: ${track.name} -> ${bestMatch.youtubeId} (score: ${bestMatch.score})")
            return@withContext buildSongItem(
                youtubeId = bestMatch.youtubeId,
                spotifyTrack = track,
                ytTitle = bestMatch.title,
                ytArtist = bestMatch.artist,
                ytThumbnailUrl = bestMatch.thumbnailUrl,
            )
        }

        Log.w(TAG, "No YouTube match found for Spotify track: ${track.name} by ${track.artists.firstOrNull()?.name}")
        null
    }

    /**
     * Persists a user-chosen YouTube match for a Spotify track.
     * Manual overrides are never replaced by the automatic fuzzy matcher.
     */
    suspend fun overrideMatch(
        spotifyId: String,
        youtubeId: String,
        title: String,
        artist: String,
    ) = withContext(Dispatchers.IO) {
        spotifyMatchDao.upsertSpotifyMatch(
            SpotifyMatchEntity(
                spotifyId = spotifyId,
                youtubeId = youtubeId,
                title = title,
                artist = artist,
                matchScore = 1.0,
                isManualOverride = true,
            )
        )
        memoryCache.put(spotifyId, CachedMatch(youtubeId, title, artist, isManualOverride = true))
        Log.d(TAG, "Manual override saved: $spotifyId -> $youtubeId ($title by $artist)")
    }

    private fun buildSongItem(
        youtubeId: String,
        spotifyTrack: SpotifyTrack,
        ytTitle: String,
        ytArtist: String,
        ytThumbnailUrl: String? = null,
    ): SongItem {
        // Spotify album art পছন্দ করা হয় (বড়, পরিষ্কার); না পেলে YouTube thumbnail
        val thumbnail = SpotifyMapper.getTrackThumbnail(spotifyTrack)
            ?: ytThumbnailUrl
            ?: "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"

        return SongItem(
            videoId = youtubeId,
            title = ytTitle.ifEmpty { spotifyTrack.name },
            artist = ytArtist.ifEmpty { spotifyTrack.artistName },
            thumbnailUrl = thumbnail,
            streamUrl = null,
            duration = (spotifyTrack.durationMs / 1000).toLong(),
            playedAt = System.currentTimeMillis(),
            isFavorite = false,
        )
    }

    private fun findBestMatch(
        spotifyTrack: SpotifyTrack,
        searchResult: List<SongItem>,
    ): MatchCandidate? {
        val spotifyArtist = spotifyTrack.artists.firstOrNull()?.name ?: ""

        // Pre-compute normalization and bigrams for the Spotify side once
        val precomputed = SpotifyMapper.precompute(
            title = spotifyTrack.name,
            artist = spotifyArtist,
            durationMs = spotifyTrack.durationMs,
        )

        var bestCandidate: MatchCandidate? = null
        val earlyExitThreshold = SpotifyMapper.earlyExitThreshold()

        for (song in searchResult) {
            val score = SpotifyMapper.matchScorePrecomputed(
                precomputed = precomputed,
                candidateTitle = song.title,
                candidateArtist = song.artist,
                candidateDurationSec = song.duration.toInt(),
            )

            if (bestCandidate == null || score > bestCandidate.score) {
                bestCandidate = MatchCandidate(
                    youtubeId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    durationSec = song.duration.toInt(),
                    thumbnailUrl = song.thumbnailUrl,
                    score = score,
                )
                // Early exit: if this match is excellent, skip remaining candidates
                if (score >= earlyExitThreshold) break
            }
        }

        return bestCandidate?.takeIf { it.score >= MIN_MATCH_THRESHOLD }
    }

    private data class MatchCandidate(
        val youtubeId: String,
        val title: String,
        val artist: String,
        val durationSec: Int,
        val thumbnailUrl: String,
        val score: Double,
    )

    companion object {
        private const val TAG = "SpotifyYouTubeMapper"
        private const val MIN_MATCH_THRESHOLD = 0.35
        private const val MEM_CACHE_MAX_SIZE = 512

        /**
         * Process-wide, thread-safe LRU cache of recently resolved Spotify→YouTube
         * matches. Shared across all mapper instances so a match resolved once is
         * reused everywhere without DB I/O. [android.util.LruCache] serializes
         * get/put internally, which is required because queues resolve batches in
         * parallel on Dispatchers.IO.
         */
        private val memoryCache = LruCache<String, CachedMatch>(MEM_CACHE_MAX_SIZE)
    }
}
