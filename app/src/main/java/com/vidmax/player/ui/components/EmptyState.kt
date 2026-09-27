package com.vidmax.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R

/**
 * Reusable empty state component with illustration, title, subtitle, and optional action.
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    iconSize: Dp = 64.dp,
    titleSize: Dp = 18.dp,
    subtitleSize: Dp = 14.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        subtitle?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = subtitleSize,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        (actionLabel != null && onAction != null).let {
            if (it) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction!!) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Empty state with a specific icon for "no videos/media found".
 */
@Composable
fun EmptyMediaState(
    modifier: Modifier = Modifier,
    title: String = "No media found",
    subtitle: String? = "Add some media to get started",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    EmptyState(
        modifier = modifier,
        icon = androidx.compose.material.icons.filled.VideoLibrary,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * Empty state for folders.
 */
@Composable
fun EmptyFolderState(
    modifier: Modifier = Modifier,
    title: String = "No folders found",
    subtitle: String? = "Create a folder to organize your media",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    EmptyState(
        modifier = modifier,
        icon = androidx.compose.material.icons.filled.FolderOpen,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * Empty state for playlists.
 */
@Composable
fun EmptyPlaylistState(
    modifier: Modifier = Modifier,
    title: String = "No playlists yet",
    subtitle: String? = "Create your first playlist to organize your media",
    actionLabel: String = "Create playlist",
    onAction: (() -> Unit)? = null,
) {
    EmptyState(
        modifier = modifier,
        icon = androidx.compose.material.icons.filled.PlaylistAdd,
        title = title,
        subtitle = subtitle,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * Empty state for search results.
 */
@Composable
fun EmptySearchState(
    modifier: Modifier = Modifier,
    query: String,
    onClear: (() -> Unit)? = null,
) {
    EmptyState(
        modifier = modifier,
        icon = androidx.compose.material.icons.filled.SearchOff,
        title = stringResource(R.string.search_no_results_title),
        subtitle = stringResource(R.string.search_no_results_hint, query),
        actionLabel = stringResource(R.string.search_clear_confirm),
        onAction = onClear,
    )
}