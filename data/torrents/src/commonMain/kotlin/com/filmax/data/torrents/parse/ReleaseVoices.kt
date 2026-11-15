package com.filmax.data.torrents.parse

import com.filmax.core.domain.torrent.VoiceKind

/**
 * Озвучка из названия раздачи: виды (дубляж, многоголосая…) и студии. Виды пишут словами
 * («Дубляж», «MVO») в любом месте названия, а коротко кодами — только в перечне озвучек: у
 * MegaPeer «| D, P, P2, A», у Кинозала «/ ДБ, ПМ, СТ /». Одиночное «D» или «A» в другом месте
 * названия — уже не озвучка, поэтому коды разбираются только внутри перечня.
 */
internal object ReleaseVoices {

    fun kinds(title: String): Set<VoiceKind> {
        val spelled = WORDS.filter { (_, pattern) -> pattern.containsMatchIn(title) }.map { it.first }
        val coded = voiceTokens(title).mapNotNull { CODES[it.lowercase()] }
        return (spelled + coded).toSet()
    }

    /**
     * Студии в порядке названия, без повторов: всё в перечне озвучек, что не код вида и не
     * техническая пометка. «HDrezka Studio» и «HDrezka» — одна студия, слово «Studio» отбрасывается.
     */
    fun studios(title: String): List<String> = voiceTokens(title)
        .filterNot { it.lowercase() in CODES || isKindWord(it) || isTechnical(it) }
        .map { it.replace(STUDIO_SUFFIX, "").trim() }
        .filter { name -> name.length in STUDIO_LENGTH && name.any { it.isLetter() } }
        .map { name -> KNOWN_STUDIOS[name.lowercase()] ?: name }
        .distinctBy { it.lowercase() }

    /**
     * Части перечня озвучек. Перечень — после последней «|» (BigFANGroup, MegaPeer), иначе после
     * последней «]» (NNM-Club, Rutracker: «[…, WEB-DL 1080p] Dub + MVO + Sub»), иначе после двух
     * пробелов (NNM-Club без скобок: «WEB-DL  LostFilm»), иначе приклеенный к источнику хвост
     * («WEB-DL-LostFilm»). Скобки внутри — тоже перечень: «MVO (LostFilm)».
     */
    private fun voiceTokens(title: String): List<String> {
        val segment = voiceSegment(title) ?: return emptyList()
        return segment.split(SEPARATORS)
            .map { it.replace(ELLIPSIS, "").replace(SPACES, " ").trim(' ', '.', '-', ':') }
            .filter { it.isNotEmpty() }
    }

    private fun voiceSegment(title: String): String? {
        if ('|' in title) return title.substringAfterLast('|')
        val afterBracket = title.substringAfterLast(']', missingDelimiterValue = "")
        // Два пробела у NNM-Club стоят и перед «/ S1E1-10»: часть со слешем — ещё название.
        val afterGap = title.trimEnd().substringAfterLast("  ", missingDelimiterValue = "").takeUnless { '/' in it }
        return listOfNotNull(afterBracket, afterGap).firstOrNull { part -> part.any { it.isLetter() } }
            ?: SOURCE_SUFFIX.find(title)?.groupValues?.get(1)
    }

    private fun isKindWord(token: String): Boolean = WORDS.any { (_, pattern) -> pattern.matches(token) }

    /** Техническая пометка или примечание к раздаче — где-то в части, а не вся часть: «60 fps-Лицензия». */
    private fun isTechnical(token: String): Boolean = TECHNICAL.containsMatchIn(token) || NOTES.containsMatchIn(token)

    private val WORDS = listOf(
        VoiceKind.DUBBING to token("""Дубляж[$LETTERS]*|Дублирован[$LETTERS]*|Dub(?:bing|bed)?"""),
        VoiceKind.MULTI_VOICE to token(
            """Многоголос[$LETTERS]*|Профессиональн[$LETTERS]* \(многоголос[$LETTERS]*\)|MVO""",
        ),
        VoiceKind.TWO_VOICE to token("""Двухголос[$LETTERS]*|DVO"""),
        VoiceKind.SINGLE_VOICE to token("""Одноголос[$LETTERS]*|Авторск[$LETTERS]*|AVO|VO"""),
        VoiceKind.ORIGINAL to token("""Оригинал[$LETTERS]*|Original|Eng"""),
        VoiceKind.SUBTITLES to token("""Субтитр[$LETTERS]*|Subs?"""),
    )

