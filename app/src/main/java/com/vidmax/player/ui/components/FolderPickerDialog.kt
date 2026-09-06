package com.vidmax.player.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.FolderItem

/**
 * Destination-folder picker shared by Copy and Move.
 *
 * Follows the existing VidMax "Move to folder" dialog look (folder rows with
 * name + video count) so it feels native. Copy allows the current folder
 * (unique names handle collisions); Move excludes it via [folders] already
 * filtered by the caller.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderItem>,
    busy: Boolean,
    error: String?,
    emptyText: String = "No folders found.",
    onFolderClick: (FolderItem) -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = { if (!busy) onDismiss() },
      title = { Text(title, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          if (folders.isEmpty()) {
            Text(emptyText, fontSize = 14.sp)
          } else {
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
              items(folders, key = { it.path }) { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !busy) { onFolderClick(folder) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                      imageVector = Icons.Filled.Folder,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${folder.videoCount} videos",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
          if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error)
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
      })
}
