package com.vidmax.player.service

// ⚠️ যেহেতু আমরা Media3 (ExoPlayer) রিমুভ করে IJKPlayer-এ শিফট হয়েছি,
// তাই এই MediaSessionService-এর আর প্রয়োজন নেই।
// CodeAssist-এ যেন বিল্ড এরর না আসে, তাই এই ফাইলের সব কোড কমেন্ট করে রাখা হলো।
// আপনি চাইলে ফাইল ম্যানেজার থেকে এই পুরো ফাইলটি ডিলিটও করে দিতে পারেন।

/*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

private var mediaSession: MediaSession? = null
private var player: ExoPlayer? = null

override fun onCreate() {
super.onCreate()
val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).build()
player = exoPlayer
mediaSession = MediaSession.Builder(this, exoPlayer).build()
}

override fun onGetSession(
controllerInfo: MediaSession.ControllerInfo
): MediaSession? = mediaSession

override fun onDestroy() {
// CodeAssist 'run', 'let' সাপোর্ট করে না, তাই এটি পরিবর্তন করা হয়েছিল
val currentSession = mediaSession
if (currentSession != null) {
player?.release()
currentSession.release()
mediaSession = null
}
player = null
super.onDestroy()
}
}
*/
