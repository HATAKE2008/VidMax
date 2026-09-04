package com.vidmax.player.data.repository

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * P4b — local Settings Export/Import.
 *
 * Source of truth stays `vidmax_settings` SharedPreferences. This helper only
 * serializes/deserializes scalar settings to a versioned JSON document; it
 * never touches media, playlists, favorites, recents or the network.
 *
 * Format:
 * ```
 * {"app":"VidMax","formatVersion":1,"settings":{"key":{"t":"bool","v":true}}}
 * ```
 * Type tags: bool, string, int, long, float.
 *
 * Intentionally EXCLUDED from backups (never exported, never imported even if
 * a hand-crafted file contains them):
 * - per-video resume positions (`resume_pos_*`, session state)
 * - per-video bookmarks (`video_bookmarks_*`, separate persistent model)
 * - favorites (`favorite_videos`, `favorites`) and last-played references
 *   (`recent_video_*`, `recent_music_*`)
 * - every StringSet value (only favorites/bookmarks use sets today)
 */
object SettingsBackup {

  const val PREFS_NAME: String = "vidmax_settings"
  const val FORMAT_VERSION: Int = 1
  const val APP_ID: String = "VidMax"

  /** Hard cap for imported files; real backups are a few KB. */
  const val MAX_IMPORT_BYTES: Int = 128 * 1024

  private val EXCLUDED_KEYS: Set<String> = setOf(
      "favorite_videos",
      "favorites",
      "recent_video_title",
      "recent_video_path",
      "recent_music_title",
      "recent_music_path"
  )

  private val EXCLUDED_PREFIXES: List<String> = listOf(
      "resume_pos_",
      "video_bookmarks_"
  )

  sealed interface ImportResult {
    data class Applied(val appliedCount: Int) : ImportResult
    data class Invalid(val reason: String) : ImportResult
  }

  fun isExcluded(key: String, value: Any?): Boolean {
    if (key in EXCLUDED_KEYS) return true
    if (EXCLUDED_PREFIXES.any { key.startsWith(it) }) return true
    if (value is Set<*>) return true
    return false
  }

  /** Serializes all exportable `vidmax_settings` entries, preserving types. */
  fun buildBackupJson(prefs: SharedPreferences): String {
    val settings = JSONObject()
    for ((key, value) in prefs.all) {
      if (isExcluded(key, value)) continue
      val entry = JSONObject()
      when (value) {
        is Boolean -> { entry.put("t", "bool"); entry.put("v", value) }
        is String -> { entry.put("t", "string"); entry.put("v", value) }
        is Int -> { entry.put("t", "int"); entry.put("v", value) }
        is Long -> { entry.put("t", "long"); entry.put("v", value) }
        is Float -> { entry.put("t", "float"); entry.put("v", value.toDouble()) }
        is Double -> { entry.put("t", "float"); entry.put("v", value) }
        else -> continue
      }
      settings.put(key, entry)
    }
    return JSONObject()
        .put("app", APP_ID)
        .put("formatVersion", FORMAT_VERSION)
        .put("settings", settings)
        .toString()
  }

  /**
   * Validates and applies a backup document to `vidmax_settings`.
   * Unknown keys and unknown type tags are ignored (forward tolerance);
   * missing keys keep their current values. Only scalar settings are written.
   */
  fun applyBackupJson(prefs: SharedPreferences, raw: String): ImportResult {
    val root: JSONObject
    try {
      root = JSONObject(raw)
    } catch (e: Exception) {
      return ImportResult.Invalid("malformed")
    }
    if (!root.has("app") || root.optString("app", "") != APP_ID) {
      return ImportResult.Invalid("app")
    }
    if (!root.has("formatVersion") || root.optInt("formatVersion", -1) != FORMAT_VERSION) {
      return ImportResult.Invalid("version")
    }
    val settings: JSONObject = try {
      root.getJSONObject("settings")
    } catch (e: Exception) {
      return ImportResult.Invalid("settings")
    }
    val editor = prefs.edit()
    var applied = 0
    val names = settings.keys()
    while (names.hasNext()) {
      val key = names.next()
      if (key in EXCLUDED_KEYS || EXCLUDED_PREFIXES.any { key.startsWith(it) }) continue
      val entry: JSONObject = try {
        settings.getJSONObject(key)
      } catch (e: Exception) {
        continue
      }
      val tag = entry.optString("t", "")
      if (!entry.has("v")) continue
      val rawValue: Any = try {
        entry.get("v")
      } catch (e: Exception) {
        continue
      }
      when (tag) {
        "bool" -> {
          if (rawValue is Boolean) {
            editor.putBoolean(key, rawValue)
            applied++
          }
        }
        "string" -> {
          if (rawValue is String) {
            editor.putString(key, rawValue)
            applied++
          }
        }
        "int" -> {
          val asLong = (rawValue as? Number)?.toLong() ?: continue
          if (asLong < Int.MIN_VALUE || asLong > Int.MAX_VALUE) continue
          editor.putInt(key, asLong.toInt())
          applied++
        }
        "long" -> {
          val asLong = (rawValue as? Number)?.toLong() ?: continue
          editor.putLong(key, asLong)
          applied++
        }
        "float" -> {
          // JSONObject.NULL must not be treated as a number.
          if (rawValue === JSONObject.NULL) continue
          val asNumber = rawValue as? Number ?: continue
          editor.putFloat(key, asNumber.toFloat())
          applied++
        }
        else -> {
          // Unknown type tag: ignore for forward compatibility.
        }
      }
    }
    return try {
      editor.apply()
      ImportResult.Applied(applied)
    } catch (e: Exception) {
      ImportResult.Invalid("apply")
    }
  }
}
