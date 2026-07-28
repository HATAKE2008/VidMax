package com.vidmax.player.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer

// সাবটাইটেলের ডেটা মডেল
data class SubtitleItem(val startTimeMs: Long, val endTimeMs: Long, val text: String)

object SubtitleParser {

  // ১. স্মার্ট পার্সার: ফাইল নিজে থেকে ডিটেক্ট করবে এটা SRT, VTT নাকি ASS
  fun parseExternalSubtitle(context: Context, uri: Uri): List<SubtitleItem> {
    try {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
      val reader = BufferedReader(InputStreamReader(inputStream))
      val lines = reader.readLines()
      reader.close()

      if (lines.isEmpty()) return emptyList()

      // ফরম্যাট চেকিং
      return when {
        lines[0].contains("WEBVTT") -> parseVttLines(lines)
        lines.any { it.contains("[Script Info]") || it.startsWith("Dialogue:") } ->
            parseAssLines(lines)
        else -> parseSrtLines(lines)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      return emptyList()
    }
  }

  // --- SRT পার্সিং ---
  private fun parseSrtLines(lines: List<String>): List<SubtitleItem> {
    val subtitles = mutableListOf<SubtitleItem>()
    var startTime = 0L
    var endTime = 0L
    var text = StringBuilder()

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.contains("-->")) {
        val parts = trimmed.split("-->")
        if (parts.size == 2) {
          startTime = parseTime(parts[0].trim())
          endTime = parseTime(parts[1].trim())
        }
        text.clear()
      } else if (trimmed.isNotEmpty() && !trimmed.matches(Regex("\\d+"))) {
        if (text.isNotEmpty()) text.append("\n")
        text.append(trimmed)
      } else if (trimmed.isEmpty() && text.isNotEmpty()) {
        subtitles.add(SubtitleItem(startTime, endTime, text.toString()))
        text.clear()
      }
    }
    if (text.isNotEmpty()) {
      subtitles.add(SubtitleItem(startTime, endTime, text.toString()))
    }
    return subtitles
  }

  // --- VTT পার্সিং ---
  private fun parseVttLines(lines: List<String>): List<SubtitleItem> {
    val subtitles = mutableListOf<SubtitleItem>()
    var startTime = 0L
    var endTime = 0L
    var text = StringBuilder()

    for (line in lines) {
      val trimmed = line.trim()
      if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("NOTE")) continue

      if (trimmed.contains("-->")) {
        val parts = trimmed.split("-->")
        if (parts.size == 2) {
          // VTT তে টাইম ফরমেট 00:00.000 হতে পারে, parseTime সেটা হ্যান্ডেল করবে
          startTime = parseTime(parts[0].trim().substringBefore(" align:"))
          endTime = parseTime(parts[1].trim().substringBefore(" align:"))
        }
        text.clear()
      } else if (trimmed.isNotEmpty() && !trimmed.matches(Regex("\\d+"))) {
        // ট্যাগ রিমুভ (যেমন: <c.color>text</c>)
        val cleanText = trimmed.replace(Regex("<.*?>"), "")
        if (text.isNotEmpty()) text.append("\n")
        text.append(cleanText)
      } else if (trimmed.isEmpty() && text.isNotEmpty()) {
        subtitles.add(SubtitleItem(startTime, endTime, text.toString()))
        text.clear()
      }
    }
    if (text.isNotEmpty()) {
      subtitles.add(SubtitleItem(startTime, endTime, text.toString()))
    }
    return subtitles
  }

  // --- ASS / SSA পার্সিং ---
  private fun parseAssLines(lines: List<String>): List<SubtitleItem> {
    val subtitles = mutableListOf<SubtitleItem>()
    for (line in lines) {
      if (line.startsWith("Dialogue:")) {
        // ফরমেট: Dialogue: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        val content = line.substringAfter("Dialogue:").trim()
        val parts = content.split(",", limit = 10) // ১০ ভাগে ভাগ করবো, শেষের সবটুকু Text

        if (parts.size >= 10) {
          val startTime = parseTime(parts[1].trim())
          val endTime = parseTime(parts[2].trim())
          var text = parts[9].trim()

          // ASS এর হাবিজাবি ট্যাগগুলো মুছে ফেলা (যেমন: {\an8}, {\b1}) এবং \N কে লাইন ব্রেক এ
          // কনভার্ট করা
          text = text.replace(Regex("\\{.*?\\}"), "").replace("\\N", "\n").replace("\\n", "\n")

          subtitles.add(SubtitleItem(startTime, endTime, text))
        }
      }
    }
    return subtitles
  }

  // ২. ভিডিও ফাইলের ভেতর থেকে এমবেডেড সাবটাইটেল বের করার ফাংশন (আগেরটাই)
  fun extractEmbeddedSubtitles(context: Context, videoUri: Uri): List<SubtitleItem> {
    val subtitles = mutableListOf<SubtitleItem>()
    val extractor = MediaExtractor()

    try {
      extractor.setDataSource(context, videoUri, null)
      var subtitleTrackIndex = -1

      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

        if (mime.startsWith("text/") ||
            mime.contains("subrip") ||
            mime.contains("vtt") ||
            mime.contains("tx3g")) {
          subtitleTrackIndex = i
          break
        }
      }

      if (subtitleTrackIndex != -1) {
        extractor.selectTrack(subtitleTrackIndex)
        val buffer = ByteBuffer.allocate(1024 * 512)

        var currentStartTime = -1L
        var currentText = ""

        while (true) {
          val sampleSize = extractor.readSampleData(buffer, 0)
          if (sampleSize < 0) break

          val timeMs = extractor.sampleTime / 1000
          val bytes = ByteArray(sampleSize)
          buffer.get(bytes, 0, sampleSize)
          buffer.clear()

          val text = String(bytes, Charsets.UTF_8).trim()

          if (currentStartTime != -1L && currentText.isNotEmpty()) {
            subtitles.add(SubtitleItem(currentStartTime, timeMs, currentText))
          }

          if (text.isNotEmpty()) {
            currentStartTime = timeMs
            currentText = text
          } else {
            currentStartTime = -1L
            currentText = ""
          }
          extractor.advance()
        }

        if (currentStartTime != -1L && currentText.isNotEmpty()) {
          subtitles.add(SubtitleItem(currentStartTime, currentStartTime + 5000L, currentText))
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      extractor.release()
    }

    return subtitles
  }

  // টাইম কনভার্ট করার ম্যাজিক ফাংশন (SRT, VTT, ASS সবার টাইম হ্যান্ডেল করতে পারবে)
  private fun parseTime(timeStr: String): Long {
    try {
      val cleanTime = timeStr.replace(",", ".").trim()
      val parts = cleanTime.split(":")

      if (parts.size >= 2) {
        val hours = if (parts.size == 3) parts[0].toLong() else 0L
        val minsIndex = if (parts.size == 3) 1 else 0
        val mins = parts[minsIndex].toLong()

        val secsParts = parts[minsIndex + 1].split(".")
        val secs = secsParts[0].toLong()

        var ms = 0L
        if (secsParts.size > 1) {
          // ASS এ মিলি-সেকেন্ড ২ ডিজিট থাকে, তাই সেটা ৩ ডিজিট বানানোর জন্য
          val msStr = secsParts[1].padEnd(3, '0').substring(0, 3)
          ms = msStr.toLong()
        }
        return (hours * 3600000) + (mins * 60000) + (secs * 1000) + ms
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return 0L
  }
}
