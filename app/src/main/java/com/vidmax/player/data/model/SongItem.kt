package com.vidmax.player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_history")
data class SongItem(
    @PrimaryKey
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val streamUrl: String? = null,
    val duration: Long = 0L,
    val playedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
