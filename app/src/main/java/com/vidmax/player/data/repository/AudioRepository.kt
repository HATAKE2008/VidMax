package com.vidmax.player.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.vidmax.player.data.model.AudioItem

class AudioRepository(private val contentResolver: ContentResolver) {

  fun getAllAudio(): List<AudioItem> {
    val audioList = mutableListOf<AudioItem>()
    val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val projection =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED)

    // 🔥 প্রথম ফিল্টার: অ্যান্ড্রয়েডকে বলা হচ্ছে, "ভাই, আমাকে শুধু আসল মিউজিক দিবা, হাবিজাবি না!"
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    // selection প্যারামিটারটা অ্যাড করে দিয়েছি
    val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, sortOrder)

    cursor?.use {
      val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
      val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
      val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
      val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
      val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
      val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

      while (it.moveToNext()) {
        val path = it.getString(pathColumn) ?: ""
        val duration = it.getLong(durationColumn)

        // 🔥 দ্বিতীয় কড়া ফিল্টার: ফোল্ডারের নামে WhatsApp বা Voice Note থাকলে সোজা ইগনোর!
        val isVoiceNote =
            path.contains("WhatsApp Voice Notes", ignoreCase = true) ||
                path.contains("WhatsApp Audio", ignoreCase = true) ||
                path.contains("Recordings", ignoreCase = true) ||
                path.contains("Voice Recorder", ignoreCase = true) ||
                path.contains("CallRecord", ignoreCase = true)

        // ম্যাজিক: ১০ সেকেন্ডের বড় হতে হবে এবং ভয়েস নোট হওয়া যাবে না
        if (duration >= 10000 && !isVoiceNote) {
          val id = it.getLong(idColumn)
          val title = it.getString(titleColumn) ?: "Unknown"

          // নামের শেষে .mp3 বা .m4a থাকলে সেটা সরিয়ে সুন্দর করে দেখানো
          val cleanTitle = title.substringBeforeLast(".")

          val artist = it.getString(artistColumn) ?: "Unknown Artist"
          val size = it.getLong(sizeColumn)
          val dateAdded = it.getLong(dateAddedColumn)

          val finalArtist = if (artist == "<unknown>") "Unknown Artist" else artist

          audioList.add(AudioItem(id, cleanTitle, finalArtist, path, duration, size, dateAdded))
        }
      }
    }
    return audioList
  }
}
