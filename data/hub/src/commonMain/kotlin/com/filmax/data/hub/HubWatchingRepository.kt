package com.filmax.data.hub

import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.watching.WatchingRepository
import com.filmax.core.domain.watching.model.WatchHistory
import com.filmax.core.domain.watching.model.WatchProgress
import com.filmax.data.hub.local.HubProgress
import com.filmax.data.hub.local.StoredTitle

/**
 * Просмотр на хабе — прогресс на устройстве ([HubProgress]). Карточка тайтла для снимка в историю
 * берётся из [HubTitles]: плеер открывают из деталей, и она уже в памяти.
 */
internal class HubWatchingRepository(
    private val progress: HubProgress,
    private val titles: HubTitles,
) : WatchingRepository {

    override suspend fun getHistory(type: String): RequestResult<List<WatchHistory>> = safeRequest {
        progress.history().filter { title -> type.fits(title) }.map { it.toHistory() }
    }

    override suspend fun saveProgress(itemId: Int, video: VideoRef, timeSeconds: Int): RequestResult<Unit> =
        safeRequest { progress.save(titles.get(itemId), video, timeSeconds) }

    override suspend fun toggleWatched(itemId: Int): RequestResult<Unit> =
        safeRequest { progress.toggleWatched(titles.get(itemId)) }

    override suspend fun clearHistory(itemId: Int): RequestResult<Unit> = safeRequest { progress.clear(itemId) }

    /** Типы истории — как у kino.pub: «movie», «serial» или всё. */
    private fun String.fits(title: StoredTitle): Boolean = when (this) {
        "movie" -> !title.episodic
        "serial" -> title.episodic
        else -> true
    }

    private fun StoredTitle.toHistory(): WatchHistory {
        val video = HubProgress.video(last)
        val position = videos[last]
        return WatchHistory(
            itemId = itemId,
            title = title,
            posterSmall = poster,
            progress = WatchProgress(
                status = if (watched) HubProgress.WATCHED else position?.status ?: HubProgress.STARTED,
                timeSeconds = position?.seconds,
                durationSeconds = position?.duration?.takeIf { it > 0 },
                videoId = video?.number,
                season = (video as? VideoRef.Episode)?.season,
            ),
            posterWide = wide,
            lastSeenAt = lastSeenAt,
        )
    }
}
