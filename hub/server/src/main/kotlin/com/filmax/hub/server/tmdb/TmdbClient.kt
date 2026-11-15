package com.filmax.hub.server.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Фильмы и сериалы у TMDB — разные разделы API с разными путями и полями. */
internal enum class TmdbKind(val path: String) { MOVIE("movie"), TV("tv") }

/**
 * Запросы к TMDB API v3. Язык — русский: названия, описания и жанры приходят переведёнными, а
 * где перевода нет, TMDB отдаёт оригинал.
 */
internal class TmdbClient(private val http: HttpClient) {

    suspend fun page(path: String, page: Int, query: Map<String, String> = emptyMap()): TmdbPageDto =
        http.get(path) {
            parameter("page", page)
            query.forEach { (name, value) -> parameter(name, value) }
        }.body()

    /** Карточка с актёрами, роликами и id IMDb — одним запросом. */
    suspend fun details(kind: TmdbKind, id: Int): TmdbDetailsDto = http.get("${kind.path}/$id") {
        parameter("append_to_response", "credits,videos,external_ids")
        // Ролики на русском бывают не у всех: английский трейлер лучше никакого.
        parameter("include_video_language", "ru,en,null")
    }.body()

    /**
     * Сезоны сериала с сериями. TMDB отдаёт до 20 сезонов за запрос через append_to_response —
     * у «Симпсонов» их 36, поэтому номера идут пачками.
     */
    suspend fun seasons(id: Int, numbers: List<Int>): List<TmdbSeasonDto> =
        numbers.chunked(SEASONS_PER_REQUEST).flatMap { chunk ->
            val response: JsonObject = http.get("tv/$id") {
                parameter("append_to_response", chunk.joinToString(",") { "season/$it" })
            }.body()
            chunk.mapNotNull { number ->
                val season = response["season/$number"] ?: return@mapNotNull null
                json.decodeFromJsonElement(TmdbSeasonDto.serializer(), season)
            }
        }

    suspend fun genres(kind: TmdbKind): List<TmdbGenreDto> =
        http.get("genre/${kind.path}/list").body<TmdbGenresDto>().genres

    private companion object {
        const val SEASONS_PER_REQUEST = 20
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Клиент TMDB с токеном чтения (API Read Access Token) и русским языком по умолчанию. */
internal fun tmdbHttpClient(token: String, engine: HttpClient = HttpClient()): HttpClient = engine.config {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) { requestTimeoutMillis = TMDB_TIMEOUT_MS }
    defaultRequest {
        url(TMDB_API)
        bearerAuth(token)
        url.parameters.append("language", TMDB_LANGUAGE)
    }
    expectSuccess = true
}

private const val TMDB_API = "https://api.themoviedb.org/3/"
private const val TMDB_LANGUAGE = "ru-RU"
private const val TMDB_TIMEOUT_MS = 15_000L
