package com.filmax.hub.server.catalog

import com.filmax.hub.server.tmdb.TmdbKind

/**
 * id тайтла хаба. У TMDB фильмы и сериалы нумеруются отдельно (фильм 1399 и сериал 1399 —
 * разные тайтлы), а приложению нужен один id, поэтому сериалы идут со сдвигом. Сдвиг держит id
 * ниже 2³⁰, где начинаются id домашнего сервера, — общие на устройстве ключи (выбор раздачи) не
 * путаются.
 */
internal object HubIds {
    const val SERIES_OFFSET = 500_000_000

    fun of(kind: TmdbKind, tmdbId: Int): Int = if (kind == TmdbKind.TV) SERIES_OFFSET + tmdbId else tmdbId

    fun kind(id: Int): TmdbKind = if (id >= SERIES_OFFSET) TmdbKind.TV else TmdbKind.MOVIE

    fun tmdbId(id: Int): Int = if (id >= SERIES_OFFSET) id - SERIES_OFFSET else id
}
