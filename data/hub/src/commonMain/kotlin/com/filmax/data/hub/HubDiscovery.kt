package com.filmax.data.hub

import com.filmax.data.hub.remote.hubJson
import com.filmax.hub.protocol.HUB_DEFAULT_PORT
import com.filmax.hub.protocol.HUB_DISCOVERY_QUESTION
import com.filmax.hub.protocol.HubDiscoveryAnswer
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.core.writeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withTimeoutOrNull

/** Хаб, ответивший в сети: адрес API и имя для списка найденных. */
internal data class FoundHub(val address: String, val name: String)

/**
 * Поиск хаба в локальной сети: широковещательный UDP-вопрос на порт хаба. Адрес берётся из
 * отправителя ответа — хаб не знает, каким из своих адресов виден телевизору. Ничего не нашлось
 * (роутер режет широковещание) — пустой список, адрес вводят вручную.
 */
internal class HubDiscovery(private val dispatcher: CoroutineDispatcher) {

    // Граница ошибок поиска: любой сбой сокета значит «никого не нашли», а не падение экрана, и
    // причина сбоя экрану не нужна — ему нечего с ней делать, кроме как предложить ввести адрес.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun find(): List<FoundHub> = try {
        listen()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        emptyList()
    }

    private suspend fun listen(): List<FoundHub> = SelectorManager(dispatcher).use { selector ->
        aSocket(selector).udp().bind(InetSocketAddress(ANY_HOST, 0)) { broadcast = true }.use { socket ->
            val question = buildPacket { writeText(HUB_DISCOVERY_QUESTION) }
            socket.send(Datagram(question, InetSocketAddress(BROADCAST, HUB_DEFAULT_PORT)))
            val found = mutableListOf<FoundHub>()
            withTimeoutOrNull(LISTEN_MS) {
                while (true) {
                    socket.receive().toFoundHub()?.let(found::add)
                }
            }
            found.distinctBy { it.address }
        }
    }

    private fun Datagram.toFoundHub(): FoundHub? {
        val host = (address as? InetSocketAddress)?.hostname ?: return null
        // Чужой ответ на тот же порт — не хаб: пропускаем, а не роняем поиск.
        return runCatching { hubJson.decodeFromString(HubDiscoveryAnswer.serializer(), packet.readText()) }
            .getOrNull()
            ?.let { answer -> FoundHub(address = "http://$host:${answer.port}", name = answer.name) }
    }

    private companion object {
        const val ANY_HOST = "0.0.0.0"
        const val BROADCAST = "255.255.255.255"

        /** Хаб в своей сети отвечает за десятки миллисекунд; дольше ждать — тянуть экран. */
        const val LISTEN_MS = 1_500L
    }
}
