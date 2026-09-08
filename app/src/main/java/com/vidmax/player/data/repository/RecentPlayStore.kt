package com.vidmax.player.data.repository

import android.content.SharedPreferences
import java.io.File

/**
 * Prefs-backed recently-played history (REX RecentlyPlayedOps concepts
 * adapted to VidMax's SharedPreferences storage, so no database migration
 * is needed: newest first, upsert-by-path, capped, rename/move/delete
 * synchronized, missing files pruned).
 */
object RecentPlayStore {

  const val HISTORY_KEY: String = "recent_play_history"
  const val HISTORY_MAX: Int = 50

  data class RecentEntry(val path: String, val title: String, val timestamp: Long)

  fun read(prefs: SharedPreferences): List<RecentEntry> {
    return runCatching {
      val raw = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
      val arr = org.json.JSONArray(raw)
      val out = mutableListOf<RecentEntry>()
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val path = o.optString("path", "")
        if (path.isEmpty()) continue
        out.add(RecentEntry(path, o.optString("title", path), o.optLong("ts", 0L)))
      }
      out.sortedByDescending { it.timestamp }
    }.getOrDefault(emptyList())
  }

  private fun persist(prefs: SharedPreferences, history: List<RecentEntry>) {
    runCatching {
      val arr = org.json.JSONArray()
      history.take(HISTORY_MAX).forEach {
        arr.put(org.json.JSONObject()
            .put("path", it.path)
            .put("title", it.title)
            .put("ts", it.timestamp))
      }
      prefs.edit().putString(HISTORY_KEY, arr.toString()).apply()
    }
  }

  /** Records a playback start: moves any existing entry to the top. */
  fun record(prefs: SharedPreferences, path: String, title: String) {
    if (path.isEmpty()) return
    val updated = (listOf(RecentEntry(path, title, System.currentTimeMillis())) +
        read(prefs).filter { it.path != path })
        .take(HISTORY_MAX)
    persist(prefs, updated)
  }

  /** Follows renames/moves so the entry tracks the file. */
  fun migratePath(prefs: SharedPreferences, oldPath: String, newPath: String, newTitle: String) {
    if (oldPath == newPath) return
    val updated = read(prefs).map {
      if (it.path == oldPath) it.copy(path = newPath, title = newTitle) else it
    }
    persist(prefs, updated)
  }

  /** Drops entries for deleted files. */
  fun removePaths(prefs: SharedPreferences, paths: Set<String>) {
    if (paths.isEmpty()) return
    val updated = read(prefs).filterNot { paths.contains(it.path) }
    persist(prefs, updated)
  }

  /**
   * Removes entries whose files no longer exist (REX auto-remove). Returns
   * true when anything was pruned.
   */
  fun pruneMissing(prefs: SharedPreferences): Boolean {
    val current = read(prefs)
    if (current.isEmpty()) return false
    val kept = current.filter {
      runCatching { File(it.path).exists() }.getOrDefault(true)
    }
    if (kept.size == current.size) return false
    persist(prefs, kept)
    return true
  }

  fun clear(prefs: SharedPreferences) {
    runCatching { prefs.edit().remove(HISTORY_KEY).apply() }
  }
}
