package com.vidmax.player.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.AudioItem
import com.vidmax.player.ui.components.VidMaxSearchBar
import com.vidmax.player.viewmodel.LibraryViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    viewModel: LibraryViewModel,
    onSettingsClick: () -> Unit,
    onAudioClick: (List<AudioItem>, Int) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenMyMix: () -> Unit
) {
  val context: Context = LocalContext.current
  val audioList: List<AudioItem> by viewModel.filteredAudio.collectAsState()
  val searchQuery: String by viewModel.audioSearchQuery.collectAsState()

  val currentlyPlayingPath: String by viewModel.recentlyPlayedPath.collectAsState()
  val isAudioPlaying: Boolean by viewModel.isAudioPlaying.collectAsState()

  var selectedAudioIds: Set<Long> by remember { mutableStateOf(emptySet<Long>()) }
  var showDeleteConfirmDialog: Boolean by remember { mutableStateOf(false) }

  val inSelectionMode: Boolean = selectedAudioIds.isNotEmpty()
  var isSearchExpanded: Boolean by remember { mutableStateOf(false) }

  val deleteLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
              Toast.makeText(context, "Selected audio deleted successfully", Toast.LENGTH_SHORT)
                  .show()
              selectedAudioIds = emptySet()
            } else {
              Toast.makeText(context, "Delete Cancelled", Toast.LENGTH_SHORT).show()
            }
          }

  if (showDeleteConfirmDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = { Text(text = "Delete Audios", fontWeight = FontWeight.Bold) },
        text = {
          Text(
              text =
                  "Are you sure you want to delete ${selectedAudioIds.size} selected songs? This action cannot be undone.")
        },
        confirmButton = {
          TextButton(
              onClick = {
                showDeleteConfirmDialog = false
                val urisToDelete: List<Uri> =
                    selectedAudioIds.mapNotNull { id: Long ->
                      val path: String = audioList.find { audioItem: AudioItem -> audioItem.id == id }?.path ?: return@mapNotNull null
                      getAudioUriFromPathForMulti(context, path)
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && urisToDelete.isNotEmpty()) {
                  val pendingIntent =
                      MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                  deleteLauncher.launch(
                      IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else {
                  var deletedCount = 0
                  selectedAudioIds.forEach { id: Long ->
                    val path: String = audioList.find { audioItem: AudioItem -> audioItem.id == id }?.path ?: return@forEach
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                      deletedCount++
                    } else {
                      val uri: Uri? = getAudioUriFromPathForMulti(context, path)
                      if (uri != null) {
                        val rows = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) deletedCount++
                      }
                    }
                  }
                  Toast.makeText(context, "$deletedCount audio(s) deleted", Toast.LENGTH_SHORT)
                      .show()
                  selectedAudioIds = emptySet()
                }
              }) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold)
              }
        },
        dismissButton = {
          TextButton(onClick = { showDeleteConfirmDialog = false }) {
            Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
          }
        })
  }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(6.dp))

        if (inSelectionMode) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(vertical = 6.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                      .padding(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(onClick = { selectedAudioIds = emptySet() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_custom),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp))
                  }
                  Text(
                      text = "${selectedAudioIds.size} Selected",
                      color = MaterialTheme.colorScheme.onBackground,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold)
                }
                Row {
                  IconButton(
                      onClick = {
                        selectedAudioIds =
                            if (selectedAudioIds.size == audioList.size) emptySet()
                            else audioList.map { audioItem: AudioItem -> audioItem.id }.toSet()
                      }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_select_all),
                            contentDescription = "Select All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                  IconButton(
                      onClick = {
                        val uris: ArrayList<Uri> =
                            selectedAudioIds
                                .mapNotNull { id: Long ->
                                  val path: String =
                                      audioList.find { audioItem: AudioItem -> audioItem.id == id }?.path ?: return@mapNotNull null
                                  getAudioUriFromPathForMulti(context, path)
                                }
                                .toCollection(ArrayList())

                        if (uris.isNotEmpty()) {
                          val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
                          intent.type = "audio/*"
                          intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                          context.startActivity(
                              Intent.createChooser(intent, "Share ${uris.size} Audios"))
                          selectedAudioIds = emptySet()
                        }
                      }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share_custom),
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp))
                      }
                  IconButton(onClick = { showDeleteConfirmDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete_custom),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp))
                  }
                }
              }
        } else if (isSearchExpanded) {
          Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
              verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                      isSearchExpanded = false
                      viewModel.setAudioSearchQuery("")
                    }) {
                      Icon(
                          imageVector = Icons.Default.ArrowBack,
                          contentDescription = "Back",
                          tint = MaterialTheme.colorScheme.onBackground)
                    }
                Box(modifier = Modifier.weight(1f)) {
                  VidMaxSearchBar(
                      query = searchQuery,
                      onQueryChange = { query: String -> viewModel.setAudioSearchQuery(query) },
                      placeholder = "Search songs...")
                }
              }
        } else {
          Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Music",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                      onClick = { isSearchExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp))
                      }
                  IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp))
                  }
                }
              }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp)) {
              item {
                Text(
                    text = "Playlists",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                      Box(
                          modifier =
                              Modifier.weight(1f)
                                  .height(95.dp)
                                  .clip(RoundedCornerShape(16.dp))
                                  .background(
                                      Brush.linearGradient(
                                          listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))))
                                  .clickable { onOpenFavorites() }) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier =
                                    Modifier.size(75.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 16.dp, y = 16.dp)
                                        .graphicsLayer { rotationZ = -15f })
                            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                              Icon(
                                  imageVector = Icons.Filled.Favorite,
                                  contentDescription = null,
                                  tint = Color.White,
                                  modifier = Modifier.size(28.dp))
                              Spacer(modifier = Modifier.weight(1f))
                              Text(
                                  text = "Favorites",
                                  color = Color.White,
                                  fontSize = 16.sp,
                                  fontWeight = FontWeight.Bold)
                            }
                          }

                      Box(
                          modifier =
                              Modifier.weight(1f)
                                  .height(95.dp)
                                  .clip(RoundedCornerShape(16.dp))
                                  .background(
                                      Brush.linearGradient(
                                          listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))))
                                  .clickable { onOpenMyMix() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_music_note),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier =
                                    Modifier.size(75.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 16.dp, y = 16.dp)
                                        .graphicsLayer { rotationZ = -15f })
                            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                              Icon(
                                  painter = painterResource(id = R.drawable.ic_music_note),
                                  contentDescription = null,
                                  tint = Color.White,
                                  modifier = Modifier.size(28.dp))
                              Spacer(modifier = Modifier.weight(1f))
                              Text(
                                  text = "My Mix",
                                  color = Color.White,
                                  fontSize = 16.sp,
                                  fontWeight = FontWeight.Bold)
                            }
                          }
                    }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "All Songs",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))
              }

              itemsIndexed(items = audioList, key = { _, item -> item.id }) { index, audio ->
                val isSelected: Boolean = selectedAudioIds.contains(audio.id)
                val isPlayingNow: Boolean = currentlyPlayingPath == audio.path

                AudioCard(
                    audio = audio,
                    duration = viewModel.formatDuration(audio.duration),
                    isSelected = isSelected,
                    isPlayingNow = isPlayingNow,
                    isAudioPlayingState = isAudioPlaying,
                    onClick = {
                      if (inSelectionMode) {
                        selectedAudioIds =
                            if (isSelected) selectedAudioIds - audio.id
                            else selectedAudioIds + audio.id
                      } else {
                        onAudioClick(audioList, index)
                      }
                    },
                    onLongClick = {
                      selectedAudioIds =
                          if (isSelected) selectedAudioIds - audio.id
                          else selectedAudioIds + audio.id
                    })
              }
            }
      }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioCard(
    audio: AudioItem,
    duration: String,
    isSelected: Boolean = false,
    isPlayingNow: Boolean = false,
    isAudioPlayingState: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
  val context: Context = LocalContext.current
  var albumArt: Bitmap? by remember { mutableStateOf(null) }
  val folderName: String = File(audio.path).parentFile?.name ?: "Unknown"

  LaunchedEffect(audio.path) {
    withContext(Dispatchers.IO) {
      try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(audio.path))
        val art: ByteArray? = retriever.embeddedPicture
        if (art != null) {
          // 🔥 ম্যাজিক: র‍্যাম ইউজ এবং ল্যাগ কমানোর জন্য ছবির কোয়ালিটি রিসাইজ করা হলো
          val options = BitmapFactory.Options()
          options.inSampleSize = 4 
          albumArt = BitmapFactory.decodeByteArray(art, 0, art.size, options)
        }
        retriever.release()
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  val backgroundColor: Color =
      if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
      } else {
        Color.Transparent
      }

  val borderColor: Color =
      when {
        isSelected || isPlayingNow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> Color.Transparent
      }

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(backgroundColor)
              .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center) {
              if (albumArt != null) {
                Image(
                    bitmap = albumArt!!.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
              } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
              }

              if (isPlayingNow && isAudioPlayingState) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 6.dp),
                    contentAlignment = Alignment.BottomCenter) {
                      PlayingEqualizerAnim()
                    }
              }
            }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = audio.title,
              color =
                  if (isPlayingNow) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurface,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)

          Spacer(modifier = Modifier.height(6.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                  Text(
                      text = duration,
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold)
                }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${audio.artist}  •  $folderName",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }

        if (isSelected) {
          Icon(
              imageVector = Icons.Filled.Done,
              contentDescription = "Selected",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(end = 4.dp).size(24.dp))
        } else if (isPlayingNow) {
          Box(
              modifier =
                  Modifier.padding(end = 4.dp)
                      .size(32.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center) {
                Icon(
                    painter =
                        painterResource(
                            id =
                                if (isAudioPlayingState) R.drawable.ic_pause
                                else R.drawable.ic_play),
                    contentDescription = "Playing State",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
              }
        }
      }
}

@Composable
fun PlayingEqualizerAnim() {
  val transition = rememberInfiniteTransition(label = "dj_eq_transition")

  val bar1: Float by
      transition.animateFloat(
          initialValue = 4f,
          targetValue = 4f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 450
                        26f at 120 with EaseInOutSine
                        14f at 280
                        4f at 450
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar1")

  val bar2: Float by
      transition.animateFloat(
          initialValue = 6f,
          targetValue = 6f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 350
                        20f at 90 with EaseInOutSine
                        8f at 220
                        6f at 350
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar2")

  val bar3: Float by
      transition.animateFloat(
          initialValue = 5f,
          targetValue = 5f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 500
                        28f at 150 with EaseInOutSine
                        16f at 320
                        5f at 500
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar3")

  val bar4: Float by
      transition.animateFloat(
          initialValue = 4f,
          targetValue = 4f,
          animationSpec =
              infiniteRepeatable(
                  animation =
                      keyframes {
                        durationMillis = 400
                        18f at 100 with EaseInOutSine
                        10f at 240
                        4f at 400
                      },
                  repeatMode = RepeatMode.Restart),
          label = "bar4")

  Row(
      modifier = Modifier.size(24.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalAlignment = Alignment.Bottom) {
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar1.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar2.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar3.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
        Box(
            modifier =
                Modifier.width(3f.dp)
                    .height(bar4.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)))
      }
}

fun getAudioUriFromPathForMulti(context: Context, path: String): Uri? {
  var cursor: android.database.Cursor? = null
  try {
    cursor =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            MediaStore.Audio.Media.DATA + "=?",
            arrayOf(path),
            null)
    if (cursor != null && cursor.moveToFirst()) {
      val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
      return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
    return null
  } catch (e: Exception) {
    return null
  } finally {
    cursor?.close()
  }
}
