package com.vidmax.player.ui.screen

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidmax.player.R
import com.vidmax.player.data.model.ConnectionStatus
import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.model.NetworkFile
import com.vidmax.player.ui.components.AddConnectionDialog
import com.vidmax.player.ui.components.RecentStreamLinkRow
import com.vidmax.player.ui.components.StreamLinkSection
import com.vidmax.player.viewmodel.LibraryViewModel
import com.vidmax.player.viewmodel.NetworkViewModel
import kotlinx.coroutines.launch

@Composable
fun NetworkScreen(libraryViewModel: LibraryViewModel) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: NetworkViewModel = viewModel(factory = NetworkViewModel.factory(app))

    val connections by viewModel.connections.collectAsState()
    val statuses by viewModel.connectionStatuses.collectAsState()
    val currentConnection by viewModel.currentConnection.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val playedLinks by viewModel.playedLinks.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<NetworkConnection?>(null) }
    var isNetworkSearchOpen by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Internal back navigation: search -> folder up -> close connection ->
    // (falls through to MainScreen's tab back handling)
    BackHandler(enabled = isNetworkSearchOpen || currentConnection != null) {
        if (isNetworkSearchOpen) isNetworkSearchOpen = false
        else viewModel.navigateUp()
    }

    LaunchedEffect(error) {
        error?.let { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.clearError()
        }
    }

    if (showAddDialog || editingConnection != null) {
        AddConnectionDialog(
            initial = editingConnection,
            onDismiss = {
                showAddDialog = false
                editingConnection = null
            },
            onSave = { connection ->
                if (connection.id == 0L) viewModel.addConnection(connection)
                else viewModel.updateConnection(connection)
                showAddDialog = false
                editingConnection = null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val conn = currentConnection
        if (conn == null) {
            ConnectionsList(
                connections = connections,
                statuses = statuses,
                playedLinks = playedLinks,
                onAdd = { showAddDialog = true },
                onPlayLink = { viewModel.playStreamLink(it) },
                onRemoveLink = { viewModel.removePlayedLink(it) },
                onEdit = { editingConnection = it },
                onDelete = { viewModel.deleteConnection(it) },
                onConnect = { viewModel.connect(it) },
                onDisconnect = { viewModel.disconnect(it) },
                onOpen = { viewModel.openConnection(it) },
            )
        } else {
            NetworkBrowser(
                connection = conn,
                currentPath = currentPath,
                files = files,
                isLoading = isLoading,
                onBack = { viewModel.navigateUp() },
                onRefresh = { viewModel.refresh() },
                onSearchClick = { isNetworkSearchOpen = true },
                onOpenFolder = { viewModel.navigateInto(it) },
                onPlayFile = { viewModel.playFile(it) },
            )
        }

        if (isNetworkSearchOpen) {
            SearchScreen(
                scope = SearchScope.NETWORK,
                viewModel = libraryViewModel,
                networkFiles = files,
                onBack = { isNetworkSearchOpen = false },
                onPlayNetworkFile = { file ->
                    isNetworkSearchOpen = false
                    viewModel.playFile(file)
                },
                onOpenNetworkFolder = { folder ->
                    isNetworkSearchOpen = false
                    viewModel.navigateInto(folder)
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ============================== Connections List ==============================

@Composable
private fun ConnectionsList(
    connections: List<NetworkConnection>,
    statuses: Map<Long, ConnectionStatus>,
    playedLinks: List<String>,
    onAdd: () -> Unit,
    onPlayLink: (String) -> Unit,
    onRemoveLink: (String) -> Unit,
    onEdit: (NetworkConnection) -> Unit,
    onDelete: (NetworkConnection) -> Unit,
    onConnect: (NetworkConnection) -> Unit,
    onDisconnect: (NetworkConnection) -> Unit,
    onOpen: (NetworkConnection) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.net_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Connection",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StreamLinkSection(onPlayLink = onPlayLink)
            }

            if (playedLinks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.net_recent_links),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                items(items = playedLinks, key = { it }) { link ->
                    RecentStreamLinkRow(
                        link = link,
                        onClick = { onPlayLink(link) },
                        onRemove = { onRemoveLink(link) },
                    )
                }
            }

            if (connections.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.net_empty_title),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.net_empty_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAdd) {
                            Text(stringResource(R.string.net_add_server))
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.net_servers),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                items(items = connections, key = { it.id }) { connection ->
                    ConnectionCard(
                        connection = connection,
                        status = statuses[connection.id],
                        onEdit = { onEdit(connection) },
                        onDelete = { onDelete(connection) },
                        onConnect = { onConnect(connection) },
                        onDisconnect = { onDisconnect(connection) },
                        onOpen = { onOpen(connection) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: NetworkConnection,
    status: ConnectionStatus?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: () -> Unit,
) {
    val isConnected = status?.isConnected == true
    val isConnecting = status?.isConnecting == true
    val errorText = status?.error

    // REX NetworkConnectionCard language: M3 Card with title + protocol/host
    // subtitle, edit/delete actions, status error and tonal action buttons.
    // All callbacks unchanged.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        onClick = onOpen,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = connection.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${connection.protocol.displayName} • ${connection.host}:${connection.port}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit connection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete connection",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (connection.path != "/") {
                Text(
                    text = stringResource(R.string.net_path_label, connection.path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (connection.username.isNotEmpty() && !connection.isAnonymous) {
                Text(
                    text = stringResource(R.string.net_user_label, connection.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                when {
                    isConnecting -> {
                        FilledTonalButton(onClick = {}, enabled = false) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                                Text(
                                    stringResource(R.string.net_connecting),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    }
                    isConnected -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onOpen() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(stringResource(R.string.net_browse))
                            }
                            FilledTonalButton(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.LinkOff,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(stringResource(R.string.net_disconnect))
                            }
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = onConnect,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                                Text(stringResource(R.string.net_connect))
                        }
                    }
                }
            }
        }
    }
}

// ============================== File Browser ==============================

@Composable
private fun NetworkBrowser(
    connection: NetworkConnection,
    currentPath: String,
    files: List<NetworkFile>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenFolder: (NetworkFile) -> Unit,
    onPlayFile: (NetworkFile) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentPath.ifBlank { "/" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            files.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.net_empty_files),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = files, key = { it.path }) { file ->
                        if (file.isDirectory) {
                            NetworkFolderRow(file = file, onClick = { onOpenFolder(file) })
                        } else {
                            NetworkVideoRow(
                                file = file,
                                onClick = { onPlayFile(file) },
                            )
                        }
                    }
                }
            }
        }
        }

        // mpvRex-style floating pill bottom bar (FloatingBottomBar port):
        // icon-only tonal buttons in a rounded floating surface.
        NetworkFloatingBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onUpClick = onBack,
            onRefreshClick = onRefresh,
        )
    }
}

/**
 * Ported from mpvRex's FloatingBottomBar: a pill-shaped Surface with
 * FilledTonalIconButtons, floating above the bottom navigation bar with an
 * animated offset.
 */
@Composable
private fun NetworkFloatingBottomBar(
    modifier: Modifier = Modifier,
    onUpClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val targetBottomPadding = 96.dp
    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = tween(220),
        label = "networkBottomBarPadding",
    )

    Surface(
        modifier = modifier.padding(bottom = animatedBottomPadding),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            FilledTonalIconButton(
                onClick = onUpClick,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up",
                    modifier = Modifier.size(20.dp),
                )
            }
            FilledTonalIconButton(
                onClick = onRefreshClick,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun NetworkFolderRow(file: NetworkFile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NetworkVideoRow(file: NetworkFile, onClick: () -> Unit) {
    // REX NetworkVideoCard-style row: 16:9 thumbnail placeholder on the
    // left, two-line title and a size chip — playback logic unchanged.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (file.size > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.vidmax.player.ui.components.MetaChip(
                            text = formatFileSize(file.size))
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${size} B" else "${"%.1f".format(value)} ${units[unitIndex]}"
}
