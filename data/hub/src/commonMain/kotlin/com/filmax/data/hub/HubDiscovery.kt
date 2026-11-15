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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Хаб, ответивший в сети: адрес API и имя для списка найденных. */
internal data class FoundHub(val address: String, val name: String)

/**
 * Поиск хаба в локальной сети: широковещательный UDP-вопрос на порт хаба. Адрес берётся из
 * отправителя ответа — хаб не знает, каким из своих адресов виден телевизору. Ничего не нашлось
 * (роутер режет широковещание) — пустой список, адрес вводят вручную.
 *
 * Вопрос уходит и на общий 255.255.255.255, и на адреса своих подсетей ([broadcasts]): хаб на
 * macOS слышит только второй (см. [localBroadcasts]).
 */
internal class HubDiscovery(
    private val dispatcher: CoroutineDispatcher,
    private val broadcasts: () -> List<String> = ::localBroadcasts,
) {

    // Граница ошибок поиска: любой сбой сокета значит «никого не нашли», а не падение экрана, и
    // причина сбоя экрану не нужна — ему нечего с ней делать, кроме как предложить ввести адрес.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun find(): List<FoundHub> = try {
        // Экран зовёт поиск из главного потока, а сокетам там не место: Android роняет сетевые
        // вызовы в нём (NetworkOnMainThreadException), и поиск молча ничего не находил.
        withContext(dispatcher) { listen() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        emptyList()
    }

    private suspend fun listen(): List<FoundHub> = SelectorManager(dispatcher).use { selector ->
        aSocket(selector).udp().bind(InetSocketAddress(ANY_HOST, 0)) { broadcast = true }.use { socket ->
            // Пакет вопроса вычитывается при отправке — на каждый адрес свой. Не ушёл на один адрес
            // (у сети нет маршрута для 255.255.255.255) — остальные всё равно спрашиваем.
            (listOf(BROADCAST) + broadcasts()).distinct().forEach { target ->
                val question = buildPacket { writeText(HUB_DISCOVERY_QUESTION) }
                runCatching { socket.send(Datagram(question, InetSocketAddress(target, HUB_DEFAULT_PORT))) }
            }
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
        val host = (address as? InetSocketAddress)?.ipv4() ?: return null
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
