package com.filmax.hub.server.catalog

import com.filmax.hub.protocol.HubGenre
import com.filmax.hub.protocol.HubItem
import com.filmax.hub.protocol.HubPage
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.cache.TtlCache
import com.filmax.hub.server.tmdb.TmdbClient
import com.filmax.hub.server.tmdb.TmdbKind
import com.filmax.hub.server.tmdb.TmdbPageDto
import java.time.LocalDate

/**
 * Витрина хаба поверх TMDB: ряды, каталог, поиск и карточки в терминах протокола. Ответы
 * кешируются: ряды и выдача — на час, карточки — на шесть часов (сезоны дополняются сериями, но
 * не так часто), справочник жанров — на сутки.
 */
internal class HubCatalog(
    private val tmdb: TmdbClient,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val pages = TtlCache<HubPage>(PAGE_TTL_MS)
    private val items = TtlCache<HubItem>(ITEM_TTL_MS)
    private val genreNames = TtlCache<Map<Int, String>>(GENRES_TTL_MS)

    suspend fun shortcut(shortcut: HubShortcut, type: HubType, page: Int): HubPage =
        pages.get("shortcut:$shortcut:$type:$page") {
            val trending = shortcut.trendingPath(type)
            val response = if (trending != null) {
                tmdb.page(trending, page)
            } else {
                tmdb.page("discover/${type.kind.path}", page, shortcut.discoverParameters(type, today()))
            }
            response.toHubPage(type.kind)
        }

    suspend fun items(query: CatalogQuery): HubPage = pages.get("items:$query") {
        tmdb.page("discover/${query.type.kind.path}", query.page, query.discoverParameters(today()))
            .toHubPage(query.type.kind)
    }

    /** Карточка с сезонами: серии каждого сезона — отдельный раздел TMDB, они дозапрашиваются. */
    suspend fun item(id: Int): HubItem = items.get("item:$id") {
        val kind = HubIds.kind(id)
        val tmdbId = HubIds.tmdbId(id)
        val details = tmdb.details(kind, tmdbId)
        val seasonNumbers = details.seasons.mapNotNull { it.number }.filter { it > 0 }
        val seasons = if (kind == TmdbKind.TV) tmdb.seasons(tmdbId, seasonNumbers) else emptyList()
        details.toHubItem(kind, seasons) ?: throw NoSuchElementException("Тайтл $id без названия")
    }

    /** Похожие — рекомендации TMDB: они точнее «похожих» по жанрам. */
    suspend fun similar(id: Int): List<HubItem> = pages.get("similar:$id") {
        val kind = HubIds.kind(id)
        tmdb.page("${kind.path}/${HubIds.tmdbId(id)}/recommendations", page = 1).toHubPage(kind)
    }.items

    /** Жанры фильмов и сериалов одним списком: у общих жанров (драма, комедия) и id общий. */
    suspend fun genres(): List<HubGenre> = TmdbKind.entries
        .flatMap { kind -> genreNames(kind).map { (id, title) -> HubGenre(id, title) } }
        .distinctBy { it.id }
        .sortedBy { it.title }

    /** Поиск по фильмам и сериалам разом; персоны из выдачи TMDB отбрасываются. */
    suspend fun search(query: String, page: Int): HubPage {
        val response = tmdb.page("search/multi", page, mapOf("query" to query, "include_adult" to "false"))
        val movieGenres = genreNames(TmdbKind.MOVIE)
        val tvGenres = genreNames(TmdbKind.TV)
        val found = response.results.mapNotNull { result ->
            when (result.mediaType) {
                TmdbKind.MOVIE.path -> result.toHubItem(TmdbKind.MOVIE, movieGenres)
                TmdbKind.TV.path -> result.toHubItem(TmdbKind.TV, tvGenres)
                else -> null
            }
        }
        return HubPage(found, response.page ?: page, response.totalPages ?: page)
    }

    private suspend fun genreNames(kind: TmdbKind): Map<Int, String> = genreNames.get("genres:$kind") {
        tmdb.genres(kind).mapNotNull { genre -> genre.id?.let { it to genre.name.orEmpty() } }.toMap()
    }

    private suspend fun TmdbPageDto.toHubPage(kind: TmdbKind): HubPage {
        val names = genreNames(kind)
        val pageNumber = page ?: 1
        return HubPage(results.mapNotNull { it.toHubItem(kind, names) }, pageNumber, lastPage(pageNumber))
    }

    /** TMDB насчитывает десятки тысяч страниц, но дальше 500-й отвечает ошибкой — дальше не листаем. */
    private fun TmdbPageDto.lastPage(pageNumber: Int): Int = minOf(totalPages ?: pageNumber, MAX_PAGE)

    companion object {
        const val MAX_PAGE = 500

        const val PAGE_TTL_MS = 60 * 60 * 1000L
        const val ITEM_TTL_MS = 6 * 60 * 60 * 1000L
        const val GENRES_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
