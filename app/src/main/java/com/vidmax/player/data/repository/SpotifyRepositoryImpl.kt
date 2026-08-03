package com.vidmax.player.data.repository

import android.content.Context
import android.util.Log
import com.vidmax.player.data.local.SpotifyMatchDao
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.spotify.SpotifyAuth
import com.vidmax.player.data.spotify.SpotifyClient
import com.vidmax.player.data.spotify.SpotifyTokenManager
import com.vidmax.player.data.spotify.SpotifyYouTubeMapper
import com.vidmax.player.data.spotify.model.SpotifyAlbum
import com.vidmax.player.data.spotify.model.SpotifyArtist
import com.vidmax.player.data.spotify.model.SpotifyExternalIds
import com.vidmax.player.data.spotify.model.SpotifyHomeData
import com.vidmax.player.data.spotify.model.SpotifyHomeFeedItem
import com.vidmax.player.data.spotify.model.SpotifyHomeFeedSection
import com.vidmax.player.data.spotify.model.SpotifyHomeSection
import com.vidmax.player.data.spotify.model.SpotifyImage
import com.vidmax.player.data.spotify.model.SpotifyPlaylist
import com.vidmax.player.data.spotify.model.SpotifyPlaylistOwner
import com.vidmax.player.data.spotify.model.SectionType
import com.vidmax.player.data.spotify.model.SpotifySimpleAlbum
import com.vidmax.player.data.spotify.model.SpotifySimpleArtist
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.data.spotify.model.SpotifyUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Spotify repository. Backs [SpotifyClient] + [SpotifyTokenManager] +
 * [SpotifyYouTubeMapper] এবং Home data-কে SharedPreferences-এ 6 ঘণ্টার TTL সহ cache করে।
 */
