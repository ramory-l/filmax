package com.filmax.data.torrents

import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.core.domain.torrent.VoiceKind

/**
 * Правила автовыбора раздачи — как решил пользователь: сначала любимая озвучка (LostFilm,
 * HDrezka, Red Head Sound, потом любой дубляж), потом качество под телевизор (1080p — 4K по
 * торренту часто не успевает), потом сиды. Экранки отбрасываются, а мёртвые раздачи берутся,
 * только если живых нет.
 */
internal object ReleaseRanking {

    /** Лучшая раздача, в которой есть сезон [season] (null — фильм или сезон неважен). */
    fun best(releases: List<TorrentRelease>, season: Int?): TorrentRelease? {
        val candidates = releases.filter { it.traits.source != ReleaseSource.CAM && it.hasSeason(season) }
        val alive = candidates.filter { (it.seeders ?: 0) >= MIN_SEEDERS }.ifEmpty { candidates }
        return alive.minWithOrNull(ORDER)
    }

    /** Без перечня сезонов в названии — вероятно, раздача всего сериала: лучше, чем ничего. */
    private fun TorrentRelease.hasSeason(season: Int?): Boolean =
        season == null || traits.seasons?.contains(season) != false

    private val ORDER: Comparator<TorrentRelease> = compareBy<TorrentRelease>(
        { voiceRank(it.traits) },
        { qualityRank(it.traits.quality) },
    ).thenByDescending { it.seeders ?: 0 }

    /** Место озвучки в предпочтениях: студия из списка, дубляж, многоголоска, остальное. */
    private fun voiceRank(traits: ReleaseTraits): Int {
        val studio = STUDIOS.indexOfFirst { favourite -> traits.voices.any { it.equals(favourite, ignoreCase = true) } }
        return when {
            studio >= 0 -> studio
            VoiceKind.DUBBING in traits.voiceKinds -> STUDIOS.size
            VoiceKind.MULTI_VOICE in traits.voiceKinds -> STUDIOS.size + 1
            else -> STUDIOS.size + 2
        }
    }

    private fun qualityRank(quality: VideoQuality?): Int =
        QUALITIES.indexOf(quality).takeIf { it >= 0 } ?: QUALITIES.size

    /** Названия студий — как их пишет разбор озвучек (ReleaseVoices.KNOWN_STUDIOS). */
    private val STUDIOS = listOf("LostFilm", "HDrezka", "Red Head Sound")

    /** Качество по убыванию предпочтения; неизвестное — после всех известных. */
    private val QUALITIES = listOf(VideoQuality.FULL_HD, VideoQuality.ULTRA_HD, VideoQuality.HD, null, VideoQuality.SD)

    /** Меньше трёх сидов — раздача обычно не стартует за разумное время. */
    private const val MIN_SEEDERS = 3
}
