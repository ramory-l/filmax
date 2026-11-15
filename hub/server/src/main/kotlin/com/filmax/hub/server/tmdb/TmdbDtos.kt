package com.filmax.hub.server.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Ответы TMDB API v3. Всё nullable с умолчаниями: TMDB пропускает поля у свежих и малоизвестных
// тайтлов, а одно обязательное поле роняло бы разбор всей страницы.

@Serializable
internal data class TmdbPageDto(
    val page: Int? = null,
    val results: List<TmdbResultDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int? = null,
)

/** Тайтл в выдаче: у фильма — title и release_date, у сериала — name и first_air_date. */
@Serializable
internal data class TmdbResultDto(
    val id: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
)

/** Карточка фильма или сериала с `append_to_response=credits,videos,external_ids`. */
@Serializable
internal data class TmdbDetailsDto(
    val id: Int? = null,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    @SerialName("production_countries") val productionCountries: List<TmdbCountryDto> = emptyList(),
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("created_by") val createdBy: List<TmdbPersonDto> = emptyList(),
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    val credits: TmdbCreditsDto? = null,
    val videos: TmdbVideosDto? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null,
)

@Serializable
internal data class TmdbGenreDto(val id: Int? = null, val name: String? = null)

@Serializable
internal data class TmdbGenresDto(val genres: List<TmdbGenreDto> = emptyList())

@Serializable
internal data class TmdbCountryDto(@SerialName("iso_3166_1") val code: String? = null)

@Serializable
internal data class TmdbPersonDto(val name: String? = null, val job: String? = null)

@Serializable
internal data class TmdbCreditsDto(
    val cast: List<TmdbPersonDto> = emptyList(),
    val crew: List<TmdbPersonDto> = emptyList(),
)

@Serializable
internal data class TmdbVideosDto(val results: List<TmdbVideoDto> = emptyList())

@Serializable
internal data class TmdbVideoDto(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
)

@Serializable
internal data class TmdbExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
internal data class TmdbSeasonSummaryDto(
    @SerialName("season_number") val number: Int? = null,
    @SerialName("episode_count") val episodeCount: Int? = null,
)

@Serializable
internal data class TmdbSeasonDto(
    @SerialName("season_number") val number: Int? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

@Serializable
internal data class TmdbEpisodeDto(
    @SerialName("episode_number") val number: Int? = null,
    val name: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
)
