package com.filmax.hub.server.catalog

import com.filmax.hub.protocol.HubEpisode
import com.filmax.hub.protocol.HubGenre
import com.filmax.hub.protocol.HubItem
import com.filmax.hub.protocol.HubSeason
import com.filmax.hub.protocol.HubType
import com.filmax.hub.server.tmdb.TmdbDetailsDto
import com.filmax.hub.server.tmdb.TmdbKind
import com.filmax.hub.server.tmdb.TmdbResultDto
import com.filmax.hub.server.tmdb.TmdbSeasonDto
import com.filmax.hub.server.tmdb.TmdbVideoDto
import java.util.Locale

/** Тайтл выдачи. [genres] — справочник жанров раздела: в выдаче у TMDB только их id. */
internal fun TmdbResultDto.toHubItem(kind: TmdbKind, genres: Map<Int, String>): HubItem? {
    val tmdbId = id
    val name = displayName(kind)
    if (tmdbId == null || name == null) return null
    return HubItem(
        id = HubIds.of(kind, tmdbId),
        type = hubType(kind, genreIds),
        title = name,
        year = yearOf(if (kind == TmdbKind.TV) firstAirDate else releaseDate),
        overview = overview.orEmpty(),
        genres = genreIds.mapNotNull { genreId -> genres[genreId]?.let { HubGenre(genreId, it) } },
        countries = originCountry.map(::countryName),
        rating = voteAverage.takeIf { (voteCount ?: 0) > 0 },
        poster = posterPath?.removePrefix("/"),
        backdrop = backdropPath?.removePrefix("/"),
    )
}

/** Карточка: выдача плюс режиссёры, актёры, длительность, трейлер и сезоны с сериями. */
internal fun TmdbDetailsDto.toHubItem(kind: TmdbKind, seasons: List<TmdbSeasonDto>): HubItem? {
    val tmdbId = id
    val name = displayName(kind)
    if (tmdbId == null || name == null) return null
    val directors = if (kind == TmdbKind.TV) createdBy else credits?.crew.orEmpty().filter { it.job == DIRECTOR }
    val hubSeasons = seasons.mapNotNull { it.toHubSeason() }
    return HubItem(
        id = HubIds.of(kind, tmdbId),
        type = hubType(kind, genres.mapNotNull { it.id }),
        title = name,
        year = yearOf(if (kind == TmdbKind.TV) firstAirDate else releaseDate),
        overview = overview.orEmpty(),
        genres = genres.mapNotNull { genre -> genre.id?.let { HubGenre(it, genre.name.orEmpty()) } },
        countries = (productionCountries.mapNotNull { it.code } + originCountry).distinct().map(::countryName),
        rating = voteAverage.takeIf { (voteCount ?: 0) > 0 },
        imdbId = (imdbId ?: externalIds?.imdbId)?.takeIf { it.isNotBlank() },
        poster = posterPath?.removePrefix("/"),
        backdrop = backdropPath?.removePrefix("/"),
        runtimeMinutes = runtime ?: episodeRunTime.firstOrNull() ?: hubSeasons.firstNotNullOfOrNull { season ->
            season.episodes.firstNotNullOfOrNull { it.runtimeMinutes }
        },
        directors = directors.mapNotNull { it.name },
        cast = credits?.cast.orEmpty().mapNotNull { it.name }.take(CAST_SIZE),
        seasons = hubSeasons,
        trailer = trailerKey(videos?.results.orEmpty()),
    )
}

/** Сезон без спецвыпусков (нулевой у TMDB — допы и короткометражки, на трекерах их нет). */
private fun TmdbSeasonDto.toHubSeason(): HubSeason? {
    val seasonNumber = number?.takeIf { it > 0 } ?: return null
    val episodes = episodes.mapNotNull { episode ->
        val episodeNumber = episode.number ?: return@mapNotNull null
        HubEpisode(
            number = episodeNumber,
            title = episode.name.orEmpty(),
            still = episode.stillPath?.removePrefix("/"),
            runtimeMinutes = episode.runtime,
        )
    }
    return HubSeason(seasonNumber, episodes)
}

/** Документальное, реалити и ток-шоу — по жанрам: другого типа у TMDB нет. */
private fun hubType(kind: TmdbKind, genreIds: List<Int>): HubType = when {
    kind == TmdbKind.MOVIE && DOCUMENTARY in genreIds -> HubType.DOCUMENTARY_MOVIE
    kind == TmdbKind.MOVIE -> HubType.MOVIE
    DOCUMENTARY in genreIds -> HubType.DOCUMENTARY
    REALITY in genreIds || TALK in genreIds -> HubType.TV_SHOW
    else -> HubType.SERIES
}

private fun yearOf(date: String?): Int = date?.take(YEAR_LENGTH)?.toIntOrNull() ?: 0

/** Страна по-русски из кода ISO: в карточке TMDB названия стран английские даже с ru-RU. */
private fun countryName(code: String): String =
    runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(RUSSIAN) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: code

/** Трейлер с YouTube: русский, если есть, потом английский, потом любой. */
private fun trailerKey(videos: List<TmdbVideoDto>): String? = videos
    .filter { it.site == YOUTUBE && it.type == TRAILER && !it.key.isNullOrBlank() }
    .minByOrNull { video -> TRAILER_LANGUAGES.indexOf(video.language).takeIf { it >= 0 } ?: TRAILER_LANGUAGES.size }
    ?.key

private val RUSSIAN = Locale.forLanguageTag("ru")
private val TRAILER_LANGUAGES = listOf("ru", "en")
private const val DIRECTOR = "Director"
private const val YOUTUBE = "YouTube"
private const val TRAILER = "Trailer"
private const val CAST_SIZE = 12
private const val YEAR_LENGTH = 4
private const val DOCUMENTARY = 99
private const val REALITY = 10764
private const val TALK = 10767
