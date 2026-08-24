package com.vidmax.player.data.local.video

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Ported from mpvRex's MpvExDatabase, scoped to video playlists.
 *
 * A process-wide singleton via [getInstance] so the Hilt module and any
 * non-injected ViewModel (LibraryViewModel) share one database instance.
 */
@Database(
  entities = [
    VidMaxVideoPlaylist::class,
    VidMaxVideoPlaylistItem::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class VidMaxVideoDatabase : RoomDatabase() {
  abstract fun videoPlaylistDao(): VideoPlaylistDao

  companion object {
    @Volatile
    private var instance: VidMaxVideoDatabase? = null

    fun getInstance(context: Context): VidMaxVideoDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          VidMaxVideoDatabase::class.java,
          "video_playlist_database",
        ).fallbackToDestructiveMigration().build().also { instance = it }
      }
  }
}
