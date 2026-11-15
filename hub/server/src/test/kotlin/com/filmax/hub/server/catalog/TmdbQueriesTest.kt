package com.filmax.hub.server.catalog

import com.filmax.hub.protocol.HubReferenceGenre
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubSort
import com.filmax.hub.protocol.HubType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbQueriesTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `anime is japanese animation`() {
        val parameters = CatalogQuery(HubType.ANIME).discoverParameters(today)

        assertEquals("16", parameters["with_genres"])
        assertEquals("ja", parameters["with_original_language"])
    }

    @Test
    fun `reference genre and chosen genre are both required`() {
        val query = CatalogQuery(HubType.MOVIE, genre = 28, referenceGenre = HubReferenceGenre.COMEDY)

        assertEquals("35,28", query.discoverParameters(today)["with_genres"])
    }

    @Test
    fun `science fiction has its own id for series`() {
        val query = CatalogQuery(HubType.SERIES, referenceGenre = HubReferenceGenre.SCI_FI)

        assertEquals("10765", query.discoverParameters(today)["with_genres"])
    }

    @Test
    fun `years never reach past today`() {
        val parameters = CatalogQuery(HubType.MOVIE, years = 2020..2030).discoverParameters(today)

        assertEquals("2020-01-01", parameters["primary_release_date.gte"])
        assertEquals("2026-11-15", parameters["primary_release_date.lte"])
    }

    @Test
    fun `rating sort skips titles with a handful of votes`() {
        val parameters = CatalogQuery(HubType.MOVIE, sort = HubSort.RATING).discoverParameters(today)

        assertEquals("vote_average.desc", parameters["sort_by"])
        assertEquals("300", parameters["vote_count.gte"])
    }

    @Test
    fun `updated series are sorted by recent episodes`() {
        val parameters = CatalogQuery(HubType.SERIES, sort = HubSort.UPDATED).discoverParameters(today)

        assertEquals("2026-11-01", parameters["air_date.gte"])
    }

    @Test
    fun `only whole movies and series have weekly trends`() {
        assertEquals("trending/tv/week", HubShortcut.HOT.trendingPath(HubType.SERIES))
        assertNull(HubShortcut.HOT.trendingPath(HubType.ANIME))
        assertNull(HubShortcut.POPULAR.trendingPath(HubType.MOVIE))
    }

    @Test
    fun `fresh movies are recent releases`() {
        val parameters = HubShortcut.FRESH.discoverParameters(HubType.MOVIE, today)

        assertEquals("2026-07-18", parameters["primary_release_date.gte"])
        assertEquals("2026-11-15", parameters["primary_release_date.lte"])
    }
}
