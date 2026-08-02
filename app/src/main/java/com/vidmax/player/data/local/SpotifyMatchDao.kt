package com.vidmax.player.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Cached Spotify → YouTube match. Spotify track যার YouTube equivalent খুঁজে
 * পেয়েছি তা এখানে সংরক্ষণ করা হয় যাতে বারবার নেটওয়ার্ক সার্চ করতে না হয়।
 * Manual override (user-নির্বাচিত) match কখনোই automatic matcher-এ বদলানো হয় না।
 */
@Entity(tableName = "spotify_match")
data class SpotifyMatchEntity(
    @PrimaryKey
    val spotifyId: String,
    val youtubeId: String,
    val title: String,
    val artist: String,
    val matchScore: Double = 0.0,
    val isManualOverride: Boolean = false,
)

@Dao
interface SpotifyMatchDao {

    @Query("SELECT * FROM spotify_match WHERE spotifyId = :spotifyId")
    suspend fun getSpotifyMatch(spotifyId: String): SpotifyMatchEntity?

    @Query("SELECT * FROM spotify_match WHERE youtubeId = :youtubeId")
    suspend fun getSpotifyMatchByYouTubeId(youtubeId: String): SpotifyMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpotifyMatch(entity: SpotifyMatchEntity)

    @Query("DELETE FROM spotify_match")
    suspend fun deleteAll()
}
