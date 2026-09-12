package com.vidmax.player.data.repository

import android.content.SharedPreferences

/**
 * Persists M3U/M3U8 source URLs per playlist id (no database migration
 * needed): enables source badges and refresh-from-URL. Playlist rows
 * themselves stay untouched.
 */
object M3uSourceStore {

  private const val KEY: String = "m3u_source_urls"

  private fun readAll(prefs: SharedPreferences): MutableMap<Int, String> {
    return runCatching {
      val raw = prefs.getString(KEY, null) ?: return mutableMapOf()
      val json = org.json.JSONObject(raw)
      val out = mutableMapOf<Int, String>()
      json.keys().forEach { k ->
        k.toIntOrNull()?.let { id -> out[id] = json.optString(k, "") }
      }
      out
    }.getOrDefault(mutableMapOf())
  }

  private fun persist(prefs: SharedPreferences, map: Map<Int, String>) {
    runCatching {
      val json = org.json.JSONObject()
      map.forEach { (id, url) -> json.put(id.toString(), url) }
      prefs.edit().putString(KEY, json.toString()).apply()
    }
  }

  fun getUrl(prefs: SharedPreferences, playlistId: Int): String? {
    return readAll(prefs)[playlistId]?.takeIf { it.isNotEmpty() }
  }

  fun setUrl(prefs: SharedPreferences, playlistId: Int, url: String) {
    val map = readAll(prefs)
    map[playlistId] = url
    persist(prefs, map)
  }

  fun removeId(prefs: SharedPreferences, playlistId: Int) {
    val map = readAll(prefs)
    if (map.remove(playlistId) != null) persist(prefs, map)
  }

  fun allIds(prefs: SharedPreferences): Set<Int> {
    return readAll(prefs).keys
  }
}
