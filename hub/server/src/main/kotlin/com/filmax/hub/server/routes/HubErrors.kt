package com.filmax.hub.server.routes

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import java.io.IOException

/** На Маке не задан токен TMDB: хаб жив, но каталог взять неоткуда. */
internal class HubNotConfiguredException : IllegalStateException("На хабе не задан токен TMDB (TMDB_TOKEN)")

/**
 * Ответы на ошибки. Различие важно приложению: 400 — ошибка в запросе, 404 — тайтла нет, 502 —
 * TMDB не ответил и стоит повторить позже, 503 — хаб не настроен и повтор не поможет.
 */
internal fun StatusPagesConfig.hubErrors() {
    exception<HubNotConfiguredException> { call, cause -> call.fail(HttpStatusCode.ServiceUnavailable, cause) }
    exception<BadRequestException> { call, cause -> call.fail(HttpStatusCode.BadRequest, cause) }
    exception<IllegalArgumentException> { call, cause -> call.fail(HttpStatusCode.BadRequest, cause) }
    exception<NoSuchElementException> { call, cause -> call.fail(HttpStatusCode.NotFound, cause) }
    exception<ClientRequestException> { call, cause -> call.fail(cause.hubStatus(), cause) }
    exception<ResponseException> { call, cause -> call.fail(HttpStatusCode.BadGateway, cause) }
    exception<HttpRequestTimeoutException> { call, cause -> call.fail(HttpStatusCode.BadGateway, cause) }
    exception<IOException> { call, cause -> call.fail(HttpStatusCode.BadGateway, cause) }
    exception<Throwable> { call, cause -> call.fail(HttpStatusCode.InternalServerError, cause) }
}

/**
 * 4xx от TMDB: 404 — такого тайтла нет и у нас, 401 — токен не принят (хаб не настроен), остальное —
 * TMDB отклонил запрос хаба, то есть сбой шлюза, а не ошибка приложения.
 */
private fun ClientRequestException.hubStatus(): HttpStatusCode = when (response.status) {
    HttpStatusCode.NotFound -> HttpStatusCode.NotFound
    HttpStatusCode.Unauthorized -> HttpStatusCode.ServiceUnavailable
    else -> HttpStatusCode.BadGateway
}

private suspend fun ApplicationCall.fail(status: HttpStatusCode, cause: Throwable) {
    // Ошибки клиента — обычное дело (несуществующий id), а у «хаб не настроен» причина известна
    // заранее: стек нужен только для сбоев самого хаба и TMDB.
    when {
        cause is HubNotConfiguredException -> application.log.warn("${request.local.uri} → $status: ${cause.message}")
        status.value >= SERVER_ERRORS -> application.log.warn("${request.local.uri} → $status", cause)
    }
    respondText(cause.message ?: status.description, status = status)
}

private const val SERVER_ERRORS = 500
