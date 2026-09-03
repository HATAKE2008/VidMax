package com.vidmax.player.ui.screen

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.AddToPlaylistDialog
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File

/**
 * Shared video long-press action menu (unified across Videos / Folders /
 * Favorites / Search results / Playlist video lists).
 *
 * UI, action set, labels, ordering and icons are the Folder screen's P1 menu,
 * extracted verbatim. All business logic reuses the existing P1 flows:
 * - Play: caller-resolves list+index into the existing player flow.
 * - Share/Rename/Details/Delete/Favorite/Playlist: same callbacks as before.
 */
fun shareVideo(context: Context, video: VideoItem) {
  val uri = getVideoUriFromPathForMulti(context, video.path)
  if (uri != null) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "video/*"
          putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "Share Video"))
  } else {
    Toast.makeText(context, "Could not share this video", Toast.LENGTH_SHORT).show()
  }
}

/**
 * Self-contained host: bottom-sheet menu plus the playlist / delete-confirm /
 * details / rename dialogs. Delete execution is caller-provided because the
 * MediaStore R+ delete IntentSender launcher lives at the HomeScreen level.
 */
@Composable
fun VideoActionMenuHost(
    viewModel: LibraryViewModel,
    video: VideoItem?,
    onPlay: (VideoItem) -> Unit,
    onDeleteRequest: (VideoItem) -> Unit,
    onDismiss: () -> Unit
) {
  if (video == null) return
  val context = LocalContext.current
  val favorites by viewModel.favoriteVideoPaths.collectAsState()
  val isFavorite = favorites.contains(video.path)

  var showPlaylist by remember(video) { mutableStateOf(false) }
  var showDeleteConfirm by remember(video) { mutableStateOf(false) }
  var showDetails by remember(video) { mutableStateOf(false) }
  var renameOpen by remember(video) { mutableStateOf(false) }
  var renameError by remember(video) { mutableStateOf<String?>(null) }
  var renameBusy by remember(video) { mutableStateOf(false) }

  val subDialogOpen = showPlaylist || showDeleteConfirm || showDetails || renameOpen

  if (!subDialogOpen) {
    VideoActionSheet(
        video = video,
        isFavorite = isFavorite,
        onPlay = { onPlay(video) },
        onRename = {
          renameError = null
          renameOpen = true
        },
        onShare = {
          shareVideo(context, video)
          onDismiss()
        },
        onToggleFavorite = {
          viewModel.toggleVideoFavorite(video.path)
          onDismiss()
        },
        onAddToPlaylist = { showPlaylist = true },
        onDetails = { showDetails = true },
        onDelete = { showDeleteConfirm = true },
        onDismiss = onDismiss)
  }

  if (showPlaylist) {
    AddToPlaylistDialog(
        viewModel = viewModel,
        videos = listOf(video),
        onDismiss = {
          showPlaylist = false
          onDismiss()
        })
  }

  if (showDeleteConfirm) {
    AlertDialog(
        onDismissRequest = {
          showDeleteConfirm = false
          onDismiss()
        },
        title = { Text("Delete Video", fontWeight = FontWeight.Bold) },
        text = {
          Text("Are you sure you want to delete \"${video.title}\"? This action cannot be undone.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirm = false
                onDismiss()
                onDeleteRequest(video)
              }) {
                Text(
                    "Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showDeleteConfirm = false
                onDismiss()
              }) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
              }
        })
  }

  if (showDetails) {
    VideoDetailsDialog(
        video = video,
        viewModel = viewModel,
        onDismiss = {
          showDetails = false
          onDismiss()
        })
  }

  if (renameOpen) {
    RenameVideoDialog(
        currentBaseName = File(video.path).nameWithoutExtension.ifEmpty { video.title },
        extension = File(video.path).extension.ifEmpty { "mp4" },
        error = renameError,
        busy = renameBusy,
        onDismiss = {
          if (!renameBusy) {
            renameOpen = false
            renameError = null
            onDismiss()
          }
        },
        onConfirm = { base ->
          renameBusy = true
          renameError = null
          viewModel.renameVideo(video, base) { result ->
            renameBusy = false
            result
                .onSuccess {
                  renameOpen = false
                  renameError = null
                  onDismiss()
                  Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                }
                .onFailure { renameError = it.message ?: "Rename failed" }
          }
        })
  }
}

@Composable
fun VideoActionSheet(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 32.dp)) {
          Text(
              text = video.title,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
          val actions =
              listOf(
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.PlayArrow, "Play", onPlay),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Edit, "Rename", onRename),
                  Triple<ImageVector, String, () -> Unit>(Icons.Filled.Share, "Share", onShare),
                  Triple<ImageVector, String, () -> Unit>(
                      if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                      if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                      onToggleFavorite),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.PlaylistAdd, "Add to Playlist", onAddToPlaylist),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Info, "Details", onDetails),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Delete, "Delete", onDelete))
          actions.forEach { (icon, label, action) ->
            val isDestructive = label == "Delete"
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { action() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint =
                          if (isDestructive) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(16.dp))
                  Text(
                      text = label,
                      color =
                          if (isDestructive) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.onSurface,
                      fontSize = 15.sp)
                }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
  }
}
