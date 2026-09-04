package com.vidmax.player.ui.player

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoBookmark(val positionMs: Long, val label: String)

fun bookmarkPrefsKey(videoPath: String): String = "video_bookmarks_${videoPath.hashCode()}"

fun loadBookmarks(prefs: SharedPreferences, videoPath: String): List<VideoBookmark> {
  return (prefs.getStringSet(bookmarkPrefsKey(videoPath), emptySet()) ?: emptySet())
      .mapNotNull { entry ->
        val pos = entry.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
        if (pos < 0) return@mapNotNull null
        VideoBookmark(pos, entry.substringAfter('|', ""))
      }
      .sortedBy { it.positionMs }
      .take(50)
}

fun saveBookmarks(prefs: SharedPreferences, videoPath: String, bookmarks: List<VideoBookmark>) {
  prefs.edit()
      .putStringSet(
          bookmarkPrefsKey(videoPath),
          bookmarks.take(50).map { "${it.positionMs}|${it.label}" }.toSet())
      .apply()
}

fun screenshotFileName(videoPath: String, extension: String): String {
  val base =
      File(videoPath).nameWithoutExtension.ifEmpty { "video" }.take(24).replace(Regex("[^A-Za-z0-9_-]"), "_")
  val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
  return "VIDMAX_${base}_${stamp}.$extension"
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              val values =
                  ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VidMax")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                  }
              val uri =
                  context.contentResolver.insert(
                      MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
              context.contentResolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return@runCatching false
              } ?: return@runCatching false
              values.clear()
              values.put(MediaStore.Images.Media.IS_PENDING, 0)
              context.contentResolver.update(uri, values, null, null)
            } else {
              @Suppress("DEPRECATION")
              val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "VidMax")
              dir.mkdirs()
              val out = File(dir, displayName)
              out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
              MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
            }
            true
          }
          .getOrDefault(false)
    }

suspend fun saveImageFileToGallery(
    context: Context,
    src: File,
    displayName: String,
    mimeType: String
): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
            if (!src.exists()) return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              val values =
                  ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VidMax")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                  }
              val uri =
                  context.contentResolver.insert(
                      MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
              context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
              } ?: return@runCatching false
              values.clear()
              values.put(MediaStore.Images.Media.IS_PENDING, 0)
              context.contentResolver.update(uri, values, null, null)
            } else {
              @Suppress("DEPRECATION")
              val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "VidMax")
              dir.mkdirs()
              val out = File(dir, displayName)
              src.copyTo(out, overwrite = true)
              MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
            }
            true
          }
          .getOrDefault(false)
    }
