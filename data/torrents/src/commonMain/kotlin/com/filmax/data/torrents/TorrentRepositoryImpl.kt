package com.filmax.data.torrents

import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.torrent.TorrentContents
import com.filmax.core.domain.torrent.TorrentQuery
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.TorrentVideo
import com.filmax.data.torrents.parse.EpisodeNumbers
import com.filmax.data.torrents.parse.ReleaseMatcher
import com.filmax.data.torrents.parse.ReleaseNames
import com.filmax.data.torrents.parse.ReleaseTraitsParser
import com.filmax.data.torrents.parse.SizeText
import com.filmax.data.torrents.remote.TorrServerApi
import com.filmax.data.torrents.remote.TorrentStatusDto
import com.filmax.data.torrents.remote.TorznabResultDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

internal class TorrentRepositoryImpl(
    private val api: TorrServerApi,
    private val store: TorrServerStore,
) : TorrentRepository {

    /**
     * Ищет по каждому названию отдельно и параллельно: русские трекеры находят «Сёгун», но не
     * «Shogun», зарубежные релизы — наоборот. Раздача, найденная обоими запросами, — одна строка:
     * склеиваются по [TorrentRelease.key], ссылки у них разные.
     */
    override suspend fun search(query: TorrentQuery): RequestResult<List<TorrentRelease>> = safeRequest {
        val address = store.requireAddress()
        val names = query.names.filter { it.isNotBlank() }.distinctBy(ReleaseNames::normalize).take(MAX_QUERIES)
        val found = coroutineScope { names.map { name -> async { api.search(address, name) } }.awaitAll() }
        found.flatten()
            .mapNotNull { it.toRelease() }
            .filter { ReleaseMatcher.matches(it.title, query) }
            .distinctBy { it.key }
            .sortedByDescending { it.seeders ?: 0 }
    }

    override suspend fun open(link: String, poster: String?): RequestResult<TorrentContents> =
        safeRequest {
            val address = store.requireAddress()
            val added = api.add(address, link, poster)
            val hash = added.hash?.takeIf { it.isNotBlank() } ?: error("TorrServer не принял раздачу")
            val status = if (added.files.isNullOrEmpty()) awaitFiles(address, hash) else added
            status.toContents(hash)
        }

    /** Магнит: файлы известны, когда пиры отдадут метаданные; ссылка на .torrent — сразу. */
    private suspend fun awaitFiles(address: String, hash: String): TorrentStatusDto {
        repeat(METADATA_POLLS) {
            delay(METADATA_POLL_MS)
            val status = api.status(address, hash)
            if (!status.files.isNullOrEmpty()) return status
        }
        error("Раздача не отвечает: нет пиров")
    }

    private companion object {
        /** Русское и оригинальное название; больше — уже повторы и лишние запросы к трекерам. */
        const val MAX_QUERIES = 2
        const val METADATA_POLLS = 30
        const val METADATA_POLL_MS = 1_000L
    }
}

private fun TorznabResultDto.toRelease(): TorrentRelease? {
    val releaseTitle = (title ?: name)?.trim()?.takeIf { it.isNotEmpty() }
    val releaseLink = magnet?.takeIf { it.isNotBlank() } ?: link?.takeIf { it.isNotBlank() }
    if (releaseTitle == null || releaseLink == null) return null
    return TorrentRelease(
        title = releaseTitle,
        tracker = tracker.orEmpty(),
        link = releaseLink,
        sizeBytes = SizeText.bytes(size),
        seeders = seeders?.toInt(),
        peers = peers?.toInt(),
        publishedDate = createDate?.take(DATE_LENGTH)?.takeIf { DATE.matches(it) },
        traits = ReleaseTraitsParser.parse(releaseTitle),
    )
}

/** Видео раздачи без образцов: номер серии — из пути, у фильма он просто не пригодится. */
private fun TorrentStatusDto.toContents(hash: String): TorrentContents = TorrentContents(
    hash = hash,
    name = name ?: title.orEmpty(),
    videos = files.orEmpty().mapNotNull { file ->
        val index = file.id ?: return@mapNotNull null
        val path = file.path ?: return@mapNotNull null
        val size = file.length ?: 0
        if (!EpisodeNumbers.isVideo(path) || EpisodeNumbers.isSample(path, size)) return@mapNotNull null
        val number = EpisodeNumbers.parse(path)
        TorrentVideo(index = index, path = path, sizeBytes = size, season = number?.season, episode = number?.episode)
    },
)

private const val DATE_LENGTH = 10
private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
