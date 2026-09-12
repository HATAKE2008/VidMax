package com.vidmax.player.ui.selection

import androidx.compose.runtime.Stable
import com.vidmax.player.data.model.VideoItem

/**
 * Immutable selection state for videos, keyed by file PATH.
 *
 * Adapted from REX Player's SelectionState (xyz.mpv.rex.ui.browser.selection):
 * REX keys selection by item id, but VidMax's MediaStore ids change on every
 * rescan (a new row id is issued after each move/scan), which silently broke
 * selection across refreshes. The file path is the only stable identity, so
 * it is used as the selection key here.
 */
@Stable
data class VideoSelection(
    val selectedPaths: Set<String> = emptySet(),
) {
  val isInSelectionMode: Boolean
    get() = selectedPaths.isNotEmpty()

  val selectedCount: Int
    get() = selectedPaths.size

  val isSingleSelection: Boolean
    get() = selectedPaths.size == 1

  fun isSelected(path: String): Boolean = selectedPaths.contains(path)

  fun toggle(path: String): VideoSelection {
    return copy(
        selectedPaths = if (selectedPaths.contains(path)) selectedPaths - path
        else selectedPaths + path)
  }

  fun clear(): VideoSelection = copy(selectedPaths = emptySet())

  fun selectAll(paths: List<String>): VideoSelection = copy(selectedPaths = paths.toSet())

  /** Resolves the selected videos against the current list (survives refresh). */
  fun getSelected(videos: List<VideoItem>): List<VideoItem> {
    if (selectedPaths.isEmpty()) return emptyList()
    return videos.filter { selectedPaths.contains(it.path) }
  }
}
