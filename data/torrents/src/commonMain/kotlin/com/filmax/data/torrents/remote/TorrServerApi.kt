package com.filmax.data.torrents.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val torrServerJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    // Числа и строки Go-сервер пишет как придётся: размер строкой, сиды числом или null.
    isLenient = true
    explicitNulls = false
}

/**
 * Клиент TorrServer — свой, как у домашнего сервера: адрес у каждого свой, авторизации нет.
 * Движок общий: на Android это тот же OkHttp с инспектором запросов в debug.
 */
internal fun torrServerHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(torrServerJson) }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        // Поиск TorrServer ждёт ответа всех трекеров Jackett, медленный трекер — десятки секунд.
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }
}

/** Протокол TorrServer MatriX: проверка, поиск, добавление раздачи и её состояние. */
internal class TorrServerApi(private val client: HttpClient) {

    /** Сборка сервера («MatriX.150»). Отвечает и без раздач — по нему адрес и проверяют. */
    suspend fun echo(address: String): String = client.get("$address/echo").bodyAsText().trim()

    /** Слеш в конце обязателен: без него сервер отвечает переадресацией. */
    suspend fun search(address: String, query: String): List<TorznabResultDto> =
        client.get("$address/torznab/search/") { parameter("query", query) }.body()

    /**
     * Раздача открывается без названия: та же раздача может уже лежать в медиатеке TorrServer, и
     * своё название сервер берёт из неё. Присланное название он поставил бы поверх, и мост в
     * Jellyfin переложил бы фильм в папку с другим именем — как новый.
     */
    suspend fun add(address: String, link: String, poster: String?): TorrentStatusDto =
        torrents(
            address,
            TorrentsRequest(
                action = "add",
                link = link,
                poster = poster,
                data = FILMAX_MARK,
                saveToDb = false,
            ),
        )

    suspend fun status(address: String, hash: String): TorrentStatusDto =
        torrents(address, TorrentsRequest(action = "get", hash = hash))

    private suspend fun torrents(address: String, request: TorrentsRequest): TorrentStatusDto =
        client.post("$address/torrents") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}

/**
 * Поток файла раздачи. Имя файла в пути — только для плеера и логов (по нему видно, что играет),
 * сервер ищет файл по хешу и номеру; `play` — отдать поток, а не страницу состояния.
 */
internal fun streamUrl(address: String, hash: String, index: Int, fileName: String): String =
    URLBuilder("$address/stream").apply {
        appendPathSegments(fileName)
        parameters.append("link", hash)
        parameters.append("index", index.toString())
        parameters.append("play", "")
    }.buildString()

/**
 * Метка раздач, добавленных Filmax для просмотра. По ней мост TorrServer → Jellyfin не заводит их
 * в медиатеку: туда попадает то, что человек добавил сам, а не всё, что он пролистал. Раздачу,
 * которая в медиатеке уже есть, мост оставляет и с меткой.
 */
internal const val FILMAX_MARK = """{"filmax":"stream"}"""

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 90_000L
