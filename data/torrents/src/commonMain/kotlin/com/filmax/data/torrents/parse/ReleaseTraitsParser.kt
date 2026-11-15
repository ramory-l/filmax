package com.filmax.data.torrents.parse

import com.filmax.core.domain.torrent.DynamicRange
import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.VideoCodec
import com.filmax.core.domain.torrent.VideoQuality

/**
 * Качество, источник, кодек и озвучка — из названия раздачи. Трекеры пишут их по-разному:
 * «WEB-DL 1080p | HDrezka Studio, LostFilm» (BigFANGroup), «WEB-DL [H.265/2160p] [4K, HDR10]
 * Red Head Sound» (NNM-Club), «WEB-DL-AVC | D, P, P2» (MegaPeer), поэтому каждое поле ищется
 * своим набором шаблонов по всему названию, а не по позиции.
 */
internal object ReleaseTraitsParser {

    fun parse(title: String): ReleaseTraits = ReleaseTraits(
        quality = quality(title),
        dynamicRange = dynamicRange(title),
        source = source(title),
        codec = codec(title),
        voiceKinds = ReleaseVoices.kinds(title),
        voices = ReleaseVoices.studios(title),
        seasons = ReleaseNames.seasons(title),
    )

    /**
     * Явная высота кадра важнее слов: «4K DMRip 1080p» — рип 1080p с 4K-мастера, а «4K» в нём
     * про исходник. Из нескольких высот — лучшая: «720p, 1080p» — это раздача 1080p.
     */
    private fun quality(title: String): VideoQuality? =
        listOf(HEIGHTS, QUALITY_WORDS).firstNotNullOfOrNull { patterns ->
            patterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(title) }?.first
        }

    private fun dynamicRange(title: String): Set<DynamicRange> =
        RANGE.filter { (_, pattern) -> pattern.containsMatchIn(title) }.mapTo(mutableSetOf()) { it.first }

    private fun source(title: String): ReleaseSource? =
        SOURCE.firstOrNull { (_, pattern) -> pattern.containsMatchIn(title) }?.first

    private fun codec(title: String): VideoCodec? =
        CODEC.firstOrNull { (_, pattern) -> pattern.containsMatchIn(title) }?.first

    /** От лучшего к худшему: побеждает первое найденное. */
    private val HEIGHTS = listOf(
        VideoQuality.ULTRA_HD to token("""2160[pр]"""),
        VideoQuality.FULL_HD to token("""1080[pрi]"""),
        VideoQuality.HD to token("""720[pр]"""),
        VideoQuality.SD to token("""(?:480|576|360)[pр]"""),
    )

    /** Когда высоты нет: «4K» и «UHD» — это 2160p, а рипы без высоты — SD. */
    private val QUALITY_WORDS = listOf(
        VideoQuality.ULTRA_HD to token("""4K|UHD"""),
        VideoQuality.SD to token("""WEB-?DL-?Rip|DVD-?Rip|HDRip|SAT-?Rip|TV-?Rip|VHS-?Rip|CAM-?Rip"""),
    )

    private val RANGE = listOf(
        DynamicRange.HDR10_PLUS to token("""HDR10\+|HDR10 ?Plus"""),
        // «HDR10+» — это не HDR10: обычный HDR ищется там, где за ним не идёт плюс.
        DynamicRange.HDR10 to token("""HDR(?:10)?(?![+\d]| ?Plus)"""),
        DynamicRange.DOLBY_VISION to token("""Dolby ?Vision|DoVi|DV"""),
    )

    /**
     * Порядок проверки, а не качества: «WEB-DLRip» — рип, и проверяется раньше WEB-DL, в который
     * иначе попал бы по префиксу.
     */
    private val SOURCE = listOf(
        ReleaseSource.REMUX to Regex("""Remux""", RegexOption.IGNORE_CASE),
        ReleaseSource.WEB_RIP to token("""WEB-?DL-?Rip|WEB-?Rip"""),
        ReleaseSource.WEB_DL to token("""WEB-?DL|WEB"""),
        ReleaseSource.BLU_RAY to token("""(?:UHD )?BD-?Rip|BR-?Rip|Blu-?Ray"""),
        ReleaseSource.HD_RIP to token("""HDRip"""),
        ReleaseSource.HDTV to token("""HDTV(?:-?Rip)?|SAT-?Rip|TV-?Rip|IPTV"""),
        ReleaseSource.DVD to token("""DVD-?Rip|DVD"""),
        ReleaseSource.CAM to token("""CAM-?Rip|CAM|TS|TC|TeleSync|Telecine"""),
    )

    private val CODEC = listOf(
        VideoCodec.HEVC to token("""H\.?265|x265|HEVC"""),
        VideoCodec.AV1 to token("""AV1"""),
        VideoCodec.AVC to token("""H\.?264|x264|AVC"""),
    )
}

/** Буквы обоих алфавитов: «границы слова» `\b` в регулярках Kotlin кириллицу не различают. */
internal const val LETTERS = "A-Za-zА-Яа-яЁё"

/** Шаблон [body] целым словом: не внутри другого слова или числа («DV» — не часть «DVD»). */
internal fun token(body: String): Regex =
    Regex("""(?<![$LETTERS\d])(?:$body)(?![$LETTERS\d])""", RegexOption.IGNORE_CASE)
