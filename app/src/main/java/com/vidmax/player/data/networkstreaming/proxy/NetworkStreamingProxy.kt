package com.vidmax.player.data.networkstreaming.proxy

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.vidmax.player.data.model.NetworkConnection
import com.vidmax.player.data.networkstreaming.clients.FtpClient
import com.vidmax.player.data.networkstreaming.clients.NetworkClient
import com.vidmax.player.data.networkstreaming.clients.NetworkClientFactory
import com.vidmax.player.data.networkstreaming.clients.SmbClient
import com.vidmax.player.data.networkstreaming.clients.WebDavClient
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local HTTP proxy server that enables seeking for network streaming protocols
 * that don't support it natively.
 *
 * The proxy runs on 127.0.0.1 with a random free port, and exposes each stream
 * as `http://127.0.0.1:<port>/<streamId>`. It honours HTTP Range headers so
 * both ExoPlayer and mpv can seek into the file.
 */
class NetworkStreamingProxy private constructor() : NanoHTTPD("127.0.0.1", 0) {

    companion object {
        private const val TAG = "NetworkStreamingProxy"

        @Volatile
        private var instance: NetworkStreamingProxy? = null

        fun getInstance(): NetworkStreamingProxy {
            return instance ?: synchronized(this) {
                instance ?: NetworkStreamingProxy().also {
                    it.start()
                    instance = it
                }
            }
        }

        fun stopInstance() {
            synchronized(this) {
                instance?.let { proxy ->
                    proxy.stop()
                    proxy.cleanup()
                    instance = null
                }
            }
        }
    }

    // Store active connections and their clients
    private val activeStreams = ConcurrentHashMap<String, StreamInfo>()

    data class StreamInfo(
        val connection: NetworkConnection,
        val filePath: String,
        val client: NetworkClient,
        var fileSize: Long = -1L,
        var mimeType: String = "video/mp4",
    )

    /**
     * Register a stream for proxying.
     *
     * @return The local URL to use for playback
     */
    fun registerStream(
        streamId: String,
        connection: NetworkConnection,
        filePath: String,
        fileSize: Long = -1L,
        mimeType: String = "video/mp4",
    ): String {
        val client = NetworkClientFactory.createClient(connection)

        val streamInfo = StreamInfo(
            connection = connection,
            filePath = filePath,
            client = client,
            fileSize = fileSize,
            mimeType = mimeType,
        )

        activeStreams[streamId] = streamInfo

        return "http://127.0.0.1:$listeningPort/$streamId"
    }

    /**
     * Unregister a stream
     */
    fun unregisterStream(streamId: String) {
        activeStreams.remove(streamId)?.let { streamInfo ->
            runBlocking {
                try {
                    streamInfo.client.disconnect()
                } catch (e: Exception) {
                    // Ignore disconnect errors
                }
            }
        }
    }

