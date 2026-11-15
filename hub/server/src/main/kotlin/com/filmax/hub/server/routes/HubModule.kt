package com.filmax.hub.server.routes

import com.filmax.hub.protocol.HUB_PROTOCOL_VERSION
import com.filmax.hub.protocol.HubInfo
import com.filmax.hub.protocol.HubParams
import com.filmax.hub.protocol.HubPaths
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.HubConfig
import com.filmax.hub.server.catalog.HubCatalog
import com.filmax.hub.server.images.ImageProxy
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/** Версия сборки хаба для Профиля: протокол сверяется отдельно, по [HUB_PROTOCOL_VERSION]. */
internal const val HUB_VERSION = "0.1.0"

/**
 * Приложение Ktor хаба. [catalog] нет, пока на Маке не задан токен TMDB: хаб всё равно
 * поднимается и отвечает на `/info`, а каталог — 503, чтобы телевизор сказал «хаб не настроен»,
 * а не «нет сети».
 */
internal fun Application.hubModule(config: HubConfig, catalog: HubCatalog?, images: ImageProxy) {
    install(ContentNegotiation) { json(Json { explicitNulls = false }) }
    install(StatusPages) { hubErrors() }
    routing {
        get(HubPaths.INFO) {
            call.respond(HubInfo(config.name, HUB_VERSION, HUB_PROTOCOL_VERSION, config.torrServer))
        }
        catalogRoutes { catalog ?: throw HubNotConfiguredException() }
        get(HubPaths.IMAGE) {
            val file = images.file(call.parameters.required("size"), call.parameters.required("file"))
            // Имя файла TMDB меняется вместе с картинкой, поэтому кешировать на клиенте можно надолго.
            call.response.header(HttpHeaders.CacheControl, IMAGE_CACHE_CONTROL)
            call.respondFile(file)
        }
    }
}

private fun Route.catalogRoutes(catalog: () -> HubCatalog) {
    get(HubPaths.SHORTCUT) {
        val shortcut = call.parameters.enum<HubShortcut>("shortcut") ?: throw MissingParameterException("shortcut")
        val query = call.request.queryParameters
        val type = query.enum<HubType>(HubParams.TYPE) ?: HubType.MOVIE
        call.respond(catalog().shortcut(shortcut, type, query.page()))
    }
    get(HubPaths.ITEMS) { call.respond(catalog().items(call.request.queryParameters.catalogQuery())) }
    get(HubPaths.ITEM) { call.respond(catalog().item(call.parameters.id())) }
    get(HubPaths.SIMILAR) { call.respond(catalog().similar(call.parameters.id())) }
    get(HubPaths.GENRES) { call.respond(catalog().genres()) }
    get(HubPaths.SEARCH) {
        val query = call.request.queryParameters
        val text = query.required(HubParams.QUERY).trim()
        require(text.isNotEmpty()) { "Пустой поисковый запрос" }
        call.respond(catalog().search(text, query.page()))
    }
}

private const val IMAGE_CACHE_CONTROL = "public, max-age=2592000, immutable"
