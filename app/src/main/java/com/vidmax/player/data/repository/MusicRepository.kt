package com.vidmax.player.data.repository

import com.vidmax.player.data.local.SongHistoryDao
import com.vidmax.player.data.model.SongItem
import org.schabi.newpipe.extractor.ServiceList
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
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), // শুধু video, channel/playlist বাদ
                ""
            )
            extractor.fetchPage()

            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                        val videoId = item.url
                            .substringAfter("v=")
                            .substringBefore("&")
                        SongItem(
                            videoId = videoId,
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown Artist",
                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                            duration = item.duration
                        )
                    }
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
                .take(10)
                .map { item ->
                        val relatedVideoId = item.url
                            .substringAfter("v=")
                            .substringBefore("&")
                        SongItem(
                            videoId = relatedVideoId,
                            title = item.name,
                            artist = item.uploaderName ?: "Unknown Artist",
                            thumbnailUrl = "https://img.youtube.com/vi/$relatedVideoId/hqdefault.jpg",
                            duration = item.duration
                        )
                    }
        }
    }

    // Room Operations
    suspend fun saveToHistory(song: SongItem) {
        historyDao.insertSong(song.copy(playedAt = System.currentTimeMillis()))
    }

    fun getRecentlyPlayed(): Flow<List<SongItem>> {
        return historyDao.getRecentSongs()
    }

    fun getFavorites(): Flow<List<SongItem>> {
        return historyDao.getFavoriteSongs()
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
}
