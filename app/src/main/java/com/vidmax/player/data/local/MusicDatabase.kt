package com.vidmax.player.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vidmax.player.data.model.SongItem

// 💡 যদি SongHistoryDao অন্য প্যাকেজে থাকে, তবে এভাবে ইমপোর্ট করুন:
// import com.vidmax.player.data.local.dao.SongHistoryDao

@Database(entities = [SongItem::class, SpotifyMatchEntity::class], version = 3, exportSchema = false)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songHistoryDao(): SongHistoryDao
    abstract fun spotifyMatchDao(): SpotifyMatchDao
}
