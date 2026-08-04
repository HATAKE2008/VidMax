package com.vidmax.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.SongItem

/**
 * Polished horizontal song card — square 14dp-rounded artwork with a shimmer
 * placeholder that fades into the real art, press scale 0.96 and a two-line
 * title+artist layout.
 */
@Composable
fun SongCard(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 140.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "songCardScale",
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
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
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Skeleton placeholder card used while chart/category rows load. */
@Composable
fun SongCardSkeleton(modifier: Modifier = Modifier, cardWidth: Dp = 140.dp) {
    Column(modifier = modifier.width(cardWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .shimmer(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(4.dp)).shimmer())
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(4.dp)).shimmer())
    }
}

/** Shimmer skeleton version of [CategoryRow]'s horizontal card list. */
@Composable
fun CategorySkeletonRow(cardCount: Int = 6) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .size(width = 120.dp, height = 18.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer(),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(count = cardCount) {
                SongCardSkeleton()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
