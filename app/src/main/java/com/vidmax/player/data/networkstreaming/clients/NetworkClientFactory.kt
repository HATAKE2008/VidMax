package com.vidmax.player.data.networkstreaming.clients

import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.model.NetworkProtocol

object NetworkClientFactory {
    fun createClient(connection: NetworkConnection): NetworkClient =
        when (connection.protocol) {
            NetworkProtocol.SMB -> SmbClient(connection)
            NetworkProtocol.FTP -> FtpClient(connection)
            NetworkProtocol.WEBDAV -> WebDavClient(connection)
        }
}
