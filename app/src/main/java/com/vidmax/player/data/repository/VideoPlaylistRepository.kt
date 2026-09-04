package com.vidmax.player.data.repository

import android.net.Uri
import com.vidmax.player.data.local.video.VidMaxVideoDatabase
import com.vidmax.player.data.local.video.VidMaxVideoPlaylist
import com.vidmax.player.data.local.video.VidMaxVideoPlaylistItem
import com.vidmax.player.data.local.video.VideoPlaylistDao
import kotlinx.coroutines.flow.Flow

/**
 * Ported from mpvRex's PlaylistRepository
 * (xyz.mpv.rex.database.repository.PlaylistRepository), minus the M3U
 * import/refresh features which VidMax does not use.
 */
class VideoPlaylistRepository(private val videoPlaylistDao: VideoPlaylistDao) {
  // Playlist operations
  suspend fun createPlaylist(name: String): Long {
    val now = System.currentTimeMillis()
    return videoPlaylistDao.insertPlaylist(
      VidMaxVideoPlaylist(
        name = name,
        createdAt = now,
        updatedAt = now,
      ),
    )
  }

  suspend fun updatePlaylist(playlist: VidMaxVideoPlaylist) {
    videoPlaylistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
  }

  suspend fun deletePlaylist(playlist: VidMaxVideoPlaylist) {
    videoPlaylistDao.deletePlaylist(playlist)
  }

  fun observeAllPlaylists(): Flow<List<VidMaxVideoPlaylist>> = videoPlaylistDao.observeAllPlaylists()

  suspend fun getAllPlaylists(): List<VidMaxVideoPlaylist> = videoPlaylistDao.getAllPlaylists()

  suspend fun getPlaylistById(playlistId: Int): VidMaxVideoPlaylist? = videoPlaylistDao.getPlaylistById(playlistId)

  fun observePlaylistById(playlistId: Int): Flow<VidMaxVideoPlaylist?> = videoPlaylistDao.observePlaylistById(playlistId)

