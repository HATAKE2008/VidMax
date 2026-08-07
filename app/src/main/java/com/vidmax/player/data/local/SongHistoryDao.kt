package com.vidmax.player.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vidmax.player.data.model.SongItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SongHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongItem)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(song: SongItem): Long

    @Query("UPDATE song_history SET playedAt = :playedAt, playCount = playCount + 1, title = :title, artist = :artist, thumbnailUrl = :thumbnailUrl WHERE videoId = :videoId")
    suspend fun bumpSong(
        videoId: String,
        title: String,
        artist: String,
        thumbnailUrl: String,
        playedAt: Long
    )

    @Query("SELECT * FROM song_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentSongs(limit: Int = 20): Flow<List<SongItem>>

    @Query("SELECT * FROM song_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentSongsSnapshot(limit: Int): List<SongItem>

    @Query("SELECT * FROM song_history WHERE isFavorite = 1 ORDER BY playedAt DESC")
    fun getFavoriteSongs(): Flow<List<SongItem>>

    @Query("SELECT * FROM song_history WHERE isFavorite = 1 ORDER BY playedAt DESC")
    suspend fun getFavoriteSongsSnapshot(): List<SongItem>

    @Query("SELECT * FROM song_history ORDER BY playCount DESC, playedAt DESC LIMIT :limit")
    suspend fun getMostPlayedSongsSnapshot(limit: Int): List<SongItem>

    @Query("SELECT * FROM song_history WHERE isFavorite = 1 AND playedAt < :cutoff ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getForgottenFavoritesSnapshot(cutoff: Long, limit: Int): List<SongItem>

    @Query("UPDATE song_history SET isFavorite = :isFavorite WHERE videoId = :videoId")
    suspend fun setFavorite(videoId: String, isFavorite: Boolean)

    @Query("SELECT isFavorite FROM song_history WHERE videoId = :videoId")
    suspend fun isFavorite(videoId: String): Boolean?

    @Query("DELETE FROM song_history")
    suspend fun clearHistory()
}
