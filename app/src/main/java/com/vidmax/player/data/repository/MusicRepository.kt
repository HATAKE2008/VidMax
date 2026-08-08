package com.vidmax.player.data.repository

import com.vidmax.player.data.local.SongHistoryDao
import com.vidmax.player.data.model.ArtistItem
import com.vidmax.player.data.model.SongItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val historyDao: SongHistoryDao
) {

    suspend fun searchSongs(query: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), // শুধু songs, movie/natok/jukebox বাদ
                ""
            )
            extractor.fetchPage()

            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> toSongItem(item) }
                .filter { isLikelySong(it.title) }
                .distinctBy { it.videoId }
        }
    }

    /**
     * Artist (channel) খোঁজে — YouTube search-এর CHANNELS filter দিয়ে।
     * MUSIC_ARTISTS filter-টা music.youtube.com-এ যায় যেখানে artist হিসেবে
     * channelRenderer আসে না, তাই নরমাল CHANNELS filter-ই নির্ভরযোগ্য।
     */
    suspend fun searchChannels(query: String): Result<List<ArtistItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.CHANNELS),
                ""
            )
            extractor.fetchPage()

            extractor.initialPage.items
                .filterIsInstance<ChannelInfoItem>()
                .mapNotNull { item -> toArtistItem(item) }
                .distinctBy { it.channelId }
        }
    }

    /**
     * কোনো artist-এর (channel) latest গানগুলো আনে।
     */
    suspend fun getArtistSongs(channelId: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val channelUrl = "https://www.youtube.com/channel/$channelId"
            val info = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)

            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> toSongItem(item) }
                .filter { isLikelySong(it.title) }
                .distinctBy { it.videoId }
                .take(30)
        }
    }

    suspend fun getAudioStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            info.audioStreams
                .maxByOrNull { it.bitrate }
                ?.content
                ?: throw Exception("No audio stream found for $videoId")
        }
    }

    suspend fun getCategorySongs(categoryQuery: String): List<SongItem> {
        return searchSongs(categoryQuery).getOrDefault(emptyList())
    }

    suspend fun getRelatedSongs(videoId: String): Result<List<SongItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> toSongItem(item) }
                .filter { isLikelySong(it.title) }
                .distinctBy { it.videoId }
                .take(25)
        }
    }

    // Room Operations
    suspend fun saveToHistory(song: SongItem) {
        val now = System.currentTimeMillis()
        val updated = song.copy(playedAt = now)
        val inserted = historyDao.insertIfAbsent(updated)
        if (inserted == -1L) {
            // Already in history -> bump play count + refresh metadata
            historyDao.bumpSong(
                videoId = updated.videoId,
                title = updated.title,
                artist = updated.artist,
                thumbnailUrl = updated.thumbnailUrl,
                playedAt = now
            )
        }
    }

    fun getRecentlyPlayed(): Flow<List<SongItem>> {
        return historyDao.getRecentSongs()
    }

    fun getFavorites(): Flow<List<SongItem>> {
        return historyDao.getFavoriteSongs()
    }

    suspend fun getRecentlyPlayedSnapshot(limit: Int = 40): List<SongItem> {
        return historyDao.getRecentSongsSnapshot(limit)
    }

    suspend fun getFavoritesSnapshot(): List<SongItem> {
        return historyDao.getFavoriteSongsSnapshot()
    }

    suspend fun getMostPlayedSongs(limit: Int = 40): List<SongItem> {
        return historyDao.getMostPlayedSongsSnapshot(limit)
    }

    suspend fun getForgottenFavorites(cutoff: Long, limit: Int = 20): List<SongItem> {
        return historyDao.getForgottenFavoritesSnapshot(cutoff, limit)
    }

    /**
     * Meld-style radio queue: seed song's related tracks mixed with the user's
     * most-played and favorite tracks, diversified so no artist dominates.
     * No login required — everything comes from the device's listening history.
     */
    suspend fun getRadioQueue(seedVideoId: String, limit: Int = 30): Result<List<SongItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val related = getRelatedSongs(seedVideoId).getOrDefault(emptyList())
                val history = getMostPlayedSongs(30)
                val favorites = getFavoritesSnapshot()
                RecommendationEngine.buildQueue(
                    seedVideoId = seedVideoId,
                    related = related,
                    history = history,
                    favorites = favorites,
                    limit = limit
                )
            }
        }

    suspend fun isFavorite(videoId: String): Boolean {
        return historyDao.isFavorite(videoId) ?: false
    }

    suspend fun setFavorite(videoId: String, isFavorite: Boolean) {
        historyDao.setFavorite(videoId, isFavorite)
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    private fun toSongItem(item: StreamInfoItem): SongItem? {
        val videoId = item.url
            .substringAfter("v=")
            .substringBefore("&")
        if (videoId.isEmpty()) return null
        return SongItem(
            videoId = videoId,
            title = item.name,
            artist = item.uploaderName ?: "Unknown Artist",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            duration = item.duration
        )
    }

    private fun toArtistItem(item: ChannelInfoItem): ArtistItem? {
        val channelId = item.url
            .substringAfter("/channel/")
            .substringBefore("/")
        if (channelId.isBlank()) return null
        return ArtistItem(
            channelId = channelId,
            name = item.name,
            avatarUrl = item.avatars.firstOrNull()?.url ?: "",
            subscriberCount = item.subscriberCount
        )
    }

    /**
     * Songs-only check: movie, natok (drama), jukebox ও compilation টাইটেল
     * বাদ দেয় — YouTube search-এ এগুলো এসে পড়লে play-যোগ্য গানই থাকে।
     */
    private fun isLikelySong(title: String): Boolean {
        val normalized = title.lowercase()
        return NON_SONG_MARKERS.none { normalized.contains(it) }
    }

    companion object {
        private val NON_SONG_MARKERS = listOf(
            // Movies / trailers
            "movie", "film", "মুভি", "সিনেমা", "trailer", "teaser",
            "movie song", "film song", "full movie", "মুভি গান",
            // OST / soundtracks (সিনেমা/নাটকের গান)
            "ost", "soundtrack", "title song", "title track",
            // Bengali drama (natok) / telefilm / serials
            "natok", "নাটক", "telefilm", "টেলিফিল্ম", "drama serial", "full episode",
            "সিরিয়াল", "সিরিজ", "ধারাবাহিক", "পর্ব", "ওয়েব সিরিজ", "ওয়েবসিরিজ",
            "বায়োস্কোপ", "অডিও নাটক", "নাটকের গান", "সিনেমার গান", "টাইটেল গান",
            // Hindi serials / movies
            "धारावाहिक", "सीरियल", "एपिसोड", "फिल्म", "सिनेमा", "मूवी",
            // Jukeboxes
            "jukebox", "জুকবক্স",
            // Compilations / nonstop mixes
            "nonstop", "ননস্টপ", "megamix", "মেগামিক্স", "compilation", "মিক্সড",
            "full album", "top 10", "top 20", "top 50", "best of", "superhit songs"
        )
    }
}
