package com.filmax.hub.server

import com.filmax.hub.server.tmdb.TmdbClient
import com.filmax.hub.server.tmdb.tmdbHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf

/**
 * TMDB в памяти: ответы по пути запроса (без `/3/`), всё остальное — 404, как у настоящего API
 * на несуществующий тайтл. [requests] — что хаб спросил, чтобы проверять параметры и кеш.
 */
internal class FakeTmdb {
    val requests = mutableListOf<Url>()
    private val responses = mutableMapOf<String, (Url) -> String>()

    private val engine = MockEngine { request ->
        requests += request.url
        val answer = responses[request.url.encodedPath.removePrefix(API_PREFIX)]
            ?: return@MockEngine respond("{}", HttpStatusCode.NotFound, JSON_HEADERS)
        respond(answer(request.url), HttpStatusCode.OK, JSON_HEADERS)
    }

    val client: TmdbClient = TmdbClient(tmdbHttpClient(TOKEN, HttpClient(engine)))

    fun respond(path: String, json: String) {
        responses[path] = { json }
    }

    fun respond(path: String, answer: (Url) -> String) {
        responses[path] = answer
    }

    /** Жанры обоих разделов: без справочника выдача остаётся без названий жанров. */
    fun withGenres(): FakeTmdb = apply {
        respond("genre/movie/list", MOVIE_GENRES)
        respond("genre/tv/list", TV_GENRES)
    }

    fun requested(path: String): List<Url> = requests.filter { it.encodedPath.removePrefix(API_PREFIX) == path }

    private companion object {
        const val TOKEN = "test-token"
        const val API_PREFIX = "/3/"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

        val MOVIE_GENRES = """
            {"genres":[{"id":28,"name":"боевик"},{"id":878,"name":"фантастика"},{"id":18,"name":"драма"}]}
        """.trimIndent()
        const val TV_GENRES = """{"genres":[{"id":18,"name":"драма"},{"id":10765,"name":"НФ и фэнтези"}]}"""
    }
}
