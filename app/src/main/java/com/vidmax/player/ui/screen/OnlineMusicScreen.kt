package com.vidmax.player.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.vidmax.player.R
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.data.spotify.model.SpotifyAlbum
import com.vidmax.player.data.spotify.model.SpotifyArtist
import com.vidmax.player.data.spotify.model.SpotifyPlaylist
import com.vidmax.player.data.spotify.model.SpotifyTrack
import com.vidmax.player.ui.spotify.SpotifyLoginScreen
import com.vidmax.player.ui.spotify.spotifyHomeItems
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.MusicHomeViewModel
import com.vidmax.player.viewmodel.MusicPlayerViewModel
import com.vidmax.player.viewmodel.MusicSearchViewModel
import com.vidmax.player.viewmodel.SpotifyUiState
import com.vidmax.player.viewmodel.SpotifyViewModel
import kotlinx.coroutines.launch

@Composable
fun OnlineMusicScreen(
    homeViewModel: MusicHomeViewModel,
    searchViewModel: MusicSearchViewModel,
    playerViewModel: MusicPlayerViewModel,
    spotifyViewModel: SpotifyViewModel,
    onOpenFullPlayer: () -> Unit,
    libraryViewModel: LibraryViewModel? = null
) {
    val searchState by searchViewModel.uiState.collectAsState()
    val homeState by homeViewModel.uiState.collectAsState()
    val playerState by playerViewModel.uiState.collectAsState()
    val spotifyState by spotifyViewModel.uiState.collectAsState()

    val focusManager = LocalFocusManager.current
    val isSearchActive = searchState.query.isNotBlank() || searchState.searchResults.isNotEmpty()

    // Spotify লগইন ওভারলে খোলা/বন্ধ
    var showSpotifyLogin by remember { mutableStateOf(false) }

    if (showSpotifyLogin) {
        BackHandler { showSpotifyLogin = false }
    }

    val handleSongClick: (SongItem) -> Unit = { song ->
        libraryViewModel?.pauseAudio()
        playerViewModel.playSong(song)
        focusManager.clearFocus()
    }

    // Spotify ট্র্যাক ক্লিক → resolve করে প্লেয়ারে চালানো
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val handleSpotifyTrackClick: (SpotifyTrack) -> Unit = { track ->
        spotifyViewModel.resolveAndPlay(
            track = track,
            onSong = handleSongClick,
            onError = { msg ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        )
    }

    // Album / Radio / Playlist → পুরো track list queue আকারে play
    val showSnack: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    val handleSongsList: (List<SongItem>) -> Unit = { songs ->
        libraryViewModel?.pauseAudio()
        playerViewModel.playQueue(songs)
        focusManager.clearFocus()
    }

    val handleAlbumClick: (SpotifyAlbum) -> Unit = { album ->
        spotifyViewModel.playAlbum(album, onSongs = handleSongsList, onError = showSnack)
    }
    val handleArtistClick: (SpotifyArtist) -> Unit = { artist ->
        spotifyViewModel.playArtistRadio(artist, onSongs = handleSongsList, onError = showSnack)
    }
    val handlePlaylistClick: (SpotifyPlaylist) -> Unit = { playlist ->
        spotifyViewModel.playPlaylist(playlist, onSongs = handleSongsList, onError = showSnack)
    }

    // স্ট্রিম লোড/প্লেব্যাক ব্যর্থ হলে নীরবে বসে না থেকে ব্যবহারকারীকে জানাও
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
            
            OnlineSearchBar(
                query = searchState.query,
                onQueryChange = { searchViewModel.onQueryChange(it) },
                onClearClick = { 
                    searchViewModel.clearSearch()
                    focusManager.clearFocus()
                },
                onSearchAction = { focusManager.clearFocus() }
            )

            Crossfade(targetState = isSearchActive, label = "ScreenTransition") { showSearch ->
                if (showSearch) {
                    OnlineSearchContent(
                        searchState = searchState,
                        onSongClick = handleSongClick
                    )
                } else {
                    OnlineHomeContent(
                        homeState = homeState,
                        spotifyState = spotifyState,
                        onSongClick = handleSongClick,
                        onSpotifyLoginClick = { showSpotifyLogin = true },
                        onSpotifyRetry = { spotifyViewModel.loadHomeData(forceRefresh = true) },
                        onTrackClick = handleSpotifyTrackClick,
                        onArtistClick = handleArtistClick,
                        onAlbumClick = handleAlbumClick,
                        onPlaylistClick = handlePlaylistClick
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
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

        // Spotify লগইন ওভারলে — slide-in করে উপরে আসে
        AnimatedVisibility(
            visible = showSpotifyLogin,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SpotifyLoginScreen(
                    onClose = {
                        showSpotifyLogin = false
                        spotifyViewModel.checkSession()
                    }
                )
            }
        }

        // Spotify resolve failure হলে feedback — mini player-এর উপরে দেখায়
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
        placeholder = { Text("গান খুঁজুন...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        // 🔥 এখানে OutlinedTextFieldDefaults.colors ব্যবহার করে আপডেট করা হলো
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
    spotifyState: SpotifyUiState,
    onSongClick: (SongItem) -> Unit,
    onSpotifyLoginClick: () -> Unit,
    onSpotifyRetry: () -> Unit,
    onTrackClick: (SpotifyTrack) -> Unit,
    onArtistClick: (SpotifyArtist) -> Unit,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onPlaylistClick: (SpotifyPlaylist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp) // Extra padding for Mini Player
    ) {
        // 🔥 Spotify সাজেশন — লগইন কার্ড / shimmer / গ্রিটিং + সেকশন
        spotifyHomeItems(
            state = spotifyState,
            onLoginClick = onSpotifyLoginClick,
            onRetry = onSpotifyRetry,
            onTrackClick = onTrackClick,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onPlaylistClick = onPlaylistClick
        )

        if (homeState.isLoading && homeState.categories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Recently Played Section
            if (homeState.recentlyPlayed.isNotEmpty()) {
                item {
                    CategoryRow(
                        title = "সম্প্রতি শোনা",
                        songs = homeState.recentlyPlayed,
                        onSongClick = onSongClick
                    )
                }
            }

            // Other Categories (For You, Bengali Hits, etc.)
            items(homeState.categories) { category ->
                CategoryRow(
                    title = category.title,
                    songs = category.songs,
                    onSongClick = onSongClick
                )
            }
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
            items(searchState.searchResults) { song ->
                SongListItem(song = song, onClick = { onSongClick(song) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    } else if (!searchState.error.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = searchState.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CategoryRow(
    title: String,
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onSongClick(song) }
                ) {
                    GlideImage(
                        model = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(140.dp, 100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SongListItem(song: SongItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            model = song.thumbnailUrl,
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

@OptIn(ExperimentalGlideComposeApi::class)
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
                    GlideImage(
                        model = song.thumbnailUrl,
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
