package com.filmax.feature.torrents.common.navigation

import com.filmax.core.domain.catalog.model.VideoRef
import kotlinx.serialization.Serializable

/**
 * Маршрут выбора раздачи. [season] и [number] — видео, которое запустится из выбранной раздачи,
 * парой чисел, как у плеера: номер серии уникален только в сезоне, поэтому сезон едет вместе с
 * ним. [VideoRef.NONE] — фильм или «что продолжить», решит плеер.
 */
@Serializable
data class TorrentsRoute(
    val itemId: Int,
    val season: Int = VideoRef.NONE,
    val number: Int = VideoRef.NONE,
)
