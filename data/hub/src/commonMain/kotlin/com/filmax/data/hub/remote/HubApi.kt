package com.filmax.data.hub.remote

import com.filmax.data.hub.HubSession
import com.filmax.hub.protocol.HubGenre
import com.filmax.hub.protocol.HubInfo
import com.filmax.hub.protocol.HubItem
import com.filmax.hub.protocol.HubPage
import com.filmax.hub.protocol.HubParams
import com.filmax.hub.protocol.HubPaths
import com.filmax.hub.protocol.HubReferenceGenre
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubSort
import com.filmax.hub.protocol.HubType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Выдача каталога хаба — то, что каталог приложения может у него попросить. */
internal data class HubCatalogQuery(
    val type: HubType,
    val sort: HubSort = HubSort.VIEWS,
    val ascending: Boolean = false,
    val genre: Int? = null,
    val referenceGenre: HubReferenceGenre? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val page: Int = 1,
)

/** Запросы к хабу. Адрес — из сессии на каждый запрос: хаб можно сменить без перезапуска. */
internal class HubApi(private val client: HttpClient, private val session: HubSession) {

    /** Проверка адреса до подключения — поэтому адрес явный, а не из сессии. */
    suspend fun info(address: String): HubInfo = client.get("$address${HubPaths.INFO}").body()

    suspend fun shortcut(shortcut: HubShortcut, type: HubType, page: Int): HubPage =
        client.get(url(HubPaths.shortcut(shortcut))) {
            parameter(HubParams.TYPE, type.name)
            parameter(HubParams.PAGE, page)
        }.body()

    suspend fun items(query: HubCatalogQuery): HubPage = client.get(url(HubPaths.ITEMS)) {
        parameter(HubParams.TYPE, query.type.name)
        parameter(HubParams.SORT, query.sort.name)
        parameter(HubParams.ASCENDING, query.ascending)
        parameter(HubParams.GENRE, query.genre)
        parameter(HubParams.REFERENCE_GENRE, query.referenceGenre?.name)
        parameter(HubParams.YEAR_FROM, query.yearFrom)
        parameter(HubParams.YEAR_TO, query.yearTo)
        parameter(HubParams.PAGE, query.page)
    }.body()

    suspend fun item(id: Int): HubItem = client.get(url(HubPaths.item(id))).body()

    suspend fun similar(id: Int): List<HubItem> = client.get(url(HubPaths.similar(id))).body()

    suspend fun genres(): List<HubGenre> = client.get(url(HubPaths.GENRES)).body()

    suspend fun search(query: String, page: Int = 1): HubPage = client.get(url(HubPaths.SEARCH)) {
        parameter(HubParams.QUERY, query)
        parameter(HubParams.PAGE, page)
    }.body()

    private fun url(path: String): String = "${session.require().address}$path"
}

internal val hubJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Клиент хаба — свой, как у домашнего сервера: адрес у каждого свой, авторизации нет. Движок —
 * общий: на Android это тот же OkHttp с инспектором запросов в debug.
 */
internal fun hubHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(hubJson) }
    install(HttpTimeout) {
        // Хаб в своей сети отвечает быстро, но карточка сериала — десяток запросов к TMDB подряд.
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }
}

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
