package com.vidmax.player.data.model

data class AudioItem(
val id: Long,
val title: String,
val artist: String,
val path: String,
val duration: Long,
val size: Long,
val dateAdded: Long
)