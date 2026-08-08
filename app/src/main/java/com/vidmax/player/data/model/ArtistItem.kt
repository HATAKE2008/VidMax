package com.vidmax.player.data.model

data class ArtistItem(
    val channelId: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: Long = -1L
)
