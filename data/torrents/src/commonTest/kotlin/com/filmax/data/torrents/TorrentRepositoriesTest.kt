package com.filmax.data.torrents

import com.filmax.core.domain.catalog.model.AudioTrack
import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.catalog.model.SubtitleTrack
import com.filmax.core.domain.catalog.model.VideoFile
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.TorrServer
import com.filmax.core.domain.torrent.TorrentContents
import com.filmax.core.domain.torrent.TorrentQuery
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Адрес сервера, поиск, открытие раздачи, выбор на тайтл и поток серии — на подменённом TorrServer. */
class TorrentRepositoriesTest {

    private val shogun = TorrentQuery(names = listOf("Сёгун", "Shogun"), year = 2024, episodic = true)

    @Test
    fun `address without scheme gets http and the default port`() {
        assertEquals("http://192.168.1.20:8090", normalizeTorrServerAddress(" 192.168.1.20 "))
        assertEquals("http://nas.local:8092", normalizeTorrServerAddress("nas.local:8092/"))
        assertEquals("https://ts.example.ru", normalizeTorrServerAddress("https://ts.example.ru/"))
        assertFailsWith<IllegalArgumentException> { normalizeTorrServerAddress("  ") }
    }

    @Test
    fun `connect checks the server and remembers it`() = runTest {
        val server = FakeTorrServer()
        val repository = TorrServerRepositoryImpl(server.api, server.store)

        val connected = repository.connect("192.168.1.178:8092")

        assertEquals(RequestResult.Success(TorrServer("http://192.168.1.178:8092", "MatriX.150")), connected)
        assertEquals("http://192.168.1.178:8092", TorrServerStore(server.settings).server.value?.address)
        repository.forget()
        assertNull(repository.server.value)
    }

    @Test
    fun `search asks for every name and keeps only releases of the title`() = runTest {
        val server = FakeTorrServer(
            searches = mapOf(
                "Сёгун" to torznab(
                    Triple(BIGFAN_SHOGUN, "40.0 GiB", 1183),
                    Triple("Сёгун / Shogun  / S1E1-3 of 3 (1980) BDRip [H.264/1080p]  ОРТ", "37.9 GiB", 11),
                    Triple(NNM_SHOGUN_4K, "69.4 GiB", 14),
                ),
                "Shogun" to torznab(
                    Triple(BIGFAN_SHOGUN, "40.0 GiB", 1183),
                    Triple("Shogun.2024.S01.1080p.WEB-DL.H264", "30.2 GiB", null),
                ),
            ),
        )
        server.store.save(TorrServer(ADDRESS, "MatriX.150"))

        val found = assertIs<RequestResult.Success<List<TorrentRelease>>>(
            TorrentRepositoryImpl(server.api, server.store).search(shogun),
        ).data

        val expected = listOf(BIGFAN_SHOGUN, NNM_SHOGUN_4K.trim(), "Shogun.2024.S01.1080p.WEB-DL.H264")
        assertEquals(expected, found.map { it.title }, "раздача из обоих запросов — одна строка, хоть ссылки и разные")
        assertEquals(42_949_672_960, found.first().sizeBytes)
        assertEquals("2025-01-08", found.first().publishedDate)
        val queries = server.requestsTo("/torznab/search/").map { it.parameters["query"] }
        assertEquals(setOf("Сёгун", "Shogun"), queries.toSet())
    }

