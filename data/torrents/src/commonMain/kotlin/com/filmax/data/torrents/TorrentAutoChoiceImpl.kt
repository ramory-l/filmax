package com.filmax.data.torrents

import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.TorrentAutoChoice
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.TorrentRepository
import com.filmax.core.domain.torrent.toChosen
import com.filmax.core.domain.torrent.torrentQuery

/** Поиск по трекерам и выбор по [ReleaseRanking]; выбор запоминается, как ручной. */
internal class TorrentAutoChoiceImpl(
    private val torrents: TorrentRepository,
    private val choices: TorrentChoices,
) : TorrentAutoChoice {

    override suspend fun choose(item: Item, track: MediaTrack): RequestResult<ChosenRelease> =
        when (val found = torrents.search(item.torrentQuery())) {
            is RequestResult.Error -> found
            is RequestResult.Success -> {
                val best = ReleaseRanking.best(found.data, track.seasonNumber.takeIf { it > 0 })?.toChosen()
                best?.let { chosen ->
                    choices.choose(item.id, chosen)
                    RequestResult.Success(chosen)
                } ?: RequestResult.Error(NOTHING_FITS)
            }
        }

    private companion object {
        const val NOTHING_FITS = "Подходящей раздачи не нашлось — выберите её в «Торрентах»"
    }
}
