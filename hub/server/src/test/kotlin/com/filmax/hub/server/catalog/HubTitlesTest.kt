package com.filmax.hub.server.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubTitlesTest {

    @Test
    fun `russian and latin original are joined like on kino pub`() {
        assertEquals("Матрица / The Matrix", displayTitle("Матрица", "The Matrix"))
    }

    @Test
    fun `original equal to local title is not repeated`() {
        assertEquals("Dune", displayTitle("Dune", "DUNE"))
    }

    @Test
    fun `non latin original is dropped`() {
        assertEquals("Унесённые призраками", displayTitle("Унесённые призраками", "千と千尋の神隠し"))
    }

    @Test
    fun `latin with diacritics counts as latin`() {
        assertEquals("Амели / Le Destin d'Amélie", displayTitle("Амели", "Le Destin d'Amélie"))
    }

    @Test
    fun `original stands in for a missing translation`() {
        assertEquals("Severance", displayTitle(" ", "Severance"))
    }

    @Test
    fun `title without any name is skipped`() {
        assertNull(displayTitle(null, "ナルト"))
    }
}
