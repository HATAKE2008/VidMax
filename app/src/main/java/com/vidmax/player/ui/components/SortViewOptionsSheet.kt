package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.ui.screen.HomeViewStyle
import com.vidmax.player.viewmodel.SortOrder

/**
 * Modern "Sort & View Options" modal bottom sheet (REX-inspired).
 *
 * UI overlay only: every action routes into the existing LibraryViewModel
 * sort state ([SortOrder] + ascending), the existing home view-style state
 * and the existing grid-column override. No query, loading or playback
 * logic is touched here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortViewOptionsSheet(
    sortOrder: SortOrder,
    sortAscending: Boolean,
    viewStyle: HomeViewStyle,
    gridColumns: Int,
    onSort: (SortOrder, Boolean) -> Unit,
    onViewStyle: (HomeViewStyle) -> Unit,
    onGridColumns: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.comp_sort_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            SortByCard(
                sortOrder = sortOrder,
                sortAscending = sortAscending,
                onSort = onSort
            )

            LayoutCard(
                viewStyle = viewStyle,
                gridColumns = gridColumns,
                onViewStyle = onViewStyle,
                onGridColumns = onGridColumns,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun directionLabel(order: SortOrder, ascending: Boolean): String {
    return when (order) {
        SortOrder.DATE -> if (ascending) stringResource(R.string.comp_sort_oldest) else stringResource(R.string.comp_sort_newest)
        SortOrder.NAME -> if (ascending) stringResource(R.string.comp_sort_az) else stringResource(R.string.comp_sort_za)
        SortOrder.SIZE -> if (ascending) stringResource(R.string.comp_sort_smallest) else stringResource(R.string.comp_sort_largest)
        SortOrder.DURATION -> if (ascending) stringResource(R.string.comp_sort_shortest) else stringResource(R.string.comp_sort_longest)
    }
}

@Composable
private fun SortByCard(
    sortOrder: SortOrder,
    sortAscending: Boolean,
    onSort: (SortOrder, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.comp_sort_by),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onSort(sortOrder, !sortAscending) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = directionLabel(sortOrder, sortAscending),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        val options: List<Triple<SortOrder, String, ImageVector>> = listOf(
            Triple(SortOrder.NAME, stringResource(R.string.comp_sort_title_opt), Icons.Filled.SortByAlpha),
            Triple(SortOrder.DURATION, stringResource(R.string.comp_sort_duration), Icons.Filled.Schedule),
            Triple(SortOrder.DATE, stringResource(R.string.comp_sort_date), Icons.Filled.CalendarToday),
            Triple(SortOrder.SIZE, stringResource(R.string.comp_sort_size), Icons.Filled.SwapVert)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (order, label, icon) ->
                val selected: Boolean = sortOrder == order
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { onSort(order, sortAscending) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutCard(
    viewStyle: HomeViewStyle,
    gridColumns: Int,
    onViewStyle: (HomeViewStyle) -> Unit,
    onGridColumns: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.comp_layout_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LayoutOption(
                label = stringResource(R.string.comp_layout_list),
                icon = Icons.Filled.ViewList,
                selected = viewStyle == HomeViewStyle.LIST,
                onClick = { onViewStyle(HomeViewStyle.LIST) },
                modifier = Modifier.weight(1f)
            )
            LayoutOption(
                label = stringResource(R.string.comp_layout_grid),
                icon = Icons.Filled.GridView,
                selected = viewStyle == HomeViewStyle.GRID_MEDIUM,
                onClick = { onViewStyle(HomeViewStyle.GRID_MEDIUM) },
                modifier = Modifier.weight(1f)
            )
            LayoutOption(
                label = stringResource(R.string.comp_layout_large),
                icon = Icons.Filled.ViewAgenda,
                selected = viewStyle == HomeViewStyle.GRID_LARGE,
                onClick = { onViewStyle(HomeViewStyle.GRID_LARGE) },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = stringResource(R.string.comp_grid_columns),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val columnOptions: List<Int> = listOf(0, 2, 3, 4, 6, 12)
            items(columnOptions, key = { it }) { cols ->
                val label: String = if (cols == 0) stringResource(R.string.comp_grid_auto) else "$cols"
                FilterChip(
                    selected = gridColumns == cols,
                    onClick = { onGridColumns(cols) },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (gridColumns == cols) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = if (gridColumns == cols) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { onRefresh() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.comp_refresh_library),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LayoutOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
