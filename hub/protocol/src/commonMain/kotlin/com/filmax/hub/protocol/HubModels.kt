package com.filmax.hub.protocol

import kotlinx.serialization.Serializable

/** Что за хаб отвечает: имя для Профиля, версия сборки и протокола, TorrServer, которым он качает. */
@Serializable
data class HubInfo(
    val name: String,
    val version: String,
    val protocol: Int,
    /** Адрес TorrServer, видный из домашней сети: приложение берёт его и не просит набрать второй раз. */
    val torrServer: String? = null,
)

/** Ответ на поиск в сети: имя для списка найденных и порт HTTP API (он может быть не стандартным). */
@Serializable
data class HubDiscoveryAnswer(val name: String, val port: Int, val protocol: Int)

/**
 * Тип тайтла. TMDB знает только фильмы и сериалы, остальное хаб выводит из жанров: документальное,
 * реалити и ток-шоу. Аниме — раздел каталога, а не тип: в выдаче тайтл приходит фильмом или сериалом.
 */
@Serializable
enum class HubType { MOVIE, SERIES, ANIME, DOCUMENTARY, DOCUMENTARY_MOVIE, TV_SHOW }

/** Готовые выборки: что смотрят сейчас, популярное, свежее. */
@Serializable
enum class HubShortcut { HOT, POPULAR, FRESH }

/** Сортировка каталога. «Просмотры» у TMDB — популярность. */
@Serializable
enum class HubSort { UPDATED, CREATED, RATING, VIEWS, YEAR }

/** Опорные жанры экранов: каждый хаб сам знает, как найти их у себя (у TMDB — жанр плюс страна). */
@Serializable
enum class HubReferenceGenre { COMEDY, SCI_FI, CARTOON, ANIME, DORAMA }

@Serializable
data class HubGenre(val id: Int, val title: String)

/**
 * Тайтл. Картинки — имена файлов TMDB («abc.jpg»): клиент сам строит адрес `/img/{size}/{file}`
 * под нужный размер, а хаб проксирует и кеширует TMDB — телевизор не ходит к image.tmdb.org сам.
 */
@Serializable
data class HubItem(
    val id: Int,
    val type: HubType,
    /** «Русское / Original», как у kino.pub: торренты ищутся по обоим названиям. */
    val title: String,
    val year: Int = 0,
    val overview: String = "",
    val genres: List<HubGenre> = emptyList(),
    val countries: List<String> = emptyList(),
    /** Оценка зрителей TMDB, 0–10. */
    val rating: Double? = null,
    val imdbId: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val runtimeMinutes: Int? = null,
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    /** Сезоны с сериями — только в карточке тайтла; в выдаче каталога пусто. */
    val seasons: List<HubSeason> = emptyList(),
    /** Ключ трейлера на YouTube. */
    val trailer: String? = null,
)

@Serializable
data class HubSeason(val number: Int, val episodes: List<HubEpisode>)

@Serializable
data class HubEpisode(
    val number: Int,
    val title: String = "",
    val still: String? = null,
    val runtimeMinutes: Int? = null,
)

@Serializable
data class HubPage(val items: List<HubItem>, val page: Int, val totalPages: Int)
