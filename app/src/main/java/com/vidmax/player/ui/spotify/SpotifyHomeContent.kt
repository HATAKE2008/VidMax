package com.vidmax.player.ui.spotify

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.spotify.model.SpotifyAlbum
import com.vidmax.player.data.spotify.model.SpotifyArtist
import com.vidmax.player.data.spotify.model.SpotifyHomeSection
import com.vidmax.player.data.spotify.model.SpotifyPlaylist
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.ui.components.SpotifySectionRow
import com.vidmax.player.ui.components.SpotifySectionShimmer
import com.vidmax.player.viewmodel.SpotifyUiState
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Spotify ব্র্যান্ড কালার
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyDark = Color(0xFF191414)

/**
 * Spotify হোম কনটেন্টের সব item গুলো একটি [LazyListScope]-এ ঢোকায় যাতে
 * OnlineMusicScreen এর বিদ্যমান LazyColumn-এর সাথে মিশিয়ে দেওয়া যায়।
 *
 * State অনুযায়ী: লগইন কার্ড → shimmer লোডিং → গ্রিটিং + সেকশন।
 */
fun LazyListScope.spotifyHomeItems(
    state: SpotifyUiState,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onTrackClick: (SpotifyTrack) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
) {
    when {
        // সেশন যাচাই চলছে
        state.checkingSession -> {
            item(key = "spotify_loading") { SpotifyHomeLoadingSkeleton() }
        }

        // লগইন করা নেই — লগইন কার্ড দেখাও
        !state.isLoggedIn -> {
            item(key = "spotify_login_card") { SpotifyLoginCard(onLoginClick = onLoginClick) }
        }

        // লগইন আছে, কিন্তু ডেটা এখনও লোড হচ্ছে — shimmer skeleton
        state.sections.isEmpty() && state.isLoading -> {
            item(key = "spotify_loading") { SpotifyHomeLoadingSkeleton() }
        }

        // লগইন আছে, কোনো সেকশন নেই — এরর বা খালি স্টেট
        state.sections.isEmpty() -> {
            if (state.error != null) {
                item(key = "spotify_error") {
                    SpotifyErrorRow(message = state.error, onRetry = onRetry)
                }
            } else {
                item(key = "spotify_empty") { SpotifyEmptyState() }
            }
        }

        // ডেটা আছে — গ্রিটিং + staggered entrance সহ প্রতিটি সেকশন
        else -> {
            item(key = "spotify_greeting") { SpotifyGreeting(state = state) }
            if (state.error != null) {
                item(key = "spotify_error") {
                    SpotifyErrorRow(message = state.error, onRetry = onRetry)
                }
            }
            itemsIndexed(
                items = state.sections,
                key = { index, section -> "spotify_${index}_${section.type.name}_${section.title}" },
            ) { index, section ->
                SpotifySectionItem(
                    index = index,
                    section = section,
                    onTrackClick = onTrackClick,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                )
            }
        }
    }
}

/**
 * প্রতিটি সেকশন ছোট delay দিয়ে fade + scale ইন হয়ে আসে (staggered entrance)।
 */
@Composable
private fun SpotifySectionItem(
    index: Int,
    section: SpotifyHomeSection,
    onTrackClick: (SpotifyTrack) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        launch { alpha.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        SpotifySectionRow(
            section = section,
            onTrackClick = onTrackClick,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onPlaylistClick = onPlaylistClick,
        )
    }
}

/**
 * গ্রিটিং হেডার — "শুভ সন্ধ্যা, UserName" + "Made for you" সাবটাইটেল।
 */
@Composable
private fun SpotifyGreeting(state: SpotifyUiState) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing)) }
    }

    val displayName = state.user?.displayName
    val greeting = state.homeData?.greeting?.takeIf { it.isNotBlank() }
        ?: greetingForCurrentTime()
    val greetingText = if (displayName.isNullOrBlank()) greeting else "$greeting, $displayName"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = greetingText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.spotify_suggestions_title),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun greetingForCurrentTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "শুভ সকাল"
        in 12..16 -> "শুভ দুপুর"
        in 17..20 -> "শুভ সন্ধ্যা"
        else -> "শুভ রাত"
    }
}

/**
 * লগইন করা না থাকলে দেখানো Spotify-ব্র্যান্ডেড কার্ড —
 * subtle pulse অ্যানিমেশনসহ লগইন বাটন।
 */
@Composable
private fun SpotifyLoginCard(onLoginClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spotifyLoginPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loginPulseScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SpotifyDark)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(SpotifyGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Spotify",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.spotify_connect_description),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpotifyGreen,
                contentColor = SpotifyDark,
            ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = null,
                tint = SpotifyDark,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.spotify_login_with_spotify),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * ডেটা লোড ব্যর্থ হলে এরর মেসেজ + Retry বাটন।
 */
@Composable
private fun SpotifyErrorRow(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.spotify_retry))
        }
    }
}

/**
 * লগইন করা আছে কিন্তু কোনো সাজেশন পাওয়া যায়নি — বন্ধুত্বপূর্ণ খালি স্টেট।
 */
@Composable
private fun SpotifyEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.spotify_suggestions_empty_title),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.spotify_suggestions_empty_message),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * হোম ডেটা লোড হওয়ার সময় Meld-শৈলীর shimmer skeleton।
 */
@Composable
private fun SpotifyHomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextPlaceholder(
            height = 24.dp,
            widthFraction = 0.55f,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        repeat(3) {
            SpotifySectionShimmer()
        }
    }
}
