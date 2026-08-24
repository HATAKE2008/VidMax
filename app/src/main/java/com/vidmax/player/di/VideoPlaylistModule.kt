package com.vidmax.player.di

import android.content.Context
import com.vidmax.player.data.local.video.VidMaxVideoDatabase
import com.vidmax.player.data.local.video.VideoPlaylistDao
import com.vidmax.player.data.repository.VideoPlaylistRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VideoPlaylistModule {

    @Provides
    @Singleton
    fun provideVideoPlaylistDatabase(@ApplicationContext context: Context): VidMaxVideoDatabase {
        // Shared singleton so LibraryViewModel (non-injected) sees the same DB.
        return VidMaxVideoDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideVideoPlaylistDao(database: VidMaxVideoDatabase): VideoPlaylistDao {
        return database.videoPlaylistDao()
    }

    @Provides
    @Singleton
    fun provideVideoPlaylistRepository(dao: VideoPlaylistDao): VideoPlaylistRepository {
        return VideoPlaylistRepository(dao)
    }
}
