package com.filmax.hub.server.routes

import com.filmax.hub.protocol.HUB_PROTOCOL_VERSION
import com.filmax.hub.protocol.HubInfo
import com.filmax.hub.protocol.HubPage
import com.filmax.hub.protocol.HubPaths
import com.filmax.hub.server.FakeTmdb
import com.filmax.hub.server.HubConfig
import com.filmax.hub.server.catalog.HubCatalog
import com.filmax.hub.server.images.ImageProxy
import com.filmax.hub.server.images.imageHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HubRoutesTest {

    private val cacheDir: File = createTempDirectory("hub-routes").toFile()
    private val config = HubConfig(
        port = 0,
        name = "Мак в гостиной",
        tmdbToken = "test-token",
        cacheDir = cacheDir,
        torrServer = "http://192.168.1.178:8092",
    )
    private val tmdb = FakeTmdb().withGenres()

    /** CDN картинок недоступен: всё, что тест получает, обязано прийти из кеша на диске. */
    private val images = ImageProxy(
        imageHttpClient(HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) })),
        cacheDir,
        UnconfinedTestDispatcher(),
    )

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    private fun hub(catalog: HubCatalog? = HubCatalog(tmdb.client), block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { hubModule(config, catalog, images) }
            block()
        }

    @Test
    fun `info names the hub and its torrserver`() = hub {
        val info = Json.decodeFromString<HubInfo>(client.get(HubPaths.INFO).bodyAsText())

        assertEquals(HubInfo("Мак в гостиной", HUB_VERSION, HUB_PROTOCOL_VERSION, "http://192.168.1.178:8092"), info)
    }

    @Test
    fun `catalog without tmdb token answers 503 but info still works`() = hub(catalog = null) {
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get(HubPaths.GENRES).status)
        assertEquals(HttpStatusCode.OK, client.get(HubPaths.INFO).status)
    }

    @Test
    fun `catalog query is read from parameters`() = hub {
        tmdb.respond("discover/tv", """{"page":2,"total_pages":3,"results":[{"id":1399,"name":"Игра престолов"}]}""")

        val response = client.get("${HubPaths.ITEMS}?type=series&sort=RATING&page=2&genre=18")

        val page = Json.decodeFromString<HubPage>(response.bodyAsText())
        assertEquals(listOf(500_001_399), page.items.map { it.id })
        val parameters = tmdb.requested("discover/tv").single().parameters
        assertEquals("2", parameters["page"])
        assertEquals("18", parameters["with_genres"])
        assertEquals("vote_average.desc", parameters["sort_by"])
    }

    @Test
    fun `unknown type and page out of range are client errors`() = hub {
        assertEquals(HttpStatusCode.BadRequest, client.get("${HubPaths.ITEMS}?type=CARTOONS").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("${HubPaths.ITEMS}?page=501").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("${HubPaths.SEARCH}?query=%20").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/items/abc").status)
    }

    @Test
    fun `title missing on tmdb is missing on the hub`() = hub {
        assertEquals(HttpStatusCode.NotFound, client.get(HubPaths.item(42)).status)
    }

    @Test
    fun `shortcut name is case insensitive`() = hub {
        tmdb.respond("trending/movie/week", """{"page":1,"total_pages":1,"results":[]}""")

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/shortcuts/hot?type=MOVIE").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/v1/shortcuts/coldest").status)
    }

    @Test
    fun `cached image is served with long client caching`() = hub {
        File(cacheDir, "w342").mkdirs()
        File(cacheDir, "w342/poster.jpg").writeBytes(byteArrayOf(7, 7, 7))

        val response = client.get("/img/w342/poster.jpg")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(byteArrayOf(7, 7, 7), response.bodyAsBytes())
        assertTrue(response.headers[HttpHeaders.CacheControl].orEmpty().contains("max-age"))
    }

    @Test
    fun `image path cannot leave the cache`() = hub {
        assertEquals(HttpStatusCode.BadRequest, client.get("/img/w342/..%2Fconfig.jpg").status)
    }
}
