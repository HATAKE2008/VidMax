package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.viewmodel.SortOrder

private data class SortChip(val label: String, val order: SortOrder)

private val sortChips =
    listOf(
        SortChip("Date", SortOrder.DATE),
        SortChip("Name", SortOrder.NAME),
        SortChip("Size", SortOrder.SIZE),
        SortChip("Duration", SortOrder.DURATION))

@Composable
fun SortChips(
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
  LazyRow(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(horizontal = 0.dp)) {
        items(sortChips) { chip: SortChip ->
          val isSelected: Boolean = chip.order == currentSort
          Box(
              modifier =
                  Modifier.clip(RoundedCornerShape(20.dp))
                      .background(
                          if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.surfaceVariant)
                      .clickable { onSortSelected(chip.order) }
                      .padding(horizontal = 16.dp, vertical = 7.dp),
              contentAlignment = Alignment.Center) {
                Text(
                    text = chip.label,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
              }
        }
      }
}
