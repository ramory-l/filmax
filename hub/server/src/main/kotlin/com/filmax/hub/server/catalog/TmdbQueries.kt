package com.filmax.hub.server.catalog

import com.filmax.hub.protocol.HubReferenceGenre
import com.filmax.hub.protocol.HubShortcut
import com.filmax.hub.protocol.HubSort
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.tmdb.TmdbKind
import java.time.LocalDate

/** Выдача каталога в терминах хаба: что экран просит у источника. */
internal data class CatalogQuery(
    val type: HubType,
    val genre: Int? = null,
    val referenceGenre: HubReferenceGenre? = null,
    val sort: HubSort = HubSort.VIEWS,
    val ascending: Boolean = false,
    val years: IntRange? = null,
    val page: Int = 1,
)

/** Раздел TMDB типа: документальное кино — фильмы, всё эпизодическое — сериалы. */
internal val HubType.kind: TmdbKind
    get() = if (this == HubType.MOVIE || this == HubType.DOCUMENTARY_MOVIE) TmdbKind.MOVIE else TmdbKind.TV

/**
 * Параметры discover для выдачи каталога: тип — жанрами и страной, потом опорный жанр, сортировка
 * и годы. Даты — не позже сегодняшней: иначе «новое» открывалось бы анонсами без единой раздачи.
 */
internal fun CatalogQuery.discoverParameters(today: LocalDate): Map<String, String> {
    val kind = type.kind
    val dateField = if (kind == TmdbKind.MOVIE) "primary_release_date" else "first_air_date"
    val direction = if (ascending) "asc" else "desc"
    val sorting = when (sort) {
        HubSort.VIEWS -> mapOf(SORT_BY to "popularity.$direction")
        HubSort.RATING -> mapOf(SORT_BY to "vote_average.$direction", VOTES_FROM to "$MIN_RATED_VOTES")
        HubSort.CREATED, HubSort.YEAR -> datedBy(dateField, direction, today)
        HubSort.UPDATED -> if (kind == TmdbKind.TV) airingNow(today) else datedBy(dateField, direction, today)
    }
    val yearRange = years?.let { range ->
        val lastDay = minOf(LocalDate.of(range.last, LAST_MONTH, LAST_DAY), today)
        mapOf("$dateField.gte" to "${range.first}-01-01", "$dateField.lte" to "$lastDay")
    }.orEmpty()
    return type.discoverFilter()
        .and(referenceGenre?.discoverFilter(kind).orEmpty())
        .and(genre?.let { mapOf(WITH_GENRES to "$it") }.orEmpty())
        .and(sorting)
        .and(yearRange)
}

/**
 * Готовая выборка: что смотрят сейчас — тренды недели (они есть только у фильмов и сериалов
 * целиком, у разделов — популярное), популярное с отсечкой по голосам и свежее за последние месяцы.
 */
internal fun HubShortcut.trendingPath(type: HubType): String? =
    if (this == HubShortcut.HOT && (type == HubType.MOVIE || type == HubType.SERIES)) {
        "trending/${type.kind.path}/week"
    } else {
        null
    }

internal fun HubShortcut.discoverParameters(type: HubType, today: LocalDate): Map<String, String> {
    val kind = type.kind
    val selection = when {
        this == HubShortcut.FRESH && kind == TmdbKind.TV -> airingNow(today)
        this == HubShortcut.FRESH -> mapOf(
            SORT_BY to "popularity.desc",
            "primary_release_date.gte" to "${today.minusDays(FRESH_MOVIE_DAYS)}",
            "primary_release_date.lte" to "$today",
            VOTES_FROM to "$MIN_FRESH_VOTES",
        )
        else -> mapOf(SORT_BY to "popularity.desc", VOTES_FROM to "$MIN_POPULAR_VOTES")
    }
    return type.discoverFilter().and(selection)
}

/** Тип раздела — жанрами и страной: у TMDB нет ни документальных, ни аниме как типа. */
private fun HubType.discoverFilter(): Map<String, String> = when (this) {
    HubType.MOVIE -> mapOf(WITHOUT_GENRES to "$DOCUMENTARY")
    HubType.SERIES -> mapOf(WITHOUT_GENRES to "$DOCUMENTARY,$REALITY,$TALK")
    HubType.ANIME -> mapOf(WITH_GENRES to "$ANIMATION", ORIGINAL_LANGUAGE to "ja")
    HubType.DOCUMENTARY, HubType.DOCUMENTARY_MOVIE -> mapOf(WITH_GENRES to "$DOCUMENTARY")
    HubType.TV_SHOW -> mapOf(WITH_GENRES to "$REALITY|$TALK")
}

/** Опорные жанры: у фантастики разные id у фильмов и сериалов, аниме и дорамы — ещё и страна. */
private fun HubReferenceGenre.discoverFilter(kind: TmdbKind): Map<String, String> = when (this) {
    HubReferenceGenre.COMEDY -> mapOf(WITH_GENRES to "$COMEDY")
    HubReferenceGenre.SCI_FI -> mapOf(WITH_GENRES to if (kind == TmdbKind.TV) "$SCI_FI_TV" else "$SCI_FI_MOVIE")
    HubReferenceGenre.CARTOON -> mapOf(WITH_GENRES to "$ANIMATION")
    HubReferenceGenre.ANIME -> mapOf(WITH_GENRES to "$ANIMATION", ORIGINAL_LANGUAGE to "ja")
    HubReferenceGenre.DORAMA -> mapOf(WITH_GENRES to "$DRAMA", ORIGIN_COUNTRY to "KR")
}

/** Сериалы, у которых серии выходили в последние две недели, — «обновлённые» по смыслу kino.pub. */
private fun airingNow(today: LocalDate): Map<String, String> = mapOf(
    SORT_BY to "popularity.desc",
    "air_date.gte" to "${today.minusDays(AIRING_DAYS)}",
    "air_date.lte" to "$today",
)

/** По дате выхода — с отсечкой по голосам: без неё сверху тысячи безвестных тайтлов без раздач. */
private fun datedBy(dateField: String, direction: String, today: LocalDate): Map<String, String> = mapOf(
    SORT_BY to "$dateField.$direction",
    "$dateField.lte" to "$today",
    VOTES_FROM to "$MIN_DATED_VOTES",
)

/** Слияние параметров: жанры складываются (TMDB читает запятую как «и»), остальное перекрывается. */
private fun Map<String, String>.and(other: Map<String, String>): Map<String, String> {
    val genres = listOfNotNull(this[WITH_GENRES], other[WITH_GENRES]).joinToString(",")
    val merged = this + other
    return if (genres.isEmpty()) merged else merged + (WITH_GENRES to genres)
}

private const val SORT_BY = "sort_by"
private const val WITH_GENRES = "with_genres"
private const val WITHOUT_GENRES = "without_genres"
private const val ORIGINAL_LANGUAGE = "with_original_language"
private const val ORIGIN_COUNTRY = "with_origin_country"
private const val VOTES_FROM = "vote_count.gte"

private const val ANIMATION = 16
private const val COMEDY = 35
private const val DRAMA = 18
private const val DOCUMENTARY = 99
private const val SCI_FI_MOVIE = 878
private const val SCI_FI_TV = 10765
private const val REALITY = 10764
private const val TALK = 10767

private const val MIN_RATED_VOTES = 300
private const val MIN_POPULAR_VOTES = 100
private const val MIN_DATED_VOTES = 20
private const val MIN_FRESH_VOTES = 20
private const val FRESH_MOVIE_DAYS = 120L
private const val AIRING_DAYS = 14L
private const val LAST_MONTH = 12
private const val LAST_DAY = 31