    @Test
    fun `search without a server is an error, not a request to nowhere`() = runTest {
        val server = FakeTorrServer()

        assertIs<RequestResult.Error>(TorrentRepositoryImpl(server.api, server.store).search(shogun))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `open lists videos with episode numbers and drops subtitles and samples`() = runTest {
        val server = FakeTorrServer(added = SHOGUN_STATUS)
        server.store.save(TorrServer(ADDRESS, "MatriX.150"))

        val contents = assertIs<RequestResult.Success<TorrentContents>>(
            TorrentRepositoryImpl(server.api, server.store).open("http://jackett/dl/1", poster = null),
        ).data

        assertEquals(SHOGUN_HASH, contents.hash)
        assertEquals(listOf(1 to 1, 1 to 2), contents.videos.map { it.season to it.episode })
        val body = server.requestsTo("/torrents").single().body
        assertTrue("\"save_to_db\":false" in body && "filmax" in body && "title" !in body, body)
    }

    @Test
    fun `magnet waits for metadata before listing files`() = runTest {
        val server = FakeTorrServer(
            added = """{"hash":"$SHOGUN_HASH","stat":1,"file_stats":[]}""",
            statuses = listOf("""{"hash":"$SHOGUN_HASH","stat":1}""", SHOGUN_STATUS),
        )
        server.store.save(TorrServer(ADDRESS, "MatriX.150"))

        val repository = TorrentRepositoryImpl(server.api, server.store)
        val contents = repository.open("magnet:?xt=urn:btih:$SHOGUN_HASH", poster = null)

        assertEquals(2, assertIs<RequestResult.Success<TorrentContents>>(contents).data.videos.size)
        assertEquals(3, server.requestsTo("/torrents").size)
    }

    @Test
    fun `chosen release survives a restart and notifies its title`() = runTest {
        val settings = FakeTorrServer().settings
        val choices = TorrentChoicesImpl(settings)

        choices.choose(ITEM_ID, CHOSEN)

        assertEquals(CHOSEN, TorrentChoicesImpl(settings).chosen(ITEM_ID))
        assertEquals(CHOSEN, choices.observe(ITEM_ID).first())
        choices.forget(ITEM_ID)
        assertNull(choices.observe(ITEM_ID).first())
    }

    @Test
    fun `episode of the chosen release plays from torrserver`() = runTest {
        val server = FakeTorrServer(added = SHOGUN_STATUS)
        server.store.save(TorrServer(ADDRESS, "MatriX.150"))
        val choices = TorrentChoicesImpl(server.settings).apply { choose(ITEM_ID, CHOSEN) }
        val streams = TorrentStreamsImpl(choices, TorrentRepositoryImpl(server.api, server.store), server.store)
        val sourceTrack = track(season = 1, number = 2).copy(
            files = listOf(VideoFile("1080p", hls4 = "https://cdn/hls4", hls = null, http = null)),
            audios = listOf(AudioTrack(id = 1, index = 1, lang = "rus", title = null, channels = 2)),
            subtitles = listOf(SubtitleTrack(lang = "rus", url = "https://cdn/sub.srt", shiftMs = 0)),
        )

        val played = assertIs<RequestResult.Success<MediaTrack>>(
            streams.playable(ITEM_ID, sourceTrack),
        ).data

        val file = played.files.single()
        assertEquals("1080p", file.quality)
        assertEquals("$ADDRESS/stream/Shogun.S01E02.WEB-DLRip.avi?link=$SHOGUN_HASH&index=2&play=", file.http)
        assertEquals(sourceTrack.id, played.id)
        assertTrue(played.audios.isEmpty() && played.subtitles.isEmpty())
        streams.playable(ITEM_ID, track(season = 1, number = 1))
        assertEquals(1, server.requestsTo("/torrents").size, "соседняя серия не добавляет раздачу заново")
    }

    @Test
    fun `source plays when nothing is chosen, the server is gone or the episode is missing`() = runTest {
        val server = FakeTorrServer(added = SHOGUN_STATUS)
        val choices = TorrentChoicesImpl(server.settings)
        val streams = TorrentStreamsImpl(choices, TorrentRepositoryImpl(server.api, server.store), server.store)

        assertNull(streams.playable(ITEM_ID, track(season = 1, number = 1)))
        choices.choose(ITEM_ID, CHOSEN)
        assertNull(streams.playable(ITEM_ID, track(season = 1, number = 1)), "сервер не настроен")
        server.store.save(TorrServer(ADDRESS, "MatriX.150"))
        assertNull(streams.playable(ITEM_ID, track(season = 1, number = 9)), "девятой серии в раздаче нет")
    }

    private companion object {
        const val ADDRESS = "http://192.168.1.178:8092"
        const val ITEM_ID = 4242
        val CHOSEN = ChosenRelease(
            link = "http://jackett/dl/1",
            key = "NoNaMe Club|Сёгун|42949672960",
            title = "Сёгун",
            quality = VideoQuality.FULL_HD,
            seasons = 1..1,
        )
    }
}
