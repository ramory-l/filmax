package com.filmax.core.presentation.format

import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.VideoQuality

/** «4K», «1080p», «720p», «SD» — как качество пишут трекеры и плеер. */
fun qualityLabel(quality: VideoQuality): String = when (quality) {
    VideoQuality.SD -> "SD"
    VideoQuality.HD -> "720p"
    VideoQuality.FULL_HD -> "1080p"
    VideoQuality.ULTRA_HD -> "4K"
}

/**
 * Подпись кнопки торрентов в деталях: выбранная раздача — «Торрент · 4K», чтобы было видно, откуда
 * пойдёт видео; без выбора — «Торренты».
 */
fun torrentButtonLabel(chosen: ChosenRelease?): String {
    if (chosen == null) return "Торренты"
    return chosen.quality?.let { "Торрент · ${qualityLabel(it)}" } ?: "Торрент"
}
