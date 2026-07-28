package com.vidmax.player.data.model

data class FolderItem(
val path: String,
val name: String,
val videoCount: Int,
val totalSize: Long,
val firstVideoPath: String
)