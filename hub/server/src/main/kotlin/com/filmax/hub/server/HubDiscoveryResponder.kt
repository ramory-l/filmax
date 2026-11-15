package com.filmax.hub.server

import com.filmax.hub.protocol.HUB_DISCOVERY_QUESTION
import com.filmax.hub.protocol.HUB_PROTOCOL_VERSION
import com.filmax.hub.protocol.HubDiscoveryAnswer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Отвечает на широковещательный поиск хаба: телевизор находит Мак сам, и адрес не набирают
 * пультом. Тот же приём, что у Jellyfin (UDP-вопрос — ответ с именем), только на своём порту.
 */
internal class HubDiscoveryResponder(
    private val name: String,
    private val httpPort: Int,
    private val io: CoroutineDispatcher,
) {
    private val answer = Json.encodeToString(
        HubDiscoveryAnswer.serializer(),
        HubDiscoveryAnswer(name, httpPort, HUB_PROTOCOL_VERSION),
    ).encodeToByteArray()

    /** Слушает, пока жива корутина; [socket] — для тестов, по умолчанию порт API, но по UDP. */
    suspend fun run(socket: DatagramSocket = DatagramSocket(InetSocketAddress(httpPort))) = withContext(io) {
        socket.use { udp ->
            // Таймаут приёма — чтобы цикл замечал отмену корутины, а не висел в receive вечно.
            udp.soTimeout = RECEIVE_TIMEOUT_MS
            val buffer = ByteArray(MAX_QUESTION)
            while (currentCoroutineContext().isActive) {
                val question = DatagramPacket(buffer, buffer.size)
                if (udp.receiveOrTimeout(question) && question.text() == HUB_DISCOVERY_QUESTION) {
                    udp.send(DatagramPacket(answer, answer.size, question.socketAddress))
                }
            }
        }
    }

    /** false — за секунду никто не спросил: обычное дело, вопрос приходит раз в запуск приложения. */
    private fun DatagramSocket.receiveOrTimeout(packet: DatagramPacket): Boolean = try {
        receive(packet)
        true
    } catch (expected: SocketTimeoutException) {
        false
    }

    private fun DatagramPacket.text(): String = data.decodeToString(offset, offset + length).trim()

    private companion object {
        const val RECEIVE_TIMEOUT_MS = 1_000
        const val MAX_QUESTION = 256
    }
}
