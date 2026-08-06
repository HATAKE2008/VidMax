package com.vidmax.player.utils

import com.vidmax.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * In-app update checker for VidMax — mirrors the approach used by BloomeeTunes.
 *
 * It queries the GitHub releases API for the latest release of this repository
 * and compares its version tag against the locally installed app version
 * (BuildConfig.VERSION_NAME / VERSION_CODE). A tag like `v1.2.0+3` is parsed
 * as version "1.2.0" with build "3".
 */
object UpdateChecker {

    const val REPO = "HATAKE2008/vidamx"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    data class AppUpdateInfo(
        val tagName: String,
        val versionName: String,
        val buildNumber: Int?,
        val releaseNotes: String,
        val downloadUrl: String,
        val releasePageUrl: String,
        val publishedAt: String,
    )

    sealed class CheckResult {
        data class Success(val info: AppUpdateInfo) : CheckResult()
        object NoRelease : CheckResult()
        object Failed : CheckResult()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** Returns true when the remote release is newer than the installed app. */
    fun isNewerVersion(info: AppUpdateInfo): Boolean {
        val current = parseVersion(BuildConfig.VERSION_NAME)
        val remote = parseVersion(info.versionName)
        val maxLen = maxOf(current.size, remote.size)
        for (i in 0 until maxLen) {
            val cur = current.getOrElse(i) { 0 }
            val rem = remote.getOrElse(i) { 0 }
            if (rem > cur) return true
            if (rem < cur) return false
        }
        val remoteBuild = info.buildNumber ?: return false
        return remoteBuild > BuildConfig.VERSION_CODE
    }

    /**
     * Fetches the latest release from GitHub.
     *
     * - [CheckResult.Success] when a release exists (even if it is not newer)
     * - [CheckResult.NoRelease] when the repo has no releases yet (HTTP 404)
     * - [CheckResult.Failed] on any network / parse error
     *
     * Strategy: try the GitHub API first. If that is unavailable (rate limit,
     * blocked region, timeout), fall back to the public releases page and read
     * the latest tag from the redirect URL, which is far more reliable on
     * restricted networks.
     */
    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        tryFetchFromApi() ?: fetchFromReleasePage()
    }

    /** Returns null when the API is unreachable / rate-limited, so the caller can fall back. */
    private fun tryFetchFromApi(): CheckResult? = try {
        val request = Request.Builder()
            .url(API_LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "VidMax-Android")
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            when {
                code == 404 -> CheckResult.NoRelease
                !response.isSuccessful -> null
                else -> {
                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name", "")
                    if (tag.isBlank()) return@use null
                    val versionName = tag.removePrefix("v").removePrefix("V")
                        .substringBefore("+")
                    val buildNumber = tag.substringAfter("+", "")
                        .toIntOrNull()
                    val releaseNotes = json.optString("body", "").trim()
                    val releasePageUrl = json.optString("html_url", "").ifEmpty { RELEASES_URL }
                    val publishedAt = json.optString("published_at", "")

                    var downloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name", "").lowercase()
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    CheckResult.Success(
                        AppUpdateInfo(
                            tagName = tag,
                            versionName = versionName,
                            buildNumber = buildNumber,
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl,
                            releasePageUrl = releasePageUrl,
                            publishedAt = publishedAt,
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Fallback: `https://github.com/<repo>/releases/latest` redirects to the
     * actual release page (e.g. .../releases/tag/v1.1.0). We read the tag from
     * the final URL and build a matching APK download URL (VidMax-<tag>-release.apk).
     */
    private fun fetchFromReleasePage(): CheckResult = try {
        val request = Request.Builder()
            .url("https://github.com/$REPO/releases/latest")
            .header("User-Agent", "VidMax-Android")
            .build()

        client.newCall(request).execute().use { response ->
            val code = response.code
            if (code == 404) return@use CheckResult.NoRelease
            if (!response.isSuccessful) return@use CheckResult.Failed
            val finalUrl = response.request.url.toString()
            val tag = finalUrl.substringAfter("/releases/tag/", "")
                .ifBlank { return@use CheckResult.Failed }
            val versionName = tag.removePrefix("v").removePrefix("V")
                .substringBefore("+")
            val buildNumber = tag.substringAfter("+", "")
                .toIntOrNull()
            val apkUrl = "https://github.com/$REPO/releases/download/$tag/VidMax-$tag-release.apk"
            CheckResult.Success(
                AppUpdateInfo(
                    tagName = tag,
                    versionName = versionName,
                    buildNumber = buildNumber,
                    releaseNotes = "",
                    downloadUrl = apkUrl,
                    releasePageUrl = "https://github.com/$REPO/releases/tag/$tag",
                    publishedAt = "",
                )
            )
        }
    } catch (e: Exception) {
        CheckResult.Failed
    }

    /** Parses "1.2.3" (or "v1.2", "1.0-beta") into comparable int parts. */
    private fun parseVersion(value: String): List<Int> {
        val cleaned = value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
        return cleaned.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
