package com.filmax.data.torrents

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.catalog.model.VideoFile
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.TorrentContents
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.TorrentStreams
import com.filmax.core.domain.torrent.TorrentVideo
import com.filmax.data.torrents.remote.streamUrl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Серия из выбранной раздачи. Трек остаётся треком источника (id, номер, прогресс), меняется
 * только поток: один файл с TorrServer. Озвучки и субтитры источника к чужому файлу не подходят —
 * плеер возьмёт дорожки из самого файла.
 */
internal class TorrentStreamsImpl(
    private val choices: TorrentChoices,
    private val torrents: TorrentRepository,
    private val store: TorrServerStore,
) : TorrentStreams {

    /** Открытые раздачи: соседняя серия не добавляет ту же раздачу в TorrServer заново. */
    private val opened = mutableMapOf<String, TorrentContents>()
    private val lock = Mutex()

    override suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack>? {
        val chosen = choices.chosen(itemId)
        val address = store.server.value?.address
        if (chosen == null || address == null) return null
        return when (val result = contents(chosen)) {
            is RequestResult.Error -> result
            is RequestResult.Success -> TorrentVideoPicker.pick(result.data.videos, track, chosen.seasons)
                ?.let { video -> RequestResult.Success(track.streamedFrom(address, result.data.hash, video, chosen)) }
        }
    }

    private fun MediaTrack.streamedFrom(
        address: String,
        hash: String,
        video: TorrentVideo,
        chosen: ChosenRelease,
    ): MediaTrack {
        val url = streamUrl(address, hash, video.index, video.path.substringAfterLast('/'))
        val file = VideoFile(quality = qualityLabel(chosen), hls4 = null, hls = null, http = url)
        return copy(files = listOf(file), audios = emptyList(), subtitles = emptyList())
    }

    private suspend fun contents(chosen: ChosenRelease): RequestResult<TorrentContents> = lock.withLock {
        opened[chosen.link]?.let { return@withLock RequestResult.Success(it) }
        torrents.open(chosen.link, poster = null).also { result ->
            if (result is RequestResult.Success) opened[chosen.link] = result.data
        }
    }

    /** Подпись качества плеера: «1080p» — по ней плеер решает, не выше ли она потолка декодера. */
    private fun qualityLabel(chosen: ChosenRelease): String = chosen.quality?.let { "${it.height}p" } ?: TORRENT_LABEL

    private companion object {
        const val TORRENT_LABEL = "Торрент"
    }
}

/** Какой файл раздачи играть для трека источника. */
internal object TorrentVideoPicker {

    /**
     * Фильм (трек без сезона) — самый большой файл: рядом лежат трейлеры и допы. Серия — файл с тем
     * же номером в том же сезоне. Сезон не нашёлся ни в пути, ни в названии раздачи — значит, он
     * один, первый. null — такой серии в раздаче нет.
     */
    fun pick(videos: List<TorrentVideo>, track: MediaTrack, releaseSeasons: IntRange?): TorrentVideo? {
        if (track.seasonNumber <= 0) return videos.maxByOrNull { it.sizeBytes }
        val releaseSeason = releaseSeasons?.takeIf { it.first == it.last }?.first
        return videos.firstOrNull { video ->
            video.episode == track.number && (video.season ?: releaseSeason ?: FIRST_SEASON) == track.seasonNumber
        }
    }

    private const val FIRST_SEASON = 1
}
