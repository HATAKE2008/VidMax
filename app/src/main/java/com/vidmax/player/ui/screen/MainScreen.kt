package com.vidmax.player.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class NavItem(val label: String)

// 🔥 ৩টা আইটেম
private val navItems = listOf(NavItem("Videos"), NavItem("Folders"), NavItem("Music"))

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: LibraryViewModel, onVideoClick: (List<VideoItem>, Int) -> Unit) {
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf(0) }
  var isSettingsOpen by remember { mutableStateOf(false) }
  var isMusicPlayerOpen by remember { mutableStateOf(false) }

  val currentFolderPath by viewModel.currentFolderPath.collectAsState()
  val openedPlaylistTitle by viewModel.openedPlaylistTitle.collectAsState()

  // Music States
  val recentMusicTitle by viewModel.recentlyPlayedTitle.collectAsState()
  val recentMusicPath by viewModel.recentlyPlayedPath.collectAsState()
  val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()

  var albumArtBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

  // 🔥 স্ক্রল ডিটেক্ট করার জন্য স্টেট এবং কানেকশন
  val isScrollingDown = remember { mutableStateOf(false) }

  val nestedScrollConnection = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < -15f) { // নিচের দিকে স্ক্রল
          isScrollingDown.value = true
        } else if (available.y > 15f) { // ওপরের দিকে স্ক্রল
          isScrollingDown.value = false
        }
        return Offset.Zero // আমরা স্ক্রল ব্লক করছি না, শুধু ইভেন্ট শুনছি
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

  BackHandler(
      enabled =
          isMusicPlayerOpen ||
              isSettingsOpen ||
              openedPlaylistTitle.isNotEmpty() ||
              selectedTab != 0 ||
              currentFolderPath.isNotEmpty()) {
        if (isMusicPlayerOpen) {
          isMusicPlayerOpen = false
        } else if (isSettingsOpen) {
          isSettingsOpen = false
        } else if (openedPlaylistTitle.isNotEmpty()) {
          viewModel.closePlaylist()
        } else if (currentFolderPath.isNotEmpty()) {
          viewModel.closeFolder()
        } else if (selectedTab != 0) {
          selectedTab = 0
        }
      }

  val handleVideoClick = { videos: List<VideoItem>, index: Int ->
    viewModel.pauseAudio()
    onVideoClick(videos, index)
  }

  val showMusicRecentBar =
      (selectedTab == 2 || openedPlaylistTitle.isNotEmpty()) && recentMusicTitle.isNotEmpty()

  // 🔥 মেইন বক্সে NestedScrollConnection অ্যাড করা হলো
  Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {

    // --- MAIN BACKGROUND CONTENT ---
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(MaterialTheme.colorScheme.background)
                  .padding(paddingValues)) {
            if (openedPlaylistTitle.isNotEmpty()) {
              PlaylistScreen(
                  viewModel = viewModel,
                  onBack = { viewModel.closePlaylist() },
                  onAudioClick = { audioList, index ->
                    viewModel.playAudioFromList(audioList, index)
                  })
            } else {
              when (selectedTab) {
                0 ->
                    HomeScreen(
                        viewModel = viewModel,
                        onVideoClick = handleVideoClick,
                        onSettingsClick = { isSettingsOpen = true })
                1 -> FoldersScreen(viewModel = viewModel, onVideoClick = handleVideoClick)
                2 ->
                    MusicScreen(
                        viewModel = viewModel,
                        onSettingsClick = { isSettingsOpen = true },
                        onAudioClick = { audioList, index ->
                          viewModel.playAudioFromList(audioList, index)
                        },
                        onOpenFavorites = { viewModel.openFavorites() },
                        onOpenMyMix = { viewModel.openMyMix() })
              }
            }
          }
    }

    // --- 🔥 FLOATING NAVIGATION BARS 🔥 ---
    Column(modifier = Modifier.align(Alignment.BottomCenter)) {

      // --- MUSIC RECENT BAR (Theme Adaptive + Scroll Auto Hide) ---
      AnimatedVisibility(
          visible = showMusicRecentBar && !isScrollingDown.value, // 🔥 স্ক্রল ডাউনে হাইড হবে
          enter = slideInVertically(initialOffsetY = { fullHeight: Int -> fullHeight }),
          exit = slideOutVertically(targetOffsetY = { fullHeight: Int -> fullHeight })) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        // 🔥 থিমের সাথে মানানসই কালার করার জন্য surface ব্যবহার করা হলো
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(24.dp))
                        .clickable { isMusicPlayerOpen = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp)) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.fillMaxWidth()) {
                        // ১. অ্যালবাম আর্ট (থাম্বনেইল)
                        Box(
                            modifier =
                                Modifier.size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) {
                              if (albumArtBitmap != null) {
                                Image(
                                    bitmap = albumArtBitmap!!,
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize())
                              } else {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp))
                              }
                            }

                        Spacer(modifier = Modifier.width(14.dp))

                        // ২. গান এবং আর্টিস্টের নাম
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                              text = recentMusicTitle,
                              color = MaterialTheme.colorScheme.onSurface,
                              fontSize = 14.sp,
                              fontWeight = FontWeight.Bold,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                          Text(
                              text = "Vibe Music",
                              color = MaterialTheme.colorScheme.onSurfaceVariant,
                              fontSize = 12.sp,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis)
                        }

                        // ৩. মিডিয়া কন্ট্রোল বাটন গ্রুপ
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                              // Previous Button
                              IconButton(
                                  onClick = { viewModel.previousAudio() },
                                  modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_skip_previous),
                                        contentDescription = "Previous",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp))
                                  }

                              // Play/Pause Button (Theme Primary Circle)
                              Box(
                                  modifier =
                                      Modifier.size(44.dp)
                                          .clip(androidx.compose.foundation.shape.CircleShape)
                                          .background(MaterialTheme.colorScheme.primary)
                                          .clickable { viewModel.toggleAudio() },
                                  contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id =
                                                    if (isAudioPlaying) R.drawable.ic_pause
                                                    else R.drawable.ic_play),
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp))
                                  }

                              // Next Button
                              IconButton(
                                  onClick = { viewModel.nextAudio() },
                                  modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_skip_next),
                                        contentDescription = "Next",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp))
                                  }
                            }
                      }
                }
          }

      // --- CUSTOM SLEEK & SLIM NAVIGATION BAR ---
      Row(
          modifier =
              Modifier.padding(horizontal = 16.dp)
                  .padding(bottom = 16.dp)
                  .fillMaxWidth()
                  .height(64.dp)
                  .shadow(12.dp, RoundedCornerShape(50.dp))
                  .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50.dp))
                  .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(50.dp)),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically) {
            navItems.forEachIndexed { index, item ->
              val isSelected = selectedTab == index

              val contentColor by
                  animateColorAsState(
                      targetValue =
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                      animationSpec = tween(250),
                      label = "colorAnim")

              val iconScale by
                  animateFloatAsState(
                      targetValue = if (isSelected) 1.15f else 1.0f,
                      animationSpec =
                          spring(
                              dampingRatio = Spring.DampingRatioMediumBouncy,
                              stiffness = Spring.StiffnessLow),
                      label = "scaleAnim")

              val arcProgress by
                  animateFloatAsState(
                      targetValue = if (isSelected) 1f else 0f,
                      animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                      label = "arcAnim")

              Column(
                  modifier =
                      Modifier.weight(1f)
                          .fillMaxHeight()
                          .clickable(
                              interactionSource = remember { MutableInteractionSource() },
                              indication = null,
                              onClick = {
                                selectedTab = index
                                if (index != 1) viewModel.closeFolder()
                                viewModel.closePlaylist()
                              }),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center) {
                    val iconRes =
                        when (item.label) {
                          "Videos" -> R.drawable.ic_video_library
                          "Folders" -> R.drawable.ic_folder
                          "Music" -> R.drawable.ic_music_note
                          else -> R.drawable.ic_video_library
                        }

                    Box(
                        modifier =
                            Modifier.wrapContentSize()
                                .drawBehind {
                                  if (arcProgress > 0f) {
                                    val strokeWidth = 2.5.dp.toPx()
                                    val diameter = maxOf(size.width, size.height) + 6.dp.toPx()
                                    val topLeftX = (size.width - diameter) / 2f + 5.dp.toPx()
                                    val topLeftY = (size.height - diameter) / 2f

                                    drawArc(
                                        color = contentColor,
                                        startAngle = -50f,
                                        sweepAngle = 100f * arcProgress,
                                        useCenter = false,
                                        topLeft = Offset(topLeftX, topLeftY),
                                        size = Size(diameter, diameter),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                                  }
                                }
                                .padding(4.dp),
                        contentAlignment = Alignment.Center) {
                          Icon(
                              painter = painterResource(id = iconRes),
                              contentDescription = item.label,
                              tint = contentColor,
                              modifier = Modifier.size(24.dp).scale(iconScale))
                        }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                  }
            }
          }
    }

    // SETTINGS OVERLAY
    AnimatedVisibility(
        visible = isSettingsOpen,
        enter =
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(350, easing = FastOutSlowInEasing)),
        exit =
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(350, easing = FastOutSlowInEasing)),
        modifier = Modifier.fillMaxSize().zIndex(5f)) {
          Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}) {
            SettingsScreen(viewModel = viewModel, onBack = { isSettingsOpen = false })
          }
        }

    // FULL-SCREEN OVERLAY FOR MUSIC PLAYER
    AnimatedVisibility(
        visible = isMusicPlayerOpen,
        enter =
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)),
        exit =
            slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)),
        modifier = Modifier.fillMaxSize().zIndex(10f)) {
          Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}) {
            MusicPlayerScreen(viewModel = viewModel, onBack = { isMusicPlayerOpen = false })
          }
        }
  }
}
