package com.vidmax.player.data.model

data class VideoItem(
val id: Long,
val title: String,
val path: String,
val duration: Long,
val size: Long,
val width: Int,
val height: Int,
val dateAdded: Long,
val folderPath: String,
val folderName: String
)