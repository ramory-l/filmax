package com.filmax.hub.protocol

/**
 * Версия протокола. Приложение сверяет её при подключении: хаб старее клиента не знает новых
 * полей и путей, и лучше сказать об этом сразу, чем показывать пустые ряды.
 */
const val HUB_PROTOCOL_VERSION = 1

/** Порт хаба по умолчанию: 8090–8096 на Маке уже заняты (Resonant, TorrServer, дашборд, Jellyfin). */
const val HUB_DEFAULT_PORT = 8097

/**
 * Поиск хаба в сети: широковещательный UDP-вопрос на [HUB_DEFAULT_PORT], ответ — [HubDiscoveryAnswer].
 * Адрес хаба клиент берёт из отправителя ответа: хаб не знает, каким из своих адресов виден в сети.
 */
const val HUB_DISCOVERY_QUESTION = "Who is FilmaxHub?"

/**
 * Пути API. Шаблоны с `{…}` — для маршрутов сервера, функции — для запросов клиента: и тем и
 * другим путь берётся отсюда, поэтому разойтись им негде.
 */
object HubPaths {
    const val INFO = "/api/v1/info"
    const val SHORTCUT = "/api/v1/shortcuts/{shortcut}"
    const val ITEMS = "/api/v1/items"
    const val ITEM = "/api/v1/items/{id}"
    const val SIMILAR = "/api/v1/items/{id}/similar"
    const val GENRES = "/api/v1/genres"
    const val SEARCH = "/api/v1/search"
    const val IMAGE = "/img/{size}/{file}"

    fun shortcut(shortcut: HubShortcut): String = "/api/v1/shortcuts/${shortcut.name}"

    fun item(id: Int): String = "/api/v1/items/$id"

    fun similar(id: Int): String = "/api/v1/items/$id/similar"
}

/** Параметры запросов: имена общие, чтобы сервер читал ровно то, что шлёт клиент. */
object HubParams {
    const val TYPE = "type"
    const val PAGE = "page"
    const val GENRE = "genre"
    const val REFERENCE_GENRE = "referenceGenre"
    const val SORT = "sort"
    const val ASCENDING = "ascending"
    const val YEAR_FROM = "yearFrom"
    const val YEAR_TO = "yearTo"
    const val QUERY = "query"
}
