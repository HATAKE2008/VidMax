package com.vidmax.player.ui.screen

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidmax.player.data.model.ConnectionStatus
import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.model.NetworkFile
import com.vidmax.player.ui.components.AddConnectionDialog
import com.vidmax.player.ui.components.RecentStreamLinkRow
import com.vidmax.player.ui.components.StreamLinkSection
import com.vidmax.player.viewmodel.NetworkViewModel
import kotlinx.coroutines.launch

@Composable
fun NetworkScreen() {
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Internal back navigation: folder up -> close connection -> (falls through
    // to MainScreen's tab back handling)
    BackHandler(enabled = currentConnection != null) {
        viewModel.navigateUp()
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
                onOpenFolder = { viewModel.navigateInto(it) },
                onPlayFile = { viewModel.playFile(it) },
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
                text = "Network",
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
                        text = "Recent Links",
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
                            text = "No servers yet",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add an SMB, FTP or WebDAV server to stream videos",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAdd) {
                            Text("Add Server")
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Servers",
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${connection.host}:${connection.port} • ${connection.protocol.displayName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (errorText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isConnected) {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = "Connected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else if (isConnected) {
            TextButton(onClick = onDisconnect) {
                Text("Disconnect")
            }
        } else {
            TextButton(onClick = onConnect) {
                Text("Connect")
            }
        }

        DropdownMenuActions(
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun DropdownMenuActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
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
                        text = "No files found",
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = file.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NetworkVideoRow(file: NetworkFile, onClick: () -> Unit) {
    // mpvRex NetworkVideoCard-style row: 16:9 thumbnail placeholder on the
    // left, two-line title and a size chip — playback logic unchanged.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(10.dp),
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
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (file.size > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(file.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
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
