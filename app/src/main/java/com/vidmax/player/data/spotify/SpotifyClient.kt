package com.vidmax.player.data.spotify

import android.util.Log
import com.vidmax.player.data.spotify.model.ArtistTopTracksResponse
import com.vidmax.player.data.spotify.model.NewReleasesResponse
import com.vidmax.player.data.spotify.model.SpotifyAlbum
import com.vidmax.player.data.spotify.model.SpotifyArtist
import com.vidmax.player.data.spotify.model.SpotifyExternalIds
import com.vidmax.player.data.spotify.model.SpotifyHomeFeed
import com.vidmax.player.data.spotify.model.SpotifyHomeFeedItem
import com.vidmax.player.data.spotify.model.SpotifyHomeFeedSection
import com.vidmax.player.data.spotify.model.SpotifyImage
import com.vidmax.player.data.spotify.model.SpotifyPaging
import com.vidmax.player.data.spotify.model.SpotifyPlaylist
import com.vidmax.player.data.spotify.model.SpotifyPlaylistOwner
import com.vidmax.player.data.spotify.model.SpotifyPlaylistTrack
import com.vidmax.player.data.spotify.model.SpotifySavedTrack
import com.vidmax.player.data.spotify.model.SpotifySearchResult
import com.vidmax.player.data.spotify.model.SpotifySimpleAlbum
import com.vidmax.player.data.spotify.model.SpotifySimpleArtist
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.data.spotify.model.SpotifyUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spotify API client that uses the internal GraphQL API (api-partner.spotify.com)
 * for most operations, falling back to the public REST API (api.spotify.com/v1/)
 * only for endpoints without a GraphQL equivalent (top tracks/artists).
 *
 * GraphQL persisted-query hashes sourced from:
 * https://github.com/sonic-liberation/hetu_spotify_gql_client
 *
 * Ported from Meld's Ktor client to OkHttp + org.json (কোনো নতুন dependency নেই)।
 */
object SpotifyClient {

    private const val TAG = "SpotifyClient"
    private const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val REST_HOST = "api.spotify.com"
    // Fix 1: Removed 'const' because MediaType is not a primitive type or String
    private val GQL_JSON_MEDIA_TYPE: MediaType = "application/json; charset=UTF-8".toMediaType()

    @Volatile
    var accessToken: String? = null

    /**
     * OkHttp client used for GQL + REST calls. Defaults to a browser-like client;
     * the Hilt module replaces it at startup via [setHttpClient].
     */
    @Volatile
    var httpClient: OkHttpClient = buildDefaultClient()

    fun setHttpClient(client: OkHttpClient) {
        httpClient = client
    }

    private fun buildDefaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private fun randomUserAgent(): String {
        val osOptions = arrayOf(
            "Windows NT 10.0; Win64; x64",
            "Macintosh; Intel Mac OS X 10_15_7",
            "X11; Linux x86_64",
        )
        val chromeBase = 140
        val chromeMajor = chromeBase - (0..4).random()
        val chromePatch = (0..499).random()
        val os = osOptions.random()
        return "Mozilla/5.0 ($os) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chromeMajor.0.$chromePatch.0 Safari/537.36"
    }

    class SpotifyException(
        val statusCode: Int,
        override val message: String,
        val retryAfterSec: Long = 0,
    ) : Exception(message)

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null

    private fun log(
        level: String,
        message: String,
    ) {
        val handler = logger
        if (handler != null) {
            handler(level, message)
        } else {
            when (level) {
                "E" -> Log.e(TAG, message)
                "W" -> Log.w(TAG, message)
                else -> Log.d(TAG, message)
            }
        }
    }

    // ── JSON navigation helpers (org.json) ─────────────────────────────

    private fun JSONObject.obj(key: String): JSONObject? =
        try {
            optJSONObject(key)
        } catch (_: Exception) {
            null
        }

    private fun JSONObject.str(key: String): String? =
        try {
            if (isNull(key)) null else optString(key, null)
        } catch (_: Exception) {
            null
        }

    private fun JSONObject.int(key: String): Int? =
        try {
            if (isNull(key)) null else optInt(key)
        } catch (_: Exception) {
            null
        }

    private fun JSONObject.arr(key: String): JSONArray? =
        try {
            optJSONArray(key)
        } catch (_: Exception) {
            null
        }

