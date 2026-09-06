package com.vidmax.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * REX-style floating bottom action bar for selection mode.
 *
 * Adapted from REX Player's BrowserBottomBar
 * (xyz.mpv.rex.ui.browser.components.FloatingBottomBar): icon-only actions in
 * a floating pill that animates in/out with selection mode. Unlike REX (which
 * offsets for its own nav + mini player), this is a pure overlay — VidMax's
 * bottom navigation is untouched and the bar only floats above it while
 * selection mode is active.
 *
 * Actions: Copy, Move, Rename (single selection only), Add to Playlist,
 * Delete. Rename is hidden for multi-selection, matching REX behavior.
 */
@Composable
fun SelectionBottomBar(
    visible: Boolean,
    isSingleSelection: Boolean,
    onCopyClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRenameClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current

  AnimatedVisibility(
      visible = visible,
      modifier = modifier,
      enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
          slideInVertically(
              animationSpec = spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMediumLow),
              initialOffsetY = { it / 2 }) +
          scaleIn(
              animationSpec = spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMediumLow),
              initialScale = 0.85f),
      exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
          slideOutVertically(
              animationSpec = spring(stiffness = Spring.StiffnessMedium),
              targetOffsetY = { it / 2 }) +
          scaleOut(
              animationSpec = spring(stiffness = Spring.StiffnessMedium),
              targetScale = 0.9f),
  ) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp) {
      Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically) {
        val buttonShape = RoundedCornerShape(14.dp)
        val buttonSize = 44.dp
        val tonalColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer)

        FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onCopyClick()
            },
            modifier = Modifier.size(buttonSize),
            shape = buttonShape,
            colors = tonalColors) {
          Icon(
              Icons.Filled.ContentCopy,
              contentDescription = "Copy",
              modifier = Modifier.size(20.dp))
        }

        FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onMoveClick()
            },
            modifier = Modifier.size(buttonSize),
            shape = buttonShape,
            colors = tonalColors) {
          Icon(
              Icons.AutoMirrored.Filled.DriveFileMove,
              contentDescription = "Move",
              modifier = Modifier.size(20.dp))
        }

        if (isSingleSelection) {
          FilledTonalIconButton(
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRenameClick()
              },
              modifier = Modifier.size(buttonSize),
              shape = buttonShape,
              colors = tonalColors) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = "Rename",
                modifier = Modifier.size(20.dp))
          }
        }

        FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              onAddToPlaylistClick()
            },
            modifier = Modifier.size(buttonSize),
            shape = buttonShape,
            colors = tonalColors) {
          Icon(
              Icons.AutoMirrored.Filled.PlaylistAdd,
              contentDescription = "Add to Playlist",
              modifier = Modifier.size(20.dp))
        }

        FilledTonalIconButton(
            onClick = {
              haptic.performHapticFeedback(HapticFeedbackType.LongPress)
              onDeleteClick()
            },
            modifier = Modifier.size(buttonSize),
            shape = buttonShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
          Icon(
              Icons.Filled.Delete,
              contentDescription = "Delete",
              modifier = Modifier.size(20.dp))
        }
      }
    }
  }
}
