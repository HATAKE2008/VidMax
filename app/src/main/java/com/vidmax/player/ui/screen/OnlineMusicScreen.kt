package com.vidmax.player.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.vidmax.player.R
import com.vidmax.player.data.model.ArtistItem
import com.vidmax.player.data.model.SongItem
import com.vidmax.player.ui.components.ArtworkImage
import com.vidmax.player.ui.components.CategorySkeletonRow
import com.vidmax.player.ui.components.MeldChipsRow
import com.vidmax.player.ui.components.MeldSpeedDialSection
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

// 🌸 Mood / Genre specs (Updated to match the screenshot)
data class PlaylistSpec(
    val title: String,
    val subtitle: String,
    val query: String
)

data class PlaylistDetailState(
    val spec: PlaylistSpec,
    val songs: List<SongItem> = emptyList(),
    val isLoading: Boolean = false
)

data class ArtistDetailState(
    val artist: ArtistItem,
    val songs: List<SongItem> = emptyList(),
    val isLoading: Boolean = false
)

private val playlistSpecs = listOf(
    PlaylistSpec("Chill", "Relax & unwind", "chill music playlist"),
    PlaylistSpec("Focus", "Deep work", "focus music instrumental"),
    PlaylistSpec("Commute", "On the go", "commute travel songs"),
    PlaylistSpec("Gaming", "Level up", "gaming music mix"),
    PlaylistSpec("Energize", "Boost your day", "energizing workout songs"),
    PlaylistSpec("Party", "Dance floor", "party dance songs"),
    PlaylistSpec("Feel good", "Happy vibes", "feel good happy songs"),
    PlaylistSpec("Romance", "Love songs", "romantic love songs")
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

    var isSearchOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                if (index > previousIndex || (index == previousIndex && offset > previousScrollOffset + 15)) {
                    isTopBarVisible = false
                } else if (index < previousIndex || (index == previousIndex && offset < previousScrollOffset - 15)) {
                    isTopBarVisible = true
                }
                if (index == 0 && offset == 0) {
                    isTopBarVisible = true
                }
                previousIndex = index
                previousScrollOffset = offset
            }
    }

    var playlistState by remember { mutableStateOf<PlaylistDetailState?>(null) }
    var artistState by remember { mutableStateOf<ArtistDetailState?>(null) }
    var selectedChip by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfileSheet by remember { mutableStateOf(false) }
    
    BackHandler(enabled = playlistState != null || artistState != null || selectedChip != null || isSearchOpen) {
        when {
            playlistState != null -> playlistState = null
            artistState != null -> artistState = null
            selectedChip != null -> selectedChip = null
            else -> {
                isSearchOpen = false
                searchViewModel.clearSearch()
                focusManager.clearFocus()
            }
        }
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

    val handleArtistClick: (ArtistItem) -> Unit = { artist ->
        artistState = ArtistDetailState(artist = artist, isLoading = true)
        homeViewModel.fetchArtistSongs(artist.channelId) { songs ->
            artistState = if (songs.isEmpty()) {
                null
            } else {
                ArtistDetailState(artist = artist, songs = songs)
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
            
            AnimatedVisibility(
                visible = isTopBarVisible || isSearchActive || isSearchOpen,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column {
                    Crossfade(
                        targetState = isSearchOpen || isSearchActive,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "headerSearchToggle"
                    ) { showSearchBar ->
                        if (showSearchBar) {
                            OnlineSearchBar(
                                query = searchState.query,
                                onQueryChange = { searchViewModel.onQueryChange(it) },
                                onClearClick = { searchViewModel.clearSearch() },
                                onSearchAction = { focusManager.clearFocus() },
                                onBackClick = {
                                    isSearchOpen = false
                                    searchViewModel.clearSearch()
                                    focusManager.clearFocus()
                                },
                                focusRequester = searchFocusRequester
                            )
                        } else {
                            OnlineHeader(
                                onProfileClick = { showProfileSheet = true },
                                onSearchClick = { isSearchOpen = true },
                                onSettingsClick = onSettingsClick
                            )
                        }
                    }
                }
            }

            LaunchedEffect(isSearchOpen) {
                if (isSearchOpen) {
                    delay(250)
                    runCatching { searchFocusRequester.requestFocus() }
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
                        selectedChip = selectedChip,
                        onChipSelect = { selectedChip = it },
                        onSongClick = handleSongClick,
                        onPlaylistClick = handleMoodClick,
                        onArtistClick = handleArtistClick,
                        onPlayQueue = handlePlayQueue,
                        onRandomize = {
                            val pool =
                                homeState.categories.flatMap { it.songs } +
                                    homeState.recentlyPlayed +
                                    homeState.favorites
                            val distinct = pool.distinctBy { it.videoId }
                            if (distinct.isNotEmpty()) {
                                handleSongClick(distinct.random())
                            }
                        },
                        onRefresh = { homeViewModel.loadHomeScreenData(forceRefresh = true) }
                    )
                }
            }
        }

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
            visible = artistState != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(280)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6f)
        ) {
            artistState?.let { state ->
                MeldArtistDetailScreen(
                    state = state,
                    activeVideoId = playerState.currentSong?.videoId,
                    onBack = { artistState = null },
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

        androidx.compose.animation.AnimatedVisibility(
            visible = playerState.currentSong == null && playlistState == null,
            enter = androidx.compose.animation.scaleIn(animationSpec = tween(250)) +
                androidx.compose.animation.fadeIn(animationSpec = tween(250)),
            exit = androidx.compose.animation.scaleOut(animationSpec = tween(200)) +
                androidx.compose.animation.fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 165.dp, end = 20.dp)
        ) {
            var fabPressed by remember { mutableStateOf(false) }
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
                label = "fabScale"
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(fabScale)
                    .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        fabPressed = true
                        val pool = homeState.categories.flatMap { it.songs } + homeState.recentlyPlayed
                        val distinct = pool.distinctBy { it.videoId }
                        if (distinct.isNotEmpty()) {
                            handleSongClick(distinct.random())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_shuffle),
                    contentDescription = "Shuffle",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (showProfileSheet) {
            ProfileSheet(
                favoriteCount = homeState.favorites.size,
                recentlyPlayedCount = homeState.recentlyPlayed.size,
                onDismiss = { showProfileSheet = false }
            )
        }
    }
}

@Composable
private fun OnlineHeader(
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Account",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
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
private fun ProfileSheet(
    favoriteCount: Int,
    recentlyPlayedCount: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "You",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "VidMax is account-free — no YouTube login needed.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard(label = "Favorites", value = favoriteCount.toString(), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatCard(label = "Recently Played", value = recentlyPlayedCount.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MeldOnlineHomeContent(
    homeState: MusicHomeUiState,
    listState: LazyListState,
    selectedChip: String?,
    onChipSelect: (String?) -> Unit,
    onSongClick: (SongItem) -> Unit,
    onPlaylistClick: (PlaylistSpec) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onPlayQueue: (List<SongItem>) -> Unit,
    onRandomize: () -> Unit,
    onRefresh: () -> Unit
) {
    val quickPicks = remember(homeState.categories, homeState.quickPicks) {
        val forYou = homeState.categories.firstOrNull { it.title == "For You" }
            ?: homeState.categories.firstOrNull { it.songs.isNotEmpty() }
        (forYou?.songs.orEmpty() + homeState.quickPicks)
            .distinctBy { it.videoId }
            .take(8)
    }
    val speedDialSongs = remember(homeState.quickPicks, homeState.recentlyPlayed, homeState.favorites) {
        (homeState.quickPicks + homeState.favorites + homeState.recentlyPlayed)
            .distinctBy { it.videoId }
            .take(18)
    }
    val allCategories = homeState.categories.filter { it.title != "For You" }
    val visibleCategories =
        if (selectedChip == null) allCategories
        else allCategories.filter { it.title == selectedChip }
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
                    item(key = "chips") {
                        MeldChipsRow(
                            chips = allCategories.map { it.title },
                            selectedChip = selectedChip,
                            onChipSelect = onChipSelect,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // 🌸 Recently Played Section (Moved to top, styled as list per screenshot)
                    if (selectedChip == null && homeState.recentlyPlayed.isNotEmpty()) {
                        item(key = "recently_played") {
                            RecentlyPlayedSection(
                                songs = homeState.recentlyPlayed,
                                onSongClick = onSongClick,
                                onPlayAllClick = { onPlayQueue(homeState.recentlyPlayed) },
                                index = 0
                            )
                        }
                    }

                    // Quick Picks
                    if (selectedChip == null && quickPicks.isNotEmpty()) {
                        item(key = "quick_picks") {
                            QuickPicksSection(
                                songs = quickPicks,
                                onSongClick = onSongClick,
                                onPlayAllClick = { onPlayQueue(quickPicks) },
                                index = 1
                            )
                        }
                    }
                    
                    // Mood and Genres
                    if (selectedChip == null) {
                        item(key = "mood_and_genres") {
                            MoodAndGenresRow(
                                onMoodClick = onPlaylistClick,
                                index = 2
                            )
                        }
                    }

                    // Popular Artists
                    if (selectedChip == null && homeState.artists.isNotEmpty()) {
                        item(key = "popular_artists") {
                            PopularArtistsRow(
                                artists = homeState.artists,
                                onArtistClick = onArtistClick,
                                index = 3
                            )
                        }
                    }

                    // Speed Dial
                    if (selectedChip == null && speedDialSongs.isNotEmpty()) {
                        item(key = "speed_dial_title") {
                            NavigationTitle(
                                title = "Speed Dial",
                                onPlayAllClick = null
                            )
                        }
                        item(key = "speed_dial") {
                            MeldSpeedDialSection(
                                songs = speedDialSongs,
                                onSongClick = onSongClick,
                                onRandomizeClick = onRandomize,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "speed_dial_spacer") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Other Sections
                    if (selectedChip == null) {
                        if (homeState.dailyDiscover.isNotEmpty()) {
                            item(key = "daily_discover") {
                                MusicSectionRow(
                                    title = "Daily Discover",
                                    songs = homeState.dailyDiscover,
                                    onSongClick = onSongClick,
                                    onPlayAllClick = { onPlayQueue(homeState.dailyDiscover) },
                                    index = 3
                                )
                            }
                        }
                        if (homeState.keepListening.isNotEmpty()) {
                            item(key = "keep_listening") {
                                MusicSectionRow(
                                    title = "Keep Listening",
                                    songs = homeState.keepListening,
                                    onSongClick = onSongClick,
                                    onPlayAllClick = { onPlayQueue(homeState.keepListening) },
                                    index = 4
                                )
                            }
                        }
                        homeState.similarRecommendations.forEachIndexed { simIdx, rec ->
                            item(key = "similar_${rec.seed.videoId}") {
                                MusicSectionRow(
                                    title = "Similar to ${rec.seed.title}",
                                    songs = rec.items,
                                    onSongClick = onSongClick,
                                    onPlayAllClick = { onPlayQueue(rec.items) },
                                    index = 5 + simIdx
                                )
                            }
                        }
                        if (homeState.forgottenFavorites.isNotEmpty()) {
                            item(key = "forgotten_favorites") {
                                MusicSectionRow(
                                    title = "Forgotten Favorites",
                                    songs = homeState.forgottenFavorites,
                                    onSongClick = onSongClick,
                                    onPlayAllClick = { onPlayQueue(homeState.forgottenFavorites) },
                                    index = 6
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
                                    index = 7
                                )
                            }
                        }
                    }

                    itemsIndexed(visibleCategories, key = { _, category -> category.title }) { idx, category ->
                        MusicSectionRow(
                            title = category.title,
                            songs = category.songs,
                            onSongClick = onSongClick,
                            onPlayAllClick = { onPlayQueue(category.songs) },
                            index = idx + 8
                        )
                    }
                }
            }
        }
    }
}

// 🌸 Recently Played Section (Top Position, List Style)
@Composable
fun RecentlyPlayedSection(
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
        // Custom Header for Recently Played to match screenshot
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onPlayAllClick,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Play all", 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Vertical list of up to 4 recent items
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            songs.take(4).forEach { song ->
                MeldRecentListItem(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// 🌸 List Item for Recently Played
@Composable
fun MeldRecentListItem(
    song: SongItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "recentScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            videoId = song.videoId,
            fallbackUrl = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* Handle options menu */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 🌸 Standard Navigation Title
@Composable
fun NavigationTitle(
    title: String,
    onPlayAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onPlayAllClick != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy, 
                    stiffness = Spring.StiffnessMedium
                ),
                label = "playAllScale"
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
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
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun MeldSongCard(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "cardScale"
    )
    
    Column(
        modifier = modifier
            .width(136.dp)
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
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                            startY = 100f
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
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = song.title,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = song.artist,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            onPlayAllClick = onPlayAllClick
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp) 
        ) {
            items(songs, key = { it.videoId }) { song ->
                MeldSongCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

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
            onPlayAllClick = null
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            items(playlistSpecs) { spec ->
                MeldMoodCard(
                    spec = spec,
                    onClick = { onMoodClick(spec) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MeldMoodCard(
    spec: PlaylistSpec,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "moodScale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .width(170.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = spec.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PopularArtistsRow(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterAnimation(index)
    ) {
        NavigationTitle(
            title = "Popular Artists",
            onPlayAllClick = null
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { it.channelId }) { artist ->
                ArtistCard(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "artistScale"
    )
    Column(
        modifier = Modifier
            .width(110.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarUrl.isNotBlank()) {
                GlideImage(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = placeholder {
                        Box(modifier = Modifier.fillMaxSize().shimmer())
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = artist.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = formatSubscribers(artist.subscriberCount),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun formatSubscribers(count: Long): String {
    return when {
        count < 0 -> "Artist"
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
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
            onPlayAllClick = onPlayAllClick
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songs.distinctBy { it.videoId }) { song ->
                MeldSongCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MeldHomeShimmer() {
    Column(modifier = Modifier.fillMaxWidth()) {
        
        // 🌸 Shimmer for Recently Played (Now at the top)
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .size(width = 140.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column {
            repeat(3) { PlaylistSongRowSkeleton() }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 🌸 Shimmer for Quick Picks
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .size(width = 120.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            items(count = 8) {
                QuickPickSkeleton()
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        // 🌸 Shimmer for Mood & Genres
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .size(width = 140.dp, height = 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            items(count = 8) {
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmer()
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        CategorySkeletonRow()
    }
}

@Composable
fun QuickPickSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(136.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
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
                            .height(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Text(
                                text = state.spec.title,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.spec.subtitle} • ${state.songs.size} songs",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
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
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play all", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MeldArtistDetailScreen(
    state: ArtistDetailState,
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
                text = state.artist.name,
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
                            .height(200.dp)
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
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.artist.avatarUrl.isNotBlank()) {
                                    GlideImage(
                                        model = state.artist.avatarUrl,
                                        contentDescription = state.artist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = state.artist.name,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${formatSubscribers(state.artist.subscriberCount)} • ${state.songs.size} songs",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
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
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play all", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PlaylistSongRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onSearchAction: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        placeholder = { Text("Search songs, artists...", fontSize = 15.sp) },
        leadingIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(24.dp))
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )
            }
            itemsIndexed(searchState.searchResults) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    modifier = Modifier.enterAnimation(index)
                )
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
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
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
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp)
            .shadow(16.dp, RoundedCornerShape(50), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
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
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onNextClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
