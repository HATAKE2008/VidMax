package com.vidmax.player.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.provider.MediaStore
import com.vidmax.player.data.model.FolderItem
import com.vidmax.player.data.model.VideoItem

class VideoRepository(private val contentResolver: ContentResolver) {

fun getAllVideos(): List<VideoItem> {
val videos: MutableList<VideoItem> = mutableListOf()
val projection: Array<String> = arrayOf(
MediaStore.Video.Media._ID,
MediaStore.Video.Media.TITLE,
MediaStore.Video.Media.DATA,
MediaStore.Video.Media.DURATION,
MediaStore.Video.Media.SIZE,
MediaStore.Video.Media.WIDTH,
MediaStore.Video.Media.HEIGHT,
MediaStore.Video.Media.DATE_ADDED,
MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
MediaStore.Video.Media.BUCKET_ID
)
val sortOrder: String = MediaStore.Video.Media.DATE_ADDED + " DESC"
val cursor: Cursor? = contentResolver.query(
MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
projection,
null,
null,
sortOrder
)
cursor?.use { c: Cursor ->
val idCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
val titleCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
val dataCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
val durCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
val sizeCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
val widthCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
val heightCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
val dateCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
val bucketCol: Int = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
while (c.moveToNext()) {
val id: Long = c.getLong(idCol)
val title: String = c.getString(titleCol) ?: "Unknown"
val path: String = c.getString(dataCol) ?: ""
val duration: Long = c.getLong(durCol)
val size: Long = c.getLong(sizeCol)
val width: Int = c.getInt(widthCol)
val height: Int = c.getInt(heightCol)
val dateAdded: Long = c.getLong(dateCol)
val folderName: String = c.getString(bucketCol) ?: "Unknown"
val folderPath: String =
if (path.contains("/")) path.substringBeforeLast("/") else ""
if (path.isNotEmpty() && duration > 0) {
videos.add(
VideoItem(
id = id,
title = title,
path = path,
duration = duration,
size = size,
width = width,
height = height,
dateAdded = dateAdded,
folderPath = folderPath,
folderName = folderName
)
)
}
}
}
return videos
}

fun getFolders(videos: List<VideoItem>): List<FolderItem> {
val folderMap: MutableMap<String, MutableList<VideoItem>> = mutableMapOf()
for (video: VideoItem in videos) {
val existing: MutableList<VideoItem> =
folderMap.getOrPut(video.folderPath) { mutableListOf() }
existing.add(video)
}
val folders: MutableList<FolderItem> = mutableListOf()
for (entry: Map.Entry<String, MutableList<VideoItem>> in folderMap.entries) {
val list: List<VideoItem> = entry.value
val totalSize: Long = list.fold(0L) { acc: Long, v: VideoItem -> acc + v.size }
folders.add(
FolderItem(
path = entry.key,
name = list.first().folderName,
videoCount = list.size,
totalSize = totalSize,
firstVideoPath = list.first().path
)
)
}
return folders.sortedBy { folder: FolderItem -> folder.name }
}

fun formatDuration(ms: Long): String {
val totalSec: Long = ms / 1000
val hours: Long = totalSec / 3600
val minutes: Long = (totalSec % 3600) / 60
val seconds: Long = totalSec % 60
return if (hours > 0) {
String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
} else {
String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}
}

fun formatSize(bytes: Long): String {
return when {
bytes >= 1_073_741_824L ->
String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
bytes >= 1_048_576L ->
String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
else ->
String.format(java.util.Locale.US, "%.1f KB", bytes / 1_024.0)
}
}

fun getResolutionLabel(width: Int, height: Int): String {
val maxDim: Int = maxOf(width, height)
return when {
maxDim >= 3840 -> "4K"
maxDim >= 1920 -> "1080p"
maxDim >= 1280 -> "720p"
maxDim >= 854  -> "480p"
else           -> "SD"
}
}
}