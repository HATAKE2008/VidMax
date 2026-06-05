package com.vidmax.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.vidmax.player.MainActivity
import com.vidmax.player.R

class AudioService : Service() {

    private var mediaSession: MediaSession? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isCurrentlyPlaying = false

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession(this, "AudioService")
        mediaSession?.isActive = true

        // 🔥 লেটেস্ট ফিক্স ১: MediaSession Callback। Android 13+ নোটিফিকেশন থেকে প্লে/পজ কমান্ড এখানেই পাঠায়।
        mediaSession?.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                sendBroadcast(Intent("ACTION_TOGGLE"))
            }

            override fun onPause() {
                sendBroadcast(Intent("ACTION_TOGGLE"))
            }

            override fun onSkipToNext() {
                sendBroadcast(Intent("ACTION_NEXT"))
            }

            override fun onSkipToPrevious() {
                sendBroadcast(Intent("ACTION_PREVIOUS"))
            }

            override fun onStop() {
                sendBroadcast(Intent("ACTION_STOP"))
            }
        })

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "UPDATE_NOTIFICATION") {
            val title = intent.getStringExtra("TITLE") ?: "Unknown"
            val artist = intent.getStringExtra("ARTIST") ?: "Unknown"
            isCurrentlyPlaying = intent.getBooleanExtra("IS_PLAYING", false)
            val filePath = intent.getStringExtra("FILE_PATH")

            if (isCurrentlyPlaying) {
                requestAudioFocus()
            }

            showNotification(title, artist, isCurrentlyPlaying, filePath)
        } else if (action == "STOP_SERVICE") {
            abandonAudioFocus()
            stopForeground(true)
            stopSelf()
        } else if (action != null) {
            sendBroadcast(Intent(action))
        }

        return START_NOT_STICKY
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isCurrentlyPlaying) {
                    sendBroadcast(Intent("ACTION_TOGGLE"))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isCurrentlyPlaying) {
                    sendBroadcast(Intent("ACTION_TOGGLE"))
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun getAlbumArt(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            retriever.release()
            if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        filePath: String?
    ) {
        val channelId = "vidmax_audio_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Audio Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1, Intent(this, AudioService::class.java).apply { action = "ACTION_PREVIOUS" }, PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, AudioService::class.java).apply { action = "ACTION_TOGGLE" }, PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3, Intent(this, AudioService::class.java).apply { action = "ACTION_NEXT" }, PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 4, Intent(this, AudioService::class.java).apply { action = "ACTION_STOP" }, PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val artBitmap = getAlbumArt(filePath)

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)

        if (artBitmap != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        // 🔥 লেটেস্ট ফিক্স ২: PlaybackState আপডেট। এটি ছাড়া নতুন অ্যান্ড্রয়েডে সিস্টেম বুঝতে পারে না গান চলছে নাকি পজ আছে।
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        
        mediaSession?.setPlaybackState(playbackState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(openAppIntent)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Previous", prevIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, playPauseIcon), if (isPlaying) "Pause" else "Play", playPauseIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_next), "Next", nextIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Stop", stopIntent
                    ).build()
                )
                .setStyle(
                    Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession?.sessionToken)
                )
                .setOngoing(isPlaying)

            if (artBitmap != null) {
                builder.setLargeIcon(artBitmap)
            }

            val notification = builder.build()

            // Android 14 ফোরগ্রাউন্ড সার্ভিস টাইপ
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } else {
            @Suppress("DEPRECATION")
            val builder = Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(openAppIntent)
                .setOngoing(isPlaying)

            if (artBitmap != null) {
                builder.setLargeIcon(artBitmap)
            }

            val notification = builder.build()
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }
}
