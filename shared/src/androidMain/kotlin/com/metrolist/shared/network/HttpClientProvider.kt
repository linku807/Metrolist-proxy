package com.metrolist.shared.network

import com.metrolist.shared.settings.Settings
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

private class MetrolistProxySelector(
    private val enabled: Boolean,
    private val proxyType: String,
    private val proxyHost: String,
    private val proxyPort: String,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        requireNotNull(uri) { "URI must not be null" }
        if (!enabled) return listOf(Proxy.NO_PROXY)

        val host = proxyHost.trim()
        val port = proxyPort.toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) {
            // An enabled but invalid proxy must not silently fall back to a direct connection.
            return emptyList()
        }

        val type =
            when (proxyType.uppercase()) {
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

internal actual fun apiOkHttpClient(): OkHttpClient {
    val settings = Settings.shared
    val useHttpProxyAuth =
        settings.networkProxyEnabled &&
            settings.networkProxyType.equals("HTTP", ignoreCase = true) &&
            settings.networkProxyUsername.isNotBlank()
    val proxyAuthenticator =
        if (useHttpProxyAuth) {
            Authenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) {
                    null
                } else {
                    response.request
                        .newBuilder()
                        .header(
                            "Proxy-Authorization",
                            Credentials.basic(settings.networkProxyUsername, settings.networkProxyPassword),
                        )
                        .build()
                }
            }
        } else {
            Authenticator.NONE
        }

    return sharedOkHttpClient
        .newBuilder()
        .proxySelector(
            MetrolistProxySelector(
                enabled = settings.networkProxyEnabled,
                proxyType = settings.networkProxyType,
                proxyHost = settings.networkProxyHost,
                proxyPort = settings.networkProxyPort,
            ),
        )
        .proxyAuthenticator(proxyAuthenticator)
        .cache(Cache(File(System.getProperty("java.io.tmpdir"), "http_cache"), 50L * 1024L * 1024L))
        .build()
}
