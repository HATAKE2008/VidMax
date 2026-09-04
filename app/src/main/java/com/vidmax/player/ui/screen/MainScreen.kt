package com.vidmax.player.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.UpdateResultDialog
import com.vidmax.player.utils.UpdateChecker
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.MusicHomeViewModel
import com.vidmax.player.viewmodel.MusicPlayerViewModel
import com.vidmax.player.viewmodel.MusicSearchViewModel
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NavItem(val label: String)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: LibraryViewModel, onVideoClick: (List<VideoItem>, Int) -> Unit) {
    val context = LocalContext.current

    // 🌐 Online Music ViewModels Initialize
    val homeViewModel: MusicHomeViewModel = hiltViewModel()
    val searchViewModel: MusicSearchViewModel = hiltViewModel()
    val playerViewModel: MusicPlayerViewModel = hiltViewModel()

    // 💾 শেয়ার্ড প্রেফারেন্সেস এবং ট্যাব অর্ডার (Online বাদ দিয়ে)
    val sharedPrefs = remember { context.getSharedPreferences("NavPrefs", Context.MODE_PRIVATE) }
    var navItemsState by remember {
        val defaultTabs = listOf("Videos", "Music", "Network")
        val savedOrderStr = sharedPrefs.getString("nav_order", "") ?: ""

        val initialList = if (savedOrderStr.isNotBlank()) {
            val savedTabs = savedOrderStr.split(",").filter { it != "Online" && it != "Folders" }
            val missingTabs = defaultTabs.filter { !savedTabs.contains(it) }
            (savedTabs + missingTabs).map { NavItem(it) }
        } else {
            defaultTabs.map { NavItem(it) }
        }
        mutableStateOf(initialList)
    }

    // Selected screen name tracking
    var selectedScreen by rememberSaveable { mutableStateOf(navItemsState.firstOrNull()?.label ?: "Videos") }

    val localMode by viewModel.localMode.collectAsState()
    val musicPlayerEnabled by viewModel.musicPlayerEnabled.collectAsState()

    val visibleNavItems = remember(navItemsState, localMode, musicPlayerEnabled) {
        navItemsState.filter { item ->
            (item.label != "Music" || musicPlayerEnabled) && (item.label != "Network" || !localMode)
        }
    }
    LaunchedEffect(visibleNavItems, localMode) {
        val labels = visibleNavItems.map { it.label }
        if ((selectedScreen == "Online" && localMode) ||
            (selectedScreen != "Online" && selectedScreen !in labels)) {
            selectedScreen = labels.firstOrNull() ?: "Videos"
        }
    }

    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isMusicPlayerOpen by rememberSaveable { mutableStateOf(false) }

    // 🔄 Automatic update check on launch (GitHub releases), only when enabled
    var updateResult by remember { mutableStateOf<UpdateChecker.CheckResult?>(null) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("update_notifications", true)) {
            val result = UpdateChecker.checkForUpdate()
            if (result is UpdateChecker.CheckResult.Success &&
                UpdateChecker.isNewerVersion(result.info)
            ) {
                updateResult = result
            }
        }
    }

    val currentFolderPath by viewModel.currentFolderPath.collectAsState()
    val openedPlaylistTitle by viewModel.openedPlaylistTitle.collectAsState()

    val recentMusicTitle by viewModel.recentlyPlayedTitle.collectAsState()
    val recentMusicPath by viewModel.recentlyPlayedPath.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()

    val currentPosition by viewModel.audioPosition.collectAsState()
    val duration by viewModel.audioDuration.collectAsState()
    val currentArtist by viewModel.currentAudioArtist.collectAsState()
    val favoritePaths by viewModel.favoriteAudioPaths.collectAsState()
    val isFavorite = favoritePaths.contains(recentMusicPath)

    val audioProgress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var albumArtBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val isScrollingDown = remember { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    isScrollingDown.value = true
                } else if (available.y > 15f) {
                    isScrollingDown.value = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(recentMusicPath) {
        if (recentMusicPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    val uri: Uri =
                        if (recentMusicPath.startsWith("/")) {
                            Uri.fromFile(File(recentMusicPath))
                        } else {
                            Uri.parse(recentMusicPath)
                        }
                    mmr.setDataSource(context, uri)

                    val pic = mmr.embeddedPicture
                    if (pic != null) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.size)
                        albumArtBitmap = bmp.asImageBitmap()
                    } else {
                        albumArtBitmap = null
                    }
                    mmr.release()
                } catch (e: Exception) {
                    albumArtBitmap = null
                }
            }
        } else {
            albumArtBitmap = null
        }
    }

    // 🔄 ব্যাক হ্যান্ডলার
    BackHandler(
        enabled = isMusicPlayerOpen || isSettingsOpen || openedPlaylistTitle.isNotEmpty() || selectedScreen != visibleNavItems.firstOrNull()?.label || currentFolderPath.isNotEmpty()
    ) {
        if (isMusicPlayerOpen) {
            isMusicPlayerOpen = false
        } else if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (openedPlaylistTitle.isNotEmpty()) {
            viewModel.closePlaylist()
        } else if (currentFolderPath.isNotEmpty()) {
            viewModel.closeFolder()
        } else if (selectedScreen != visibleNavItems.firstOrNull()?.label) {
            selectedScreen = visibleNavItems.firstOrNull()?.label ?: "Videos"
        }
    }

    val handleVideoClick = { videos: List<VideoItem>, index: Int ->
        playerViewModel.clearPlayer()
        viewModel.pauseAudio()
        onVideoClick(videos, index)
    }

    val showMusicRecentBar = (selectedScreen == "Music" || openedPlaylistTitle.isNotEmpty()) && recentMusicTitle.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                if (openedPlaylistTitle.isNotEmpty()) {
                    PlaylistScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.closePlaylist() },
                        onAudioClick = { audioList, index ->
                            playerViewModel.clearPlayer()
                            viewModel.playAudioFromList(audioList, index)
                        }
                    )
                } else {
                    // 🌟 Flutter-style Fade + Scale Page Transition
                    AnimatedContent(
                        targetState = selectedScreen,
                        transitionSpec = {
                            (fadeIn(tween(250, easing = FastOutSlowInEasing)) +
                             scaleIn(initialScale = 0.96f, animationSpec = tween(250, easing = FastOutSlowInEasing)))
                             .togetherWith(
                                 fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                                 scaleOut(targetScale = 0.96f, animationSpec = tween(250, easing = FastOutSlowInEasing))
                             )
                        },
                        label = "pageTransition"
                    ) { screen ->
                        when (screen) {
                            "Videos" -> HomeScreen(
                                viewModel = viewModel,
                                onVideoClick = handleVideoClick,
                                onSettingsClick = { isSettingsOpen = true }
                            )
                            "Music" -> MusicScreen(
                                viewModel = viewModel,
                                onSettingsClick = { isSettingsOpen = true },
                                onAudioClick = { audioList, index ->
                                    playerViewModel.clearPlayer()
                                    viewModel.playAudioFromList(audioList, index)
                                },
                                onOpenFavorites = { viewModel.openFavorites() },
                                onOpenMyMix = { viewModel.openMyMix() }
                            )
                            "Online" -> {
                                OnlineMusicScreen(
                                    homeViewModel = homeViewModel,
                                    searchViewModel = searchViewModel,
                                    playerViewModel = playerViewModel,
                                    onOpenFullPlayer = { isMusicPlayerOpen = true },
                                    onSettingsClick = { isSettingsOpen = true },
                                    libraryViewModel = viewModel
                                )
                            }
                            "Network" -> {
                                NetworkScreen(libraryViewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {

            // MINI PLAYER BAR (Local Music)
            AnimatedVisibility(
                visible = showMusicRecentBar && !isScrollingDown.value,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
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
                        .clickable { isMusicPlayerOpen = true }
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
                                ) {
                                    playerViewModel.clearPlayer()
                                    viewModel.toggleAudio()
                                },
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
                                progress = { audioProgress },
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
                                if (albumArtBitmap != null) {
                                    Image(
                                        bitmap = albumArtBitmap!!,
                                        contentDescription = "Album Art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_music_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isAudioPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                        ),
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recentMusicTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentArtist.ifEmpty { "Vibe Music" },
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
                                    .clickable {
                                        playerViewModel.clearPlayer()
                                        viewModel.nextAudio()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_skip_next),
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
                                    .clickable { viewModel.toggleFavorite(recentMusicPath) },
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

            // BOTTOM NAVIGATION BAR (Detached Online/Search Button Style)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Navigation Pill (SOLID - never transparent)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(32.dp),
                            ambientColor = Color.Black.copy(alpha = 0.05f),
                            spotColor = Color.Black.copy(alpha = 0.10f)
                        )
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.2.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    val averageTabWidthPx = with(LocalDensity.current) { (maxWidth / visibleNavItems.size).toPx() }
                    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }

                    // Merges a reordered visible list back into the full tab order,
                    // keeping hidden tabs (Music/Network when disabled) in place.
                    fun persistVisibleOrder(newVisible: List<NavItem>) {
                        val hidden = navItemsState.filter { h -> newVisible.none { it.label == h.label } }
                        navItemsState = newVisible + hidden
                        sharedPrefs.edit().putString("nav_order", navItemsState.joinToString(",") { it.label }).apply()
                    }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleNavItems.forEachIndexed { index, item ->
                            key(item.label) {
                                var offsetX by remember { mutableStateOf(0f) }
                                val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "dragX")

                                val currentIndex = visibleNavItems.indexOf(item)
                                val isSelected = selectedScreen == item.label

                                val tabWeight by animateFloatAsState(
                                    targetValue = if (isSelected) 2.4f else 1.0f,
                                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                                    label = "tabWeight"
                                )

                                val contentColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    label = "colorAnim"
                                )

                                val tabBgAlpha by animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0f,
                                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                                    label = "tabBgAlpha"
                                )

                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.05f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "scaleAnim"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(tabWeight)
                                        .fillMaxHeight()
                                        .zIndex(if (draggedItemIndex == currentIndex) 1f else 0f)
                                        .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                                        .pointerInput(item.label) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { draggedItemIndex = visibleNavItems.indexOf(item) },
                                                onDragEnd = {
                                                    draggedItemIndex = null
                                                    offsetX = 0f
                                                },
                                                onDragCancel = {
                                                    draggedItemIndex = null
                                                    offsetX = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    offsetX += dragAmount.x
                                                    val currentActiveIndex = visibleNavItems.indexOf(item)
                                                    val offsetThreshold = averageTabWidthPx / 2

                                                    if (offsetX > offsetThreshold && currentActiveIndex < visibleNavItems.lastIndex) {
                                                        val newList = visibleNavItems.toMutableList()
                                                        val temp = newList[currentActiveIndex]
                                                        newList[currentActiveIndex] = newList[currentActiveIndex + 1]
                                                        newList[currentActiveIndex + 1] = temp

                                                        persistVisibleOrder(newList)

                                                        offsetX -= averageTabWidthPx
                                                        draggedItemIndex = currentActiveIndex + 1
                                                    }
                                                    else if (offsetX < -offsetThreshold && currentActiveIndex > 0) {
                                                        val newList = visibleNavItems.toMutableList()
                                                        val temp = newList[currentActiveIndex]
                                                        newList[currentActiveIndex] = newList[currentActiveIndex - 1]
                                                        newList[currentActiveIndex - 1] = temp

                                                        persistVisibleOrder(newList)

                                                        offsetX += averageTabWidthPx
                                                        draggedItemIndex = currentActiveIndex - 1
                                                    }
                                                }
                                            )
                                        }
                                        .clip(RoundedCornerShape(26.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = {
                                                selectedScreen = item.label
                                                viewModel.closeFolder()
                                                viewModel.closePlaylist()
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Active pill background (theme primary tint overlay)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(
                                                if (tabBgAlpha > 0.01f) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * tabBgAlpha)
                                                else Color.Transparent
                                            )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val iconRes = when (item.label) {
                                            "Videos" -> R.drawable.ic_video_library
                                            "Music" -> R.drawable.ic_music_note
                                            "Network" -> R.drawable.ic_network
                                            else -> R.drawable.ic_video_library
                                        }
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = item.label,
                                            tint = contentColor,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .scale(iconScale)
                                        )

                                        AnimatedVisibility(
                                            visible = isSelected,
                                            enter = expandHorizontally(
                                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                                expandFrom = Alignment.Start
                                            ) + fadeIn(
                                                animationSpec = tween(180, delayMillis = 100, easing = LinearEasing)
                                            ),
                                            exit = shrinkHorizontally(
                                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                                shrinkTowards = Alignment.Start
                                            ) + fadeOut(
                                                animationSpec = tween(120, easing = LinearEasing)
                                            )
                                        ) {
                                            Text(
                                                text = item.label,
                                                fontSize = 15.sp,
                                                color = contentColor,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Separate Online/Search Circular Button (hidden in Local Mode)
                val isOnlineSelected = selectedScreen == "Online"
                if (!localMode) {

                // 🌟 Same inner overlay/box animation as selected tab (Folders screenshot এর মতো)
                val searchBgAlpha by animateFloatAsState(
                    targetValue = if (isOnlineSelected) 1f else 0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "searchBgAlpha"
                )
                val searchIconColor by animateColorAsState(
                    targetValue = if (isOnlineSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "searchIconColor"
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.05f),
                            spotColor = Color.Black.copy(alpha = 0.10f)
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.2.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                selectedScreen = "Online"
                                viewModel.closeFolder()
                                viewModel.closePlaylist()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Active overlay box (primary tint) - solid surface এর উপরে, তাই কখনো transparent না
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                if (searchBgAlpha > 0.01f) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * searchBgAlpha)
                                else Color.Transparent
                            )
                    )

                    val iconScale by animateFloatAsState(
                        targetValue = if (isOnlineSelected) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "searchScaleAnim"
                    )

                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Online",
                        tint = searchIconColor,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(iconScale)
                    )
                }
                }
            }
        }

        // SETTINGS OVERLAY
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(350, easing = FastOutSlowInEasing)),
            exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }, animationSpec = tween(350, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize().zIndex(5f)
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { isSettingsOpen = false }
                )
            }
        }

        // MUSIC PLAYER OVERLAY
        AnimatedVisibility(
            visible = isMusicPlayerOpen,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }, animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }, animationSpec = tween(350, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}) {
                MusicPlayerScreen(
                    viewModel = viewModel,
                    musicPlayerViewModel = playerViewModel,
                    onBack = { isMusicPlayerOpen = false }
                )
            }
        }

        // 🔄 UPDATE AVAILABLE DIALOG
        updateResult?.let { result ->
            UpdateResultDialog(
                result = result,
                onDismiss = { updateResult = null },
                onOpenUrl = { url ->
                    updateResult = null
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            )
        }
    }
}
