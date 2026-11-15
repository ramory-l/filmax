package com.filmax.data.hub

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.playback.StreamRepository
import com.filmax.core.domain.torrent.TorrentAutoChoice
import com.filmax.core.domain.torrent.TorrentStreams

/**
 * Видео хаба — только из раздач. Сюда приходят, когда раздача тайтла не выбрана или этой серии в
 * ней нет (переключатель потоков сначала спрашивает выбранную): раздача подбирается сама, по
 * правилам, и серия играет из неё.
 */
internal class HubStreamRepository(
    private val titles: HubTitles,
    private val autoChoice: TorrentAutoChoice,
    private val torrents: TorrentStreams,
) : StreamRepository {

    override suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack> {
        val item = when (val loaded = safeRequest { titles.get(itemId) }) {
            is RequestResult.Error -> return loaded
            is RequestResult.Success -> loaded.data
        }
        return when (val chosen = autoChoice.choose(item, track)) {
            is RequestResult.Error -> chosen
            is RequestResult.Success -> torrents.playable(itemId, track) ?: RequestResult.Error(NOT_IN_RELEASE)
        }
    }

    private companion object {
        const val NOT_IN_RELEASE = "В подобранной раздаче нет этой серии — выберите другую в «Торрентах»"
    }
}
