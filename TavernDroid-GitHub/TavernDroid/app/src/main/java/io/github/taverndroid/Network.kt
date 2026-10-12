package io.github.taverndroid

import java.net.Inet4Address
import java.net.NetworkInterface

object Network {
    /** URLs other devices on the same network can use to reach this phone's server. */
    fun lanUrls(port: Int): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { "http://${it.hostAddress}:$port/" }
    } catch (_: Exception) {
        emptyList()
    }
}
