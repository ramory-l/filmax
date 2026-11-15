package com.filmax.data.hub

import com.filmax.data.fake.FakeTorrServerRepository
import com.filmax.data.hub.local.HubProgress
import com.filmax.data.hub.mapper.HubMapper
import com.filmax.data.hub.remote.HubApi
import com.filmax.data.hub.remote.hubHttpClient
import com.filmax.data.hub.remote.hubJson
import com.filmax.hub.protocol.HubEpisode
import com.filmax.hub.protocol.HubInfo
import com.filmax.hub.protocol.HubItem
import com.filmax.hub.protocol.HubPage
import com.filmax.hub.protocol.HubPaths
import com.filmax.hub.protocol.HubSeason
import com.filmax.hub.protocol.HubType
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Хаб для тестов: ответы по путям протокола, без сети. Запросы копятся в [requests] — тест видит,
 * что именно клиент попросил у хаба.
 */
internal class FakeHub(
    var info: HubInfo = HubInfo(name = "Mac mini", version = "0.1.0", protocol = 1, torrServer = TORR_SERVER),
    val items: Map<Int, HubItem> = listOf(SHOGUN, INTERSTELLAR).associateBy { it.id },
) {
    val requests = mutableListOf<HttpRequestData>()
    val settings = MapSettings()
    val session = HubSession(settings)
    val progress = HubProgress(settings, clock = { now })
    val watchlist = HubWatchlistRepository(settings)
    val torrServers = FakeTorrServerRepository()
    var now = 1_000L

    private val engine = MockEngine { request ->
        requests += request
        val path = request.url.encodedPath
        val body = when {
            path == HubPaths.INFO -> encode(HubInfo.serializer(), info)
            path == HubPaths.ITEMS || path == HubPaths.SEARCH || path.startsWith("/api/v1/shortcuts/") ->
                encode(HubPage.serializer(), HubPage(items.values.toList(), page = 1, totalPages = 3))
            path.endsWith("/similar") -> encode(ListSerializer(HubItem.serializer()), listOf(INTERSTELLAR))
            path.startsWith("/api/v1/items/") ->
                items[path.substringAfterLast('/').toInt()]?.let { encode(HubItem.serializer(), it) }
            else -> null
        }
        body?.let { respond(it, headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            ?: respondError(HttpStatusCode.NotFound)
    }

    val api = HubApi(hubHttpClient(engine), session)
    val mapper = HubMapper(session, progress, inWatchlist = watchlist::has)
    val titles = HubTitles(api, mapper)

    /** Подключение, как после шага «Домашний сервер». */
    fun connect() = session.save(StoredHub(address = ADDRESS, name = info.name, torrServer = info.torrServer))

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = hubJson.encodeToString(serializer, value)

    companion object {
        const val ADDRESS = "http://192.168.1.178:8097"
        val TORR_SERVER = FakeTorrServerRepository.SERVER.address

        val SHOGUN = HubItem(
            id = 126_308,
            type = HubType.SERIES,
            title = "Сёгун",
            year = 2024,
            poster = "shogun.jpg",
            backdrop = "shogun-wide.jpg",
            runtimeMinutes = 60,
            seasons = listOf(
                HubSeason(1, listOf(HubEpisode(1, "Анджин", still = "s1e1.jpg"), HubEpisode(2, "Слуги двух господ"))),
                HubSeason(2, listOf(HubEpisode(1, "Новый сёгун", runtimeMinutes = 70))),
            ),
        )
        val INTERSTELLAR = HubItem(
            id = 157_336,
            type = HubType.MOVIE,
            title = "Интерстеллар",
            year = 2014,
            rating = 8.457,
            poster = "interstellar.jpg",
            runtimeMinutes = 169,
        )
    }
}
