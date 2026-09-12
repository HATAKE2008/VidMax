package com.vidmax.player.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Real per-app language system (AndroidX per-app locales).
 *
 * Single source of truth, persisted as `app_locale` in `vidmax_settings` and
 * applied via [AppCompatDelegate.setApplicationLocales]. "System Default"
 * clears the override (empty list) so the device locale wins.
 *
 * MainActivity extends AppCompatActivity so the override also works below
 * Android 13. On Android 13+ it additionally surfaces in the system
 * Settings → App languages page automatically.
 *
 * Only locales with real VidMax translations are listed — every entry here
 * visibly changes the app UI (values/ + values-bn/ + values-hi/).
 */
object AppLocale {

  const val SYSTEM_DEFAULT = "system"

  data class SupportedLocale(val tag: String, val displayName: String)

  /** Only locales with complete VidMax translations — no fake entries. */
  val supported: List<SupportedLocale> = listOf(
      SupportedLocale(SYSTEM_DEFAULT, "System Default"),
      SupportedLocale("en", "English"),
      SupportedLocale("bn", "বাংলা (Bengali)"),
      SupportedLocale("hi", "हिन्दी (Hindi)"),
  )

  fun displayNameFor(tag: String): String =
      supported.firstOrNull { it.tag == tag }?.displayName ?: "System Default"

  fun isSupported(tag: String): Boolean = supported.any { it.tag == tag }

  /** Applies the locale override immediately (triggers activity recreate). */
  fun apply(tag: String) {
    val locales = if (tag == SYSTEM_DEFAULT || tag.isBlank()) {
      LocaleListCompat.getEmptyLocaleList()
    } else {
      LocaleListCompat.forLanguageTags(tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
  }

  /** Reads the currently applied override (empty → system default). */
  fun currentTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    return locales.toLanguageTags().takeIf { it.isNotBlank() } ?: SYSTEM_DEFAULT
  }
}
