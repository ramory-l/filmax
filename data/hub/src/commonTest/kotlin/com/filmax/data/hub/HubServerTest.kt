package com.filmax.data.hub

import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.source.HomeServer
import com.filmax.data.fake.FakeTorrServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Шаг «Домашний сервер» в приложении «Плёнка»: подключиться к хабу — уже войти. */
class HubServerTest {

    private val hub = FakeHub()
    private val servers = HubServerRepository(
        api = hub.api,
        session = hub.session,
        discovery = HubDiscovery(StandardTestDispatcher()),
        torrServers = hub.torrServers,
    )

    @Test
    fun `typed address gets a scheme and the default port`() {
        assertEquals("http://192.168.1.178:8097", hubAddress(" 192.168.1.178/ "))
        assertEquals("http://mac.local:9000", hubAddress("mac.local:9000"))
        assertEquals("https://hub.example.org", hubAddress("https://hub.example.org/"))
    }

    @Test
    fun `hub has no accounts, so connecting is signing in`() = runTest {
        val server = assertIs<RequestResult.Success<HomeServer>>(servers.connect("192.168.1.178")).data
        assertFalse(server.accounts)
        assertFalse(HubAuthRepository(hub.session).isAuthenticated.first())

        servers.login(server, username = "", password = "")

        assertTrue(HubAuthRepository(hub.session).isAuthenticated.first())
        assertEquals(FakeHub.ADDRESS, hub.session.current?.address)
    }

    @Test
    fun `hub's torrserver is connected along the way`() = runTest {
        val server = assertIs<RequestResult.Success<HomeServer>>(servers.connect(FakeHub.ADDRESS)).data

        servers.login(server, username = "", password = "")

        assertEquals(FakeTorrServerRepository.SERVER, hub.torrServers.server.value)
    }

    @Test
    fun `hub older than the app is refused before signing in`() = runTest {
        hub.info = hub.info.copy(protocol = 0)

        assertIs<RequestResult.Error>(servers.connect(FakeHub.ADDRESS))
        assertNull(hub.session.current)
    }

    @Test
    fun `signing out forgets the hub`() = runTest {
        hub.connect()

        HubAuthRepository(hub.session).logout()

        assertNull(hub.session.current)
    }
}
