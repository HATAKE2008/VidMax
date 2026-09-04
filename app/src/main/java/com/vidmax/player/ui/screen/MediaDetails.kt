package com.vidmax.player.ui.screen

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoMetadata(
  val videoMime: String? = null,
  val width: Int = 0,
  val height: Int = 0,
  val frameRate: Float = 0f,
  val videoBitrate: Int = 0,
  val audioMime: String? = null,
  val sampleRate: Int = 0,
  val channels: Int = 0,
  val audioBitrate: Int = 0,
  val durationUs: Long = 0L,
)

suspend fun loadVideoMetadata(path: String): VideoMetadata = withContext(Dispatchers.IO) {
  var videoMime: String? = null
  var width = 0
  var height = 0
  var frameRate = 0f
  var videoBitrate = 0
  var audioMime: String? = null
  var sampleRate = 0
  var channels = 0
  var audioBitrate = 0
  var durationUs = 0L
  var gotVideo = false
  var gotAudio = false
  val extractor = MediaExtractor()
  try {
    runCatching { extractor.setDataSource(path) }.onFailure { return@withContext VideoMetadata() }
    for (i in 0 until extractor.trackCount) {
      val format = runCatching { extractor.getTrackFormat(i) }.getOrNull() ?: continue
      val mime = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull() ?: continue
      if (mime.startsWith("video/") && !gotVideo) {
        gotVideo = true
        videoMime = mime
        if (format.containsKey(MediaFormat.KEY_WIDTH)) width = format.getInteger(MediaFormat.KEY_WIDTH)
        if (format.containsKey(MediaFormat.KEY_HEIGHT)) height = format.getInteger(MediaFormat.KEY_HEIGHT)
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE)
        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) videoBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
        if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION)
      } else if (mime.startsWith("audio/") && !gotAudio) {
        gotAudio = true
        audioMime = mime
        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) audioBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
      }
      if (gotVideo && gotAudio) break
    }
  } catch (e: Exception) {
  } finally {
    runCatching { extractor.release() }
  }
  VideoMetadata(videoMime, width, height, frameRate, videoBitrate, audioMime, sampleRate, channels, audioBitrate, durationUs)
}

fun shortCodecName(mime: String?): String {
  if (mime == null) return "Unknown"
  val suffix = mime.substringAfter('/', "")
  if (suffix.isEmpty()) return mime
  return when (suffix.lowercase(Locale.US)) {
    "avc" -> "H.264"
    "hevc" -> "H.265"
    "mp4v-es" -> "MPEG-4"
    "mpeg2-video", "mpeg2" -> "MPEG-2"
    "x-vnd.on2.vp8" -> "VP8"
    "x-vnd.on2.vp9" -> "VP9"
    "av01" -> "AV1"
    "mpeg4-generic", "mp4a-latm" -> "AAC"
    "ac3" -> "AC-3"
    "eac3" -> "E-AC-3"
    "vorbis" -> "Vorbis"
    "opus" -> "Opus"
    "flac" -> "FLAC"
    "pcm", "raw" -> "PCM"
    else -> suffix.uppercase(Locale.US)
  }
}

fun formatBitrate(bps: Int): String {
  if (bps <= 0) return "Unknown"
  return if (bps >= 1_000_000) String.format(Locale.US, "%.1f Mbps", bps / 1_000_000f)
  else String.format(Locale.US, "%d kbps", bps / 1000)
}

@Composable
fun VideoDetailsDialog(
    video: VideoItem,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
  var meta by remember { mutableStateOf<VideoMetadata?>(null) }
  LaunchedEffect(video.path) {
    meta = runCatching { loadVideoMetadata(video.path) }.getOrNull()
  }
  val file = remember(video.path) { File(video.path) }
  val modified = remember(video.path) {
    val t = runCatching { file.lastModified() }.getOrDefault(0L)
    if (t > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t)) else "Unknown"
  }
  val m = meta
  val w = if (m != null && m.width > 0) m.width else video.width
  val h = if (m != null && m.height > 0) m.height else video.height
  val aspect = if (w > 0 && h > 0) String.format(Locale.US, "%.2f:1", w.toFloat() / h.toFloat()) else "Unknown"
  val ext = file.extension.ifEmpty { video.path.substringAfterLast('.', "") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Details", fontWeight = FontWeight.Bold) },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
          VideoDetailRow("Filename", video.title)
          VideoDetailRow("Location", video.path)
          VideoDetailRow("Size", viewModel.formatSize(video.size))
          VideoDetailRow("Modified", modified)
          VideoDetailRow("Duration", viewModel.formatDuration(video.duration))
          VideoDetailRow("Resolution", if (w > 0 && h > 0) "${w}x${h} (${viewModel.getResolutionLabel(w, h)})" else "Unknown")
          VideoDetailRow("Aspect ratio", aspect)
          VideoDetailRow("Frame rate", if (m != null && m.frameRate > 0) String.format(Locale.US, "%.2f fps", m.frameRate) else "Unknown")
          VideoDetailRow("Video codec", shortCodecName(m?.videoMime))
          VideoDetailRow("Video bitrate", formatBitrate(m?.videoBitrate ?: 0))
          VideoDetailRow("Audio codec", shortCodecName(m?.audioMime))
          VideoDetailRow(
              "Sample rate",
              if (m != null && m.sampleRate > 0) "${m.sampleRate} Hz" else "Unknown")
          VideoDetailRow(
              "Channels",
              if (m != null && m.channels > 0) m.channels.toString() else "Unknown")
          VideoDetailRow(
              "Container",
              if (ext.isNotEmpty()) ext.uppercase(Locale.US) else "Unknown")
          VideoDetailRow("MIME type", m?.videoMime ?: "video/*")
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun VideoDetailRow(label: String, value: String) {
  Column {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold)
    Text(
        text = value,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis)
  }
}

@Composable
fun RenameVideoDialog(
    currentBaseName: String,
    extension: String,
    error: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var text by remember(currentBaseName) { mutableStateOf(currentBaseName) }
  AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      title = { Text("Rename", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
              value = text,
              onValueChange = { text = it },
              label = { Text("Name") },
              suffix = { Text(".$extension", color = MaterialTheme.colorScheme.onSurfaceVariant) },
              singleLine = true,
              enabled = !busy,
              isError = error != null,
              supportingText = {
                Text(
                    text = error ?: "Extension .$extension is kept automatically",
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
              },
              modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        TextButton(
            enabled = !busy && text.isNotBlank(),
            onClick = { onConfirm(text.trim()) }) {
          Text("Rename")
        }
      },
      dismissButton = {
        TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
      })
}
