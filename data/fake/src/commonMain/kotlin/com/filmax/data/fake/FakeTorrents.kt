package com.filmax.data.fake

import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.TorrServer
import com.filmax.core.domain.torrent.TorrServerRepository
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.TorrentContents
import com.filmax.core.domain.torrent.TorrentQuery
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.TorrentVideo
import com.filmax.core.domain.torrent.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** TorrServer для юнит-тестов: отвечает только по адресу [reachable]. */
class FakeTorrServerRepository(
    initial: TorrServer? = null,
    private val reachable: String = SERVER.address,
) : TorrServerRepository {

    private val state = MutableStateFlow(initial)
    override val server: StateFlow<TorrServer?> = state.asStateFlow()

    override suspend fun connect(address: String): RequestResult<TorrServer> {
        if (address != reachable) return RequestResult.Error("Failed to connect to $address")
        return RequestResult.Success(SERVER).also { state.value = SERVER }
    }

    override fun forget() {
        state.value = null
    }

    companion object {
        val SERVER = TorrServer(address = "192.168.1.20:8090", version = "MatriX.150")
    }
}

/**
 * Трекеры для юнит-тестов: [releases] на любой запрос, [failSearch] — поиск не ответил. Раздача
 * открывается с [videos] видеофайлами; запросы складываются в [queries] и [opened].
 */
class FakeTorrentRepository(
    var releases: List<TorrentRelease> = SAMPLE,
    var failSearch: Boolean = false,
    var videos: Int = 1,
) : TorrentRepository {

    val queries = mutableListOf<TorrentQuery>()
    val opened = mutableListOf<String>()

    override suspend fun search(query: TorrentQuery): RequestResult<List<TorrentRelease>> {
        queries += query
        return if (failSearch) RequestResult.Error("timeout") else RequestResult.Success(releases)
    }

    override suspend fun open(link: String, poster: String?): RequestResult<TorrentContents> {
        opened += link
        val files = (1..videos).map { index ->
            TorrentVideo(index = index, path = "$link/E$index.mkv", sizeBytes = GIB, season = 1, episode = index)
        }
        return RequestResult.Success(TorrentContents(hash = "hash-$link", name = link, videos = files))
    }

    companion object {
        private const val GIB = 1L shl 30

        fun release(
            link: String,
            seeders: Int,
            quality: VideoQuality? = null,
            voices: List<String> = emptyList(),
            seasons: IntRange? = null,
        ) = TorrentRelease(
            title = "Раздача $link",
            tracker = "NoNaMe Club",
            link = link,
            sizeBytes = GIB * seeders,
            seeders = seeders,
            peers = 1,
            publishedDate = "2025-01-0${seeders % 10}",
            traits = ReleaseTraits(quality = quality, voices = voices, seasons = seasons),
        )

        val SAMPLE = listOf(
            release("uhd", seeders = 3, quality = VideoQuality.ULTRA_HD, voices = listOf("LostFilm"), seasons = 1..1),
            release("fhd", seeders = 9, quality = VideoQuality.FULL_HD, voices = listOf("HDrezka"), seasons = 1..2),
            release("s2", seeders = 5, quality = VideoQuality.FULL_HD, voices = listOf("LostFilm"), seasons = 2..2),
        )
    }
}

/** Выбор раздачи в памяти. */
class FakeTorrentChoices : TorrentChoices {

    private val state = MutableStateFlow<Map<Int, ChosenRelease>>(emptyMap())

    override fun chosen(itemId: Int): ChosenRelease? = state.value[itemId]

    override fun observe(itemId: Int): Flow<ChosenRelease?> = state.map { it[itemId] }

    override fun choose(itemId: Int, release: ChosenRelease) {
        state.value += itemId to release
    }

    override fun forget(itemId: Int) {
        state.value -= itemId
    }
}
