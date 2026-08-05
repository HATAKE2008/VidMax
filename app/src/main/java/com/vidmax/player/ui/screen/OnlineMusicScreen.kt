package com.vidmax.player.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vidmax.player.R
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.ui.components.ArtworkImage
import com.vidmax.player.ui.components.CategorySkeletonRow
import com.vidmax.player.ui.components.SongCard
import com.vidmax.player.ui.components.shimmer
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.MusicHomeUiState
import com.vidmax.player.viewmodel.MusicHomeViewModel
import com.vidmax.player.viewmodel.MusicPlayerViewModel
import com.vidmax.player.viewmodel.MusicSearchViewModel
import com.vidmax.player.viewmodel.SearchUiState
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// 🌸 Mood / Genre specs (Spotify-style without emojis)
data class PlaylistSpec(
    val title: String,
    val subtitle: String,
    val query: String,
    val colors: List<Color>
)

// 🌸 Mood / Genre playlist state
data class PlaylistDetailState(
    val spec: PlaylistSpec,
    val songs: List<SongItem> = emptyList(),
    val isLoading: Boolean = false
)

private val playlistSpecs = listOf(
    PlaylistSpec("Bengali Chill", "Relax & unwind", "bengali chill songs playlist", listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
    PlaylistSpec("Late Night Vibes", "Sleepy mood", "late night vibes playlist", listOf(Color(0xFF0F2027), Color(0xFF2C5364))),
    PlaylistSpec("Romantic Evening", "Love songs", "romantic hindi songs 2026", listOf(Color(0xFFE96443), Color(0xFF904E95))),
    PlaylistSpec("Workout Energy", "Gym motivation", "workout gym songs 2026", listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
    PlaylistSpec("Road Trip", "Travel beats", "hindi road trip songs", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    PlaylistSpec("Study Focus", "No distractions", "study music instrumental", listOf(Color(0xFF4568DC), Color(0xFFB06AB3))),
    PlaylistSpec("Party Mix", "Dance floor", "party dance songs 2026", listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))),
)

// 🌸 Staggered enter animation
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
    
    val listState = rememberLazyListState()
    var isTopBarVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    // Scroll listener to hide/show top bar
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                if (index > previousIndex || (index == previousIndex && offset > previousScrollOffset + 15)) {
                    isTopBarVisible = false // Scrolling down -> Hide
                } else if (index < previousIndex || (index == previousIndex && offset < previousScrollOffset - 15)) {
                    isTopBarVisible = true // Scrolling up -> Show
                }
                if (index == 0 && offset == 0) {
                    isTopBarVisible = true
                }
                previousIndex = index
                previousScrollOffset = offset
            }
    }

    var playlistState by remember { mutableStateOf<PlaylistDetailState?>(null) }
    BackHandler(enabled = playlistState != null) {
        playlistState = null
    }

    val handleSongClick: (SongItem) -> Unit = { song ->
        libraryViewModel?.pauseAudio()
        playerViewModel.playSong(song)
        focusManager.clearFocus()
    }

    val handleMoodClick: (PlaylistSpec) -> Unit = { spec ->
        playlistState = PlaylistDetailState(spec = spec, isLoading = true)
        homeViewModel.fetchPlaylist(spec.query) { songs ->
            if (songs.isEmpty()) {
                playlistState = null
                scope.launch { snackbarHostState.showSnackbar("Playlist is empty") }
            } else {
                playlistState = PlaylistDetailState(spec = spec, songs = songs)
            }
        }
    }

    val handlePlayQueue: (List<SongItem>) -> Unit = { songs ->
        if (songs.isNotEmpty()) {
            libraryViewModel?.pauseAudio()
            playerViewModel.playQueue(songs)
            focusManager.clearFocus()
        }
    }

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
            
            // 🌸 Animated Hide/Show Header and Search Bar
            AnimatedVisibility(
                visible = isTopBarVisible || isSearchActive,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    OnlineHeader(onSettingsClick = onSettingsClick)
                    OnlineSearchBar(
                        query = searchState.query,
                        onQueryChange = { searchViewModel.onQueryChange(it) },
                        onClearClick = {
                            searchViewModel.clearSearch()
                            focusManager.clearFocus()
                        },
                        onSearchAction = { focusManager.clearFocus() }
                    )
                }
            }

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
                    MeldOnlineHomeContent(
                        homeState = homeState,
                        listState = listState,
                        onSongClick = handleSongClick,
                        onPlaylistClick = handleMoodClick,
                        onPlayQueue = handlePlayQueue,
                        onRefresh = { homeViewModel.loadHomeScreenData(forceRefresh = true) }
                    )
                }
            }
        }

        // Playlist Overlay
        AnimatedVisibility(
            visible = playlistState != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(280)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f)
        ) {
            playlistState?.let { state ->
                MeldPlaylistDetailScreen(
                    state = state,
                    activeVideoId = playerState.currentSong?.videoId,
                    onBack = { playlistState = null },
                    onPlayAll = {
                        if (state.songs.isNotEmpty()) {
                            libraryViewModel?.pauseAudio()
                            playerViewModel.playQueue(state.songs)
                            focusManager.clearFocus()
                        }
                    },
                    onSongClick = { index ->
                        libraryViewModel?.pauseAudio()
                        playerViewModel.playQueue(state.songs, index)
                        focusManager.clearFocus()
                    }
                )
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 170.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun OnlineHeader(onSettingsClick: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "VidMax",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeldOnlineHomeContent(
    homeState: MusicHomeUiState,
    listState: LazyListState,
    onSongClick: (SongItem) -> Unit,
    onPlaylistClick: (PlaylistSpec) -> Unit,
    onPlayQueue: (List<SongItem>) -> Unit,
    onRefresh: () -> Unit
) {
    val quickPicks = remember(homeState.categories) {
        val source = homeState.categories.firstOrNull { it.title == "For You" }
            ?: homeState.categories.firstOrNull { it.songs.isNotEmpty() }
        source?.songs?.distinctBy { it.videoId }?.take(8) ?: emptyList()
    }
    val hasError = homeState.error != null && homeState.categories.isEmpty()

    PullToRefreshBox(
        isRefreshing = homeState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp, top = 8.dp)
        ) {
            when {
                homeState.isLoading && homeState.categories.isEmpty() -> {
                    item { MeldHomeShimmer() }
                }
                hasError -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = homeState.error ?: "Unable to load music suggestions",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = onRefresh) {
                                Text("Try again")
                            }
                        }
                    }
                }
                else -> {
                    if (quickPicks.isNotEmpty()) {
                        item(key = "quick_picks") {
                            QuickPicksSection(
                                songs = quickPicks,
                                onSongClick = onSongClick,
                                onPlayAllClick = { onPlayQueue(quickPicks) },
                                index = 0
                            )
                        }
                    }
                    item(key = "mood_and_genres") {
                        MoodAndGenresRow(
                            onMoodClick = onPlaylistClick,
                            index = 1
                        )
                    }
                    if (homeState.recentlyPlayed.isNotEmpty()) {
                        item(key = "recently_played") {
                            MusicSectionRow(
                                title = "Recently Played",
                                songs = homeState.recentlyPlayed,
                                onSongClick = onSongClick,
                                onPlayAllClick = { onPlayQueue(homeState.recentlyPlayed) },
                                index = 2
                            )
                        }
                    }
                    if (homeState.favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            MusicSectionRow(
                                title = "Favorites",
                                songs = homeState.favorites,
                                onSongClick = onSongClick,
                                onPlayAllClick = { onPlayQueue(homeState.favorites) },
                                index = 3
                            )
                        }
                    }
                    itemsIndexed(homeState.categories, key = { _, category -> category.title }) { idx, category ->
                        MusicSectionRow(
                            title = category.title,
                            songs = category.songs,
                            onSongClick = onSongClick,
                            onPlayAllClick = { onPlayQueue(category.songs) },
                            index = idx + 4
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationTitle(
    title: String,
    onPlayAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onPlayAllClick != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.82f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "playAllScale"
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPlayAllClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play all",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun QuickPicksSection(
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: () -> Unit,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        NavigationTitle(
            title = "Quick Picks",
            onPlayAllClick = onPlayAllClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            items(songs, key = { it.videoId }) { song ->
                QuickPickCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
fun QuickPickCard(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "quickPickScale"
    )
    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            ArtworkImage(
                videoId = song.videoId,
                fallbackUrl = song.thumbnailUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loadingPlaceholder = {
                    Box(modifier = Modifier.fillMaxSize().shimmer())
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = song.artist,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 🌸 Mood & Genres Spotify Style
@Composable
fun MoodAndGenresRow(
    onMoodClick: (PlaylistSpec) -> Unit,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        NavigationTitle(
            title = "Mood & Genres",
            onPlayAllClick = null,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlistSpecs) { spec ->
                MoodAndGenresButton(
                    spec = spec,
                    onClick = { onMoodClick(spec) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MoodAndGenresButton(
    spec: PlaylistSpec,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "moodScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .width(130.dp)
            .height(75.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(spec.colors))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Text(
            text = spec.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
fun MusicSectionRow(
    title: String,
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    onPlayAllClick: (() -> Unit)?,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        NavigationTitle(
            title = title,
            onPlayAllClick = onPlayAllClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(songs.distinctBy { it.videoId }) { song ->
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
fun MeldHomeShimmer() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .size(width = 90.dp, height = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(width = 140.dp, height = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            items(count = 8) {
                QuickPickSkeleton()
            }
        }
        CategorySkeletonRow()
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .size(width = 130.dp, height = 18.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(count = 6) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(75.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        CategorySkeletonRow()
    }
}

@Composable
fun QuickPickSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
    }
}

@Composable
fun MeldPlaylistDetailScreen(
    state: PlaylistDetailState,
    activeVideoId: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = state.spec.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .shimmer()
                    )
                }
                items(count = 8) {
                    PlaylistSongRowSkeleton()
                }
            }
        } else if (state.songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No songs found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Brush.linearGradient(state.spec.colors))
                            .padding(20.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(
                                text = state.spec.title,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.spec.subtitle} • ${state.songs.size} songs",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play all")
                        }
                    }
                }
                itemsIndexed(state.songs, key = { _, song -> song.videoId }) { index, song ->
                    PlaylistSongRow(
                        song = song,
                        isActive = song.videoId == activeVideoId,
                        onClick = { onSongClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistSongRow(
    song: SongItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            videoId = song.videoId,
            fallbackUrl = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlaylistSongRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
    }
}

// 🌸 Redesigned Compact Search Bar
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp),
        placeholder = { Text("Search songs...", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                }
            }
        },
        shape = RoundedCornerShape(26.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() })
    )
}

@Composable
fun OnlineSearchContent(
    searchState: SearchUiState,
    onSongClick: (SongItem) -> Unit
) {
    if (searchState.isSearching) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (searchState.searchResults.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 160.dp)
        ) {
            item {
                Text(
                    text = "Suggested Results",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
            }
            itemsIndexed(searchState.searchResults) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.enterAnimation(index)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    } else if (!searchState.error.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = searchState.error, color = MaterialTheme.colorScheme.error)
        }
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
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            videoId = song.videoId,
            fallbackUrl = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(6.dp))
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
            Spacer(modifier = Modifier.height(2.dp))
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
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