    /**
     * Cleanup all streams
     */
    private fun cleanup() {
        val streamIds = activeStreams.keys.toList()
        streamIds.forEach { unregisterStream(it) }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Extract stream ID from URI (format: /streamId/optionalFileName)
        val streamId = uri.removePrefix("/").split("/").firstOrNull()
        if (streamId.isNullOrEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")
        }

        val streamInfo = activeStreams[streamId]
        if (streamInfo == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not found")
        }

        // Handle range requests for seeking
        val rangeHeader = session.headers["range"]

        return try {
            val response = if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequest(session, streamInfo, rangeHeader)
            } else {
                handleFullRequest(session, streamInfo)
            }
            Log.d(TAG, "SERVE $uri range=[$rangeHeader] -> ${response.status.requestStatus}")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request for stream $streamId: ${streamInfo.filePath}", e)
            Log.e(
                TAG,
                "Connection: ${streamInfo.connection.protocol} ${streamInfo.connection.host}:${streamInfo.connection.port}${streamInfo.connection.path}",
            )
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}",
            )
        }
    }

    private fun handleRangeRequest(
        session: IHTTPSession,
        streamInfo: StreamInfo,
        rangeHeader: String,
    ): Response {
        // Parse range header: bytes=start-end
        val rangeValue = rangeHeader.removePrefix("bytes=")
        val parts = rangeValue.split("-")
        val start = parts[0].toLongOrNull() ?: 0L
        val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
            parts[1].toLongOrNull()
        } else {
            null
        }

        // Get file size if not known; if the size still can't be determined we
        // cannot answer a range request correctly (invalid Content-Length), so
        // fall through to a chunked full response instead.
        if (streamInfo.fileSize < 0) {
            streamInfo.fileSize = getFileSize(streamInfo)
        }

        if (streamInfo.fileSize <= 0) {
            return handleFullRequest(session, streamInfo)
        }

        val fileSize = streamInfo.fileSize
        val safeStart = start.coerceIn(0L, fileSize - 1)
        val rangeEnd = end?.coerceAtMost(fileSize - 1) ?: (fileSize - 1)
        val contentLength = rangeEnd - safeStart + 1
        if (contentLength <= 0) {
            return newFixedLengthResponse(
                Response.Status.RANGE_NOT_SATISFIABLE,
                MIME_PLAINTEXT,
                "Invalid range",
            )
        }

        // Get stream with offset
        val inputStream = getStreamWithOffset(streamInfo, safeStart, contentLength)

        if (inputStream == null) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Failed to open stream",
            )
        }

        // Create response with partial content
        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            streamInfo.mimeType,
            inputStream,
            contentLength,
        )

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Range", "bytes $safeStart-$rangeEnd/$fileSize")

        return response
    }

    private fun handleFullRequest(
        session: IHTTPSession,
        streamInfo: StreamInfo,
    ): Response {
        // Get file size if not known
        if (streamInfo.fileSize < 0) {
            streamInfo.fileSize = getFileSize(streamInfo)
        }

        val inputStream = getStream(streamInfo)

        if (inputStream == null) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Failed to open stream",
            )
        }

        // When the size is unknown a fixed-length response would send an
        // invalid Content-Length; chunked keeps HTTP/1.1 players happy.
        if (streamInfo.fileSize <= 0) {
            val response = newChunkedResponse(Response.Status.OK, streamInfo.mimeType, inputStream)
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }

        val response = newFixedLengthResponse(
            Response.Status.OK,
            streamInfo.mimeType,
            inputStream,
            streamInfo.fileSize,
        )

        response.addHeader("Accept-Ranges", "bytes")

        return response
    }

    /**
     * SMB2 paths use backslash separators; forward slashes are rejected by
     * most servers (Windows in particular) with OBJECT_NAME_NOT_FOUND.
     */
    private fun toSmbPath(path: String): String = path.trim('/').replace('/', '\\')

    /**
     * Blank credentials must authenticate as guest; a NULL session
     * ([AuthenticationContext.anonymous]) is rejected by most modern NAS/Samba
     * servers even when the share is public.
     */
    private fun resolveAuthContext(connection: NetworkConnection): AuthenticationContext =
        if (connection.isAnonymous ||
            connection.username.isBlank() && connection.password.isBlank()
        ) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(
                connection.username,
                connection.password.toCharArray(),
                "",
            )
        }

    private fun getFileSize(streamInfo: StreamInfo): Long {
        return runBlocking {
            try {
                when (streamInfo.client) {
                    is SmbClient -> {
                        getFileSizeSMB(streamInfo)
                    }

                    is FtpClient -> {
                        getFileSizeFTP(streamInfo)
                    }

                    is WebDavClient -> {
                        val webDavClient = streamInfo.client as WebDavClient
                        if (!webDavClient.isConnected()) {
                            webDavClient.connect().getOrThrow()
                        }
                        val sizeResult = webDavClient.getFileSize(streamInfo.filePath)
                        sizeResult.getOrNull() ?: -1L
                    }

                    else -> {
                        if (!streamInfo.client.isConnected()) {
                            streamInfo.client.connect().getOrThrow()
                        }
                        val ftpClient = streamInfo.client as? FtpClient
                        val sizeResult = ftpClient?.getFileSize(streamInfo.filePath)
                        sizeResult?.getOrNull() ?: -1L
                    }
                }
            } catch (e: Exception) {
                -1L
            }
        }
    }

    /**
     * Get the file size using SMB.
     * Opens a discrete connection, queries the file, and tears the socket down immediately.
     */
    private suspend fun getFileSizeSMB(streamInfo: StreamInfo): Long {
        var smbClient: SMBClient? = null
        var connection: Connection? = null
        var session: Session? = null
        var diskShare: DiskShare? = null
        var file: com.hierynomus.smbj.share.File? = null

        try {
            // Extract the base share name, explicitly rejecting nested paths
            val shareName = streamInfo.connection.path.trim('/')

            if (shareName.isEmpty() || shareName.contains('/')) {
                Log.e(TAG, "SMB: Invalid share name: $shareName")
                return -1L
            }

            // Isolate the relative file path within the share.
            // Expected input format: smb://host/shareName/path/to/file.mkv
            val relativePath = when {
                streamInfo.filePath.startsWith("smb://", ignoreCase = true) -> {
                    val pathAfterProtocol = streamInfo.filePath.substring(6)
                    val firstSlash = pathAfterProtocol.indexOf('/')
                    if (firstSlash == -1) {
                        Log.e(TAG, "Invalid SMB path format")
                        return -1L
                    }

                    val pathAfterHost = pathAfterProtocol.substring(firstSlash + 1)
                    val secondSlash = pathAfterHost.indexOf('/')
                    if (secondSlash == -1) "" else pathAfterHost.substring(secondSlash + 1)
                }
                else -> streamInfo.filePath.trim('/')
            }

            // The path is already raw (it comes straight from the directory
            // listing); only the separator needs converting for SMB2.
            val smbRelativePath = toSmbPath(relativePath)

            val smbConfig = SmbConfig.builder()
                .withTimeout(30000, TimeUnit.MILLISECONDS)
                .withSoTimeout(35000, TimeUnit.MILLISECONDS)
                .build()

            smbClient = SMBClient(smbConfig)

            val authContext = resolveAuthContext(streamInfo.connection)

            connection = smbClient.connect(streamInfo.connection.host, streamInfo.connection.port)
            session = connection.authenticate(authContext)
            diskShare = session.connectShare(shareName) as DiskShare

            file = diskShare.openFile(
                smbRelativePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )

            return file.fileInformation.standardInformation.endOfFile

        } catch (e: Exception) {
            Log.e(TAG, "SMB getFileSize error: ${e.message}", e)
            return -1L
        } finally {
            runCatching { file?.close() }
            runCatching { diskShare?.close() }
            runCatching { session?.close() }
            runCatching { connection?.close() }
            runCatching { smbClient?.close() }
        }
    }

    /**
     * Get file size using FTP listFiles command
     */
    private suspend fun getFileSizeFTP(streamInfo: StreamInfo): Long {
        val ftpClient = org.apache.commons.net.ftp.FTPClient()

        // Set UTF-8 encoding for proper handling of non-English characters
        ftpClient.controlEncoding = "UTF-8"
        ftpClient.setConnectTimeout(10000)

        try {
            // Connect
            ftpClient.connect(streamInfo.connection.host, streamInfo.connection.port)

            if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(ftpClient.replyCode)) {
                ftpClient.disconnect()
                return -1L
            }

            // Login
            val loginSuccess = if (streamInfo.connection.isAnonymous) {
                ftpClient.login("anonymous", "")
            } else {
                ftpClient.login(streamInfo.connection.username, streamInfo.connection.password)
            }

            if (!loginSuccess) {
                ftpClient.disconnect()
                return -1L
            }

            // Set binary mode
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)

            // Try to enable UTF-8 mode on the server (RFC 2640)
            try {
                ftpClient.sendCommand("OPTS UTF8 ON")
            } catch (_: Exception) {
                // Server may not support UTF-8 mode, continue anyway
            }

            // Change to base directory if needed
            if (streamInfo.connection.path != "/" && streamInfo.connection.path.isNotEmpty()) {
                ftpClient.changeWorkingDirectory(streamInfo.connection.path)
            }

            // Determine the file path to use
            val pathsToTry = mutableListOf<String>()
            pathsToTry.add(streamInfo.filePath)
            if (streamInfo.filePath.startsWith("/")) {
                pathsToTry.add(streamInfo.filePath.substring(1))
            }
            if (streamInfo.connection.path != "/" && streamInfo.connection.path.isNotEmpty() &&
                streamInfo.filePath.startsWith(streamInfo.connection.path)
            ) {
                val relativePath = streamInfo.filePath.substring(streamInfo.connection.path.length).trimStart('/')
                if (relativePath.isNotEmpty()) {
                    pathsToTry.add(relativePath)
                }
            }

            // Try to get file size using listFiles
            for (path in pathsToTry) {
                try {
                    val files = ftpClient.listFiles(path)
                    if (files.isNotEmpty() && !files[0].isDirectory) {
                        val size = files[0].size
                        ftpClient.disconnect()
                        return size
                    }
                } catch (e: Exception) {
                    // Try next path
                }
            }

            ftpClient.disconnect()
            return -1L

        } catch (e: Exception) {
            try {
                ftpClient.disconnect()
            } catch (_: Exception) {
            }
            return -1L
        }
    }

    private fun getStream(streamInfo: StreamInfo): InputStream? {
        return runBlocking {
            try {
                // FTP needs a dedicated control connection per response so
                // concurrent/overlapping player requests never corrupt the
                // shared client's control channel.
                if (streamInfo.client is FtpClient) {
                    return@runBlocking getStreamWithOffsetFTP(streamInfo, 0)
                }

                // Connect if needed
                if (!streamInfo.client.isConnected()) {
                    streamInfo.client.connect().getOrThrow()
                }

                // Get file stream
                val result = streamInfo.client.getFileStream(streamInfo.filePath)
                result.getOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting stream", e)
                null
            }
        }
    }

    private fun getStreamWithOffset(
        streamInfo: StreamInfo,
        offset: Long,
        contentLength: Long,
    ): InputStream? {
        return runBlocking {
            try {
                when (streamInfo.client) {
                    is SmbClient -> {
                        getStreamWithOffsetSMB(streamInfo, offset, contentLength)
                    }

                    is FtpClient -> {
                        getStreamWithOffsetFTP(streamInfo, offset)
                    }

                    is WebDavClient -> {
                        getStreamWithOffsetWebDAV(streamInfo, offset)
                    }

                    else -> {
                        getStreamWithOffsetGeneric(streamInfo, offset)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Range stream failed for ${streamInfo.filePath} @ $offset: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Get FTP stream with offset using REST command (efficient seeking)
     */
    private suspend fun getStreamWithOffsetFTP(streamInfo: StreamInfo, offset: Long): InputStream? {
        // Create a new FTP client for this specific range request
        val ftpClient = org.apache.commons.net.ftp.FTPClient()

        // Set UTF-8 encoding for proper handling of non-English characters
        ftpClient.controlEncoding = "UTF-8"
        ftpClient.setConnectTimeout(10000)
        ftpClient.setDataTimeout(30000)
        ftpClient.controlKeepAliveTimeout = 300

        try {
            // Connect
            ftpClient.connect(streamInfo.connection.host, streamInfo.connection.port)

            if (!org.apache.commons.net.ftp.FTPReply.isPositiveCompletion(ftpClient.replyCode)) {
                ftpClient.disconnect()
                return null
            }

            // Login
            val loginSuccess = if (streamInfo.connection.isAnonymous) {
                ftpClient.login("anonymous", "")
            } else {
                ftpClient.login(streamInfo.connection.username, streamInfo.connection.password)
            }

            if (!loginSuccess) {
                ftpClient.disconnect()
                return null
            }

            // Set binary mode and passive mode
            ftpClient.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            ftpClient.enterLocalPassiveMode()

            // Try to enable UTF-8 mode on the server (RFC 2640)
            try {
                ftpClient.sendCommand("OPTS UTF8 ON")
            } catch (_: Exception) {
                // Server may not support UTF-8 mode, continue anyway
            }

            ftpClient.setBufferSize(1024 * 64)

            // Change to base directory if needed
            if (streamInfo.connection.path != "/" && streamInfo.connection.path.isNotEmpty()) {
                ftpClient.changeWorkingDirectory(streamInfo.connection.path)
            }

            // Set restart position (offset) - this is the key for efficient seeking!
            if (offset > 0) {
                ftpClient.setRestartOffset(offset)
            }

            // Determine the file path to use
            val pathsToTry = mutableListOf<String>()
            pathsToTry.add(streamInfo.filePath)
            if (streamInfo.filePath.startsWith("/")) {
                pathsToTry.add(streamInfo.filePath.substring(1))
            }
            if (streamInfo.connection.path != "/" && streamInfo.connection.path.isNotEmpty() &&
                streamInfo.filePath.startsWith(streamInfo.connection.path)
            ) {
                val relativePath = streamInfo.filePath.substring(streamInfo.connection.path.length).trimStart('/')
                if (relativePath.isNotEmpty()) {
                    pathsToTry.add(relativePath)
                }
            }

            // Try to retrieve file stream
            var rawStream: java.io.InputStream? = null
            for (path in pathsToTry) {
                rawStream = ftpClient.retrieveFileStream(path)
                if (rawStream != null) {
                    break
                }
            }

            if (rawStream == null) {
                ftpClient.disconnect()
                return null
            }

            // Wrap stream to handle cleanup
            val wrappedStream = object : java.io.InputStream() {
                override fun read(): Int = rawStream.read()
                override fun read(b: ByteArray): Int = rawStream.read(b)
                override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)
                override fun available(): Int = rawStream.available()

                override fun close() {
                    try {
                        rawStream.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    try {
                        if (ftpClient.isConnected) {
                            ftpClient.completePendingCommand()
                            ftpClient.logout()
                            ftpClient.disconnect()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // Buffer the socket stream so NanoHTTPD's 16 KiB response reads are
            // served from memory instead of one syscall per chunk.
            return BufferedInputStream(wrappedStream, 1024 * 1024)

        } catch (e: Exception) {
            try {
                ftpClient.disconnect()
            } catch (_: Exception) {
            }
            return null
        }
    }

    /**
     * Get WebDAV stream with offset using HTTP Range header (efficient seeking)
     */
    private suspend fun getStreamWithOffsetWebDAV(streamInfo: StreamInfo, offset: Long): InputStream? {
        try {
            val protocol = if (streamInfo.connection.useHttps) "https" else "http"
            val cleanBasePath = streamInfo.connection.path.trim('/')
            val cleanFilePath = if (streamInfo.filePath.startsWith("/")) streamInfo.filePath else "/${streamInfo.filePath}"

            // Percent-encode each path segment; OkHttp rejects raw URLs that
            // contain spaces or non-ASCII characters (Bangla filenames etc.).
            val encodedPath =
                listOf(cleanBasePath, cleanFilePath.trim('/'))
                    .filter { it.isNotEmpty() }
                    .joinToString("/") { path ->
                        path.split('/').joinToString("/") { android.net.Uri.encode(it) }
                    }
            val url = "$protocol://${streamInfo.connection.host}:${streamInfo.connection.port}/$encodedPath"

            // Use OkHttp directly to add Range header support
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Range", "bytes=$offset-")
                        .build()
                    chain.proceed(request)
                }
                .build()

            // Build the request
            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .get()

            // Add auth if needed
            if (!streamInfo.connection.isAnonymous) {
                val credentials = okhttp3.Credentials.basic(streamInfo.connection.username, streamInfo.connection.password)
                requestBuilder.addHeader("Authorization", credentials)
            }

            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful && response.code != 206) {
                response.close()
                return null
            }

            val rawStream = response.body?.byteStream()
            if (rawStream == null) {
                response.close()
                return null
            }

            // Wrap stream to handle cleanup
            val wrappedStream = object : java.io.InputStream() {
                override fun read(): Int = rawStream.read()
                override fun read(b: ByteArray): Int = rawStream.read(b)
                override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)
                override fun available(): Int = rawStream.available()

                override fun close() {
                    try {
                        rawStream.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    try {
                        response.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // Buffer the response stream so NanoHTTPD's 16 KiB response reads are
            // served from memory instead of one network read per chunk.
            return BufferedInputStream(wrappedStream, 1024 * 1024)

        } catch (e: Exception) {
            Log.e(TAG, "WebDAV range stream failed for ${streamInfo.filePath}: ${e.message}", e)
            return null
        }
    }

    /**
     * Generates a seekable InputStream for SMB files.
     *
     * Reuses the SmbClient's shared session so each new HTTP range request
     * (i.e. every seek) no longer pays for a full connection setup (TCP +
     * negotiate + NTLM auth + tree connect); only a fresh tree connection and
     * file handle are opened per request, and the stream's close() tears those
     * down.
     */
    private suspend fun getStreamWithOffsetSMB(
        streamInfo: StreamInfo,
        offset: Long,
        contentLength: Long,
    ): InputStream? {
        val smbClient = streamInfo.client as? SmbClient ?: return null

        try {
            // Isolate the relative file path within the share
            val relativePath = when {
                streamInfo.filePath.startsWith("smb://", ignoreCase = true) -> {
                    val pathAfterProtocol = streamInfo.filePath.substring(6)
                    val firstSlash = pathAfterProtocol.indexOf('/')
                    if (firstSlash == -1) {
                        Log.e(TAG, "Invalid SMB path format")
                        return null
                    }

                    val pathAfterHost = pathAfterProtocol.substring(firstSlash + 1)
                    val secondSlash = pathAfterHost.indexOf('/')
                    if (secondSlash == -1) "" else pathAfterHost.substring(secondSlash + 1)
                }
                else -> streamInfo.filePath.trim('/')
            }

            // SMB2 requires backslash separators; forward slashes are rejected
            // by most servers with OBJECT_NAME_NOT_FOUND.
            val smbRelativePath = toSmbPath(relativePath)

            // Reuse the shared session (with auto-reconnect) instead of building a
            // whole new SMB connection per range request.
            return smbClient.withSharedSession { session, shareName ->
                val diskShare = session.connectShare(shareName) as DiskShare
                try {
                    val file = diskShare.openFile(
                        smbRelativePath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    )

                    PrefetchingSmbInputStream(
                        fileHandle = file,
                        diskShare = diskShare,
                        initialOffset = offset,
                        contentLength = contentLength,
                    )
                } catch (e: Exception) {
                    runCatching { diskShare.close() }
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMB getStreamWithOffset error: ${e.message}", e)
            return null
        }
    }

    /**
     * Generic stream with offset using skip (less efficient, for other protocols)
     */
    private suspend fun getStreamWithOffsetGeneric(streamInfo: StreamInfo, offset: Long): InputStream? {
        val client = NetworkClientFactory.createClient(streamInfo.connection)
        client.connect().getOrThrow()

        val stream = client.getFileStream(streamInfo.filePath).getOrNull()

        if (stream != null && offset > 0) {
            var remaining = offset
            val buffer = ByteArray(8192)

            while (remaining > 0) {
                val toSkip = minOf(remaining, buffer.size.toLong()).toInt()
                val skipped = stream.read(buffer, 0, toSkip)
                if (skipped <= 0) {
                    stream.close()
                    client.disconnect()
                    return null
                }
                remaining -= skipped
            }
        }

        return stream
    }

    /**
     * Seekable SMB stream with an internal prefetching double-buffer.
     *
     * WHY: NanoHTTPD 2.3.1 streams response bodies by reading the wrapped
     * InputStream in hard-coded 16 KiB chunks (Response.sendBody), and with a
     * non-buffered SMB stream each chunk becomes one synchronous SMB2 READ
     * round-trip. On Wi-Fi (3-5 ms RTT) that caps throughput at roughly
     * 16 KiB / RTT, no matter how much bandwidth is available.
     *
     * This stream decouples the consumer's read granularity from the SMB read
     * granularity: a dedicated daemon thread issues large (1 MiB) SMB2 reads
     * ahead of the consumer and pushes them into a small block queue.
     *
     * Only the file handle and this stream's own tree connection are closed by
     * [close]; the underlying session/connection are shared via
     * [SmbClient.withSharedSession] and owned by the client.
     */
    private class PrefetchingSmbInputStream(
        private val fileHandle: com.hierynomus.smbj.share.File,
        private val diskShare: DiskShare,
        initialOffset: Long,
        private val contentLength: Long,
    ) : InputStream() {

        companion object {
            private const val TAG = "NetworkStreamingProxy"

            /** Size of each SMB2 read issued by the prefetch thread. */
            private const val BLOCK_SIZE = 1024 * 1024

            /** Blocks buffered ahead of the consumer (double buffering). */
            private const val PREFETCH_DEPTH = 2

            /** Sentinel pushed by the prefetch thread to signal EOF / error. */
            private val EOF_MARKER = ByteArray(0)
        }

        /** Block queue; [EOF_MARKER] signals EOF / error (null is not allowed). */
        private val queue = ArrayBlockingQueue<ByteArray>(PREFETCH_DEPTH)

        private var currentBlock: ByteArray? = null
        private var blockPos = 0
        private var eof = false
        private val closed = AtomicBoolean(false)

        /** Prefetch thread's read cursor; only touched by the prefetch thread. */
        private var readPosition = initialOffset

        /** Total bytes read from the file; only touched by the prefetch thread. */
        private var bytesReadFromFile = 0L

        private val prefetchThread =
            Thread({ prefetchLoop() }, "SmbPrefetch-${System.identityHashCode(this)}").apply {
                isDaemon = true
                start()
            }

        private fun prefetchLoop() {
            try {
                while (!closed.get()) {
                    val remaining = contentLength - bytesReadFromFile
                    if (remaining <= 0) break
                    val requestLen = minOf(BLOCK_SIZE.toLong(), remaining).toInt()
                    val block = ByteArray(requestLen)
                    val n = fileHandle.read(block, readPosition, 0, requestLen)
                    if (n <= 0) break // EOF or error
                    readPosition += n
                    bytesReadFromFile += n
                    // Full-block reads are handed over as-is; short reads (last block,
                    // or a server that returned less than requested) are trimmed.
                    queue.put(if (n == block.size) block else block.copyOf(n))
                }
                // Signal EOF to the consumer. offer() is used so a consumer that
                // already went away (close()) can never leave us blocked here.
                if (!closed.get()) {
                    runCatching { queue.put(EOF_MARKER) }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.e(TAG, "SMB prefetch error: ${e.message}", e)
                runCatching { queue.offer(EOF_MARKER) }
            }
        }

        override fun read(): Int {
            val buf = ByteArray(1)
            val n = read(buf, 0, 1)
            return if (n == 1) buf[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray): Int = read(b, 0, b.size)

        @Synchronized
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed.get()) return -1
            if (len == 0) return 0

            // Ensure we have a current block to serve from.
            while (currentBlock == null) {
                if (eof) return -1
                val block =
                    try {
                        queue.take()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return -1
                    }
                if (block === EOF_MARKER) {
                    eof = true
                    return -1
                }
                currentBlock = block
                blockPos = 0
            }

            val block = currentBlock!!
            val n = minOf(len, block.size - blockPos)
            System.arraycopy(block, blockPos, b, off, n)
            blockPos += n
            if (blockPos >= block.size) {
                currentBlock = null
            }
            return n
        }

        override fun available(): Int {
            if (closed.get() || eof) return 0
            val inCurrent = currentBlock?.let { it.size - blockPos } ?: 0
            return inCurrent + queue.size * BLOCK_SIZE
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                // Wake the prefetch thread if it is blocked in queue.put().
                prefetchThread.interrupt()
                // Wake a consumer blocked in queue.take().
                queue.drainTo(ArrayList())
                queue.offer(EOF_MARKER)
                // Only the file handle and this stream's own tree connection are
                // owned here; the shared session/connection belong to SmbClient and
                // must not be closed from this stream.
                runCatching { fileHandle.close() }
                runCatching { diskShare.close() }
            }
        }
    }
}
