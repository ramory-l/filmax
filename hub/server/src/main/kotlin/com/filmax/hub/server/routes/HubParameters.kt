package com.filmax.hub.server.routes

import com.filmax.hub.protocol.HubParams
import com.filmax.hub.protocol.HubReferenceGenre
import com.filmax.hub.protocol.HubSort
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.catalog.CatalogQuery
import com.filmax.hub.server.catalog.HubCatalog
import io.ktor.http.Parameters

/** Обязательный параметр не пришёл — это ошибка клиента, 400. */
internal class MissingParameterException(name: String) : IllegalArgumentException("Нет параметра $name")

internal fun Parameters.required(name: String): String = this[name] ?: throw MissingParameterException(name)

/** Значение перечисления по имени без учёта регистра; неизвестное имя — 400, а не тихий дефолт. */
internal inline fun <reified T : Enum<T>> Parameters.enum(name: String): T? = this[name]?.let { value ->
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Неизвестное значение $name=$value")
}

internal fun Parameters.int(name: String): Int? =
    this[name]?.let { value -> value.toIntOrNull() ?: throw IllegalArgumentException("$name=$value — не число") }

internal fun Parameters.id(): Int = int("id") ?: throw MissingParameterException("id")

/** Номер страницы: TMDB отдаёт с 1-й по 500-ю, за ними — ошибка, которую лучше поймать здесь. */
internal fun Parameters.page(): Int {
    val page = int(HubParams.PAGE) ?: 1
    require(page in 1..HubCatalog.MAX_PAGE) { "Страница $page вне 1..${HubCatalog.MAX_PAGE}" }
    return page
}

internal fun Parameters.catalogQuery(): CatalogQuery = CatalogQuery(
    type = enum<HubType>(HubParams.TYPE) ?: HubType.MOVIE,
    genre = int(HubParams.GENRE),
    referenceGenre = enum<HubReferenceGenre>(HubParams.REFERENCE_GENRE),
    sort = enum<HubSort>(HubParams.SORT) ?: HubSort.VIEWS,
    ascending = this[HubParams.ASCENDING]?.toBooleanStrictOrNull() ?: false,
    years = years(),
    page = page(),
)

/** Годы фильтра: можно задать только одну границу — вторая тогда открыта. */
private fun Parameters.years(): IntRange? {
    val from = int(HubParams.YEAR_FROM)
    val to = int(HubParams.YEAR_TO)
    if (from == null && to == null) return null
    return (from ?: EARLIEST_YEAR)..(to ?: LATEST_YEAR)
}

private const val EARLIEST_YEAR = 1900
private const val LATEST_YEAR = 9999
