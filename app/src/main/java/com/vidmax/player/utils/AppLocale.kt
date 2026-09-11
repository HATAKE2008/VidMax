package com.vidmax.player.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Real per-app language system (AndroidX per-app locales).
 *
 * VidMax previously had no language preference — this is the single source of
 * truth, persisted as `app_locale` in `vidmax_settings` and applied via
 * [AppCompatDelegate.setApplicationLocales]. "System Default" clears the
 * override (empty list) so the device locale wins.
 *
 * Works on all VidMax-supported versions (minSdk 24): AppCompat backports
 * the API below Android 13; on Android 13+ it also surfaces in the system
 * Settings → App languages page automatically.
 */
object AppLocale {

  const val SYSTEM_DEFAULT = "system"

  data class SupportedLocale(val tag: String, val displayName: String)

  /** Only real BCP-47 tags applied via LocaleListCompat — no fake entries. */
  val supported: List<SupportedLocale> = listOf(
      SupportedLocale(SYSTEM_DEFAULT, "System Default"),
      SupportedLocale("en", "English"),
      SupportedLocale("bn", "বাংলা (Bengali)"),
      SupportedLocale("hi", "हिन्दी (Hindi)"),
      SupportedLocale("ur", "اردو (Urdu)"),
      SupportedLocale("ar", "العربية (Arabic)"),
      SupportedLocale("es", "Español (Spanish)"),
      SupportedLocale("fr", "Français (French)"),
      SupportedLocale("de", "Deutsch (German)"),
      SupportedLocale("pt", "Português (Portuguese)"),
      SupportedLocale("ru", "Русский (Russian)"),
      SupportedLocale("tr", "Türkçe (Turkish)"),
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
