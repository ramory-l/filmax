package com.filmax.hub.server

import com.filmax.hub.protocol.HUB_DISCOVERY_QUESTION
import com.filmax.hub.protocol.HUB_PROTOCOL_VERSION
import com.filmax.hub.protocol.HubDiscoveryAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HubDiscoveryResponderTest {

    private val loopback = InetAddress.getLoopbackAddress()

    @Test
    fun `hub answers the question with its name and http port`() = withResponder { port ->
        val answer = ask(port, HUB_DISCOVERY_QUESTION)

        assertEquals(HubDiscoveryAnswer("Мак", 8097, HUB_PROTOCOL_VERSION), answer)
    }

    @Test
    fun `other questions stay unanswered`() = withResponder { port ->
        assertFailsWith<SocketTimeoutException> { ask(port, "Who is JellyfinServer?") }
    }

    /** Ответчик на свободном порту loopback: настоящий UDP, но без широковещания на всю сеть. */
    private fun withResponder(block: (port: Int) -> Unit) = runBlocking {
        val socket = DatagramSocket(InetSocketAddress(loopback, 0))
        // Ответчик — живой поток на сокете: виртуальное время тестового диспетчера ему не подходит.
        val responder = launch(Dispatchers.IO) { HubDiscoveryResponder("Мак", 8097, Dispatchers.IO).run(socket) }
        try {
            block(socket.localPort)
        } finally {
            responder.cancelAndJoin()
        }
    }

    private fun ask(port: Int, question: String): HubDiscoveryAnswer = DatagramSocket().use { client ->
        client.soTimeout = 2_000
        val bytes = question.encodeToByteArray()
        client.send(DatagramPacket(bytes, bytes.size, InetSocketAddress(loopback, port)))
        val buffer = ByteArray(512)
        val reply = DatagramPacket(buffer, buffer.size)
        client.receive(reply)
        Json.decodeFromString(HubDiscoveryAnswer.serializer(), reply.data.decodeToString(0, reply.length))
    }
}
