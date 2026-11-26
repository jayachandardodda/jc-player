package com.jc.player.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.jc.player.JcApp
import com.jc.player.data.SmbServer
import java.io.Closeable
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** An open connection to one SMB share. */
class SmbShareConnection private constructor(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    val share: DiskShare,
) : Closeable {

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    companion object {
        private val config: SmbConfig = SmbConfig.builder()
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            // Android's built-in crypto lacks MD4, which NTLM logins need; use BouncyCastle directly.
            .withSecurityProvider(BCSecurityProvider())
            .build()

        fun open(host: String, port: Int, shareName: String, server: SmbServer?): SmbShareConnection {
            val client = SMBClient(config)
            try {
                val conn = if (port > 0) client.connect(host, port) else client.connect(host)
                val auth = if (server == null || server.user.isBlank()) AuthenticationContext.guest()
                else AuthenticationContext(server.user, server.password.toCharArray(), server.domain.ifBlank { null })
                val session = conn.authenticate(auth)
                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw IOException("\"$shareName\" is not a file share")
                return SmbShareConnection(client, conn, session, share)
            } catch (e: Exception) {
                runCatching { client.close() }
                throw if (e is IOException) e else IOException(e.message ?: "SMB connection failed", e)
            }
        }

        fun open(server: SmbServer): SmbShareConnection {
            val (host, port) = splitHostPort(server.host)
            return open(host, port, server.share, server)
        }

        fun splitHostPort(raw: String): Pair<String, Int> {
            val h = raw.removePrefix("smb://").trim('/')
            val idx = h.lastIndexOf(':')
            return if (idx > 0 && h.substring(idx + 1).all(Char::isDigit))
                h.substring(0, idx) to h.substring(idx + 1).toInt()
            else h to -1
        }
    }
}

data class SmbEntry(val name: String, val isDirectory: Boolean, val size: Long)

object SmbBrowser {
    fun list(server: SmbServer, dir: String): List<SmbEntry> =
        SmbShareConnection.open(server).use { c ->
            c.share.list(dir.replace('/', '\\'))
                .filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }
                .map {
                    val isDir = (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                    SmbEntry(it.fileName, isDir, it.endOfFile)
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

    fun uriFor(server: SmbServer, path: String): Uri {
        val segments = (listOf(server.share) + path.split('/', '\\').filter { it.isNotEmpty() })
        val builder = Uri.Builder().scheme("smb").encodedAuthority(server.host.removePrefix("smb://").trim('/'))
        segments.forEach { builder.appendPath(it) }
        return builder.build()
    }
}

/**
 * Media3 data source that streams a file straight from an SMB share, with a 1 MB
 * read-ahead buffer so playback doesn't make a network round trip per small read.
 */
class SmbDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var conn: SmbShareConnection? = null
    private var file: File? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false

    private val buffer = ByteArray(1 shl 20)
    private var bufferStart = 0L
    private var bufferLength = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val u = dataSpec.uri
        val segments = u.pathSegments
        if (u.host.isNullOrEmpty() || segments.isEmpty()) throw IOException("Bad SMB address: $u")
        val shareName = segments[0]
        val path = segments.drop(1).joinToString("\\")
        val server = JcApp.instance.settings.smbServerFor(u.authority ?: u.host!!, shareName)
            ?: JcApp.instance.settings.smbServerFor(u.host!!, shareName)
        try {
            val c = SmbShareConnection.open(u.host!!, u.port, shareName, server)
            conn = c
            val f = c.share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            file = f
            val length = f.fileInformation.standardInformation.endOfFile
            position = dataSpec.position
            if (position > length) throw IOException("Seek past end of file")
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else length - position
            bufferLength = 0
        } catch (e: Exception) {
            closeQuietly()
            throw if (e is IOException) e else IOException(e.message ?: "SMB open failed", e)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val f = file ?: throw IOException("not opened")

        if (position < bufferStart || position >= bufferStart + bufferLength) {
            val toFill = minOf(buffer.size.toLong(), bytesRemaining).toInt()
            val n = try { f.read(buffer, position, 0, toFill) } catch (e: Exception) {
                throw IOException(e.message ?: "SMB read failed", e)
            }
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufferStart = position
            bufferLength = n
        }
        val inBuffer = (bufferStart + bufferLength - position).toInt()
        val count = minOf(length, inBuffer, minOf(bytesRemaining, Int.MAX_VALUE.toLong()).toInt())
        System.arraycopy(buffer, (position - bufferStart).toInt(), target, offset, count)
        position += count
        bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeQuietly()
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeQuietly() {
        runCatching { file?.close() }
        file = null
        runCatching { conn?.close() }
        conn = null
    }
}
