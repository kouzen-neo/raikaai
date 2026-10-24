package com.raikaai.app.util

import android.content.Context
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object NetworkUtils {

    /**
     * Shared OkHttpClient factory for the entire application.
     * Different use cases require different timeout configurations,
     * so this provides pre-configured clients for common scenarios.
     */
    object HttpClient {
        /** Short-timeout client for web search and quick API calls (3.5s timeout). */
        val searchClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3500, TimeUnit.MILLISECONDS)
                .readTimeout(3500, TimeUnit.MILLISECONDS)
                .writeTimeout(3500, TimeUnit.MILLISECONDS)
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .build()
        }

        /** General client for REST APIs (15s connect, 20s read/write). */
        val apiClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .build()
        }

        /** Long-timeout client for remote AI streaming (120s read timeout). */
        val streamingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        /** No-auto-redirect variant of [searchClient] for SSRF-safe manual hops. */
        val noRedirectClient: OkHttpClient by lazy {
            searchClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
        }
    }

    /**
     * Parses [url], requiring an http(s) scheme and a publicly-routable host.
     * Hosts resolving to loopback / link-local / site-local / multicast /
     * any-local IPs are rejected, so user-supplied URLs can never reach the
     * intranet, the device itself, or cloud metadata endpoints.
     *
     * Performs blocking DNS resolution — call only from a background thread.
     */
    fun requirePublicHttpUrl(url: String): okhttp3.HttpUrl {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw IllegalArgumentException("Only http(s) URLs are allowed")
        }
        assertPublicHost(httpUrl.host)
        return httpUrl
    }

    /** Rejects hosts that resolve to non-public IPs (SSRF guard). */
    fun assertPublicHost(host: String) {
        val addresses =
            try {
                InetAddress.getAllByName(host)
            } catch (e: UnknownHostException) {
                throw IllegalArgumentException("Cannot resolve host: $host")
            }
        for (addr in addresses) {
            if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
                addr.isMulticastAddress || addr.isAnyLocalAddress
            ) {
                throw IllegalArgumentException("Host resolves to a non-public IP address: $host")
            }
        }
    }

    /**
     * Fetches a URL body as text, following up to [maxHops] redirects manually
     * and re-validating every hop with [requirePublicHttpUrl] (a redirect can
     * neither downgrade the scheme silently nor bounce to an internal address).
     * Returns null on any failure. Blocking — call only from a background thread.
     */
    fun fetchBodyStringValidated(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxChars: Int = 900,
        maxHops: Int = 5,
    ): String? {
        var current = requirePublicHttpUrl(url)
        val client = HttpClient.noRedirectClient
        repeat(maxHops + 1) {
            val builder = Request.Builder().url(current)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.string()?.take(maxChars)?.takeIf { it.isNotBlank() }
                }
                val location = response.header("Location") ?: return null
                val next = current.resolve(location) ?: return null
                current = requirePublicHttpUrl(next.toString())
            }
        }
        return null
    }
    /**
     * True for RFC 1918 private LAN addresses. Note that 172.0.0.0–172.15.x.x
     * and 172.32.0.0–172.255.x.x are public ranges, so a bare "172." prefix
     * check would wrongly classify them as LAN addresses.
     */
    private fun isPrivateLanAddress(host: String): Boolean {
        if (host.startsWith("192.168.") || host.startsWith("10.")) return true
        if (host.startsWith("172.")) {
            val second = host.substringAfter("172.").substringBefore(".").toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    /**
     * Attempts to resolve the active local IPv4 address (Wi-Fi, Ethernet, Hotspot, or LAN).
     * Returns null if no suitable LAN interface is found.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()

            for (nIf in interfaces) {
                if (nIf.isLoopback || !nIf.isUp) continue
                val name = nIf.name.lowercase()
                for (addr in nIf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        // Prioritize common local private subnets
                        if (isPrivateLanAddress(host)) {
                            // Wi-Fi or Ethernet interfaces take top priority
                            if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("rndis") || name.startsWith("ap")) {
                                return host
                            }
                            candidates.add(host)
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
        } catch (_: Exception) {
        }
        return null
    }
}
