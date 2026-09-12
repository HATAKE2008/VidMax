package com.vidmax.player.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.VideoItem
import com.vidmax.player.ui.components.AddToPlaylistDialog
import com.vidmax.player.ui.components.DialogCancelButton
import com.vidmax.player.ui.components.DialogConfirmButton
import com.vidmax.player.ui.components.DialogHeaderBadge
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.MoveDeleteConsentRequired
import com.vidmax.player.viewmodel.MoveWriteConsentRequired
import com.vidmax.player.viewmodel.RenameConsentRequiredException
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
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.vam_share_chooser)))
  } else {
    Toast.makeText(context, context.getString(R.string.vam_share_failed), Toast.LENGTH_SHORT).show()
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
  var pendingRename by remember(video) { mutableStateOf<Pair<VideoItem, String>?>(null) }

  fun succeedRename() {
    renameBusy = false
    renameOpen = false
    renameError = null
    pendingRename = null
    onDismiss()
    Toast.makeText(context, context.getString(R.string.vam_renamed), Toast.LENGTH_SHORT).show()
  }

  fun failRename(message: String?) {
    renameBusy = false
    renameError = message ?: context.getString(R.string.vam_rename_failed)
  }

  val renameWriteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingRename
            pendingRename = null
            if (result.resultCode == Activity.RESULT_OK && pending != null) {
              viewModel.renameVideo(pending.first, pending.second) { retryResult ->
                retryResult.onSuccess { succeedRename() }.onFailure { failRename(it.message) }
              }
            } else {
              failRename(context.getString(R.string.vam_rename_cancelled))
            }
          }

  fun handleRenameResult(target: VideoItem, base: String, result: Result<String>) {
    result
        .onSuccess { succeedRename() }
        .onFailure {
          if (it is RenameConsentRequiredException &&
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingRename = target to base
            val pendingIntent =
                MediaStore.createWriteRequest(context.contentResolver, it.uris)
            renameWriteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build())
          } else {
            failRename(it.message)
          }
        }
  }

  var moveOpen by remember(video) { mutableStateOf(false) }
  var moveError by remember(video) { mutableStateOf<String?>(null) }
  var moveBusy by remember(video) { mutableStateOf(false) }
  var pendingMoveDelete by remember(video) { mutableStateOf<MoveDeleteConsentRequired?>(null) }
  var pendingMoveWrite by remember(video) { mutableStateOf<MoveWriteConsentRequired?>(null) }
  val folders by viewModel.folders.collectAsState()

  fun succeedMove() {
    moveBusy = false
    moveOpen = false
    moveError = null
    pendingMoveDelete = null
    onDismiss()
    Toast.makeText(context, context.getString(R.string.vam_moved), Toast.LENGTH_SHORT).show()
  }

  fun failMove(message: String?) {
    moveBusy = false
    val base = message ?: context.getString(R.string.vam_move_failed)
    moveError = if (!viewModel.hasFullStorageAccess() &&
        (base.contains("Move failed", ignoreCase = true) ||
            base.contains("permission", ignoreCase = true))) {
      context.getString(R.string.vam_move_access_hint, base)
    } else {
      base
    }
  }

  val moveDeleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingMoveDelete
            pendingMoveDelete = null
            if (result.resultCode == Activity.RESULT_OK && pending != null) {
              viewModel.completeMoveDelete(pending) { retryResult ->
                retryResult.onSuccess { succeedMove() }.onFailure { failMove(it.message) }
              }
            } else {
              failMove(context.getString(R.string.vam_move_kept))
            }
          }

  val moveWriteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingMoveWrite
            pendingMoveWrite = null
            if (result.resultCode == Activity.RESULT_OK && pending != null) {
              viewModel.retryMoveAfterWriteConsent(pending) { retryResult ->
                retryResult.onSuccess { succeedMove() }.onFailure { failMove(it.message) }
              }
            } else {
              failMove(context.getString(R.string.vam_move_cancelled))
            }
          }

  fun handleMoveResult(result: Result<String>) {
    result
        .onSuccess { succeedMove() }
        .onFailure {
          if (it is MoveWriteConsentRequired &&
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingMoveWrite = it
            val videoUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.video.id)
            val pendingIntent = MediaStore.createWriteRequest(
                context.contentResolver, listOf(videoUri))
            moveWriteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build())
          } else if (it is MoveDeleteConsentRequired) {
            pendingMoveDelete = it
            val pendingIntent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                  MediaStore.createDeleteRequest(context.contentResolver, listOf(it.srcUri))
                } else {
                  MediaStore.createWriteRequest(context.contentResolver, listOf(it.srcUri))
                }
            moveDeleteLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build())
          } else {
            failMove(it.message)
          }
        }
  }

  val subDialogOpen =
      showPlaylist || showDeleteConfirm || showDetails || renameOpen || moveOpen

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
        onMove = {
          moveError = null
          moveOpen = true
        },
        onDetails = { showDetails = true },
        onDelete = { showDeleteConfirm = true },
        onDismiss = onDismiss)
  }

  if (moveOpen) {
    val currentDir = File(video.path).parent ?: ""
    val destinations = folders.filter { it.path != currentDir }
    AlertDialog(
        onDismissRequest = {
          if (!moveBusy) {
            moveOpen = false
            moveError = null
          }
        },
        title = { Text(stringResource(R.string.vam_move_title), fontWeight = FontWeight.Bold) },
        text = {
          Column {
            if (destinations.isEmpty()) {
              Text(stringResource(R.string.vam_no_folders), fontSize = 14.sp)
            } else {
              LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                items(destinations, key = { it.path }) { folder ->
                  Row(
                      modifier = Modifier.fillMaxWidth()
                          .clickable(enabled = !moveBusy) {
                            moveBusy = true
                            moveError = null
                            viewModel.moveVideoToFolder(video, folder.path) { result ->
                              handleMoveResult(result)
                            }
                          }
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
                              text = pluralStringResource(R.plurals.vam_folder_videos_count, folder.videoCount, folder.videoCount),
                              fontSize = 12.sp,
                              color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                      }
                }
              }
            }
            if (moveError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                  text = moveError!!,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.error)
            }
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(enabled = !moveBusy, onClick = {
            moveOpen = false
            moveError = null
          }) { Text(stringResource(R.string.vam_cancel)) }
        })
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
          DialogHeaderBadge(
              icon = Icons.Rounded.DeleteOutline,
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
              contentColor = MaterialTheme.colorScheme.error)
        },
        title = { Text(stringResource(R.string.vam_delete_title), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
          Text(stringResource(R.string.vam_delete_message, video.title))
        },
        confirmButton = {
          DialogConfirmButton(
              label = stringResource(R.string.vam_delete_confirm),
              danger = true,
              onClick = {
                showDeleteConfirm = false
                onDismiss()
                onDeleteRequest(video)
              })
        },
        dismissButton = {
          DialogCancelButton(
              label = stringResource(R.string.vam_cancel),
              onClick = {
                showDeleteConfirm = false
                onDismiss()
              })
        })
  }

  if (showDetails) {
    VideoDetailsDialog(
        video = video,
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
            handleRenameResult(video, base, result)
          }
        })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMove: () -> Unit,
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
          val playLabel = stringResource(R.string.vam_menu_play)
          val renameLabel = stringResource(R.string.vam_menu_rename)
          val shareLabel = stringResource(R.string.vam_menu_share)
          val favLabel = if (isFavorite) stringResource(R.string.vam_menu_remove_fav) else stringResource(R.string.vam_menu_add_fav)
          val addPlaylistLabel = stringResource(R.string.vam_menu_add_playlist)
          val moveLabel = stringResource(R.string.vam_menu_move)
          val detailsLabel = stringResource(R.string.vam_menu_details)
          val deleteLabel = stringResource(R.string.vam_menu_delete)
          val actions =
              listOf(
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.PlayArrow, playLabel, onPlay),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Edit, renameLabel, onRename),
                  Triple<ImageVector, String, () -> Unit>(Icons.Filled.Share, shareLabel, onShare),
                  Triple<ImageVector, String, () -> Unit>(
                      if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                      favLabel,
                      onToggleFavorite),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.PlaylistAdd, addPlaylistLabel, onAddToPlaylist),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.DriveFileMove, moveLabel, onMove),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Info, detailsLabel, onDetails),
                  Triple<ImageVector, String, () -> Unit>(
                      Icons.Filled.Delete, deleteLabel, onDelete))
          actions.forEach { (icon, label, action) ->
            val isDestructive = icon == Icons.Filled.Delete
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
