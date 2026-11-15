package com.filmax.feature.torrents.common

import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoQuality

/** Фильтр раздач, у которого есть панель вариантов. */
enum class ReleaseFilterKind(val label: String) {
    QUALITY("Качество"),
    VOICE("Озвучка"),
    SEASON("Сезон"),
    SORT("Порядок"),
}

/** Порядок списка. По сидам — первым делом: живая раздача стартует за секунды, мёртвая — никогда. */
enum class ReleaseSort(val label: String, internal val comparator: Comparator<TorrentRelease>) {
    SEEDERS("По сидам", compareByDescending { it.seeders ?: -1 }),
    SIZE("По размеру", compareByDescending { it.sizeBytes ?: -1L }),
    DATE("Сначала новые", compareByDescending { it.publishedDate.orEmpty() }),
}

/** Что выбрано в фильтрах; null — «все». */
data class ReleaseFilters(
    val quality: VideoQuality? = null,
    val voice: String? = null,
    val season: Int? = null,
    val sort: ReleaseSort = ReleaseSort.SEEDERS,
) {
    /**
     * Раздачи под фильтрами в порядке [sort]. Раздача без пометки сезона остаётся под любым
     * сезоном: «Южный парк (1997–2016)» без «S1-20» — скорее всё сразу, чем ни одного.
     */
    fun applyTo(releases: List<TorrentRelease>): List<TorrentRelease> = releases
        .filter { release -> quality == null || release.traits.quality == quality }
        .filter { release -> voice == null || voice in release.traits.voices }
        .filter { release -> season == null || release.traits.seasons?.contains(season) != false }
        .sortedWith(sort.comparator)

    /**
     * Фильтры после поиска: порядок остаётся, а сезон — тот, чью серию запустят, если раздачи с
     * ним есть. Так «Торренты» с пятой серии второго сезона сразу показывают раздачи второго.
     */
    internal fun startingAt(video: VideoRef?, releases: List<TorrentRelease>): ReleaseFilters {
        val season = (video as? VideoRef.Episode)?.season
        val seasons = ReleaseOptions.of(releases).seasons
        return ReleaseFilters(sort = sort, season = season?.takeIf { it in seasons })
    }
}

/** Варианты фильтров — только то, что есть среди найденных раздач. */
data class ReleaseOptions(
    /** От лучшего к худшему. */
    val qualities: List<VideoQuality>,
    /** Студии по числу раздач: самые частые — первыми, редкие не помещаются. */
    val voices: List<String>,
    val seasons: List<Int>,
) {
    companion object {
        fun of(releases: List<TorrentRelease>): ReleaseOptions = ReleaseOptions(
            qualities = releases.mapNotNull { it.traits.quality }.distinct().sortedByDescending { it.height },
            voices = releases.flatMap { it.traits.voices }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(MAX_VOICES)
                .map { it.key },
            seasons = releases.flatMap { it.traits.seasons?.toList().orEmpty() }.distinct().sorted(),
        )
    }
}

/** Студий в панели — сколько помещается на экран ТВ без прокрутки; редкие студии ищут сортировкой. */
private const val MAX_VOICES = 10

/** Есть ли у фильтра из чего выбирать: панель сезонов у фильма или с одним вариантом не нужна. */
fun TorrentsState.hasChoice(kind: ReleaseFilterKind): Boolean = when (kind) {
    ReleaseFilterKind.QUALITY -> options.qualities.size > 1
    ReleaseFilterKind.VOICE -> options.voices.isNotEmpty()
    ReleaseFilterKind.SEASON -> options.seasons.size > 1
    ReleaseFilterKind.SORT -> releases.size > 1
}

/** Варианты панели фильтра [kind]; первый у всех, кроме порядка, — «все». */
fun TorrentsState.optionLabels(kind: ReleaseFilterKind): List<String> = when (kind) {
    ReleaseFilterKind.QUALITY -> listOf(ALL_QUALITIES) + options.qualities.map(ReleaseLabels::quality)
    ReleaseFilterKind.VOICE -> listOf(ALL_VOICES) + options.voices
    ReleaseFilterKind.SEASON -> listOf(ALL_SEASONS) + options.seasons.map { "Сезон $it" }
    ReleaseFilterKind.SORT -> ReleaseSort.entries.map { it.label }
}

/** Номер выбранного варианта в [optionLabels]. */
fun TorrentsState.selectedOption(kind: ReleaseFilterKind): Int = when (kind) {
    ReleaseFilterKind.QUALITY -> options.qualities.indexOf(filters.quality) + 1
    ReleaseFilterKind.VOICE -> options.voices.indexOf(filters.voice) + 1
    ReleaseFilterKind.SEASON -> options.seasons.indexOf(filters.season) + 1
    ReleaseFilterKind.SORT -> filters.sort.ordinal
}

/** Подпись чипа фильтра: выбранное значение, а «все» — названием фильтра. */
fun TorrentsState.filterLabel(kind: ReleaseFilterKind): String {
    val selected = selectedOption(kind)
    if (kind != ReleaseFilterKind.SORT && selected == 0) return kind.label
    return optionLabels(kind).getOrElse(selected) { kind.label }
}

/** Фильтры после выбора варианта [index] в панели [kind]. */
internal fun TorrentsState.filtersWith(kind: ReleaseFilterKind, index: Int): ReleaseFilters = when (kind) {
    ReleaseFilterKind.QUALITY -> filters.copy(quality = options.qualities.getOrNull(index - 1))
    ReleaseFilterKind.VOICE -> filters.copy(voice = options.voices.getOrNull(index - 1))
    ReleaseFilterKind.SEASON -> filters.copy(season = options.seasons.getOrNull(index - 1))
    ReleaseFilterKind.SORT -> filters.copy(sort = ReleaseSort.entries.getOrElse(index) { filters.sort })
}

private const val ALL_QUALITIES = "Любое качество"
private const val ALL_VOICES = "Любая озвучка"
private const val ALL_SEASONS = "Все сезоны"