@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val spotifyMatchDao: SpotifyMatchDao,
    private val okHttpClient: OkHttpClient,
) : SpotifyRepository {

    private val tokenManager = SpotifyTokenManager(context)
    private val spotifyYouTubeMapper = SpotifyYouTubeMapper(musicRepository, spotifyMatchDao)

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<SpotifyUser?>(null)
    override val currentUser: StateFlow<SpotifyUser?> = _currentUser.asStateFlow()

    override val needsReLogin: StateFlow<Boolean> = tokenManager.needsReLogin

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var backgroundRefreshInFlight = false

    init {
        SpotifyClient.httpClient = okHttpClient
        restoreSession()
    }

    private fun restoreSession() {
        val token = tokenManager.storedAccessToken()
        val expiry = tokenManager.storedExpiry()
        val valid = token.isNotEmpty() && System.currentTimeMillis() < expiry
        if (valid) {
            SpotifyClient.accessToken = token
            _isLoggedIn.value = true
            val userId = tokenManager.storedUserId()
            if (userId.isNotEmpty()) {
                _currentUser.value = SpotifyUser(
                    id = userId,
                    displayName = tokenManager.storedUsername().ifEmpty { null },
                )
            }
        }
    }

    override suspend fun loginWithCookies(spDc: String, spKey: String): Result<SpotifyUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                tokenManager.saveCookies(spDc, spKey)

                val token = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrThrow()
                tokenManager.saveToken(token.accessToken, token.accessTokenExpirationTimestampMs)
                SpotifyClient.accessToken = token.accessToken
                tokenManager.clearReLoginFlag()

                val user = SpotifyClient.me().getOrThrow()
                tokenManager.saveProfile(user.displayName ?: "", user.id)

                _currentUser.value = user
                _isLoggedIn.value = true
                user
            }
        }

    override suspend fun logout() {
        tokenManager.clearSession()
        SpotifyClient.accessToken = null
        _currentUser.value = null
        _isLoggedIn.value = false
        tokenManager.clearReLoginFlag()
    }

    override suspend fun ensureAuthenticated(): Boolean {
        val ok = tokenManager.ensureAuthenticated()
        _isLoggedIn.value = ok
        if (ok && _currentUser.value == null) {
            val userId = tokenManager.storedUserId()
            if (userId.isNotEmpty()) {
                _currentUser.value = SpotifyUser(
                    id = userId,
                    displayName = tokenManager.storedUsername().ifEmpty { null },
                )
            }
        }
        return ok
    }

    override suspend fun fetchHomeData(forceRefresh: Boolean): SpotifyHomeData {
        val cached = loadHomeDataCache()
        val now = System.currentTimeMillis()

        if (!forceRefresh && cached != null && now - cached.generatedAt < CACHE_TTL_MS) {
            return cached
        }

        if (!forceRefresh && cached != null && !backgroundRefreshInFlight) {
            // stale-while-revalidate: পুরনো cache এখনই ফেরত, ব্যাকগ্রাউন্ডে refresh
            backgroundRefreshInFlight = true
            bgScope.launch {
                try {
                    if (ensureAuthenticated()) {
                        val fresh = buildHomeData()
                        saveHomeDataCache(fresh)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "background home refresh failed: ${e.message}")
                } finally {
                    backgroundRefreshInFlight = false
                }
            }
            return cached
        }

        if (!ensureAuthenticated()) {
            return cached ?: SpotifyHomeData(generatedAt = System.currentTimeMillis())
        }

        val fresh = buildHomeData()
        saveHomeDataCache(fresh)
        return fresh
    }

    private suspend fun buildHomeData(): SpotifyHomeData = withContext(Dispatchers.IO) {
        val user = SpotifyClient.me().getOrNull()
        val feed = SpotifyClient.home(sectionItemsLimit = 10).getOrNull()
        val topTracks = SpotifyClient.topTracks(timeRange = "medium_term", limit = 50).getOrNull()?.items ?: emptyList()
        val topArtists = SpotifyClient.topArtists(timeRange = "medium_term", limit = 50).getOrNull()?.items ?: emptyList()
        val playlists = SpotifyClient.myPlaylists(limit = 50).getOrNull()?.items ?: emptyList()
        val newReleases = SpotifyClient.newReleases(limit = 20).getOrNull()?.albums?.items ?: emptyList()

        val sections = mutableListOf<SpotifyHomeSection>()

        if (topTracks.isNotEmpty()) {
            sections.add(SpotifyHomeSection(title = "spotify_top_tracks", type = SectionType.TRACKS, tracks = topTracks))
        }
        if (topArtists.isNotEmpty()) {
            sections.add(SpotifyHomeSection(title = "spotify_top_artists", type = SectionType.ARTISTS, artists = topArtists))
        }
        if (newReleases.isNotEmpty()) {
            sections.add(SpotifyHomeSection(title = "spotify_new_releases", type = SectionType.ALBUMS, albums = newReleases))
        }
        if (playlists.isNotEmpty()) {
            sections.add(SpotifyHomeSection(title = "spotify_your_playlists", type = SectionType.PLAYLISTS, playlists = playlists))
        }

        feed?.sections?.forEach { raw ->
            convertHomeSection(raw)?.let(sections::add)
        }

        SpotifyHomeData(
            greeting = feed?.greeting,
            user = user,
            sections = sections,
            topTracks = topTracks,
            topArtists = topArtists,
            playlists = playlists,
            newReleases = newReleases,
            homeFeed = feed?.sections ?: emptyList(),
            generatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Converts a Spotify home-feed section (mixed types) into our [SpotifyHomeSection]
     * model. Picks the dominant content type when a section is heterogeneous.
     */
    private fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
        val title = feedSection.title ?: return null

        val playlists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
        val albums = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Album>()
        val artists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Artist>()

        val counts = listOf(
            SectionType.PLAYLISTS to playlists.size,
            SectionType.ALBUMS to albums.size,
            SectionType.ARTISTS to artists.size,
        )
        val (dominant, size) = counts.maxByOrNull { it.second } ?: return null
        if (size == 0) return null

        return when (dominant) {
            SectionType.PLAYLISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.PLAYLISTS,
                playlists = playlists.map(::toSpotifyPlaylist),
            )
            SectionType.ALBUMS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ALBUMS,
                albums = albums.map(::toSpotifyAlbum),
            )
            SectionType.ARTISTS -> SpotifyHomeSection(
                title = title,
                type = SectionType.ARTISTS,
                artists = artists.map(::toSpotifyArtist),
            )
            SectionType.TRACKS -> null
        }
    }

    private fun toSpotifyPlaylist(p: SpotifyHomeFeedItem.Playlist): SpotifyPlaylist =
        SpotifyPlaylist(
            id = p.id,
            name = p.name,
            description = p.description,
            images = p.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            owner = p.ownerName?.let { SpotifyPlaylistOwner(displayName = it) },
            trackCount = p.totalCount,
            uri = p.uri,
        )

    private fun toSpotifyAlbum(a: SpotifyHomeFeedItem.Album): SpotifyAlbum =
        SpotifyAlbum(
            id = a.id,
            name = a.name,
            albumType = a.albumType,
            artists = a.artists,
            images = a.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            uri = a.uri,
        )

    private fun toSpotifyArtist(a: SpotifyHomeFeedItem.Artist): SpotifyArtist =
        SpotifyArtist(
            id = a.id,
            name = a.name,
            images = a.imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
            uri = a.uri,
        )

    override suspend fun searchTracks(query: String, limit: Int): Result<List<SpotifyTrack>> {
        val result = SpotifyClient.search(query, types = listOf("track"), limit = limit)
        return result.map { it.tracks?.items ?: emptyList() }
    }

    override suspend fun resolveToSong(track: SpotifyTrack): Result<SongItem> =
        withContext(Dispatchers.IO) {
            val song = spotifyYouTubeMapper.mapToSongItem(track)
                ?: return@withContext Result.failure(IllegalStateException("No YouTube match found for '${track.name}'"))
            Result.success(song)
        }

    // ── Home data cache (SharedPreferences, 6h TTL) ──────────────────────

    private fun loadHomeDataCache(): SpotifyHomeData? {
        return try {
            val json = tokenManager.prefs().getString(HOME_CACHE_KEY, null) ?: return null
            val ts = tokenManager.prefs().getLong(HOME_CACHE_TS_KEY, 0L)
            parseHomeDataCache(JSONObject(json), ts)
        } catch (e: Exception) {
            Log.w(TAG, "failed to parse home data cache: ${e.message}")
            null
        }
    }

    private fun saveHomeDataCache(data: SpotifyHomeData) {
        try {
            val root = JSONObject()
                .put("greeting", data.greeting ?: JSONObject.NULL)
                .put("user", data.user?.let { userToJson(it) } ?: JSONObject.NULL)
                .put("sections", JSONArray().apply { data.sections.forEach { put(sectionToJson(it)) } })
                .put("topTracks", JSONArray().apply { data.topTracks.forEach { put(trackToJson(it)) } })
                .put("topArtists", JSONArray().apply { data.topArtists.forEach { put(artistToJson(it)) } })
                .put("playlists", JSONArray().apply { data.playlists.forEach { put(playlistToJson(it)) } })
                .put("newReleases", JSONArray().apply { data.newReleases.forEach { put(spotifyAlbumToJson(it)) } })
                .put("homeFeed", JSONArray().apply { data.homeFeed.forEach { put(homeFeedSectionToJson(it)) } })

            tokenManager.prefs().edit()
                .putString(HOME_CACHE_KEY, root.toString())
                .putLong(HOME_CACHE_TS_KEY, data.generatedAt)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "failed to cache home data: ${e.message}")
        }
    }

    private fun parseHomeDataCache(root: JSONObject, generatedAt: Long): SpotifyHomeData {
        val user = root.optJSONObject("user")?.let { parseUserJson(it) }
        val sections = root.optJSONArray("sections")?.let { arr ->
            buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { parseSection(it)?.let(::add) } }
        } ?: emptyList()
        return SpotifyHomeData(
            greeting = root.optString("greeting").ifEmpty { null },
            user = user,
            sections = sections,
            topTracks = root.optJSONArray("topTracks")?.let(::parseTracks) ?: emptyList(),
            topArtists = root.optJSONArray("topArtists")?.let(::parseArtists) ?: emptyList(),
            playlists = root.optJSONArray("playlists")?.let(::parsePlaylists) ?: emptyList(),
            newReleases = root.optJSONArray("newReleases")?.let(::parseAlbums) ?: emptyList(),
            homeFeed = root.optJSONArray("homeFeed")?.let(::parseHomeFeedSections) ?: emptyList(),
            generatedAt = generatedAt,
        )
    }

    private fun userToJson(u: SpotifyUser): JSONObject =
        JSONObject()
            .put("id", u.id)
            .put("name", u.displayName ?: "")

    private fun parseUserJson(o: JSONObject): SpotifyUser =
        SpotifyUser(
            id = o.optString("id", ""),
            displayName = o.optString("name").ifEmpty { null },
        )

    private fun sectionToJson(s: SpotifyHomeSection): JSONObject {
        val o = JSONObject().put("title", s.title).put("type", s.type.name)
        o.put("tracks", JSONArray().apply { s.tracks.forEach { put(trackToJson(it)) } })
        o.put("artists", JSONArray().apply { s.artists.forEach { put(artistToJson(it)) } })
        o.put("albums", JSONArray().apply { s.albums.forEach { put(spotifyAlbumToJson(it)) } })
        o.put("playlists", JSONArray().apply { s.playlists.forEach { put(playlistToJson(it)) } })
        return o
    }

    private fun parseSection(o: JSONObject): SpotifyHomeSection? {
        val type = runCatching { SectionType.valueOf(o.optString("type", "")) }.getOrNull() ?: return null
        return SpotifyHomeSection(
            title = o.optString("title", ""),
            type = type,
            tracks = o.optJSONArray("tracks")?.let(::parseTracks) ?: emptyList(),
            artists = o.optJSONArray("artists")?.let(::parseArtists) ?: emptyList(),
            albums = o.optJSONArray("albums")?.let(::parseAlbums) ?: emptyList(),
            playlists = o.optJSONArray("playlists")?.let(::parsePlaylists) ?: emptyList(),
        )
    }

    private fun trackToJson(t: SpotifyTrack): JSONObject {
        val o = JSONObject()
            .put("id", t.id)
            .put("name", t.name)
            .put("durationMs", t.durationMs)
            .put("explicit", t.explicit)
        t.uri?.let { o.put("uri", it) }
        t.previewUrl?.let { o.put("previewUrl", it) }
        t.popularity?.let { o.put("popularity", it) }
        t.externalIds?.isrc?.let { o.put("isrc", it) }
        o.put("artists", JSONArray().apply { t.artists.forEach { put(simpleArtistToJson(it)) } })
        t.album?.let { o.put("album", albumToJson(it)) }
        return o
    }

    private fun parseTracks(arr: JSONArray): List<SpotifyTrack> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { parseTrackJson(it)?.let(::add) }
        }
    }

    private fun parseTrackJson(o: JSONObject): SpotifyTrack? {
        val id = o.optString("id", "").ifEmpty { return null }
        return SpotifyTrack(
            id = id,
            name = o.optString("name", ""),
            artists = o.optJSONArray("artists")?.let(::parseSimpleArtists) ?: emptyList(),
            album = o.optJSONObject("album")?.let(::parseAlbumJson),
            durationMs = o.optInt("durationMs", 0),
            explicit = o.optBoolean("explicit", false),
            previewUrl = o.optString("previewUrl").ifEmpty { null },
            uri = o.optString("uri").ifEmpty { null },
            popularity = if (o.has("popularity")) o.optInt("popularity") else null,
            externalIds = o.optString("isrc").takeIf { it.isNotEmpty() }?.let { SpotifyExternalIds(isrc = it) },
        )
    }

    private fun simpleArtistToJson(a: SpotifySimpleArtist): JSONObject {
        val o = JSONObject().put("name", a.name)
        a.id?.let { o.put("id", it) }
        a.uri?.let { o.put("uri", it) }
        return o
    }

    private fun parseSimpleArtists(arr: JSONArray): List<SpotifySimpleArtist> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { a ->
                add(
                    SpotifySimpleArtist(
                        id = a.optString("id").ifEmpty { null },
                        name = a.optString("name", ""),
                        uri = a.optString("uri").ifEmpty { null },
                    )
                )
            }
        }
    }

    private fun albumToJson(a: SpotifySimpleAlbum): JSONObject {
        val o = JSONObject().put("id", a.id).put("name", a.name)
        a.releaseDate?.let { o.put("releaseDate", it) }
        a.albumType?.let { o.put("albumType", it) }
        a.uri?.let { o.put("uri", it) }
        o.put("artists", JSONArray().apply { a.artists.forEach { put(simpleArtistToJson(it)) } })
        o.put("images", JSONArray().apply { a.images.forEach { put(imageToJson(it)) } })
        return o
    }

    private fun parseAlbumJson(o: JSONObject): SpotifySimpleAlbum =
        SpotifySimpleAlbum(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            images = o.optJSONArray("images")?.let(::parseImages) ?: emptyList(),
            releaseDate = o.optString("releaseDate").ifEmpty { null },
            albumType = o.optString("albumType").ifEmpty { null },
            artists = o.optJSONArray("artists")?.let(::parseSimpleArtists) ?: emptyList(),
            uri = o.optString("uri").ifEmpty { null },
        )

    private fun imageToJson(img: SpotifyImage): JSONObject {
        val o = JSONObject().put("url", img.url)
        img.height?.let { o.put("height", it) }
        img.width?.let { o.put("width", it) }
        return o
    }

    private fun parseImages(arr: JSONArray): List<SpotifyImage> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { img ->
                val url = img.optString("url", "").ifEmpty { return@let }
                add(
                    SpotifyImage(
                        url = url,
                        height = if (img.has("height")) img.optInt("height") else null,
                        width = if (img.has("width")) img.optInt("width") else null,
                    )
                )
            }
        }
    }

    private fun artistToJson(a: SpotifyArtist): JSONObject {
        val o = JSONObject().put("id", a.id).put("name", a.name)
        a.popularity?.let { o.put("popularity", it) }
        a.uri?.let { o.put("uri", it) }
        o.put("genres", JSONArray().apply { a.genres.forEach { put(it) } })
        o.put("images", JSONArray().apply { a.images.forEach { put(imageToJson(it)) } })
        return o
    }

    private fun parseArtists(arr: JSONArray): List<SpotifyArtist> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { a ->
                add(
                    SpotifyArtist(
                        id = a.optString("id", ""),
                        name = a.optString("name", ""),
                        images = a.optJSONArray("images")?.let(::parseImages) ?: emptyList(),
                        genres = a.optJSONArray("genres")?.let(::parseStringArray) ?: emptyList(),
                        popularity = if (a.has("popularity")) a.optInt("popularity") else null,
                        uri = a.optString("uri").ifEmpty { null },
                    )
                )
            }
        }
    }

    private fun parseStringArray(arr: JSONArray): List<String> = buildList {
        for (i in 0 until arr.length()) {
            arr.optString(i, "").takeIf { it.isNotEmpty() }?.let(::add)
        }
    }

    private fun playlistToJson(p: SpotifyPlaylist): JSONObject {
        val o = JSONObject().put("id", p.id).put("name", p.name).put("trackCount", p.trackCount)
        p.description?.let { o.put("description", it) }
        p.uri?.let { o.put("uri", it) }
        p.public?.let { o.put("public", it) }
        p.owner?.let { own ->
            val ownerObj = JSONObject().put("id", own.id)
            own.displayName?.let { ownerObj.put("displayName", it) }
            own.uri?.let { ownerObj.put("uri", it) }
            o.put("owner", ownerObj)
        }
        o.put("images", JSONArray().apply { p.images.forEach { put(imageToJson(it)) } })
        return o
    }

    private fun parsePlaylists(arr: JSONArray): List<SpotifyPlaylist> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { p ->
                add(
                    SpotifyPlaylist(
                        id = p.optString("id", ""),
                        name = p.optString("name", ""),
                        description = p.optString("description").ifEmpty { null },
                        images = p.optJSONArray("images")?.let(::parseImages) ?: emptyList(),
                        owner = p.optJSONObject("owner")?.let { o ->
                            SpotifyPlaylistOwner(
                                id = o.optString("id", ""),
                                displayName = o.optString("displayName").ifEmpty { null },
                                uri = o.optString("uri").ifEmpty { null },
                            )
                        },
                        trackCount = p.optInt("trackCount", 0),
                        uri = p.optString("uri").ifEmpty { null },
                        public = if (p.has("public")) p.optBoolean("public") else null,
                    )
                )
            }
        }
    }

    private fun spotifyAlbumToJson(a: SpotifyAlbum): JSONObject {
        val o = JSONObject().put("id", a.id).put("name", a.name).put("totalTracks", a.totalTracks)
        a.albumType?.let { o.put("albumType", it) }
        a.releaseDate?.let { o.put("releaseDate", it) }
        a.uri?.let { o.put("uri", it) }
        a.popularity?.let { o.put("popularity", it) }
        o.put("artists", JSONArray().apply { a.artists.forEach { put(simpleArtistToJson(it)) } })
        o.put("images", JSONArray().apply { a.images.forEach { put(imageToJson(it)) } })
        return o
    }

    private fun parseAlbums(arr: JSONArray): List<SpotifyAlbum> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { a ->
                add(
                    SpotifyAlbum(
                        id = a.optString("id", ""),
                        name = a.optString("name", ""),
                        albumType = a.optString("albumType").ifEmpty { null },
                        artists = a.optJSONArray("artists")?.let(::parseSimpleArtists) ?: emptyList(),
                        images = a.optJSONArray("images")?.let(::parseImages) ?: emptyList(),
                        releaseDate = a.optString("releaseDate").ifEmpty { null },
                        totalTracks = a.optInt("totalTracks", 0),
                        uri = a.optString("uri").ifEmpty { null },
                        popularity = if (a.has("popularity")) a.optInt("popularity") else null,
                    )
                )
            }
        }
    }

    private fun homeFeedSectionToJson(s: SpotifyHomeFeedSection): JSONObject {
        val o = JSONObject()
            .put("sectionUri", s.sectionUri)
            .put("typename", s.typename)
            .put("totalCount", s.totalCount)
        s.title?.let { o.put("title", it) }
        o.put("items", JSONArray().apply { s.items.forEach { put(homeFeedItemToJson(it)) } })
        return o
    }

    private fun parseHomeFeedSections(arr: JSONArray): List<SpotifyHomeFeedSection> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { s ->
                add(
                    SpotifyHomeFeedSection(
                        sectionUri = s.optString("sectionUri", ""),
                        title = s.optString("title").ifEmpty { null },
                        typename = s.optString("typename", ""),
                        totalCount = s.optInt("totalCount", 0),
                        items = s.optJSONArray("items")?.let(::parseHomeFeedItems) ?: emptyList(),
                    )
                )
            }
        }
    }

    private fun parseHomeFeedItems(arr: JSONArray): List<SpotifyHomeFeedItem> = buildList {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { parseHomeFeedItem(it)?.let(::add) }
        }
    }

    private fun homeFeedItemToJson(item: SpotifyHomeFeedItem): JSONObject = when (item) {
        is SpotifyHomeFeedItem.Playlist -> {
            val o = JSONObject()
                .put("kind", "playlist")
                .put("uri", item.uri)
                .put("id", item.id)
                .put("name", item.name)
                .put("totalCount", item.totalCount)
            item.description?.let { o.put("description", it) }
            item.format?.let { o.put("format", it) }
            item.imageUrl?.let { o.put("imageUrl", it) }
            item.extractedColorHex?.let { o.put("extractedColorHex", it) }
            item.ownerName?.let { o.put("ownerName", it) }
            item.madeForUsername?.let { o.put("madeForUsername", it) }
            o
        }
        is SpotifyHomeFeedItem.Album -> {
            val o = JSONObject()
                .put("kind", "album")
                .put("uri", item.uri)
                .put("id", item.id)
                .put("name", item.name)
            item.albumType?.let { o.put("albumType", it) }
            item.imageUrl?.let { o.put("imageUrl", it) }
            o.put("artists", JSONArray().apply { item.artists.forEach { put(simpleArtistToJson(it)) } })
            o
        }
        is SpotifyHomeFeedItem.Artist -> {
            val o = JSONObject()
                .put("kind", "artist")
                .put("uri", item.uri)
                .put("id", item.id)
                .put("name", item.name)
            item.imageUrl?.let { o.put("imageUrl", it) }
            o
        }
    }

    private fun parseHomeFeedItem(o: JSONObject): SpotifyHomeFeedItem? {
        val uri = o.optString("uri", "")
        return when (o.optString("kind", "")) {
            "playlist" -> SpotifyHomeFeedItem.Playlist(
                uri = uri,
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                description = o.optString("description").ifEmpty { null },
                format = o.optString("format").ifEmpty { null },
                totalCount = o.optInt("totalCount", 0),
                imageUrl = o.optString("imageUrl").ifEmpty { null },
                extractedColorHex = o.optString("extractedColorHex").ifEmpty { null },
                ownerName = o.optString("ownerName").ifEmpty { null },
                madeForUsername = o.optString("madeForUsername").ifEmpty { null },
            )
            "album" -> SpotifyHomeFeedItem.Album(
                uri = uri,
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                albumType = o.optString("albumType").ifEmpty { null },
                artists = o.optJSONArray("artists")?.let(::parseSimpleArtists) ?: emptyList(),
                imageUrl = o.optString("imageUrl").ifEmpty { null },
            )
            "artist" -> SpotifyHomeFeedItem.Artist(
                uri = uri,
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                imageUrl = o.optString("imageUrl").ifEmpty { null },
            )
            else -> null
        }
    }

    private companion object {
        const val TAG = "SpotifyRepositoryImpl"
        const val HOME_CACHE_KEY = "spotify_home_cache"
        const val HOME_CACHE_TS_KEY = "spotify_home_cache_ts"
        const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
    }
}
