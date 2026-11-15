package com.filmax.data.hub

import com.filmax.core.domain.catalog.CatalogFilters
import com.filmax.core.domain.catalog.CatalogSort
import com.filmax.core.domain.catalog.GenreFilter
import com.filmax.core.domain.catalog.SortOption
import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.ItemPage
import com.filmax.core.domain.catalog.model.ItemType
import com.filmax.core.domain.catalog.model.RatingSource
import com.filmax.core.domain.common.RequestResult
import com.filmax.hub.protocol.HubParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Каталог приложения поверх хаба: что уходит в запрос и во что превращается ответ. */
class HubCatalogTest {

    private val hub = FakeHub().apply { connect() }
    private val catalog = HubCatalogRepository(hub.api, hub.titles, hub.mapper)

    @Test
    fun `catalog filters reach the hub as protocol parameters`() = runTest {
        catalog.getItems(
            type = ItemType.SERIES,
            genre = GenreFilter.Listed(GENRE_DRAMA),
            filters = CatalogFilters(yearFrom = 2000, yearTo = 2010),
            sort = SortOption(CatalogSort.KINOPOISK_RATING, ascending = true),
            page = 2,
        )

        val query = hub.requests.single().url.parameters
        assertEquals("SERIES", query[HubParams.TYPE])
        // Оценка у хаба одна — сортировки по чужим оценкам сводятся к ней.
        assertEquals("RATING", query[HubParams.SORT])
        assertEquals("true", query[HubParams.ASCENDING])
        assertEquals("$GENRE_DRAMA", query[HubParams.GENRE])
        assertEquals("2000", query[HubParams.YEAR_FROM])
        assertEquals("2010", query[HubParams.YEAR_TO])
        assertEquals("2", query[HubParams.PAGE])
    }

    @Test
    fun `types the hub has not are empty without a request`() = runTest {
        val page = assertIs<RequestResult.Success<ItemPage>>(catalog.getItems(ItemType.CONCERT, CatalogSort.VIEWS, 1))

        assertTrue(page.data.items.isEmpty())
        assertTrue(hub.requests.isEmpty())
    }

    @Test
    fun `pictures are served by the hub, not by tmdb`() = runTest {
        val item = assertIs<RequestResult.Success<Item>>(
            catalog.getItemDetails(FakeHub.SHOGUN.id),
        ).data

        assertEquals("${FakeHub.ADDRESS}/img/w342/shogun.jpg", item.posters.medium)
        assertEquals("${FakeHub.ADDRESS}/img/w1280/shogun-wide.jpg", item.posters.wide)
        assertEquals("${FakeHub.ADDRESS}/img/w300/s1e1.jpg", item.tracklist.first().thumbnail)
        // Серия без кадра берёт фон тайтла, а не пустую картинку.
        assertEquals(item.posters.wide, item.tracklist[1].thumbnail)
    }

    @Test
    fun `series episodes are numbered within their season`() = runTest {
        val item = assertIs<RequestResult.Success<Item>>(
            catalog.getItemDetails(FakeHub.SHOGUN.id),
        ).data

        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), item.tracklist.map { it.seasonNumber to it.number })
        assertEquals(listOf(3600, 3600, 4200), item.tracklist.map { it.durationSeconds })
    }

    @Test
    fun `a movie is one video number one without a season and with a rounded rating`() = runTest {
        val item = assertIs<RequestResult.Success<Item>>(
            catalog.getItemDetails(FakeHub.INTERSTELLAR.id),
        ).data

        val video = item.tracklist.single()
        assertEquals(0 to 1, video.seasonNumber to video.number)
        assertEquals(8.5, item.rating.of(RatingSource.COMMUNITY))
        assertNull(item.trailer)
    }

    private companion object {
        const val GENRE_DRAMA = 18
    }
}