    /**
     * Коды видов озвучки трекеров. Rutracker и MegaPeer: D — дубляж, P/L — профессиональный и
     * любительский многоголосый, P2/L2 — двухголосый, P1/L1/A — одноголосый и авторский, O —
     * оригинал. Кинозал — те же виды по-русски: ДБ, ПМ, ПД, ПО, ЛМ, ЛД, ЛО, АП, СТ.
     */
    private val CODES: Map<String, VoiceKind> = buildMap {
        listOf("d", "дб").forEach { put(it, VoiceKind.DUBBING) }
        listOf("p", "l", "пм", "лм", "mvo").forEach { put(it, VoiceKind.MULTI_VOICE) }
        listOf("p2", "l2", "пд", "лд", "dvo").forEach { put(it, VoiceKind.TWO_VOICE) }
        listOf("p1", "l1", "a", "по", "ло", "ап", "ао", "vo", "avo").forEach { put(it, VoiceKind.SINGLE_VOICE) }
        listOf("o", "ор", "eng", "original").forEach { put(it, VoiceKind.ORIGINAL) }
        listOf("sub", "subs", "ст").forEach { put(it, VoiceKind.SUBTITLES) }
    }

    private val SEPARATORS = Regex("""[,+;&()\[\]/]| и """)
    private val ELLIPSIS = Regex("""…|\.\.\.""")
    private val SPACES = Regex("""\s+""")
    private val STUDIO_SUFFIX = Regex("""\s+Studios?$""", RegexOption.IGNORE_CASE)
    private val STUDIO_LENGTH = 2..MAX_STUDIO_LENGTH

    /** Студия, приклеенная к источнику: «WEB-DL-LostFilm», но не «WEB-DL-AVC» — это кодек. */
    private val SOURCE_SUFFIX = Regex(
        """(?:WEB-?DL(?:-?Rip)?|WEB-?Rip|BD-?Rip|HDTV-?Rip|HDRip|DVD-?Rip)-([$LETTERS][$LETTERS ]+)$""",
        RegexOption.IGNORE_CASE,
    )

    /** Пометки качества, которые попадают в перечень: кодек, разрешение, HDR, «10-bit», «IMAX». */
    private val TECHNICAL = token(
        """AVC|HEVC|AV1|[HX]\.?26[45]|\d{3,4}[pрi]|4K|UHD|HDR\d*\+?|SDR|DV|Dolby Vision|\d+-bit|IMAX[$LETTERS ]*|""" +
            """WEB-?DL|WEB-?Rip|BD-?Rip|Remux|HDTV|DVD-?Rip|Rip|handmade[$LETTERS ]*|AI|""" +
            """of \d+|S\d+(?:E\d+)?(?:-\d+)?|\d+ ?fps""",
    )

    /**
     * Примечания BigFANGroup после «|» — там же, где озвучки: версия фильма, «чистый звук» (без
     * рекламы в дорожке), откуда дорожка («Лицензия», «iTunes»), тифлокомментарий (AD).
     */
    private val NOTES = token(
        """[$LETTERS]+ верси[$LETTERS]*|Extended[$LETTERS ]*|Director'?s Cut|Theatrical[$LETTERS ]*|Open Matte|""" +
            """Чистый звук|Лицензия|License|iTunes|Netflix|Amazon|Proper|Edition|Remastered|Hybrid|PR|AD|LQ|rus|raw""",
    )

    /**
     * Одна студия — одно написание: трекеры пишут «HDRezka» и «HDrezka», «RHS» и «Red Head Sound»,
     * а в фильтре это должна быть одна кнопка.
     */
    private val KNOWN_STUDIOS: Map<String, String> = listOf(
        "HDrezka", "LostFilm", "Red Head Sound", "Jaskier", "TVShows", "NewStudio", "NewComers", "AlexFilm",
        "BaibaKo", "ColdFilm", "IdeaFilm", "Кубик в Кубе", "Good People", "RuDub", "Продубляж", "Sunnysiders",
    ).associateBy { it.lowercase() } + mapOf(
        "rhs" to "Red Head Sound",
        "kubik" to "Кубик в Кубе",
        "кубик в кубе" to "Кубик в Кубе",
        "hdrezka" to "HDrezka",
    )
}

private const val MAX_STUDIO_LENGTH = 40