    private inline fun <T> JSONArray.mapObjectsNotNull(transform: (JSONObject) -> T?): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            transform(obj)?.let { result.add(it) }
        }
        return result
    }

    // ── GraphQL core ─────────────────────────────────────────────────────

    /**
     * Callback invoked when a GQL hash is rejected (PersistedQueryNotFound).
     * The app module sets this to trigger a remote hash refresh.
     */
    @Volatile
    var onHashExpired: ((operationName: String) -> Unit)? = null

    private suspend fun graphqlPost(
        operationName: String,
        variables: JSONObject = JSONObject(),
    ): JSONObject {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "GQL $operationName — no token")
            }

        val primaryHash = SpotifyHashProvider.getHash(operationName)
        val hashCandidates = buildList {
            add(primaryHash)
            SpotifyHashProvider.getPreviousHash(operationName)?.let { prev ->
                if (prev != primaryHash) add(prev)
            }
        }

        for ((hashIdx, sha256Hash) in hashCandidates.withIndex()) {
            val body = buildGqlBody(operationName, sha256Hash, variables)
            val result = executeGqlWithRetries(operationName, token, body)

            if (result.isPersistedQueryNotFound) {
                if (hashIdx < hashCandidates.lastIndex) {
                    log("W", "GQL $operationName hash rejected, trying previous_hash")
                    continue
                }
                log("E", "GQL $operationName all known hashes rejected, triggering remote refresh")
                onHashExpired?.invoke(operationName)
                throw SpotifyException(412, "PersistedQueryNotFound for $operationName — hash may have rotated")
            }

            // Fix 2: Added non-null assertion/fallback to match expected JSONObject return type
            return result.json ?: throw SpotifyException(500, "Empty JSON response")
        }

        throw SpotifyException(412, "No valid hash found for $operationName")
    }

    private fun buildGqlBody(
        operationName: String,
        sha256Hash: String,
        variables: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("variables", variables)
            .put("operationName", operationName)
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject()
                        .put("version", 1)
                        .put("sha256Hash", sha256Hash),
                ),
            )

    private class GqlResult(
        val json: JSONObject?,
        val isPersistedQueryNotFound: Boolean,
    )

    private suspend fun executeGqlWithRetries(
        operationName: String,
        token: String,
        body: JSONObject,
    ): GqlResult {
        val maxRetries = 3
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "GQL POST $operationName (token: ${token.take(8)}...)" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )

            val request =
                Request.Builder()
                    .url(GQL_URL)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", randomUserAgent())
                    .addHeader("app-platform", "WebPlayer")
                    .addHeader("Origin", "https://open.spotify.com")
                    .addHeader("Referer", "https://open.spotify.com/")
                    .addHeader("Accept", "application/json")
                    .post(body.toString().toRequestBody(GQL_JSON_MEDIA_TYPE))
                    .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val code = response.code
            val bodyText = response.body?.string().orEmpty()
            val retryAfterHeader = response.header("Retry-After")
            response.close()

            log("D", "GQL POST $operationName -> $code")

            if (code == 401) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (code == 429) {
                val retryAfter = retryAfterHeader?.toLongOrNull() ?: (2L * (attempt + 1))
                if (attempt < maxRetries - 1) {
                    log("W", "GQL $operationName -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (code == 412) {
                return GqlResult(json = null, isPersistedQueryNotFound = true)
            }
            if (code !in 200..299) {
                log("E", "GQL $operationName FAILED: $code — ${bodyText.take(200)}")
                throw SpotifyException(code, "GraphQL error $code: $bodyText")
            }

            val responseJson = try {
                JSONObject(bodyText)
            } catch (e: JSONException) {
                throw SpotifyException(code, "Invalid JSON from GQL: $bodyText")
            }

            val errors = responseJson.arr("errors")
            if (errors != null && errors.length() > 0) {
                val errorMsg = errors.optJSONObject(0)?.str("message") ?: "Unknown GraphQL error"
                if (errorMsg.contains("PersistedQueryNotFound", ignoreCase = true)) {
                    return GqlResult(json = null, isPersistedQueryNotFound = true)
                }
                log("E", "GQL $operationName returned error: $errorMsg")
                throw SpotifyException(400, "GraphQL: $errorMsg")
            }

            return GqlResult(json = responseJson, isPersistedQueryNotFound = false)
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    // ── REST core (fallback for endpoints without GQL equivalent) ────────

    private suspend fun authenticatedGet(
        endpoint: String,
        failFastOn429: Boolean = false,
        params: Map<String, Any> = emptyMap(),
    ): JSONObject {
        val token =
            accessToken ?: throw SpotifyException(401, "Not authenticated").also {
                log("E", "REST $endpoint — no token")
            }

        val maxRetries = if (failFastOn429) 1 else 3
        val maxRetryDelaySec = 3L
        for (attempt in 0 until maxRetries) {
            log(
                "D",
                "REST GET $endpoint (token: ${token.take(8)}...)" +
                    if (attempt > 0) " [retry $attempt]" else "",
            )

            val urlBuilder =
                HttpUrl.Builder()
                    .scheme("https")
                    .host(REST_HOST)
                    .addPathSegments("v1/$endpoint")
            params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }

            val request =
                Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("User-Agent", randomUserAgent())
                    .addHeader("app-platform", "WebPlayer")
                    .addHeader("Origin", "https://open.spotify.com")
                    .addHeader("Referer", "https://open.spotify.com/")
                    .get()
                    .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val code = response.code
            val bodyText = response.body?.string().orEmpty()
            val retryAfterHeader = response.header("Retry-After")
            response.close()

            log("D", "REST GET $endpoint -> $code")

            if (code == 401) {
                throw SpotifyException(401, "Token expired or invalid")
            }
            if (code == 429) {
                val retryAfter = retryAfterHeader?.toLongOrNull() ?: (2L * (attempt + 1))
                if (failFastOn429 || retryAfter > maxRetryDelaySec) {
                    log("W", "REST $endpoint -> 429, failing fast (retryAfter=${retryAfter}s)")
                    throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
                }
                if (attempt < maxRetries - 1) {
                    log("W", "REST $endpoint -> 429, waiting ${retryAfter}s (attempt ${attempt + 1}/$maxRetries)")
                    delay(retryAfter * 1000)
                    continue
                }
                throw SpotifyException(429, "Rate limited", retryAfterSec = retryAfter)
            }
            if (code !in 200..299) {
                log("E", "REST $endpoint FAILED: $code — ${bodyText.take(200)}")
                throw SpotifyException(code, "Spotify API error $code: $bodyText")
            }

            return try {
                JSONObject(bodyText)
            } catch (e: JSONException) {
                throw SpotifyException(code, "Invalid JSON from REST: $bodyText")
            }
        }

        throw SpotifyException(429, "Rate limited after $maxRetries retries")
    }

    // ── GQL response converters ──────────────────────────────────────────

    private fun parseGqlImage(source: JSONObject): SpotifyImage? {
        val url = source.str("url") ?: return null
        return SpotifyImage(url = url, height = source.int("height"), width = source.int("width"))
    }

    private fun parseGqlImages(sources: JSONArray?): List<SpotifyImage> =
        sources?.mapObjectsNotNull { parseGqlImage(it) } ?: emptyList()

    private fun parseGqlSimpleArtist(artistObj: JSONObject): SpotifySimpleArtist? {
        val uri = artistObj.str("uri") ?: return null
        return SpotifySimpleArtist(
            id = uri.substringAfterLast(":"),
            name = artistObj.obj("profile")?.str("name") ?: "",
            uri = uri,
        )
    }

    /**
     * Parses the common track data structure shared across multiple GQL
     * operations (fetchPlaylist, fetchLibraryTracks, queryArtistOverview, etc.).
     *
     * @param albumOverride When non-null, used instead of the `albumOfTrack`
     *   field (needed for album-track responses where no albumOfTrack is present).
     * @param uriOverride When non-null, used as the track URI instead of
     *   reading it from [trackData]. Needed when the URI lives on a wrapper
     *   object (e.g. `track._uri`) rather than inside `track.data`.
     */
    private fun parseGqlTrack(
        trackData: JSONObject,
        albumOverride: SpotifySimpleAlbum? = null,
        uriOverride: String? = null,
    ): SpotifyTrack {
        val uri = uriOverride
            ?: trackData.str("uri")
            ?: trackData.str("_uri")
            ?: ""
        val trackId = uri.substringAfterLast(":")

        val artists =
            trackData.obj("artists")?.arr("items")?.mapObjectsNotNull { elem ->
                parseGqlSimpleArtist(elem)
            } ?: emptyList()

        val album =
            albumOverride ?: run {
                val albumData = trackData.obj("albumOfTrack")
                val albumUri = albumData?.str("uri") ?: ""
                val albumId = albumUri.substringAfterLast(":")
                SpotifySimpleAlbum(
                    id = albumId,
                    name = albumData?.str("name") ?: "",
                    images = parseGqlImages(albumData?.obj("coverArt")?.arr("sources")),
                    uri = albumUri.ifEmpty { null },
                )
            }

        return SpotifyTrack(
            id = trackId,
            name = trackData.str("name") ?: "",
            artists = artists,
            album = album,
            durationMs = parseGqlTrackDurationMs(trackData),
            uri = uri.ifEmpty { null },
        )
    }

    /**
     * Extracts track duration in ms from GQL track payload.
     * Tries multiple keys because different operations may return duration
     * as nested (duration.totalMilliseconds) or flat (durationMs / duration_ms).
     */
    private fun parseGqlTrackDurationMs(trackData: JSONObject): Int {
        trackData.obj("duration")?.int("totalMilliseconds")?.let { if (it > 0) return it }
        trackData.int("durationMs")?.let { if (it > 0) return it }
        trackData.int("duration_ms")?.let { if (it > 0) return it }
        // Some APIs return duration in seconds
        trackData.int("duration")?.let { sec -> if (sec > 0) return sec * 1000 }
        return 0
    }

    /**
     * Flattens the nested `images.items[].sources[]` structure used by
     * playlists in the GQL response.
     */
    private fun parseGqlPlaylistImages(imagesObj: JSONObject?): List<SpotifyImage> {
        if (imagesObj == null) return emptyList()
        val items = imagesObj.arr("items") ?: return emptyList()
        val result = mutableListOf<SpotifyImage>()
        for (i in 0 until items.length()) {
            items.optJSONObject(i)?.let { imageGroup ->
                result.addAll(parseGqlImages(imageGroup.arr("sources")))
            }
        }
        return result
    }

    // ── User Profile (GQL with REST fallback) ──────────────────────────

    suspend fun me(): Result<SpotifyUser> =
        runCatching {
            try {
                val response =
                    graphqlPost(
                        operationName = "profileAttributes",
                    )
                val profile =
                    response.obj("data")?.obj("me")?.obj("profile")
                        ?: throw SpotifyException(500, "Invalid profileAttributes response")

                val uri = profile.str("uri") ?: ""
                SpotifyUser(
                    id = uri.substringAfterLast(":"),
                    displayName = profile.str("name"),
                    email = null,
                    images = parseGqlImages(profile.obj("avatar")?.arr("sources")),
                )
            } catch (e: Exception) {
                log("W", "GQL me() failed, falling back to REST: ${e.message}")
                parseRestUser(authenticatedGet("me"))
            }
        }

    // ── Playlists (GQL: libraryV3) ──────────────────────────────────────

    suspend fun myPlaylists(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylist>> =
        runCatching {
            val vars =
                JSONObject()
                    .put("filters", JSONArray().put("Playlists"))
                    .put("order", JSONObject.NULL)
                    .put("textFilter", "")
                    .put(
                        "features",
                        JSONArray()
                            .put("LIKED_SONGS")
                            .put("YOUR_EPISODES_V2")
                            .put("PRERELEASES")
                            .put("EVENTS"),
                    )
                    .put("limit", limit)
                    .put("offset", offset)
                    // flatten=true → ফোল্ডারের ভেতরের সব playlist-ও ফিরে আসে
                    .put("flatten", true)
                    .put("expandedFolders", JSONArray())
                    .put("folderUri", JSONObject.NULL)
                    .put("includeFoldersWhenFlattening", false)

            val response =
                graphqlPost(
                    operationName = "libraryV3",
                    variables = vars,
                )

            val libraryData =
                response.obj("data")?.obj("me")?.obj("libraryV3")
                    ?: throw SpotifyException(500, "Invalid libraryV3 response")

            val totalCount = libraryData.int("totalCount") ?: 0
            val pagingInfo = libraryData.obj("pagingInfo")

            val rawItems = libraryData.arr("items")
            val playlists =
                rawItems?.mapObjectsNotNull { itemElem ->
                    val wrapper = itemElem.obj("item") ?: return@mapObjectsNotNull null
                    val typeName = wrapper.str("__typename") ?: ""
                    // প্রতিটি Playlist*Wrapper ভেরিয়েন্ট মিলিয়ে নিই (Collaborative ইত্যাদি)
                    if (typeName != "PlaylistResponseWrapper" &&
                        !typeName.contains("Playlist", ignoreCase = true)
                    ) return@mapObjectsNotNull null
                    parsePlaylistWrapper(wrapper)
                } ?: emptyList()

            SpotifyPaging(
                items = playlists,
                total = totalCount,
                limit = pagingInfo?.int("limit") ?: limit,
                offset = pagingInfo?.int("offset") ?: offset,
            )
        }

    private fun parsePlaylistWrapper(wrapper: JSONObject): SpotifyPlaylist? {
        val data = wrapper.obj("data") ?: return null
        if (data.str("__typename") != "Playlist") return null
        val playlistUri = wrapper.str("_uri") ?: return null
        val playlistId = playlistUri.substringAfterLast(":")
        val ownerData = data.obj("ownerV2")?.obj("data")
        val ownerId = ownerData?.str("uri")?.substringAfterLast(":") ?: ownerData?.str("id") ?: ""
        return SpotifyPlaylist(
            id = playlistId,
            name = data.str("name") ?: "",
            description = data.str("description"),
            images = parseGqlPlaylistImages(data.obj("images")),
            owner = SpotifyPlaylistOwner(
                id = ownerId,
                displayName = ownerData?.str("name"),
                uri = ownerData?.str("uri"),
            ),
            trackCount = data.obj("content")?.int("totalCount") ?: 0,
            uri = playlistUri,
        )
    }

    // ── Playlist detail (GQL: fetchPlaylist) ────────────────────────────

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyPlaylistTrack>> =
        runCatching {
            val vars =
                JSONObject()
                    .put("uri", "spotify:playlist:$playlistId")
                    .put("offset", offset)
                    .put("limit", limit)
                    .put("enableWatchFeedEntrypoint", false)

            val response =
                graphqlPost(
                    operationName = "fetchPlaylist",
                    variables = vars,
                )

            val content =
                response.obj("data")?.obj("playlistV2")?.obj("content")
                    ?: throw SpotifyException(500, "No content in fetchPlaylist response")

            val tracks =
                content.arr("items")?.mapObjectsNotNull { elem ->
                    val itemWrapper = elem.obj("itemV2") ?: return@mapObjectsNotNull null
                    val itemData = itemWrapper.obj("data") ?: return@mapObjectsNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    val uid = elem.str("uid") ?: itemWrapper.str("uid")
                    SpotifyPlaylistTrack(
                        addedAt = elem.str("addedAt"),
                        track = parseGqlTrack(itemData, uriOverride = wrapperUri),
                        uid = uid,
                    )
                } ?: emptyList()

            SpotifyPaging(
                items = tracks,
                total = content.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }

    // ── Liked Songs (GQL: fetchLibraryTracks) ───────────────────────────

    suspend fun likedSongs(
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifySavedTrack>> =
        runCatching {
            val vars =
                JSONObject()
                    .put("offset", offset)
                    .put("limit", limit)

            val response =
                graphqlPost(
                    operationName = "fetchLibraryTracks",
                    variables = vars,
                )

            val tracksData =
                response.obj("data")?.obj("me")?.obj("library")?.obj("tracks")
                    ?: throw SpotifyException(500, "Invalid fetchLibraryTracks response")

            val savedTracks =
                tracksData.arr("items")?.mapObjectsNotNull { elem ->
                    val trackWrapper = elem.obj("track") ?: return@mapObjectsNotNull null
                    val trackData = trackWrapper.obj("data") ?: return@mapObjectsNotNull null
                    val wrapperUri = trackWrapper.str("_uri") ?: trackWrapper.str("uri")
                    SpotifySavedTrack(
                        addedAt = elem.str("addedAt"),
                        track = parseGqlTrack(trackData, uriOverride = wrapperUri),
                    )
                } ?: emptyList()

            SpotifyPaging(
                items = savedTracks,
                total = tracksData.int("totalCount") ?: 0,
                limit = limit,
                offset = offset,
            )
        }

    // ── Top Tracks (REST fallback — no GQL equivalent) ──────────────────

    suspend fun topTracks(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyTrack>> =
        runCatching {
            parseRestTrackPaging(
                authenticatedGet(
                    "me/top/tracks",
                    failFastOn429 = true,
                    params = mapOf("time_range" to timeRange, "limit" to limit, "offset" to offset),
                ),
                defaultLimit = limit,
                defaultOffset = offset,
            )
        }

    // ── Top Artists (REST fallback — no GQL equivalent) ─────────────────

    suspend fun topArtists(
        timeRange: String = "medium_term",
        limit: Int = 50,
        offset: Int = 0,
    ): Result<SpotifyPaging<SpotifyArtist>> =
        runCatching {
            parseRestArtistPaging(
                authenticatedGet(
                    "me/top/artists",
                    failFastOn429 = true,
                    params = mapOf("time_range" to timeRange, "limit" to limit, "offset" to offset),
                ),
                defaultLimit = limit,
                defaultOffset = offset,
            )
        }

    // ── Search (GQL: searchDesktop) ─────────────────────────────────────

    suspend fun search(
        query: String,
        types: List<String> = listOf("track"),
        limit: Int = 20,
        offset: Int = 0,
    ): Result<SpotifySearchResult> =
        runCatching {
            val vars =
                JSONObject()
                    .put("searchTerm", query)
                    .put("offset", offset)
                    .put("limit", limit)
                    .put("numberOfTopResults", 5)
                    .put("includeAudiobooks", false)
                    .put("includeArtistHasConcertsField", false)
                    .put("includePreReleases", false)
                    .put("includeLocalConcertsField", false)
                    .put("includeAuthors", false)

            val response =
                graphqlPost(
                    operationName = "searchDesktop",
                    variables = vars,
                )

            val searchData =
                response.obj("data")?.obj("searchV2")
                    ?: throw SpotifyException(500, "Invalid searchDesktop response")

            val tracksSection = searchData.obj("tracksV2")
            val trackItems =
                tracksSection?.arr("items")?.mapObjectsNotNull { elem ->
                    val itemWrapper = elem.obj("item") ?: return@mapObjectsNotNull null
                    if (itemWrapper.str("__typename") != "TrackResponseWrapper") return@mapObjectsNotNull null
                    val data = itemWrapper.obj("data") ?: return@mapObjectsNotNull null
                    if (data.str("__typename") != "Track") return@mapObjectsNotNull null
                    val wrapperUri = itemWrapper.str("_uri") ?: itemWrapper.str("uri")
                    parseGqlTrack(data, uriOverride = wrapperUri)
                } ?: emptyList()

            val albumsSection = searchData.obj("albumsV2")
            val albumItems =
                albumsSection?.arr("items")?.mapObjectsNotNull { elem ->
                    val wrapper = elem
                    if (wrapper.str("__typename") != "AlbumResponseWrapper") return@mapObjectsNotNull null
                    val data = wrapper.obj("data") ?: return@mapObjectsNotNull null
                    if (data.str("__typename") != "Album") return@mapObjectsNotNull null
                    parseGqlSearchAlbum(data)
                } ?: emptyList()

            val artistsSection = searchData.obj("artists")
            val artistItems =
                artistsSection?.arr("items")?.mapObjectsNotNull { elem ->
                    val wrapper = elem
                    if (wrapper.str("__typename") != "ArtistResponseWrapper") return@mapObjectsNotNull null
                    val data = wrapper.obj("data") ?: return@mapObjectsNotNull null
                    if (data.str("__typename") != "Artist") return@mapObjectsNotNull null
                    parseGqlSearchArtist(data)
                } ?: emptyList()

            val playlistsSection = searchData.obj("playlists")
            val playlistItems =
                playlistsSection?.arr("items")?.mapObjectsNotNull { elem ->
                    val wrapper = elem
                    if (wrapper.str("__typename") != "PlaylistResponseWrapper") return@mapObjectsNotNull null
                    val data = wrapper.obj("data") ?: return@mapObjectsNotNull null
                    if (data.str("__typename") != "Playlist") return@mapObjectsNotNull null
                    parseGqlSearchPlaylist(data)
                } ?: emptyList()

            SpotifySearchResult(
                tracks =
                    SpotifyPaging(
                        items = trackItems,
                        total = tracksSection?.int("totalCount") ?: 0,
                        limit = limit,
                        offset = offset,
                    ),
                albums =
                    if (albumItems.isNotEmpty()) {
                        SpotifyPaging(items = albumItems, total = albumsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                artists =
                    if (artistItems.isNotEmpty()) {
                        SpotifyPaging(items = artistItems, total = artistsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
                playlists =
                    if (playlistItems.isNotEmpty()) {
                        SpotifyPaging(items = playlistItems, total = playlistsSection?.int("totalCount") ?: 0, limit = limit, offset = offset)
                    } else {
                        null
                    },
            )
        }

    private fun parseGqlSearchAlbum(data: JSONObject): SpotifyAlbum {
        val uri = data.str("uri") ?: ""
        return SpotifyAlbum(
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            albumType = data.str("type")?.lowercase(),
            artists =
                data.obj("artists")?.arr("items")?.mapObjectsNotNull { parseGqlSimpleArtist(it) }
                    ?: emptyList(),
            images = parseGqlImages(data.obj("coverArt")?.arr("sources")),
            releaseDate = data.obj("date")?.int("year")?.toString(),
            uri = uri.ifEmpty { null },
        )
    }

    private fun parseGqlSearchArtist(data: JSONObject): SpotifyArtist {
        val uri = data.str("uri") ?: ""
        return SpotifyArtist(
            id = uri.substringAfterLast(":"),
            name = data.obj("profile")?.str("name") ?: "",
            images = parseGqlImages(data.obj("visuals")?.obj("avatarImage")?.arr("sources")),
            uri = uri.ifEmpty { null },
        )
    }

    private fun parseGqlSearchPlaylist(data: JSONObject): SpotifyPlaylist {
        val uri = data.str("uri") ?: ""
        val ownerData = data.obj("ownerV2")?.obj("data")
        val ownerUri = ownerData?.str("uri") ?: ""

        return SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            description = data.str("description"),
            images = parseGqlPlaylistImages(data.obj("images")),
            owner =
                SpotifyPlaylistOwner(
                    id = ownerUri.substringAfterLast(":"),
                    displayName = ownerData?.str("name"),
                    uri = ownerUri.ifEmpty { null },
                ),
            trackCount = data.obj("content")?.int("totalCount") ?: 0,
            uri = uri.ifEmpty { null },
        )
    }

    // ── Browse: New Releases (GQL: queryWhatsNewFeed) ───────────────────

    suspend fun newReleases(
        limit: Int = 20,
        offset: Int = 0,
    ): Result<NewReleasesResponse> =
        runCatching {
            val vars =
                JSONObject()
                    .put("offset", offset)
                    .put("limit", limit)
                    .put("onlyUnPlayedItems", false)
                    .put("includedContentTypes", JSONArray().put("ALBUM"))

            val response =
                graphqlPost(
                    operationName = "queryWhatsNewFeed",
                    variables = vars,
                )

            val feedData =
                response.obj("data")?.obj("whatsNewFeedItems")
                    ?: throw SpotifyException(500, "Invalid queryWhatsNewFeed response")

            val pagingInfo = feedData.obj("pagingInfo")

            val albums =
                feedData.arr("items")?.mapObjectsNotNull { elem ->
                    val content = elem.obj("content") ?: return@mapObjectsNotNull null
                    if (content.str("__typename") != "AlbumResponseWrapper") return@mapObjectsNotNull null
                    val data = content.obj("data") ?: return@mapObjectsNotNull null
                    if (data.str("__typename") != "Album") return@mapObjectsNotNull null

                    val uri = data.str("uri") ?: return@mapObjectsNotNull null
                    SpotifyAlbum(
                        id = uri.substringAfterLast(":"),
                        name = data.str("name") ?: "",
                        albumType = data.str("albumType")?.lowercase(),
                        artists =
                            data.obj("artists")?.arr("items")?.mapObjectsNotNull { parseGqlSimpleArtist(it) }
                                ?: emptyList(),
                        images = parseGqlImages(data.obj("coverArt")?.arr("sources")),
                        releaseDate = data.obj("date")?.str("isoString"),
                        uri = uri,
                    )
                } ?: emptyList()

            NewReleasesResponse(
                albums =
                    SpotifyPaging(
                        items = albums,
                        total = feedData.int("totalCount") ?: 0,
                        limit = pagingInfo?.int("limit") ?: limit,
                        offset = pagingInfo?.int("offset") ?: offset,
                    ),
            )
        }

    // ── Home feed (GQL: home) ──────────────────────────────────────────
    //
    // Returns the fully personalized Spotify home: Daily Mix, Discover Weekly,
    // Release Radar, "Jump back in", "More like <artist>", daylist, etc.
    // Shape matches open.spotify.com landing page, one request for ~21 sections.

    suspend fun home(
        sectionItemsLimit: Int = 10,
        timeZone: String = java.util.TimeZone.getDefault().id,
    ): Result<SpotifyHomeFeed> =
        runCatching {
            log("D", "spotifyHome: GQL home() request — timeZone=$timeZone limit=$sectionItemsLimit")
            val vars =
                JSONObject()
                    .put("homeEndUserIntegration", "INTEGRATION_WEB_PLAYER")
                    .put("timeZone", timeZone)
                    .put("sp_t", "")
                    .put("facet", "")
                    .put("sectionItemsLimit", sectionItemsLimit)
                    .put("includeEpisodeContentRatingsV2", false)

            val response =
                graphqlPost(
                    operationName = "home",
                    variables = vars,
                )

            val homeData =
                response.obj("data")?.obj("home")
                    ?: run {
                        log("E", "spotifyHome: GQL response has no data.home")
                        throw SpotifyException(500, "Invalid home response")
                    }

            val greeting = homeData.obj("greeting")?.str("transformedLabel")
            log("D", "spotifyHome: GQL home() OK greeting='$greeting'")

            val sectionElements =
                homeData.obj("sectionContainer")
                    ?.obj("sections")
                    ?.arr("items")
                    ?: run {
                        log("W", "spotifyHome: no sectionContainer.sections.items in response")
                        return@runCatching SpotifyHomeFeed(
                            greeting = greeting,
                            sections = emptyList(),
                        )
                    }

            log("D", "spotifyHome: parsing ${sectionElements.length()} raw sections")
            val sections =
                sectionElements.mapObjectsNotNull { parseHomeSection(it) }
            log("D", "spotifyHome: parsed ${sections.size}/${sectionElements.length()} sections successfully")

            SpotifyHomeFeed(
                greeting = greeting,
                sections = sections,
            )
        }

    private fun parseHomeSection(sectionObj: JSONObject): SpotifyHomeFeedSection? {
        val sectionData = sectionObj.obj("data") ?: return null
        val typename = sectionData.str("__typename") ?: return null
        val titleObj = sectionData.obj("title")
        val title = titleObj?.str("transformedLabel")
            ?: titleObj?.str("translatedBaseText")
            ?: titleObj?.str("text")

        val sectionItems = sectionObj.obj("sectionItems")
        val totalCount = sectionItems?.int("totalCount") ?: 0
        val itemElements = sectionItems?.arr("items") ?: return null

        val items =
            itemElements.mapObjectsNotNull { parseHomeItem(it) }

        if (items.isEmpty()) return null

        return SpotifyHomeFeedSection(
            sectionUri = sectionObj.str("uri") ?: "",
            title = title,
            typename = typename,
            totalCount = totalCount,
            items = items,
        )
    }

    private fun parseHomeItem(itemObj: JSONObject): SpotifyHomeFeedItem? {
        val content = itemObj.obj("content") ?: return null
        val wrapper = content.str("__typename") ?: return null
        val data = content.obj("data") ?: return null

        return when (wrapper) {
            "PlaylistResponseWrapper" -> parseHomePlaylist(data)
            "AlbumResponseWrapper" -> parseHomeAlbum(data)
            "ArtistResponseWrapper" -> parseHomeArtist(data)
            else -> null
        }
    }

    private fun parseHomePlaylist(data: JSONObject): SpotifyHomeFeedItem.Playlist? {
        val uri = data.str("uri") ?: return null
        val imageItem = data.obj("images")?.arr("items")?.optJSONObject(0)
        val imageUrl = imageItem?.arr("sources")?.optJSONObject(0)?.str("url")
        val colorHex = imageItem?.obj("extractedColors")?.obj("colorDark")?.str("hex")
        var madeFor: String? = null
        data.arr("attributes")?.let { attributes ->
            for (i in 0 until attributes.length()) {
                val attr = attributes.optJSONObject(i) ?: continue
                if (attr.str("key") == "madeFor.username") {
                    madeFor = attr.str("value")
                    break
                }
            }
        }

        return SpotifyHomeFeedItem.Playlist(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            description = data.str("description"),
            format = data.str("format"),
            totalCount = data.obj("content")?.int("totalCount") ?: 0,
            imageUrl = imageUrl,
            extractedColorHex = colorHex,
            ownerName = data.obj("ownerV2")?.obj("data")?.str("name"),
            madeForUsername = madeFor,
        )
    }

    private fun parseHomeAlbum(data: JSONObject): SpotifyHomeFeedItem.Album? {
        val uri = data.str("uri") ?: return null
        val artists =
            data.obj("artists")?.arr("items")?.mapObjectsNotNull { parseGqlSimpleArtist(it) }
                ?: emptyList()
        val imageUrl =
            data.obj("coverArt")?.arr("sources")?.optJSONObject(0)?.str("url")

        return SpotifyHomeFeedItem.Album(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = data.str("name") ?: "",
            albumType = data.str("type")?.lowercase(),
            artists = artists,
            imageUrl = imageUrl,
        )
    }

    private fun parseHomeArtist(data: JSONObject): SpotifyHomeFeedItem.Artist? {
        val uri = data.str("uri") ?: return null
        val profile = data.obj("profile")
        val imageUrl =
            data.obj("visuals")?.obj("avatarImage")
                ?.arr("sources")?.optJSONObject(0)?.str("url")
        return SpotifyHomeFeedItem.Artist(
            uri = uri,
            id = uri.substringAfterLast(":"),
            name = profile?.str("name") ?: "",
            imageUrl = imageUrl,
        )
    }

    // ── Artists (GQL: queryArtistOverview) ───────────────────────────────

    private suspend fun artistUnion(artistId: String): JSONObject {
        val response =
            graphqlPost(
                operationName = "queryArtistOverview",
                variables = JSONObject()
                    .put("uri", "spotify:artist:$artistId")
                    .put("locale", ""),
            )
        return response.obj("data")?.obj("artistUnion")
            ?: throw SpotifyException(500, "Invalid queryArtistOverview response")
    }

    suspend fun artistTopTracks(
        artistId: String,
        market: String = "US",
    ): Result<ArtistTopTracksResponse> =
        runCatching {
            // Note: Meld-এ market variable GQL body-তে পাঠানো হয় না (persisted query-র
            // declared variables অনুযায়ী); প্যারামিটারটি শুধু API compatibility-র জন্য।
            val artistData = artistUnion(artistId)

            val topTracksItems =
                artistData.obj("discography")
                    ?.obj("topTracks")?.arr("items")

            val tracks =
                topTracksItems?.mapObjectsNotNull { elem ->
                    val trackObj = elem.obj("track") ?: return@mapObjectsNotNull null
                    parseGqlTrack(trackObj)
                } ?: emptyList()

            ArtistTopTracksResponse(tracks = tracks)
        }

    // ── REST response parsers ────────────────────────────────────────────

    private fun parseRestSimpleArtist(obj: JSONObject): SpotifySimpleArtist =
        SpotifySimpleArtist(
            id = obj.str("id"),
            name = obj.str("name") ?: "",
            uri = obj.str("uri"),
        )

    private fun parseRestImage(obj: JSONObject): SpotifyImage? {
        val url = obj.str("url") ?: return null
        return SpotifyImage(url = url, height = obj.int("height"), width = obj.int("width"))
    }

    private fun parseRestImages(images: JSONArray?): List<SpotifyImage> =
        images?.mapObjectsNotNull { parseRestImage(it) } ?: emptyList()

    private fun parseRestTrack(obj: JSONObject): SpotifyTrack {
        val uri = obj.str("uri")
        val albumObj = obj.obj("album")
        val album =
            SpotifySimpleAlbum(
                id = albumObj?.str("id") ?: "",
                name = albumObj?.str("name") ?: "",
                images = parseRestImages(albumObj?.arr("images")),
                releaseDate = albumObj?.str("release_date"),
                albumType = albumObj?.str("album_type"),
                artists = albumObj?.arr("artists")?.mapObjectsNotNull { parseRestSimpleArtist(it) } ?: emptyList(),
                uri = albumObj?.str("uri"),
            )
        val externalIdsObj = obj.obj("external_ids")
        return SpotifyTrack(
            id = obj.str("id") ?: "",
            name = obj.str("name") ?: "",
            artists = obj.arr("artists")?.mapObjectsNotNull { parseRestSimpleArtist(it) } ?: emptyList(),
            album = album,
            durationMs = obj.int("duration_ms") ?: 0,
            explicit = obj.optBoolean("explicit", false),
            previewUrl = obj.str("preview_url"),
            uri = uri,
            popularity = obj.int("popularity"),
            externalIds = externalIdsObj?.let { SpotifyExternalIds(isrc = it.str("isrc")) },
        )
    }

    private fun parseRestArtist(obj: JSONObject): SpotifyArtist =
        SpotifyArtist(
            id = obj.str("id") ?: "",
            name = obj.str("name") ?: "",
            images = parseRestImages(obj.arr("images")),
            genres = obj.arr("genres")?.let { arr -> buildList { for (i in 0 until arr.length()) arr.optString(i, "").takeIf { it.isNotEmpty() }?.let(::add) } } ?: emptyList(),
            popularity = obj.int("popularity"),
            uri = obj.str("uri"),
        )

    private fun parseRestUser(obj: JSONObject): SpotifyUser =
        SpotifyUser(
            id = obj.str("id") ?: "",
            displayName = obj.str("display_name"),
            email = obj.str("email"),
            images = parseRestImages(obj.arr("images")),
            product = obj.str("product"),
            country = obj.str("country"),
        )

    private fun parseRestTrackPaging(
        obj: JSONObject,
        defaultLimit: Int,
        defaultOffset: Int,
    ): SpotifyPaging<SpotifyTrack> {
        val items = obj.arr("items")?.mapObjectsNotNull { parseRestTrack(it) } ?: emptyList()
        return SpotifyPaging(
            items = items,
            total = obj.int("total") ?: 0,
            limit = obj.int("limit") ?: defaultLimit,
            offset = obj.int("offset") ?: defaultOffset,
            next = obj.str("next"),
            previous = obj.str("previous"),
        )
    }

    private fun parseRestArtistPaging(
        obj: JSONObject,
        defaultLimit: Int,
        defaultOffset: Int,
    ): SpotifyPaging<SpotifyArtist> {
        val items = obj.arr("items")?.mapObjectsNotNull { parseRestArtist(it) } ?: emptyList()
        return SpotifyPaging(
            items = items,
            total = obj.int("total") ?: 0,
            limit = obj.int("limit") ?: defaultLimit,
            offset = obj.int("offset") ?: defaultOffset,
            next = obj.str("next"),
            previous = obj.str("previous"),
        )
    }

    fun isAuthenticated(): Boolean = accessToken != null
}
