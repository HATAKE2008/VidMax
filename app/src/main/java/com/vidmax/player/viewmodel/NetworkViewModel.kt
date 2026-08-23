package com.vidmax.player.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vidmax.player.data.model.ConnectionStatus
import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.model.NetworkFile
import com.vidmax.player.data.networkstreaming.proxy.NetworkStreamingProxy
import com.vidmax.player.data.repository.NetworkRepository
import com.vidmax.player.ui.player.PlayerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Network tab: manages saved connections (SMB/FTP/WebDAV),
 * browsing their file trees and streaming files to the player.
 */
class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkRepository(application)

    private val _connections = MutableStateFlow(repository.getAllConnections())
    val connections: StateFlow<List<NetworkConnection>> = _connections.asStateFlow()

    private val _connectionStatuses = MutableStateFlow(repository.connectionStatuses.value)
    val connectionStatuses: StateFlow<Map<Long, ConnectionStatus>> = _connectionStatuses.asStateFlow()

    // Browsing state
    private val _currentConnection = MutableStateFlow<NetworkConnection?>(null)
    val currentConnection: StateFlow<NetworkConnection?> = _currentConnection.asStateFlow()

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<NetworkFile>>(emptyList())
    val files: StateFlow<List<NetworkFile>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectionStatuses.collect { statuses ->
                _connectionStatuses.value = statuses
            }
        }
    }

    // ---------- Connection management ----------

    fun addConnection(connection: NetworkConnection) {
        viewModelScope.launch {
            repository.addConnection(connection)
            _connections.value = repository.getAllConnections()
        }
    }

    fun updateConnection(connection: NetworkConnection) {
        viewModelScope.launch {
            repository.updateConnection(connection)
            _connections.value = repository.getAllConnections()
        }
    }

    fun deleteConnection(connection: NetworkConnection) {
        viewModelScope.launch {
            repository.deleteConnection(connection)
            _connections.value = repository.getAllConnections()
            if (_currentConnection.value?.id == connection.id) {
                closeConnection()
            }
        }
    }

    fun connect(connection: NetworkConnection) {
        viewModelScope.launch {
            repository.connect(connection)
            _connections.value = repository.getAllConnections()
        }
    }

    fun disconnect(connection: NetworkConnection) {
        viewModelScope.launch {
            repository.disconnect(connection)
        }
    }

    // ---------- Browsing ----------

    fun openConnection(connection: NetworkConnection) {
        _currentConnection.value = connection
        _currentPath.value = ""
        loadFiles("")
    }

    fun navigateInto(folder: NetworkFile) {
        loadFiles(folder.path)
    }

    fun navigateUp() {
        val path = _currentPath.value
        if (path.isEmpty()) {
            closeConnection()
        } else {
            val parent = path.substringBeforeLast('/', "").trimEnd('/')
            loadFiles(parent)
        }
    }

    fun refresh() {
        loadFiles(_currentPath.value)
    }

    fun closeConnection() {
        _currentConnection.value?.let { connection ->
            viewModelScope.launch {
                repository.disconnect(connection)
            }
        }
        _currentConnection.value = null
        _currentPath.value = ""
        _files.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    private fun loadFiles(path: String) {
        val connection = _currentConnection.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                repository.listFiles(connection, path)
                    .onSuccess { fileList ->
                        _currentPath.value = path
                        _files.value = fileList.sortedWith(
                            compareBy<NetworkFile> { !it.isDirectory }.thenBy { it.name.lowercase() },
                        )
                    }
                    .onFailure { e ->
                        _error.value = e.message ?: "Failed to load files"
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load files"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ---------- Playback ----------

    /**
     * Register a seekable HTTP proxy stream for the file and launch the player.
     * Works with both the ExoPlayer and MPV engines since the proxy URL is a
     * plain http:// address that either engine can stream with seeking support.
     */
    fun playFile(file: NetworkFile) {
        val connection = _currentConnection.value ?: return
        viewModelScope.launch {
            try {
                val proxy = NetworkStreamingProxy.getInstance()
                val streamId = "${connection.id}_${System.currentTimeMillis()}"
                val proxyUrl = proxy.registerStream(
                    streamId = streamId,
                    connection = connection,
                    filePath = file.path,
                    fileSize = file.size,
                    mimeType = file.mimeType ?: "video/mp4",
                )
                // Append the encoded filename so the player can show a proper
                // title. The proxy only inspects the first path segment.
                val playerUrl = "$proxyUrl/${Uri.encode(file.name)}"
                PlayerActivity.start(getApplication(), listOf(playerUrl), 0)
            } catch (e: Exception) {
                _error.value = e.message ?: "Error playing video"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.disconnectAll()
        }
        NetworkStreamingProxy.stopInstance()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    NetworkViewModel(application)
                }
            }
    }
}
