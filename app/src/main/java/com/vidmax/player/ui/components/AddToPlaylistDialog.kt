package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * mpvRex-style "Add to playlist" chooser: pick an existing playlist or type a
 * new name to create one and add the selected videos in a single action.
 * Presentation follows the shared modern dialog system; all repository
 * calls are unchanged.
 */
@Composable
fun AddToPlaylistDialog(
  viewModel: LibraryViewModel,
  videos: List<VideoItem>,
  onDismiss: () -> Unit,
) {
  val playlists by viewModel.videoPlaylists.collectAsState()
  var selectedId by remember { mutableIntStateOf(-1) }
  var newName by remember { mutableStateOf("") }

  AlertDialog(
      onDismissRequest = onDismiss,
      shape = RoundedCornerShape(28.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      icon = { DialogHeaderBadge(icon = Icons.Rounded.PlaylistAdd) },
      title = {
        Text(stringResource(R.string.comp_playlist_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
      },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (playlists.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(vertical = 4.dp)) {
              items(items = playlists, key = { it.playlist.id }) { entry ->
                val selected: Boolean = selectedId == entry.playlist.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = entry.playlist.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Box(
                          modifier = Modifier
                              .size(38.dp)
                              .clip(CircleShape)
                              .background(
                                  if (selected) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.surfaceVariant),
                          contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistAdd,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                          }
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.playlist.name,
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = stringResource(R.string.comp_playlist_count, entry.itemCount),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                      RadioButton(
                          selected = selected,
                          onClick = { selectedId = entry.playlist.id })
                    }
              }
            }
          }
          OutlinedTextField(
              value = newName,
              onValueChange = {
                newName = it
                if (it.isNotBlank()) selectedId = -1
              },
              label = { Text(stringResource(R.string.comp_playlist_new_hint)) },
              leadingIcon = {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
              },
              trailingIcon = {
                if (newName.isNotEmpty()) {
                  IconButton(onClick = { newName = "" }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Clear name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              },
              singleLine = true,
              shape = RoundedCornerShape(16.dp),
              colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  focusedLabelColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        DialogConfirmButton(
            label = stringResource(R.string.comp_playlist_add),
            enabled = newName.isNotBlank() || selectedId != -1,
            onClick = {
              when {
                newName.isNotBlank() -> viewModel.createAndAddToPlaylist(newName.trim(), videos)
                selectedId != -1 -> viewModel.addVideosToPlaylist(selectedId, videos)
                else -> return@DialogConfirmButton
              }
              onDismiss()
            })
      },
      dismissButton = { DialogCancelButton(label = stringResource(R.string.comp_playlist_cancel), onClick = onDismiss) })
}
