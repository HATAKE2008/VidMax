package com.vidmax.player.utils

/** A single timed cue from an external subtitle file. */
data class SubtitleItem(
  val startTimeMs: Long,
  val endTimeMs: Long,
  val text: String
)
