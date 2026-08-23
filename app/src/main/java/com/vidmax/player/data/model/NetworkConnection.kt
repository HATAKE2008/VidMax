package com.vidmax.player.data.model

import org.json.JSONObject

/**
 * Represents a network connection configuration (SMB / FTP / WebDAV)
 */
data class NetworkConnection(
    val id: Long = 0,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val path: String = "/",
    val isAnonymous: Boolean = false,
    val lastConnected: Long = 0,
    val autoConnect: Boolean = false,
    val useHttps: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("protocol", protocol.name)
        put("host", host)
        put("port", port)
        put("username", username)
        put("password", password)
        put("path", path)
        put("isAnonymous", isAnonymous)
        put("lastConnected", lastConnected)
        put("autoConnect", autoConnect)
        put("useHttps", useHttps)
    }

    companion object {
        fun fromJson(json: JSONObject): NetworkConnection = NetworkConnection(
            id = json.optLong("id", 0),
            name = json.optString("name", "Server"),
            protocol = runCatching {
                NetworkProtocol.valueOf(json.optString("protocol", NetworkProtocol.SMB.name))
            }.getOrDefault(NetworkProtocol.SMB),
            host = json.optString("host", ""),
            port = json.optInt("port", 0),
            username = json.optString("username", ""),
            password = json.optString("password", ""),
            path = json.optString("path", "/"),
            isAnonymous = json.optBoolean("isAnonymous", false),
            lastConnected = json.optLong("lastConnected", 0),
            autoConnect = json.optBoolean("autoConnect", false),
            useHttps = json.optBoolean("useHttps", false),
        )
    }
}

/**
 * Supported network protocols
 */
enum class NetworkProtocol(val displayName: String, val defaultPort: Int) {
    SMB("SMB", 445),
    FTP("FTP", 21),
    WEBDAV("WebDAV", 80),
}

/**
 * Runtime status of a network connection
 */
data class ConnectionStatus(
    val connectionId: Long,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
)
