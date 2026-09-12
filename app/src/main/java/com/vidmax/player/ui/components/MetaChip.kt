package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Small tonal metadata chip (REX MediaMetadataChip language): rounded pill
 * with label text, used for counts, sizes, durations and type badges on
 * playlist / network cards.
 */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
        color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        maxLines = 1,
    )
}
