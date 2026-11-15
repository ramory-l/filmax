package com.filmax.data.hub

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.toJavaAddress
import java.net.Inet4Address
import java.net.NetworkInterface

/** Адреса — из поднятых интерфейсов: Ethernet телевизора, Wi-Fi телефона. */
internal actual fun localBroadcasts(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.interfaceAddresses }
        .mapNotNull { it.broadcast?.hostAddress }
}.getOrDefault(emptyList())

/** hostAddress — цифры адреса как есть, без обратного DNS (в отличие от hostName). */
internal actual fun InetSocketAddress.ipv4(): String? =
    ((toJavaAddress() as? java.net.InetSocketAddress)?.address as? Inet4Address)?.hostAddress
