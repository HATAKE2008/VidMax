package com.vidmax.player.data.local.video

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created video playlist.
 *
 * Ported from mpvRex's PlaylistEntity (xyz.mpv.rex.database.entities).
 */
@Entity
data class VidMaxVideoPlaylist(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val name: String,
  val createdAt: Long,
  val updatedAt: Long,
)
