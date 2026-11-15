package com.filmax.hub.server

import com.filmax.hub.protocol.HUB_DEFAULT_PORT
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubConfigTest {

    @Test
    fun `empty environment gives defaults and no token`() {
        val config = HubConfig.fromEnvironment(emptyMap())

        assertEquals(HUB_DEFAULT_PORT, config.port)
        assertEquals("Домашний хаб", config.name)
        assertNull(config.tmdbToken)
        assertNull(config.torrServer)
    }

    @Test
    fun `blank token counts as missing`() {
        assertNull(HubConfig.fromEnvironment(mapOf("TMDB_TOKEN" to "  ")).tmdbToken)
    }

    @Test
    fun `environment overrides every default`() {
        val config = HubConfig.fromEnvironment(
            mapOf(
                "FILMAX_HUB_PORT" to "9000",
                "FILMAX_HUB_NAME" to "Мак",
                "TMDB_TOKEN" to " token ",
                "FILMAX_HUB_CACHE" to "/tmp/hub",
                "TORRSERVER_URL" to "http://mac:8092",
            ),
        )

        assertEquals(HubConfig(9000, "Мак", "token", File("/tmp/hub"), "http://mac:8092"), config)
    }
}
