package com.filmax.feature.torrents.common

import com.filmax.core.domain.torrent.DynamicRange
import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoCodec
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.core.domain.torrent.VoiceKind
import com.filmax.core.presentation.format.qualityLabel

/**
 * Подписи раздачи человеческим языком — общие для телефона и ТВ: бейджи качества, озвучка,
 * размер и дата. Названия трекеров длинные и пёстрые, поэтому главное о раздаче строка
 * показывает отдельно от названия.
 */
object ReleaseLabels {

    fun quality(quality: VideoQuality): String = qualityLabel(quality)

    /** Бейджи строки: качество, HDR, кодек и источник. H.264 не пишется — его играет любой ТВ. */
    fun badges(traits: ReleaseTraits): List<String> = listOfNotNull(traits.quality?.let(::qualityLabel)) +
        DynamicRange.entries.filter { it in traits.dynamicRange }.map(::range) +
        listOfNotNull(traits.codec?.takeIf { it != VideoCodec.AVC }?.let(::codec), traits.source?.let(::source))

    /** Студии озвучки; если трекер написал только вид — вид: «Дубляж, многоголосая». */
    fun voices(traits: ReleaseTraits): String? {
        if (traits.voices.isNotEmpty()) return traits.voices.joinToString(", ")
        return VoiceKind.entries.filter { it in traits.voiceKinds }
            .map(::voiceKind)
            .joinToString(", ")
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { null }
    }

    /** Трекер и день публикации: «NoNaMe Club · 8 янв 2025». */
    fun meta(release: TorrentRelease): String =
        listOfNotNull(release.tracker.ifBlank { null }, release.publishedDate?.let(::date)).joinToString(" · ")

    /** «591 МБ», «1,5 ГБ», «40 ГБ»: от десяти гигабайт десятые — шум. */
    fun size(bytes: Long?): String? = when {
        bytes == null || bytes <= 0 -> null
        bytes < GIB -> "${bytes / MIB} МБ"
        bytes >= GIB * WHOLE_GIB_FROM -> "${bytes / GIB} ГБ"
        else -> (bytes * TENTHS / GIB).let { tenths -> "${tenths / TENTHS},${tenths % TENTHS} ГБ" }
    }

    /** «2025-01-08» → «8 янв 2025»; не дата — как есть. */
    fun date(day: String): String {
        val parts = day.split('-').mapNotNull { it.toIntOrNull() }
        val monthName = parts.getOrNull(1)?.let { MONTHS.getOrNull(it - 1) }
        if (parts.size != DATE_PARTS || monthName == null) return day
        val (year, _, dayOfMonth) = parts
        return "$dayOfMonth $monthName $year"
    }

    private fun range(range: DynamicRange): String = when (range) {
        DynamicRange.HDR10 -> "HDR"
        DynamicRange.HDR10_PLUS -> "HDR10+"
        DynamicRange.DOLBY_VISION -> "DV"
    }

    private fun codec(codec: VideoCodec): String = when (codec) {
        VideoCodec.AVC -> "H.264"
        VideoCodec.HEVC -> "HEVC"
        VideoCodec.AV1 -> "AV1"
    }

    private fun source(source: ReleaseSource): String = when (source) {
        ReleaseSource.REMUX -> "Remux"
        ReleaseSource.BLU_RAY -> "Blu-ray"
        ReleaseSource.WEB_DL -> "WEB-DL"
        ReleaseSource.WEB_RIP -> "WEBRip"
        ReleaseSource.HD_RIP -> "HDRip"
        ReleaseSource.HDTV -> "HDTV"
        ReleaseSource.DVD -> "DVD"
        ReleaseSource.CAM -> "CAM"
    }

    private fun voiceKind(kind: VoiceKind): String = when (kind) {
        VoiceKind.DUBBING -> "дубляж"
        VoiceKind.MULTI_VOICE -> "многоголосая"
        VoiceKind.TWO_VOICE -> "двухголосая"
        VoiceKind.SINGLE_VOICE -> "одноголосая"
        VoiceKind.ORIGINAL -> "оригинал"
        VoiceKind.SUBTITLES -> "субтитры"
    }

    private const val MIB = 1024L * 1024
    private const val GIB = MIB * 1024
    private const val WHOLE_GIB_FROM = 10
    private const val TENTHS = 10
    private const val DATE_PARTS = 3
    private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
}
