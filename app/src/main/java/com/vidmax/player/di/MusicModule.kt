package com.vidmax.player.di

import android.content.Context
import androidx.room.Room
import com.vidmax.player.data.local.MusicDatabase
import com.vidmax.player.data.local.SongHistoryDao
import com.vidmax.player.data.repository.MusicRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_database"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideSongHistoryDao(database: MusicDatabase): SongHistoryDao {
        return database.songHistoryDao()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(dao: SongHistoryDao): MusicRepository {
        return MusicRepository(dao)
    }
}
