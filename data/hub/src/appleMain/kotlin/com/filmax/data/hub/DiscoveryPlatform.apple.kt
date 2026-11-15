package com.filmax.data.hub

import io.ktor.network.sockets.InetSocketAddress

/**
 * На iPhone и Apple TV перебора интерфейсов в общем коде нет (это getifaddrs через cinterop), и
 * поиск хаба там пока не нужен — приложения торрентов для Apple нет. Остаётся общий адрес.
 */
internal actual fun localBroadcasts(): List<String> = emptyList()

/** В нативном ktor адрес отправителя — строка из sockaddr, DNS не участвует. */
internal actual fun InetSocketAddress.ipv4(): String? = hostname.takeIf { ':' !in it }
