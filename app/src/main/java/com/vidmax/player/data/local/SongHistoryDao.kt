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

    @Query("SELECT * FROM song_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentSongs(limit: Int = 20): Flow<List<SongItem>>

    @Query("DELETE FROM song_history")
    suspend fun clearHistory()
}
