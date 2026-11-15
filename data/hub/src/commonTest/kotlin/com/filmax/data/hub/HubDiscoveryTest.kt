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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Поиск хаба по настоящему UDP через loopback: адрес найденного хаба — IP отправителя цифрами, а
 * не имя из обратного DNS (127.0.0.1 превратился бы в «localhost»).
 */
class HubDiscoveryTest {

    @Test
    fun `hub answering on a subnet address is found by its ip`() = runTest {
        val selector = SelectorManager(Dispatchers.Default)
        val hub = aSocket(selector).udp().bind(InetSocketAddress(LOOPBACK, HUB_DEFAULT_PORT))
        val questions = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Default) {
            val question = hub.receive()
            questions += question.packet.readText()
            val answer = hubJson.encodeToString(
                HubDiscoveryAnswer.serializer(),
                HubDiscoveryAnswer(name = "Mac mini", port = 8097, protocol = 1),
            )
            hub.send(Datagram(buildPacket { writeText(answer) }, question.address))
        }

        val found = HubDiscovery(Dispatchers.Default, broadcasts = { listOf(LOOPBACK) }).find()
        hub.close()
        selector.close()

        assertEquals(listOf(HUB_DISCOVERY_QUESTION), questions)
        // В сети разработчика может ответить и настоящий хаб — проверяем только свой.
        assertTrue(FoundHub(address = "http://$LOOPBACK:8097", name = "Mac mini") in found, "$found")
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
