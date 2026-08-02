package com.vidmax.player.di

import android.content.Context
import com.vidmax.player.data.local.MusicDatabase
import com.vidmax.player.data.local.SpotifyMatchDao
import com.vidmax.player.data.repository.MusicRepository
import com.vidmax.player.data.repository.SpotifyRepository
import com.vidmax.player.data.repository.SpotifyRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    /**
     * SpotifyClient-এর জন্য OkHttp client — browser-like User-Agent যুক্ত করা হয়
     * প্রতিটি request-এ SpotifyClient-এর ভেতরে (randomUserAgent), এখানে শুধু base client।
     */
    @Provides
    @Singleton
    fun provideSpotifyOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideSpotifyMatchDao(database: MusicDatabase): SpotifyMatchDao {
        return database.spotifyMatchDao()
    }

    @Provides
    @Singleton
    fun provideSpotifyRepository(
        @ApplicationContext context: Context,
        musicRepository: MusicRepository,
        spotifyMatchDao: SpotifyMatchDao,
        okHttpClient: OkHttpClient,
    ): SpotifyRepository {
        return SpotifyRepositoryImpl(
            context = context,
            musicRepository = musicRepository,
            spotifyMatchDao = spotifyMatchDao,
            okHttpClient = okHttpClient,
        )
    }
}
