package com.vidmax.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.viewmodel.LibraryViewModel

/**
 * mpvRex-style "Add to playlist" chooser: pick an existing playlist or type a
 * new name to create one and add the selected videos in a single action.
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
      title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          if (playlists.isNotEmpty()) {
            LazyColumn(modifier = Modifier.height(180.dp)) {
              items(items = playlists, key = { it.playlist.id }) { entry ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable { selectedId = entry.playlist.id },
                    verticalAlignment = Alignment.CenterVertically) {
                      RadioButton(
                          selected = selectedId == entry.playlist.id,
                          onClick = { selectedId = entry.playlist.id })
                      Column {
                        Text(text = entry.playlist.name, fontSize = 15.sp)
                        Text(
                            text = "${entry.itemCount} videos",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
              value = newName,
              onValueChange = {
                newName = it
                if (it.isNotBlank()) selectedId = -1
              },
              label = { Text("Or create new playlist") },
              leadingIcon = {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth())
        }
      },
      confirmButton = {
        Button(onClick = {
          when {
            newName.isNotBlank() -> viewModel.createAndAddToPlaylist(newName.trim(), videos)
            selectedId != -1 -> viewModel.addVideosToPlaylist(selectedId, videos)
            else -> return@Button
          }
          onDismiss()
        }) {
          Icon(
              imageVector = Icons.Filled.PlaylistAdd,
              contentDescription = null,
              modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(6.dp))
          Text("Add")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
