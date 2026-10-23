package com.vidmax.player.data.repository

import android.content.SharedPreferences
import org.json.JSONObject

/** Display-only metadata parsed from an M3U source (never playback data). */
data class M3uChannelMeta(
    val logo: String? = null,
    val group: String? = null,
)

/**
 * Keeps tvg-logo / group-title values outside Room: the playlist item table and
 * its M3U parser stay untouched, so playback and existing databases are
 * unaffected. Missing entries simply fall back to the channel icon.
 */
object M3uMetadataStore {

  private const val KEY: String = "m3u_channel_metadata"

  private val LOGO = Regex("""tvg-logo\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
  private val GROUP = Regex("""group-title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

  private fun readAll(prefs: SharedPreferences): MutableMap<Int, JSONObject> {
    return runCatching {
      val raw = prefs.getString(KEY, null) ?: return mutableMapOf()
      val root = JSONObject(raw)
      val out = mutableMapOf<Int, JSONObject>()
      root.keys().forEach { key ->
        key.toIntOrNull()?.let { id ->
          root.optJSONObject(key)?.let { out[id] = it }
        }
      }
      out
    }.getOrDefault(mutableMapOf())
  }

  private fun persist(prefs: SharedPreferences, map: Map<Int, JSONObject>) {
    runCatching {
      val root = JSONObject()
      map.forEach { (id, value) -> root.put(id.toString(), value) }
      prefs.edit().putString(KEY, root.toString()).apply()
    }
  }

  fun getAll(prefs: SharedPreferences, playlistId: Int): Map<String, M3uChannelMeta> {
    val obj = readAll(prefs)[playlistId] ?: return emptyMap()
    val out = linkedMapOf<String, M3uChannelMeta>()
    obj.keys().forEach { url ->
      val entry = obj.optJSONObject(url) ?: return@forEach
      val logo = entry.optString("logo", "").trim().takeIf { it.isNotEmpty() }
      val group = entry.optString("group", "").trim().takeIf { it.isNotEmpty() }
      if (logo != null || group != null) out[url] = M3uChannelMeta(logo, group)
    }
    return out
  }

  fun setFromText(prefs: SharedPreferences, playlistId: Int, text: String) {
    val parsed = parse(text)
    val map = readAll(prefs)
    if (parsed.isEmpty()) {
      map.remove(playlistId)
    } else {
      val obj = JSONObject()
      parsed.forEach { (url, meta) ->
        obj.put(
            url,
            JSONObject().apply {
              meta.logo?.let { put("logo", it) }
              meta.group?.let { put("group", it) }
            })
      }
      map[playlistId] = obj
    }
    persist(prefs, map)
  }

  fun removeId(prefs: SharedPreferences, playlistId: Int) {
    val map = readAll(prefs)
    if (map.remove(playlistId) != null) persist(prefs, map)
  }

  /** Extracts per-URL logo and group values without touching entry parsing. */
  fun parse(text: String): Map<String, M3uChannelMeta> {
    val out = linkedMapOf<String, M3uChannelMeta>()
    var logo: String? = null
    var group: String? = null
    text.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      when {
        line.startsWith("#EXTINF", ignoreCase = true) -> {
          logo = LOGO.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
          group = GROUP.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        line.startsWith("#EXTGRP:", ignoreCase = true) -> {
          group = line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
        }
        line.startsWith("http://", ignoreCase = true) ||
            line.startsWith("https://", ignoreCase = true) -> {
          if (logo != null || group != null) out[line] = M3uChannelMeta(logo, group)
          logo = null
          group = null
        }
      }
    }
    return out
  }
}
