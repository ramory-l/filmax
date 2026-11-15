package com.filmax.core.domain.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Откуда приложение берёт каталог и видео. kino.pub — сервис по подписке; домашний сервер
 * (Jellyfin) — своя медиатека в локальной сети: фильмы и сериалы, которые лежат у человека дома.
 *
 * Источник один на всё приложение. Экраны о нём не знают: под ними целиком переключаются
 * репозитории (data:source), а граф по смене источника возвращается к корню раздела — иначе на
 * экране остались бы тайтлы прежнего источника с его id.
 */
enum class ContentSource {
    KINOPUB,
    JELLYFIN,

    /**
     * Filmax Hub — свой сервер дома: витрина из TMDB, а видео — раздачи через TorrServer. Это
     * источник отдельного приложения «Filmax Торренты», в основном его нет среди доступных.
     */
    HUB,
}

/** Выбранный источник. Хранится на устройстве: у телефона и ТВ он может быть разным. */
interface ContentSourceRepository {
    val source: StateFlow<ContentSource>

    /**
     * Из чего можно выбирать в этой сборке; первый — источник по умолчанию. Основное приложение —
     * kino.pub и домашний сервер, «Filmax Торренты» — только хаб, и выбора там нет вовсе.
     */
    val available: List<ContentSource>

    suspend fun select(source: ContentSource)
}
