package com.vidmax.player.data.local.video

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Ported from mpvRex's PlaylistDao (xyz.mpv.rex.database.dao.PlaylistDao).
 */
@Dao
interface VideoPlaylistDao {
  // Playlist operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: VidMaxVideoPlaylist): Long

  @Update
  suspend fun updatePlaylist(playlist: VidMaxVideoPlaylist)

  @Delete
  suspend fun deletePlaylist(playlist: VidMaxVideoPlaylist)

  @Query("SELECT * FROM VidMaxVideoPlaylist ORDER BY updatedAt DESC")
  fun observeAllPlaylists(): Flow<List<VidMaxVideoPlaylist>>

  @Query("SELECT * FROM VidMaxVideoPlaylist ORDER BY updatedAt DESC")
  suspend fun getAllPlaylists(): List<VidMaxVideoPlaylist>

  @Query("SELECT * FROM VidMaxVideoPlaylist WHERE id = :playlistId")
  suspend fun getPlaylistById(playlistId: Int): VidMaxVideoPlaylist?

  @Query("SELECT * FROM VidMaxVideoPlaylist WHERE id = :playlistId")
  fun observePlaylistById(playlistId: Int): Flow<VidMaxVideoPlaylist?>

  // Playlist item operations
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItem(item: VidMaxVideoPlaylistItem)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylistItems(items: List<VidMaxVideoPlaylistItem>)

  @Update
  suspend fun updatePlaylistItem(item: VidMaxVideoPlaylistItem)

  @Delete
  suspend fun deletePlaylistItem(item: VidMaxVideoPlaylistItem)

  @Delete
  suspend fun deletePlaylistItems(items: List<VidMaxVideoPlaylistItem>)

  @Query("DELETE FROM VidMaxVideoPlaylistItem WHERE id = :itemId")
  suspend fun deletePlaylistItemById(itemId: Int)

  @Query("DELETE FROM VidMaxVideoPlaylistItem WHERE id IN (:itemIds)")
  suspend fun deletePlaylistItemsByIds(itemIds: List<Int>)

  @Query(
    "SELECT * FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId ORDER BY position ASC",
  )
  fun observePlaylistItems(playlistId: Int): Flow<List<VidMaxVideoPlaylistItem>>

  @Query(
    "SELECT * FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId ORDER BY position ASC",
  )
  suspend fun getPlaylistItems(playlistId: Int): List<VidMaxVideoPlaylistItem>

  @Query("SELECT COUNT(*) FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId")
  suspend fun getPlaylistItemCount(playlistId: Int): Int

  @Query("SELECT COUNT(*) FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId")
  fun observePlaylistItemCount(playlistId: Int): Flow<Int>

  @Query("DELETE FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId")
  suspend fun deleteAllItemsFromPlaylist(playlistId: Int)

  @Query("UPDATE VidMaxVideoPlaylistItem SET position = :newPosition WHERE id = :itemId")
  suspend fun updateItemPosition(itemId: Int, newPosition: Int)

  @Transaction
  suspend fun reorderPlaylistItems(playlistId: Int, newOrder: List<Int>) {
    newOrder.forEachIndexed { index, itemId ->
      updateItemPosition(itemId, index)
    }
  }

  @Query("SELECT MAX(position) FROM VidMaxVideoPlaylistItem WHERE playlistId = :playlistId")
  suspend fun getMaxPosition(playlistId: Int): Int?

  // Pagination support for large playlists
  @Query(
    """
    SELECT * FROM VidMaxVideoPlaylistItem
    WHERE playlistId = :playlistId AND position >= :startPosition AND position < :endPosition
    ORDER BY position ASC
    """,
  )
  suspend fun getPlaylistItemsInRange(
    playlistId: Int,
    startPosition: Int,
    endPosition: Int,
  ): List<VidMaxVideoPlaylistItem>

  // Play history operations
  @Query(
    """
    UPDATE VidMaxVideoPlaylistItem
    SET lastPlayedAt = :timestamp, playCount = playCount + 1, lastPosition = :position
    WHERE playlistId = :playlistId AND filePath = :filePath
    """,
  )
  suspend fun updatePlayHistory(playlistId: Int, filePath: String, timestamp: Long, position: Long)

  @Query(
    """
    SELECT * FROM VidMaxVideoPlaylistItem
    WHERE playlistId = :playlistId
    ORDER BY lastPlayedAt DESC
    LIMIT :limit
    """,
  )
  suspend fun getRecentlyPlayedInPlaylist(playlistId: Int, limit: Int): List<VidMaxVideoPlaylistItem>

  @Query(
    """
    SELECT * FROM VidMaxVideoPlaylistItem
    WHERE playlistId = :playlistId AND filePath = :filePath
    """,
  )
  suspend fun getPlaylistItemByPath(playlistId: Int, filePath: String): VidMaxVideoPlaylistItem?
}
