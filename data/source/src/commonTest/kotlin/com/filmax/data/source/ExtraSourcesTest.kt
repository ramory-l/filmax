package com.filmax.data.source

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.playback.StreamRepository
import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.torrent.TorrentStreams
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Переключатель домашнего сервера и раздач: выключен по умолчанию, но не у того, кто уже смотрит
 * домашний сервер; выключение возвращает на kino.pub; выключенные раздачи не подменяют поток.
 */
class ExtraSourcesTest {

    private val settings = MapSettings()
    private val sources = ContentSourceRepositoryImpl(settings)

    @Test
    fun `fresh install keeps the home server and torrents hidden`() {
        assertFalse(ExtraSourcesImpl(settings, sources).enabled.value)
    }

    @Test
    fun `home server user keeps it after the update`() = runTest {
        sources.select(ContentSource.JELLYFIN)

        assertTrue(ExtraSourcesImpl(settings, sources).enabled.value)
    }

    @Test
    fun `switching off on the home server goes back to kino pub and is remembered`() = runTest {
        sources.select(ContentSource.JELLYFIN)
        val extras = ExtraSourcesImpl(settings, sources)

        extras.setEnabled(false)

        assertEquals(ContentSource.KINOPUB, sources.source.value)
        assertFalse(ExtraSourcesImpl(settings, sources).enabled.value)
    }

    @Test
    fun `chosen torrent plays only while torrents are on`() = runTest {
        val extras = ExtraSourcesImpl(settings, sources)
        val streams = RoutingStreamRepository(
            switch = SourceSwitch(
                sources = sources,
                kinopub = lazy { SourceStreams("kino.pub") },
                homeServer = lazy { SourceStreams("server") },
                hub = lazy { SourceStreams("hub") },
            ),
            torrents = TorrentStreamsStub,
            extras = extras,
        )

        assertEquals("kino.pub", streams.origin())
        extras.setEnabled(true)
        assertEquals("torrent", streams.origin())
    }

    /** Откуда пришёл трек: заглушки подписывают его своим именем. */
    private suspend fun StreamRepository.origin(): String? =
        (playable(ITEM_ID, TRACK) as? RequestResult.Success)?.data?.title

    private class SourceStreams(private val name: String) : StreamRepository {
        override suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack> =
            RequestResult.Success(track.copy(title = name))
    }

    private object TorrentStreamsStub : TorrentStreams {
        override suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack> =
            RequestResult.Success(track.copy(title = "torrent"))
    }

    private companion object {
        const val ITEM_ID = 1
        val TRACK = MediaTrack(
            id = 1,
            number = 1,
            seasonNumber = 0,
            title = "",
            thumbnail = "",
            durationSeconds = 0,
            files = emptyList(),
            audios = emptyList(),
            subtitles = emptyList(),
        )
    }
}
