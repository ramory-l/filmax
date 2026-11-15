package com.filmax.data.hub

import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.watching.model.WatchHistory
import com.filmax.core.domain.watching.model.WatchProgress
import com.filmax.data.hub.local.HubProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Прогресс хаба на устройстве: шкала та же, что у kino.pub (−1/0/1), поэтому «Продолжить» и
 * отметки серий работают без изменений в экранах.
 */
class HubWatchingTest {

    private val hub = FakeHub().apply { connect() }
    private val watching = HubWatchingRepository(hub.progress, hub.titles)

    @Test
    fun `position in an episode comes back in history with its season`() = runTest {
        watching.saveProgress(FakeHub.SHOGUN.id, VideoRef.Episode(season = 2, number = 1), timeSeconds = 600)

        val progress = onlyProgress()
        assertEquals(HubProgress.STARTED, progress.status)
        assertEquals(600, progress.timeSeconds)
        assertEquals(4200, progress.durationSeconds)
        assertEquals(2 to 1, progress.season to progress.videoId)
    }

    @Test
    fun `the last tenth of a video counts as watched`() = runTest {
        val video = VideoRef.Movie(1)
        val runtime = checkNotNull(FakeHub.INTERSTELLAR.runtimeMinutes) * 60

        watching.saveProgress(FakeHub.INTERSTELLAR.id, video, timeSeconds = runtime * 9 / 10)

        assertEquals(HubProgress.WATCHED, onlyProgress().status)
        assertNull(onlyProgress().season)
    }

    @Test
    fun `same number in another season is another episode`() = runTest {
        watching.saveProgress(FakeHub.SHOGUN.id, VideoRef.Episode(season = 1, number = 1), timeSeconds = 100)

        val (seasonOneFirst, _, seasonTwoFirst) = hub.mapper.item(FakeHub.SHOGUN).tracklist
        assertEquals(100, seasonOneFirst.watchedSeconds)
        assertEquals(-1, seasonTwoFirst.watchStatus)
    }

    @Test
    fun `marking a title watched puts it in history and back`() = runTest {
        watching.toggleWatched(FakeHub.INTERSTELLAR.id)
        assertEquals(HubProgress.WATCHED, onlyProgress().status)

        watching.toggleWatched(FakeHub.INTERSTELLAR.id)
        assertEquals(HubProgress.STARTED, onlyProgress().status)
    }

    @Test
    fun `history is newest first, filtered by type and kept across restarts`() = runTest {
        watching.saveProgress(FakeHub.INTERSTELLAR.id, VideoRef.Movie(1), timeSeconds = 60)
        hub.now += 1_000
        watching.saveProgress(FakeHub.SHOGUN.id, VideoRef.Episode(season = 1, number = 2), timeSeconds = 60)

        assertEquals(listOf(FakeHub.SHOGUN.id, FakeHub.INTERSTELLAR.id), history().map { it.itemId })
        assertEquals(listOf(FakeHub.SHOGUN.id), history(type = "serial").map { it.itemId })

        val restarted = HubWatchingRepository(HubProgress(hub.settings, clock = { hub.now }), hub.titles)
        val kept = assertIs<RequestResult.Success<List<WatchHistory>>>(restarted.getHistory("")).data
        assertEquals(listOf(FakeHub.SHOGUN.id, FakeHub.INTERSTELLAR.id), kept.map { it.itemId })
    }

    @Test
    fun `clearing a title removes it from history`() = runTest {
        watching.saveProgress(FakeHub.INTERSTELLAR.id, VideoRef.Movie(1), timeSeconds = 60)

        watching.clearHistory(FakeHub.INTERSTELLAR.id)

        assertEquals(emptyList(), history())
    }

    @Test
    fun `video keys keep the season`() {
        listOf(VideoRef.Episode(season = 3, number = 2), VideoRef.Movie(1)).forEach { video ->
            assertEquals(video, HubProgress.video(HubProgress.key(video)))
        }
    }

    private suspend fun onlyProgress(): WatchProgress = checkNotNull(history().single().progress)

    private suspend fun history(type: String = ""): List<WatchHistory> =
        assertIs<RequestResult.Success<List<WatchHistory>>>(watching.getHistory(type)).data
}
