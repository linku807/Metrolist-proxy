package com.metrolist.shared.network

import com.metrolist.shared.settings.Settings
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

private class MetrolistProxySelector : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        requireNotNull(uri) { "URI must not be null" }

        val settings = Settings.shared
        if (!settings.networkProxyEnabled) return listOf(Proxy.NO_PROXY)

        val host = settings.networkProxyHost.trim()
        val port = settings.networkProxyPort.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) {
            // An enabled but invalid proxy must not silently fall back to a direct connection.
            return emptyList()
        }

        val type =
            when (settings.networkProxyType.uppercase()) {
                "HTTP" -> Proxy.Type.HTTP
                "SOCKS", "SOCKS5" -> Proxy.Type.SOCKS
                else -> return emptyList()
            }

        return listOf(Proxy(type, InetSocketAddress.createUnresolved(host, port)))
    }

    override fun connectFailed(
        uri: URI?,
        sa: SocketAddress?,
        ioe: java.io.IOException?,
    ) {
        // Do not add a direct-connection fallback: proxy failure should remain a failed request.
    }
}

internal actual fun apiOkHttpClient(): OkHttpClient =
    sharedOkHttpClient
        .newBuilder()
        .proxySelector(MetrolistProxySelector())
        .cache(Cache(File(System.getProperty("java.io.tmpdir"), "http_cache"), 50L * 1024L * 1024L))
        .build()
