package com.vidmax.player.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.ui.components.ArtworkImage
import com.vidmax.player.ui.components.CategorySkeletonRow
import com.vidmax.player.ui.components.SongCard
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.MusicHomeViewModel
import com.vidmax.player.viewmodel.MusicPlayerViewModel
import com.vidmax.player.viewmodel.MusicSearchViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 🌸 Playlist card specs (Bloomee-style colorful cards)
private data class PlaylistSpec(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val query: String,
    val colors: List<Color>
)

private val playlistSpecs = listOf(
    PlaylistSpec("Bengali Chill", "Relax & unwind", "🌙", "bengali chill songs playlist", listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
    PlaylistSpec("Late Night Vibes", "Sleepy mood", "🌃", "late night vibes playlist", listOf(Color(0xFF0F2027), Color(0xFF2C5364))),
    PlaylistSpec("Romantic Evening", "Love songs", "❤️", "romantic hindi songs 2026", listOf(Color(0xFFE96443), Color(0xFF904E95))),
    PlaylistSpec("Workout Energy", "Gym motivation", "💪", "workout gym songs 2026", listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
    PlaylistSpec("Road Trip", "Travel beats", "🚗", "hindi road trip songs", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    PlaylistSpec("Study Focus", "No distractions", "📚", "study music instrumental", listOf(Color(0xFF4568DC), Color(0xFFB06AB3))),
    PlaylistSpec("Party Mix", "Dance floor", "🎉", "party dance songs 2026", listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))),
)

// 🌸 Staggered enter animation — rows/items fade + slide in one by one
@Composable
private fun Modifier.enterAnimation(index: Int): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80L + index * 90L)
        entered = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "enterAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "enterOffset"
    )
    return this
        .alpha(alpha)
        .offset(y = offsetY.dp)
}

@Composable
fun OnlineMusicScreen(
    homeViewModel: MusicHomeViewModel,
    searchViewModel: MusicSearchViewModel,
    playerViewModel: MusicPlayerViewModel,
    onOpenFullPlayer: () -> Unit,
    onSettingsClick: () -> Unit,
    libraryViewModel: LibraryViewModel? = null
) {
    val searchState by searchViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val playerState by playerViewModel.uiState.collectAsState()

    val focusManager = LocalFocusManager.current
    val isSearchActive = searchState.query.isNotBlank() || searchState.searchResults.isNotEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val handleSongClick: (SongItem) -> Unit = { song ->
        libraryViewModel?.pauseAudio()
        playerViewModel.playSong(song)
        focusManager.clearFocus()
    }

    // 🌸 Playlist card click → fetch songs → play whole queue
    val handlePlaylistClick: (PlaylistSpec) -> Unit = { spec ->
        scope.launch {
            snackbarHostState.showSnackbar("Loading ${spec.title}...")
        }
        homeViewModel.fetchPlaylist(spec.query) { songs ->
            if (songs.isEmpty()) {
                scope.launch { snackbarHostState.showSnackbar("Playlist is empty") }
            } else {
                libraryViewModel?.pauseAudio()
                playerViewModel.playQueue(songs)
                focusManager.clearFocus()
            }
        }
    }

    // Tell the user when stream load / playback fails (no silent failure)
    LaunchedEffect(playerState.error) {
        playerState.error?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🌸 Bloomee-style header: title + settings gear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
                    .enterAnimation(0),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discover",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            OnlineSearchBar(
                query = searchState.query,
                onQueryChange = { searchViewModel.onQueryChange(it) },
                onClearClick = {
                    searchViewModel.clearSearch()
                    focusManager.clearFocus()
                },
                onSearchAction = { focusManager.clearFocus() }
            )

            Crossfade(
                targetState = isSearchActive,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "ScreenTransition"
            ) { showSearch ->
                if (showSearch) {
                    OnlineSearchContent(
                        searchState = searchState,
                        onSongClick = handleSongClick
                    )
                } else {
                    OnlineHomeContent(
                        homeState = homeState,
                        onSongClick = handleSongClick,
                        onPlaylistClick = handlePlaylistClick
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerState.currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp, start = 16.dp, end = 16.dp)
        ) {
            playerState.currentSong?.let { song ->
                val safeDuration = if (playerState.duration > 0) playerState.duration else 1L
                val progress = (playerState.position.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                OnlineMiniPlayer(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    isLoading = playerState.isLoadingStream,
                    progress = progress,
                    isFavorite = playerState.isFavorite,
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onNextClick = { playerViewModel.playNextOnlineSong() },
                    onToggleFavorite = { playerViewModel.toggleFavorite() },
                    onClick = onOpenFullPlayer
                )
            }
        }

        // Stream failure feedback — shown above the mini player
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 170.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onSearchAction: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Search songs...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() })
    )
}

@Composable
fun OnlineHomeContent(
    homeState: com.vidmax.player.viewmodel.MusicHomeUiState,
    onSongClick: (SongItem) -> Unit,
    onPlaylistClick: (PlaylistSpec) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp) // Extra padding for Mini Player
    ) {
        if (homeState.isLoading && homeState.categories.isEmpty()) {
            // Skeleton shimmer rows instead of a spinner
            items(count = 3) {
                CategorySkeletonRow()
            }
        } else {
            // 🌸 Trending Playlists (Bloomee-style colorful cards)
            item {
                PlaylistRow(onPlaylistClick = onPlaylistClick, index = 0)
            }

            // Recently Played Section
            if (homeState.recentlyPlayed.isNotEmpty()) {
                item {
                    CategoryRow(
                        title = "Recently Played",
                        songs = homeState.recentlyPlayed,
                        onSongClick = onSongClick,
                        index = 1
                    )
                }
            }

            // Other Categories (For You, Trending, New, Sad, Happy, Romantic, Lofi)
            itemsIndexed(homeState.categories) { idx, category ->
                CategoryRow(
                    title = category.title,
                    songs = category.songs,
                    onSongClick = onSongClick,
                    index = idx + 2
                )
            }
        }
    }
}

// 🌸 Colorful playlist cards row
@Composable
fun PlaylistRow(
    onPlaylistClick: (PlaylistSpec) -> Unit,
    index: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        Text(
            text = "Trending Playlists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlistSpecs) { spec ->
                PlaylistCard(spec = spec, onClick = { onPlaylistClick(spec) })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PlaylistCard(
    spec: PlaylistSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(170.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(spec.colors))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Text(text = spec.emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = spec.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OnlineSearchContent(
    searchState: com.vidmax.player.viewmodel.SearchUiState,
    onSongClick: (SongItem) -> Unit
) {
    if (searchState.isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (searchState.searchResults.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(searchState.searchResults) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.enterAnimation(index)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    } else if (!searchState.error.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = searchState.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun CategoryRow(
    title: String,
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    index: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs) { song ->
                SongCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SongListItem(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            videoId = song.videoId,
            fallbackUrl = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun OnlineMiniPlayer(
    song: SongItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    isFavorite: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .shadow(12.dp, RoundedCornerShape(50), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlayPauseClick() },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    strokeWidth = 3.dp,
                    trackColor = Color.Transparent
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = Color.Transparent
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkImage(
                        videoId = song.videoId,
                        fallbackUrl = song.thumbnailUrl,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                ),
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onNextClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
