package com.vidmax.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.SongItem

/**
 * Meld-style horizontal chips row ("For You", categories...). Selecting a chip
 * filters the home content; tapping the active chip again clears it.
 */
@Composable
fun MeldChipsRow(
    chips: List<String>,
    selectedChip: String?,
    onChipSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedChip == null,
                onClick = { onChipSelect(null) },
                label = {
                    Text(
                        "For You",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                shape = RoundedCornerShape(18.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedChip == null,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        items(chips, key = { it }) { chip ->
            FilterChip(
                selected = selectedChip == chip,
                onClick = { onChipSelect(if (selectedChip == chip) null else chip) },
                label = {
                    Text(
                        chip,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                shape = RoundedCornerShape(18.dp),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedChip == chip,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/**
 * Meld-style Speed Dial — a paged grid of square quick-access tiles with a
 * "shuffle" (randomize) slot in the last cell of the first page.
 */
@Composable
fun MeldSpeedDialSection(
    songs: List<SongItem>,
    onSongClick: (SongItem) -> Unit,
    onRandomizeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    val rows = 2
    val itemsPerPage = columns * rows
    // Page 0's last cell is taken by the "Surprise Me" randomize tile, so it can
    // only hold itemsPerPage - 1 songs; every page after that holds a full page.
    val firstPageCapacity = itemsPerPage - 1

    val pageCount = remember(songs.size) {
        if (songs.size <= firstPageCapacity) 1
        else 1 + (songs.size - firstPageCapacity + itemsPerPage - 1) / itemsPerPage
    }
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontalPadding = 16.dp
            val pageSpacing = 16.dp
            val itemWidth = (maxWidth - horizontalPadding * 2 - pageSpacing) / columns
            val pagerHeight = itemWidth * rows

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = pageSpacing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pagerHeight),
            ) { page ->
                val pageStartIndex = when (page) {
                    0 -> 0
                    else -> firstPageCapacity + (page - 1) * itemsPerPage
                }
                val pageSize = if (page == 0) firstPageCapacity else itemsPerPage
                val pageItems = songs.drop(pageStartIndex).take(pageSize)

                Column(modifier = Modifier.fillMaxSize()) {
                    for (row in 0 until rows) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until columns) {
                                val itemIndex = row * columns + col
                                val isRandomizeSlot = page == 0 && itemIndex == itemsPerPage - 1

                                if (isRandomizeSlot) {
                                    RandomizeTile(onClick = onRandomizeClick, modifier = Modifier.width(itemWidth).height(itemWidth))
                                } else if (itemIndex < pageItems.size) {
                                    val song = pageItems[itemIndex]
                                    SpeedDialTile(
                                        song = song,
                                        onClick = { onSongClick(song) },
                                        modifier = Modifier.width(itemWidth).height(itemWidth),
                                    )
                                } else {
                                    Box(modifier = Modifier.width(itemWidth).height(itemWidth))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pagerState.pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val active = pagerState.currentPage == iteration
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            .size(if (active) 8.dp else 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialTile(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "speedDialScale",
    )

    Box(
        modifier = modifier
            .padding(5.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        ArtworkImage(
            videoId = song.videoId,
            fallbackUrl = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loadingPlaceholder = {
                Box(modifier = Modifier.fillMaxSize().shimmer())
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
                ),
        )
        Text(
            text = song.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )
    }
}

@Composable
private fun RandomizeTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "randomizeScale",
    )

    Box(
        modifier = modifier
            .padding(5.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = "Shuffle",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = "Surprise Me",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
