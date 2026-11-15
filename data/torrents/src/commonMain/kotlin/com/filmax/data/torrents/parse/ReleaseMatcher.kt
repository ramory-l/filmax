package com.filmax.data.torrents.parse

import com.filmax.core.domain.torrent.TorrentQuery

/**
 * Подходит ли раздача тайтлу. Поиск трекеров — по словам, и на «Сёгун» отвечают «Сёгун» 1980 года,
 * «Грозный Сёгун», «Сёгун Маэда» и игра «Total War: Shogun 2». Поэтому одно из названий раздачи
 * должно совпасть с названием тайтла целиком, год — с годом выхода, а фильм — не быть сериалом.
 */
internal object ReleaseMatcher {

    fun matches(title: String, query: TorrentQuery): Boolean {
        val years = ReleaseNames.years(title)
        return ReleaseFilter.isPlayable(title) &&
            namesMatch(title, query) &&
            kindFits(title, years, query) &&
            yearFits(years, query)
    }

    private fun namesMatch(title: String, query: TorrentQuery): Boolean {
        val wanted = query.names.map(ReleaseNames::normalize).filter { it.isNotEmpty() }
        val names = ReleaseNames.names(title).map(ReleaseNames::normalize)
        return names.any { it in wanted } && !originalsDiffer(names, wanted)
    }

    /** Фильму — не сериал и не сборник; сериалу подходит и раздача без пометок сезона. */
    private fun kindFits(title: String, years: IntRange?, query: TorrentQuery): Boolean =
        query.episodic || !(ReleaseNames.looksEpisodic(title) || ReleaseFilter.isCollection(title, years))

    /**
     * Русское название у разных тайтлов бывает одно: «Тьма / Dark» 2017 года и «Тьма / The Dark»
     * 2026-го. Если оригинальное название есть у обоих, одно должно начинаться с другого:
     * «Dune: Part One» — это «Dune», а «The Dark» — не «Dark».
     */
    private fun originalsDiffer(names: List<String>, wanted: List<String>): Boolean {
        val releaseOriginals = names.filter(::isLatin)
        val wantedOriginals = wanted.filter(::isLatin)
        if (releaseOriginals.isEmpty() || wantedOriginals.isEmpty()) return false
        return releaseOriginals.none { release ->
            wantedOriginals.any { original -> release.startsWithWords(original) || original.startsWithWords(release) }
        }
    }

    private fun isLatin(name: String): Boolean = name.any { it in 'a'..'z' } && name.none { it in 'а'..'я' }

    private fun String.startsWithWords(prefix: String): Boolean = this == prefix || startsWith("$prefix ")

    /**
     * Год фильма трекеры и каталоги пишут с разбросом в год (премьера на фестивале и в прокате).
     * Сериал идёт годами: раздача нового сезона — позже года выхода, но не раньше него. Год не
     * указан с одной из сторон — не повод отбрасывать раздачу.
     */
    private fun yearFits(years: IntRange?, query: TorrentQuery): Boolean {
        val year = query.year
        return when {
            year == null || years == null -> true
            query.episodic -> years.last >= year - YEAR_SLACK
            else -> years.overlaps(year - YEAR_SLACK..year + YEAR_SLACK)
        }
    }

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private const val YEAR_SLACK = 1
}

/** Раздачи, которые не стоит предлагать ни одному тайтлу, и сборники, которые не стоит — фильму. */
internal object ReleaseFilter {

    fun isPlayable(title: String): Boolean = !UNPLAYABLE.containsMatchIn(title) && !isAudioOnly(title)

    /** Фильмам не достаются сборники: «Аватар (2009–2025) [Коллекция]» сыграл бы не тот фильм. */
    fun isCollection(title: String, years: IntRange?): Boolean =
        (years != null && years.first != years.last) || COLLECTION.containsMatchIn(title)

    /** Отдельная звуковая дорожка («AC3 5.1 [Proper]»): звук есть, видео нет. */
    private fun isAudioOnly(title: String): Boolean {
        val traits = ReleaseTraitsParser.parse(title)
        val video = traits.quality != null || traits.source != null || traits.codec != null
        return !video && AUDIO.containsMatchIn(title)
    }

    /**
     * Чего плеер не сыграет или что не кино: образы дисков (ISO, BDMV, DVD9 — меню и десятки
     * файлов вместо одного видео; у NNM-Club «Blu-ray» без «Rip» — тоже диск), 3D-стереопары
     * (на обычном ТВ — две картинки рядом), тизеры, музыка, книги и игры, которые трекеры отдают
     * на тот же запрос.
     */
    private val UNPLAYABLE = token(
        """ISO|BDMV|BD(?:25|50|66|100)|\d?\*?DVD-?[59]|DVD-?Video|""" +
            """Blu-?Ray ?Disc|COMPLETE[. ]BLURAY|(?:UHD )?Blu-ray|""" +
            """3D|[$LETTERS]*стереопар[$LETTERS]*|Тизер|Teaser|Трейлер|""" +
            """MP3|FLAC|ALAC|OST|Soundtrack|Саундтрек|Аудиокниг[$LETTERS]*|RePack|PC|FB2|EPUB|PDF|DJVU""",
    )

    private val COLLECTION = token(
        """Коллекция|Дилогия|Трилогия|Квадрология|Тетралогия|Пенталогия|Антология|Collection|Duology|Trilogy""",
    )

    private val AUDIO = token("""AC3|E-?AC3|DTS(?:-HD)?|TrueHD|Atmos|DD ?5\.1|5\.1""")
}
