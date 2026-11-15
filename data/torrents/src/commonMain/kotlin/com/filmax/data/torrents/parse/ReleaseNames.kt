package com.filmax.data.torrents.parse

/**
 * Названия, годы и сезоны раздачи. Трекеры пишут «Рус / Orig, S1E1-10 of 10 (2024) WEB-DL»
 * (BigFANGroup), «Рус / Orig / S1E1-10 of 10 (2024)» (NNM-Club), «Рус / Orig (Режиссёр) [2021,
 * США, …]» (Rutracker), а зарубежные релизы — «Orig.2024.S01.1080p.WEB-DL». Название — всё до
 * года, скобок и технической части, разрезанное по «/».
 */
internal object ReleaseNames {

    fun names(title: String): List<String> {
        val head = title.substring(0, nameEnd(title))
        val spaced = if (' ' !in head.trim()) head.replace(SCENE_SEPARATORS, " ") else head
        return spaced.split('/')
            .map { part -> cleanName(part) }
            // Цифры — тоже название: «1917 / 1917 (2019)».
            .filter { name -> name.any { it.isLetterOrDigit() } }
    }

    /** Годы раздачи: «(1997-2016)» — диапазон, «(2024)» или «[2021, США» — один год. */
    fun years(title: String): IntRange? {
        val found = YEARS.firstNotNullOfOrNull { it.find(title) } ?: return null
        val start = found.groupValues[1].toInt()
        val end = found.groupValues.getOrNull(2)?.toIntOrNull() ?: start
        return start..maxOf(start, end)
    }

    fun seasons(title: String): IntRange? {
        val found = SEASONS.firstNotNullOfOrNull { it.find(title) } ?: return null
        val first = found.groupValues[1].toInt()
        val last = found.groupValues.getOrNull(2)?.toIntOrNull() ?: first
        return first..maxOf(first, last)
    }

    /** Сериал по названию: сезон, серии («E1-10 of 10», «Серии 1-8») — даже если сезон не указан. */
    fun looksEpisodic(title: String): Boolean = seasons(title) != null || EPISODES.containsMatchIn(title)

    /**
     * Форма для сравнения: регистр, «ё», диакритика («Shōgun» = «Shogun») и знаки препинания
     * не различаются — трекеры и каталоги пишут одно название по-разному.
     */
    fun normalize(name: String): String = name.lowercase()
        .map { DIACRITICS[it] ?: it }
        .joinToString("")
        .replace(NOT_ALPHANUMERIC, " ")
        .trim()

    /**
     * Где кончается название: год, скобка, перечень после «|» или разрешение. Год без скобок —
     * граница только у зарубежных релизов без пробелов («Shogun.2024.S01»): в обычном названии он
     * бывает частью имени — «Бегущий по лезвию 2049 / Blade Runner 2049 (2017)».
     */
    private fun nameEnd(title: String): Int {
        val marks = if (' ' in title.trim()) NAME_END else NAME_END + SCENE_YEAR
        return marks.mapNotNull { it.find(title)?.range?.first }.filter { it > 0 }.minOrNull() ?: title.length
    }

    /** Часть до «/» без режиссёра в скобках и без хвоста сезона: «Shogun, S1E1-10 of 10» → «Shogun». */
    private fun cleanName(part: String): String {
        val withoutNotes = part.replace(PARENTHESES, " ")
        val seasonStart = SEASON_TAIL.find(withoutNotes)?.range?.first ?: withoutNotes.length
        return withoutNotes.substring(0, seasonStart).replace(SPACES, " ").trim(' ', ',', '.', '-', ':', ';')
    }

    private const val YEAR = """(?:19|20)\d{2}"""

    private val YEARS = listOf(
        Regex("""\(\s*($YEAR)\s*(?:[-–]\s*($YEAR))?\s*\)"""),
        Regex("""\[\s*($YEAR)\s*(?:[-–]\s*($YEAR))?\s*[,\]]"""),
        Regex("""/\s*($YEAR)\s*(?:/|$)"""),
        Regex("""(?<!\d)($YEAR)(?![\dpрi])"""),
    )

    private val NAME_END = listOf(
        Regex("""[(\[]\s*$YEAR"""),
        Regex("""\["""),
        Regex("""\|"""),
        Regex("""/\s*$YEAR\s*(?:/|$)"""),
        Regex("""(?<![$LETTERS\d])\d{3,4}[pр](?![$LETTERS\d])""", RegexOption.IGNORE_CASE),
    )

    private val SCENE_YEAR = Regex("""(?<![$LETTERS\d])$YEAR(?![\dpрi])""")

    private val SEASONS = listOf(
        // «S1-20E1-277» — сезоны с 1 по 20.
        Regex("""(?<![$LETTERS])S(\d{1,2})-S?(\d{1,2})(?=E|[^\d$LETTERS]|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?<![$LETTERS])S(\d{1,2})(?=E\d|[^\d$LETTERS]|$)""", RegexOption.IGNORE_CASE),
        Regex("""Сезон[$LETTERS]*\s*[:№]?\s*(\d{1,2})(?:\s*[-–]\s*(\d{1,2}))?""", RegexOption.IGNORE_CASE),
        Regex("""(?<!\d)(\d{1,2})(?:\s*[-–]\s*(\d{1,2}))?\s*-?\s*(?:[йи]\s*)?сезон""", RegexOption.IGNORE_CASE),
        Regex("""Seasons?\s*:?\s*(\d{1,2})(?:\s*[-–]\s*(\d{1,2}))?""", RegexOption.IGNORE_CASE),
    )

    /**
     * Серии без сезона: «E1-10 of 10», «Серии 1-8». Слово — только с номером: «Эпизод III» и
     * «Сезон охоты» — фильмы.
     */
    private val EPISODES = Regex(
        """(?<![$LETTERS])E\d{1,3}(?:-\d{1,3})?(?: of \d+)?(?![$LETTERS])|Сери[яи]\s*[:№]?\s*\d|Episodes?\s*\d""",
        RegexOption.IGNORE_CASE,
    )

    /** Хвост сезона в части названия: «, S1E1-10 of 10», «,E1-3 of 3», «Сезон 2» — но не «Сезон охоты». */
    private val SEASON_TAIL = Regex(
        """(?<![$LETTERS])(?:S\d{1,2}(?:-S?\d{1,2})?(?:E\d|(?![\d$LETTERS]))|E\d{1,3}-\d|""" +
            """(?:Сезон[$LETTERS]*|Серии|Серия|Season)\s*[:№]?\s*\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val PARENTHESES = Regex("""\([^)]*\)""")
    private val SCENE_SEPARATORS = Regex("""[._]""")
    private val SPACES = Regex("""\s+""")
    private val NOT_ALPHANUMERIC = Regex("""[^a-z0-9а-я]+""")

    private val DIACRITICS: Map<Char, Char> = buildMap {
        "àáâãäåā".forEach { put(it, 'a') }
        "çč".forEach { put(it, 'c') }
        "èéêëēė".forEach { put(it, 'e') }
        "ìíîïī".forEach { put(it, 'i') }
        "ñń".forEach { put(it, 'n') }
        "òóôõöōø".forEach { put(it, 'o') }
        "ùúûüū".forEach { put(it, 'u') }
        "ýÿ".forEach { put(it, 'y') }
        "šś".forEach { put(it, 's') }
        "žźż".forEach { put(it, 'z') }
        put('ё', 'е')
    }
}
