package com.vidmax.player.data.local.video

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One video inside a [VidMaxVideoPlaylist].
 *
 * Ported from mpvRex's PlaylistItemEntity: cascade-deletes with its parent
 * playlist and keeps an explicit position for ordering.
 */
@Entity(
  foreignKeys = [
    ForeignKey(
      entity = VidMaxVideoPlaylist::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["playlistId"])],
)
data class VidMaxVideoPlaylistItem(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val playlistId: Int,
  val filePath: String,
  val fileName: String,
  val position: Int, // Order in playlist
  val addedAt: Long,
  val lastPlayedAt: Long = 0, // When this video was last played from this playlist
  val playCount: Int = 0, // How many times played from this playlist
  val lastPosition: Long = 0, // Last playback position in milliseconds
)
