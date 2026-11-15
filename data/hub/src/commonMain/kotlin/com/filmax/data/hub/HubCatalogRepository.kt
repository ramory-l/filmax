package com.filmax.data.hub

import com.filmax.core.domain.catalog.CatalogFilters
import com.filmax.core.domain.catalog.CatalogRepository
import com.filmax.core.domain.catalog.CatalogShortcut
import com.filmax.core.domain.catalog.CatalogSort
import com.filmax.core.domain.catalog.GenreFilter
import com.filmax.core.domain.catalog.ReferenceGenre
import com.filmax.core.domain.catalog.SortOption
import com.filmax.core.domain.catalog.model.Collection
import com.filmax.core.domain.catalog.model.CollectionPage
import com.filmax.core.domain.catalog.model.Country
import com.filmax.core.domain.catalog.model.Genre
import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.ItemPage
import com.filmax.core.domain.catalog.model.ItemType
import com.filmax.core.domain.catalog.model.Pagination
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.data.hub.mapper.HubMapper
import com.filmax.data.hub.remote.HubApi
import com.filmax.data.hub.remote.HubCatalogQuery
import com.filmax.hub.protocol.HubPage
import com.filmax.hub.protocol.HubReferenceGenre
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubSort

/**
 * Каталог хаба — витрина TMDB. Подборок и стран у хаба нет (их не обещают и возможности
 * источника): подборки пустые, а фильтр по стране экраны не показывают.
 */
// Реализация всего контракта CatalogRepository — столько же методов.
@Suppress("TooManyFunctions")
internal class HubCatalogRepository(
    private val api: HubApi,
    private val titles: HubTitles,
    private val mapper: HubMapper,
) : CatalogRepository {

    override suspend fun getItems(type: ItemType, sort: CatalogSort, page: Int): RequestResult<ItemPage> =
        getItems(type, genre = null, CatalogFilters(), SortOption(sort), page)

    override suspend fun getItems(
        type: ItemType,
        genre: GenreFilter?,
        filters: CatalogFilters,
        sort: SortOption,
        page: Int,
    ): RequestResult<ItemPage> = safeRequest {
        val hubType = HubMapper.hubType(type) ?: return@safeRequest emptyPage(page)
        val query = HubCatalogQuery(
            type = hubType,
            sort = hubSort(sort.field),
            ascending = sort.ascending,
            genre = (genre as? GenreFilter.Listed)?.id,
            referenceGenre = (genre as? GenreFilter.Reference)?.genre?.let(::hubGenre),
            yearFrom = filters.yearFrom,
            yearTo = filters.yearTo,
            page = page,
        )
        api.items(query).toItemPage()
    }

    override suspend fun getShortcutItems(
        shortcut: CatalogShortcut,
        type: ItemType,
        page: Int,
    ): RequestResult<ItemPage> = safeRequest {
        val hubType = HubMapper.hubType(type) ?: return@safeRequest emptyPage(page)
        api.shortcut(hubShortcut(shortcut), hubType, page).toItemPage()
    }

    override suspend fun getItemDetails(id: Int): RequestResult<Item> = safeRequest { titles.load(id) }

    override suspend fun getSimilarItems(id: Int): RequestResult<List<Item>> =
        safeRequest { api.similar(id).map(mapper::item) }

    override suspend fun getGenres(): RequestResult<List<Genre>> = safeRequest { api.genres().map(mapper::genre) }

    override suspend fun getCountries(): RequestResult<List<Country>> = RequestResult.Success(emptyList())

    override suspend fun getCollections(page: Int): RequestResult<List<Collection>> =
        RequestResult.Success(emptyList())

    override suspend fun getCollectionItems(collectionId: Int, page: Int): RequestResult<CollectionPage> =
        RequestResult.Success(
            CollectionPage(collection = null, items = emptyList(), pagination = Pagination(0, page, 0)),
        )

    private fun HubPage.toItemPage() = ItemPage(
        items = items.map(mapper::item),
        pagination = Pagination(total = totalPages, current = page, perPage = items.size),
    )

    private fun emptyPage(page: Int) = ItemPage(emptyList(), Pagination(total = 0, current = page, perPage = 0))

    private companion object {
        fun hubSort(sort: CatalogSort): HubSort = when (sort) {
            CatalogSort.UPDATED -> HubSort.UPDATED
            CatalogSort.CREATED -> HubSort.CREATED
            CatalogSort.VIEWS -> HubSort.VIEWS
            CatalogSort.YEAR -> HubSort.YEAR
            // У хаба одна оценка — зрителей TMDB: сортировки по чужим оценкам сводятся к ней.
            CatalogSort.RATING, CatalogSort.KINOPOISK_RATING, CatalogSort.IMDB_RATING -> HubSort.RATING
        }

        fun hubShortcut(shortcut: CatalogShortcut): HubShortcut = when (shortcut) {
            CatalogShortcut.HOT -> HubShortcut.HOT
            CatalogShortcut.POPULAR -> HubShortcut.POPULAR
            CatalogShortcut.FRESH -> HubShortcut.FRESH
        }

        fun hubGenre(genre: ReferenceGenre): HubReferenceGenre = when (genre) {
            ReferenceGenre.COMEDY -> HubReferenceGenre.COMEDY
            ReferenceGenre.SCI_FI -> HubReferenceGenre.SCI_FI
            ReferenceGenre.CARTOON -> HubReferenceGenre.CARTOON
            ReferenceGenre.ANIME -> HubReferenceGenre.ANIME
            ReferenceGenre.DORAMA -> HubReferenceGenre.DORAMA
        }
    }
}