  // Playlist item operations
  suspend fun addItemToPlaylist(playlistId: Int, filePath: String, fileName: String) {
    val maxPosition = videoPlaylistDao.getMaxPosition(playlistId) ?: -1
    videoPlaylistDao.insertPlaylistItem(
      VidMaxVideoPlaylistItem(
        playlistId = playlistId,
        filePath = filePath,
        fileName = fileName,
        position = maxPosition + 1,
        addedAt = System.currentTimeMillis(),
      ),
    )
    // Update playlist's updatedAt timestamp
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun addItemsToPlaylist(playlistId: Int, items: List<Pair<String, String>>) {
    val maxPosition = videoPlaylistDao.getMaxPosition(playlistId) ?: -1
    val now = System.currentTimeMillis()
    val playlistItems = items.mapIndexed { index, (filePath, fileName) ->
      VidMaxVideoPlaylistItem(
        playlistId = playlistId,
        filePath = filePath,
        fileName = fileName,
        position = maxPosition + 1 + index,
        addedAt = now,
      )
    }
    videoPlaylistDao.insertPlaylistItems(playlistItems)
    // Update playlist's updatedAt timestamp
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemFromPlaylist(item: VidMaxVideoPlaylistItem) {
    videoPlaylistDao.deletePlaylistItem(item)
    // Update playlist's updatedAt timestamp
    getPlaylistById(item.playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemsFromPlaylist(items: List<VidMaxVideoPlaylistItem>) {
    if (items.isEmpty()) return
    // Use batch delete for better performance and to avoid race conditions
    videoPlaylistDao.deletePlaylistItems(items)
    // Update playlist's updatedAt timestamp
    getPlaylistById(items.first().playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  suspend fun removeItemById(itemId: Int) {
    videoPlaylistDao.deletePlaylistItemById(itemId)
  }

  suspend fun updatePathReferences(oldPath: String, newPath: String, newName: String) {
    videoPlaylistDao.updateItemPath(oldPath, newPath, newName)
  }

  suspend fun clearPlaylist(playlistId: Int) {
    videoPlaylistDao.deleteAllItemsFromPlaylist(playlistId)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  fun observePlaylistItems(playlistId: Int): Flow<List<VidMaxVideoPlaylistItem>> =
    videoPlaylistDao.observePlaylistItems(playlistId)

  suspend fun getPlaylistItems(playlistId: Int): List<VidMaxVideoPlaylistItem> =
    videoPlaylistDao.getPlaylistItems(playlistId)

  fun observePlaylistItemCount(playlistId: Int): Flow<Int> =
    videoPlaylistDao.observePlaylistItemCount(playlistId)

  suspend fun getPlaylistItemCount(playlistId: Int): Int =
    videoPlaylistDao.getPlaylistItemCount(playlistId)

  suspend fun reorderPlaylistItems(playlistId: Int, newOrder: List<Int>) {
    videoPlaylistDao.reorderPlaylistItems(playlistId, newOrder)
    getPlaylistById(playlistId)?.let { playlist ->
      updatePlaylist(playlist)
    }
  }

  // Helper to get playlist items as URIs for playback
  suspend fun getPlaylistItemsAsUris(playlistId: Int): List<Uri> {
    return getPlaylistItems(playlistId).map { item ->
      if (item.filePath.startsWith("/") || item.filePath.startsWith("file://")) {
        val path = if (item.filePath.startsWith("file://")) item.filePath.removePrefix("file://") else item.filePath
        Uri.fromFile(java.io.File(path))
      } else {
        Uri.parse(item.filePath)
      }
    }
  }

  /**
   * Get a windowed subset of playlist items as URIs to avoid loading huge playlists at once.
   * This prevents ANR issues and TransactionTooLargeException with large playlists.
   *
   * @param playlistId The playlist ID
   * @param centerIndex The index to center the window around (typically current playing position)
   * @param windowSize Total number of items to load (default 100)
   * @return List of URIs in the windowed range
   */
  suspend fun getPlaylistItemsWindowAsUris(
    playlistId: Int,
    centerIndex: Int = 0,
    windowSize: Int = 100,
  ): List<Uri> {
    val totalCount = getPlaylistItemCount(playlistId)
    if (totalCount == 0) return emptyList()

    // If playlist is small enough, return all items
    if (totalCount <= windowSize) {
      return getPlaylistItemsAsUris(playlistId)
    }

    // Calculate window boundaries
    val halfWindow = windowSize / 2
    val startPosition = (centerIndex - halfWindow).coerceAtLeast(0)
    val endPosition = (startPosition + windowSize).coerceAtMost(totalCount)

    // Get items in range
    return videoPlaylistDao.getPlaylistItemsInRange(playlistId, startPosition, endPosition)
      .map { item ->
        if (item.filePath.startsWith("/") || item.filePath.startsWith("file://")) {
          val path = if (item.filePath.startsWith("file://")) item.filePath.removePrefix("file://") else item.filePath
          Uri.fromFile(java.io.File(path))
        } else {
          Uri.parse(item.filePath)
        }
      }
  }

  // Play history operations
  suspend fun updatePlayHistory(playlistId: Int, filePath: String, position: Long = 0) {
    videoPlaylistDao.updatePlayHistory(playlistId, filePath, System.currentTimeMillis(), position)
  }

  suspend fun getRecentlyPlayedInPlaylist(playlistId: Int, limit: Int = 20): List<VidMaxVideoPlaylistItem> {
    return videoPlaylistDao.getRecentlyPlayedInPlaylist(playlistId, limit)
  }

  suspend fun getPlaylistItemByPath(playlistId: Int, filePath: String): VidMaxVideoPlaylistItem? {
    return videoPlaylistDao.getPlaylistItemByPath(playlistId, filePath)
  }
}
