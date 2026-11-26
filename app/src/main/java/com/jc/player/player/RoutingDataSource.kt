package com.jc.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/** Sends smb:// uris to [SmbDataSource] and everything else to Media3's default source. */
class RoutingDataSource(context: Context) : DataSource {

    private val default: DataSource = DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory()
            .setUserAgent("JCPlayer/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000),
    ).createDataSource()

    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme.equals("smb", true)) {
            SmbDataSource().also { s -> listeners.forEach(s::addTransferListener) }
        } else default
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: throw IllegalStateException("not opened")

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try { active?.close() } finally { active = null }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(context.applicationContext)
    }
}
