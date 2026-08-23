package com.vidmax.player.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.vidmax.player.data.model.ConnectionStatus
import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.model.NetworkFile
import com.vidmax.player.data.networkstreaming.clients.NetworkClient
import com.vidmax.player.data.networkstreaming.clients.NetworkClientFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository for managing network connections and file browsing.
 *
 * Connections are persisted in SharedPreferences as a JSON array, since the
 * app does not use Room for this feature.
 */
class NetworkRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("network_prefs", Context.MODE_PRIVATE)

    // Active network clients (connectionId -> client)
    private val activeClients = mutableMapOf<Long, NetworkClient>()

    // Connection statuses
    private val _connectionStatuses = MutableStateFlow<Map<Long, ConnectionStatus>>(emptyMap())
    val connectionStatuses: StateFlow<Map<Long, ConnectionStatus>> = _connectionStatuses.asStateFlow()

    /**
     * Get all saved connections
     */
    fun getAllConnections(): List<NetworkConnection> {
        val raw = prefs.getString(KEY_CONNECTIONS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                NetworkConnection.fromJson(array.getJSONObject(index))
            }.sortedByDescending { it.lastConnected }
        }.getOrDefault(emptyList())
    }

    /**
     * Get a connection by ID
     */
    fun getConnectionById(id: Long): NetworkConnection? =
        getAllConnections().firstOrNull { it.id == id }

    /**
     * Add a new connection
     */
    fun addConnection(connection: NetworkConnection): Long {
        val newId = if (connection.id == 0L) System.currentTimeMillis() else connection.id
        val newConnection = connection.copy(id = newId)
        val connections = getAllConnections().toMutableList()
        connections.add(newConnection)
        saveConnections(connections)
        return newId
    }

    /**
     * Update an existing connection
     */
    fun updateConnection(connection: NetworkConnection) {
        val connections = getAllConnections().toMutableList()
        val index = connections.indexOfFirst { it.id == connection.id }
        if (index >= 0) {
            connections[index] = connection
            saveConnections(connections)
        }

        // Disconnect and remove cached client if it exists so the next
        // connection attempt uses the updated credentials.
        activeClients[connection.id]?.let { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            activeClients.remove(connection.id)
            updateConnectionStatus(
                connection.id,
                ConnectionStatus(connectionId = connection.id, isConnected = false),
            )
        }
    }

    /**
     * Delete a connection
     */
    fun deleteConnection(connection: NetworkConnection) {
        val connections = getAllConnections().toMutableList()
        connections.removeAll { it.id == connection.id }
        saveConnections(connections)

        _connectionStatuses.value -= connection.id
        activeClients[connection.id]?.let { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
            activeClients.remove(connection.id)
        }
    }

    /**
     * Connect to a network share
     */
    suspend fun connect(connection: NetworkConnection): Result<Unit> {
        updateConnectionStatus(
            connection.id,
            ConnectionStatus(connectionId = connection.id, isConnecting = true),
        )

        return try {
            val client = NetworkClientFactory.createClient(connection)
            client.connect().onSuccess {
                activeClients[connection.id] = client

                // Update last connected time
                val updated = connection.copy(lastConnected = System.currentTimeMillis())
                val connections = getAllConnections().toMutableList()
                val index = connections.indexOfFirst { it.id == connection.id }
                if (index >= 0) connections[index] = updated
                saveConnections(connections)

                updateConnectionStatus(
                    connection.id,
                    ConnectionStatus(connectionId = connection.id, isConnected = true),
                )
            }.onFailure { e ->
                updateConnectionStatus(
                    connection.id,
                    ConnectionStatus(
                        connectionId = connection.id,
                        isConnected = false,
                        isConnecting = false,
                        error = e.message ?: "Connection failed",
                    ),
                )
                throw e
            }
            Result.success(Unit)
        } catch (e: Exception) {
            updateConnectionStatus(
                connection.id,
                ConnectionStatus(
                    connectionId = connection.id,
                    isConnected = false,
                    isConnecting = false,
                    error = e.message ?: "Connection failed",
                ),
            )
            Result.failure(e)
        }
    }

    /**
     * Disconnect from a network share
     */
    suspend fun disconnect(connection: NetworkConnection): Result<Unit> =
        try {
            activeClients[connection.id]?.let { client ->
                client.disconnect()
                activeClients.remove(connection.id)
            }

            updateConnectionStatus(
                connection.id,
                ConnectionStatus(connectionId = connection.id, isConnected = false),
            )
            Result.success(Unit)
        } catch (e: Exception) {
            updateConnectionStatus(
                connection.id,
                ConnectionStatus(
                    connectionId = connection.id,
                    isConnected = false,
                    error = e.message,
                ),
            )
            Result.failure(e)
        }

    /**
     * List files in a directory on a network share
     */
    suspend fun listFiles(
        connection: NetworkConnection,
        path: String,
    ): Result<List<NetworkFile>> =
        try {
            val existingClient = activeClients[connection.id]

            val client = if (existingClient == null) {
                NetworkClientFactory.createClient(connection).also { newClient ->
                    newClient.connect().getOrThrow()
                    activeClients[connection.id] = newClient
                }
            } else {
                existingClient
            }

            client.listFiles(path)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Disconnect all active connections
     */
    suspend fun disconnectAll() {
        activeClients.values.forEach { client ->
            try {
                client.disconnect()
            } catch (e: Exception) {
                // Ignore errors during cleanup
            }
        }
        activeClients.clear()
        _connectionStatuses.value = emptyMap()
    }

    private fun saveConnections(connections: List<NetworkConnection>) {
        val array = JSONArray()
        connections.forEach { connection ->
            array.put(connection.toJson() as JSONObject)
        }
        prefs.edit().putString(KEY_CONNECTIONS, array.toString()).apply()
    }

    private fun updateConnectionStatus(connectionId: Long, status: ConnectionStatus) {
        _connectionStatuses.value += (connectionId to status)
    }

    companion object {
        private const val KEY_CONNECTIONS = "connections"
    }
}
