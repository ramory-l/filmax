package com.filmax.hub.server.catalog

import com.filmax.hub.server.tmdb.TmdbDetailsDto
import com.filmax.hub.server.tmdb.TmdbKind
import com.filmax.hub.server.tmdb.TmdbResultDto

/** Название тайтла выдачи: у фильма поля title, у сериала — name. */
internal fun TmdbResultDto.displayName(kind: TmdbKind): String? =
    if (kind == TmdbKind.TV) displayTitle(name, originalName) else displayTitle(title, originalTitle)

internal fun TmdbDetailsDto.displayName(kind: TmdbKind): String? =
    if (kind == TmdbKind.TV) displayTitle(name, originalName) else displayTitle(title, originalTitle)

/**
 * «Русское / Original», как пишет kino.pub и как ищут трекеры. Оригинал — только латиницей:
 * японское или корейское написание трекеры не находят, а на экране оно только мешает.
 */
internal fun displayTitle(local: String?, original: String?): String? {
    val localTitle = local?.trim().orEmpty()
    val originalTitle = original?.trim().orEmpty().takeIf { it.isLatin() }.orEmpty()
    return when {
        localTitle.isEmpty() -> originalTitle.ifEmpty { null }
        originalTitle.isEmpty() || originalTitle.equals(localTitle, ignoreCase = true) -> localTitle
        else -> "$localTitle / $originalTitle"
    }
}

/** Латиница с диакритикой (до IPA-расширений Unicode) — всё, что трекеры пишут английскими буквами. */
private fun String.isLatin(): Boolean = all { !it.isLetter() || it.code < LATIN_EXTENDED_END }

private const val LATIN_EXTENDED_END = 0x250
