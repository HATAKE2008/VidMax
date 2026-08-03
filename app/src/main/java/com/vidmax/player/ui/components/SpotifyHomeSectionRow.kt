package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.vidmax.player.R
import com.vidmax.player.data.spotify.model.SectionType
import com.vidmax.player.data.spotify.model.SpotifyAlbum
import com.vidmax.player.data.spotify.model.SpotifyArtist
import com.vidmax.player.data.spotify.model.SpotifyHomeSection
import com.vidmax.player.data.spotify.model.SpotifyPlaylist
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.ui.spotify.ShimmerBox
import com.vidmax.player.ui.spotify.TextPlaceholder

/**
 * Resolves the display title for a Spotify home section.
 * Handles special keys like "spotify_because_you_like:ArtistName".
 */
@Composable
fun resolveSpotifySectionTitle(section: SpotifyHomeSection): String {
    val title = section.title
    return when {
        title.startsWith("spotify_because_you_like:") -> {
            val artistName = title.removePrefix("spotify_because_you_like:")
            stringResource(R.string.spotify_because_you_like, artistName)
        }
        title == "spotify_top_tracks" -> stringResource(R.string.spotify_top_tracks)
        title == "spotify_top_artists" -> stringResource(R.string.spotify_top_artists)
        title == "spotify_made_for_you" -> stringResource(R.string.spotify_made_for_you)
        title == "spotify_discover" -> stringResource(R.string.spotify_discover)
        title == "spotify_your_playlists" -> stringResource(R.string.spotify_your_playlists)
        title == "spotify_new_releases" -> stringResource(R.string.spotify_new_releases)
        else -> title
    }
}

/**
 * একটি [SpotifyHomeSection] তার SectionType অনুযায়ী সঠিক Row-এ রেন্ডার করে।
 */
@Composable
fun SpotifySectionRow(
    section: SpotifyHomeSection,
    onTrackClick: (SpotifyTrack) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = resolveSpotifySectionTitle(section)
    when (section.type) {
        SectionType.TRACKS -> {
            if (section.tracks.isNotEmpty()) {
                SpotifyTrackSectionRow(
                    title = title,
                    tracks = section.tracks,
                    onTrackClick = onTrackClick,
                    modifier = modifier,
                )
            }
        }
        SectionType.ARTISTS -> {
            if (section.artists.isNotEmpty()) {
                SpotifyArtistSectionRow(
                    title = title,
                    artists = section.artists,
                    onArtistClick = onArtistClick,
                    modifier = modifier,
                )
            }
        }
        SectionType.ALBUMS -> {
            if (section.albums.isNotEmpty()) {
                SpotifyAlbumSectionRow(
                    title = title,
                    albums = section.albums,
                    onAlbumClick = onAlbumClick,
                    modifier = modifier,
                )
            }
        }
        SectionType.PLAYLISTS -> {
            if (section.playlists.isNotEmpty()) {
                SpotifyPlaylistSectionRow(
                    title = title,
                    playlists = section.playlists,
                    onPlaylistClick = onPlaylistClick,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * ট্র্যাক সেকশন — Meld-এর মতো 4-সারির horizontal grid।
 * প্রতিটি ঘরে কমপ্যাক্ট লিস্ট রো (ছোট থাম্বনেইল + টাইটেল + আর্টিস্ট) থাকে,
 * যা Spotify অ্যাপের মতো দেখতে।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SpotifyTrackSectionRow(
    title: String,
    tracks: List<SpotifyTrack>,
    onTrackClick: (SpotifyTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_ROW_HEIGHT * 4),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            gridItems(
                items = tracks,
                key = { "spotify_track_${it.id}" },
            ) { track ->
                SpotifyTrackGridItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Meld-এর YouTubeListItem-এর মতো কমপ্যাক্ট ট্র্যাক রো।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SpotifyTrackGridItem(
    track: SpotifyTrack,
    onClick: () -> Unit,
) {
    val artwork = remember(track.id) { track.album?.images?.firstOrNull()?.url }
    Row(
        modifier = Modifier
            .width(TRACK_GRID_ITEM_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GlideImage(
                model = artwork,
                contentDescription = track.name,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * আর্টিস্ট সেকশন — Meld-এর মতো বড় গোলাকার ছবি + নাম নিচে।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SpotifyArtistSectionRow(
    title: String,
    artists: List<SpotifyArtist>,
    onArtistClick: (SpotifyArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = artists, key = { "spotify_artist_${it.id}" }) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(116.dp)
                        .clickable { onArtistClick(artist) }
                ) {
                    GlideImage(
                        model = artist.bestImage(),
                        contentDescription = artist.name,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist.name,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * অ্যালবাম সেকশন — 130.dp বর্গাকার কার্ড + গোলাকার কোণা।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SpotifyAlbumSectionRow(
    title: String,
    albums: List<SpotifyAlbum>,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = albums, key = { "spotify_album_${it.id}" }) { album ->
                SpotifySquareCard(
                    title = album.name,
                    subtitle = album.artistName,
                    imageUrl = album.bestImage(),
                    onClick = { onAlbumClick(album) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * প্লেলিস্ট সেকশন — অ্যালবামের মতোই বর্গাকার কার্ড।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SpotifyPlaylistSectionRow(
    title: String,
    playlists: List<SpotifyPlaylist>,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = playlists, key = { "spotify_playlist_${it.id}" }) { playlist ->
                SpotifySquareCard(
                    title = playlist.name,
                    subtitle = playlist.ownerName,
                    imageUrl = playlist.bestImage(),
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * অ্যালবাম / প্লেলিস্টের জন্য সাধারণ বর্গাকার কার্ড কম্পোনেন্ট।
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SpotifySquareCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        GlideImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * লোডিং অবস্থার জন্য shimmer placeholder সেকশন — টাইটেল + কার্ড placeholder।
 */
@Composable
fun SpotifySectionShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextPlaceholder(
            height = 18.dp,
            widthFraction = 0.5f,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier.size(140.dp, 100.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private val TRACK_ROW_HEIGHT = 56.dp
private val TRACK_GRID_ITEM_WIDTH = 300.dp
