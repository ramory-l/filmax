package com.filmax.hub.server.catalog

import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.FakeTmdb
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubCatalogTest {

    private val tmdb = FakeTmdb().withGenres()
    private val catalog = HubCatalog(tmdb.client, today = { LocalDate.of(2026, 11, 15) })

    @Test
    fun `series card has shifted id, both titles and seasons without specials`() = runTest {
        tmdb.respond("tv/1399") { url ->
            if (url.parameters["append_to_response"].orEmpty().startsWith("season/")) SEASONS else SERIES_DETAILS
        }

        val item = catalog.item(HubIds.SERIES_OFFSET + 1399)

        assertEquals(500_001_399, item.id)
        assertEquals(HubType.SERIES, item.type)
        assertEquals("Игра престолов / Game of Thrones", item.title)
        assertEquals(listOf(1, 2), item.seasons.map { it.number })
        assertEquals("Зима близко", item.seasons.first().episodes.first().title)
        assertEquals("still.jpg", item.seasons.first().episodes.first().still)
        assertEquals("poster.jpg", item.poster)
        assertEquals("tt0944947", item.imdbId)
        assertEquals(listOf("Дэвид Бениофф"), item.directors)
        assertEquals(62, item.runtimeMinutes)
        assertEquals("ru-key", item.trailer)
        val seasonsRequest = tmdb.requested("tv/1399").last().parameters["append_to_response"]
        assertEquals("season/1,season/2", seasonsRequest)
    }

    @Test
    fun `search keeps movies and series and drops people`() = runTest {
        tmdb.respond("search/multi", SEARCH)

        val page = catalog.search("матрица", page = 1)

        assertEquals(listOf(603, HubIds.SERIES_OFFSET + 1399), page.items.map { it.id })
        assertEquals("Матрица / The Matrix", page.items.first().title)
        assertEquals(listOf("боевик", "фантастика"), page.items.first().genres.map { it.title })
    }

    @Test
    fun `page count stops at the last page TMDB serves`() = runTest {
        tmdb.respond("discover/movie", """{"page":1,"total_pages":46000,"results":[]}""")

        val page = catalog.items(CatalogQuery(HubType.MOVIE))

        assertEquals(HubCatalog.MAX_PAGE, page.totalPages)
    }

    @Test
    fun `hot movies come from weekly trends and repeat from cache`() = runTest {
        tmdb.respond("trending/movie/week", """{"page":1,"total_pages":1,"results":[{"id":603,"title":"Матрица"}]}""")

        catalog.shortcut(HubShortcut.HOT, HubType.MOVIE, page = 1)
        val again = catalog.shortcut(HubShortcut.HOT, HubType.MOVIE, page = 1)

        assertEquals(listOf(603), again.items.map { it.id })
        assertEquals(1, tmdb.requested("trending/movie/week").size)
    }

    @Test
    fun `fresh series are the ones airing in the last two weeks`() = runTest {
        tmdb.respond("discover/tv", """{"page":1,"total_pages":1,"results":[]}""")

        catalog.shortcut(HubShortcut.FRESH, HubType.SERIES, page = 1)

        val parameters = tmdb.requested("discover/tv").single().parameters
        assertEquals("2026-11-01", parameters["air_date.gte"])
        assertEquals("2026-11-15", parameters["air_date.lte"])
        assertTrue(parameters["without_genres"].orEmpty().contains("99"))
    }

    @Test
    fun `genres of movies and series are merged without duplicates`() = runTest {
        val genres = catalog.genres()

        assertEquals(genres.map { it.id }.distinct(), genres.map { it.id })
        assertTrue(genres.any { it.title == "НФ и фэнтези" })
        assertFalse(genres.isEmpty())
    }

    private companion object {
        val SERIES_DETAILS = """
            {"id":1399,"name":"Игра престолов","original_name":"Game of Thrones","first_air_date":"2011-04-17",
             "overview":"Семь королевств","poster_path":"/poster.jpg","backdrop_path":"/back.jpg",
             "vote_average":8.5,"vote_count":25000,
             "genres":[{"id":18,"name":"драма"},{"id":10765,"name":"НФ и фэнтези"}],
             "origin_country":["US"],"episode_run_time":[],"created_by":[{"name":"Дэвид Бениофф"}],
             "seasons":[{"season_number":0},{"season_number":1},{"season_number":2}],
             "credits":{"cast":[{"name":"Эмилия Кларк"}],"crew":[]},
             "videos":{"results":[
               {"key":"en-key","site":"YouTube","type":"Trailer","iso_639_1":"en"},
               {"key":"ru-key","site":"YouTube","type":"Trailer","iso_639_1":"ru"}]},
             "external_ids":{"imdb_id":"tt0944947"}}
        """.trimIndent()

        val SEASONS = """
            {"id":1399,
             "season/1":{"season_number":1,"episodes":[
               {"episode_number":1,"name":"Зима близко","still_path":"/still.jpg","runtime":62}]},
             "season/2":{"season_number":2,"episodes":[{"episode_number":1,"name":"Север помнит"}]}}
        """.trimIndent()

        val SEARCH = """
            {"page":1,"total_pages":1,"results":[
              {"id":603,"media_type":"movie","title":"Матрица","original_title":"The Matrix",
               "release_date":"1999-03-30","genre_ids":[28,878]},
              {"id":6384,"media_type":"person","name":"Киану Ривз"},
              {"id":1399,"media_type":"tv","name":"Игра престолов","original_name":"Game of Thrones",
               "first_air_date":"2011-04-17","genre_ids":[18]}]}
        """.trimIndent()
    }
}
