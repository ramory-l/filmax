package com.filmax.data.torrents

import com.filmax.data.torrents.remote.TorrServerApi
import com.filmax.data.torrents.remote.torrServerHttpClient
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

/** Запрос, дошедший до «сервера»: путь, параметры и тело. */
internal data class SeenRequest(val path: String, val parameters: Map<String, String>, val body: String)

/**
 * TorrServer на подменённом движке Ktor. Поиск отвечает по тексту запроса из [searches],
 * `/torrents` — по действию из тела: [added] на добавление, [statuses] по очереди на «get».
 */
internal class FakeTorrServer(
    private val searches: Map<String, String> = emptyMap(),
    private val added: String = "{}",
    private val statuses: List<String> = emptyList(),
) {
    val requests = mutableListOf<SeenRequest>()
    val settings = MapSettings()
    val store = TorrServerStore(settings)
    val api = TorrServerApi(torrServerHttpClient(MockEngine { request -> answer(request) }))
    private var statusCalls = 0

    fun requestsTo(path: String) = requests.filter { it.path == path }

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        val parameters = request.url.parameters.names().associateWith { request.url.parameters[it].orEmpty() }
        requests += SeenRequest(path = request.url.encodedPath, parameters = parameters, body = body)
        return when (request.url.encodedPath) {
            "/echo" -> respond("MatriX.150", HttpStatusCode.OK)
            "/torznab/search/" -> json(searches[parameters["query"]] ?: "[]")
            "/torrents" -> json(if ("\"add\"" in body) added else statuses.getOrElse(statusCalls++) { statuses.last() })
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private fun MockRequestHandleScope.json(content: String) =
        respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
}

private var issuedLinks = 0

/** Выдача поиска в разметке TorrServer: размер строкой, год нулём, без магнитов. */
internal fun torznab(vararg results: Triple<String, String, Int?>): String =
    results.joinToString(",", "[", "]") { (title, size, seeders) ->
        // Путь каждой выдачи свой, как у Jackett: он шифрует его со случайной солью.
        val path = "${title.hashCode()}-${issuedLinks++}"
        """{"Title":"$title","Name":"$title","Size":"$size","CreateDate":"2025-01-08T10:12:53+05:00",""" +
            """"Tracker":"NoNaMe Club","Link":"http://127.0.0.1:9117/dl/nnm/?path=$path",""" +
            """"Year":0,"Peer":5,"Seed":${seeders ?: "null"},"Magnet":"","Hash":""}"""
    }

internal const val SHOGUN_HASH = "66fb792dd542c26a3425e13fb6f6fe600480fec4"

/** Раздача с файлами — как TorrServer отвечает на ссылку .torrent: файлы известны сразу. */
internal val SHOGUN_STATUS = """
    {"hash":"$SHOGUN_HASH","name":"Shogun.S01.WEB-DLRip.LF","stat":1,"file_stats":[
      {"id":1,"path":"Shogun.S01.WEB-DLRip.LF/Shogun.S01E01.WEB-DLRip.avi","length":1047836672},
      {"id":2,"path":"Shogun.S01.WEB-DLRip.LF/Shogun.S01E02.WEB-DLRip.avi","length":876648448},
      {"id":3,"path":"Shogun.S01.WEB-DLRip.LF/Shogun.S01E02.WEB-DLRip.srt","length":51200},
      {"id":4,"path":"Shogun.S01.WEB-DLRip.LF/Sample/sample.avi","length":10485760}
    ]}
""".trimIndent()
