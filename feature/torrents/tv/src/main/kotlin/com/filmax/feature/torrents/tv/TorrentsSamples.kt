package com.filmax.feature.torrents.tv

import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.DynamicRange
import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.TorrServer
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoCodec
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.feature.torrents.common.TorrentsStage
import com.filmax.feature.torrents.common.TorrentsState

/** Состояния экрана для превью: подключение, поиск, раздачи с выбранной и сбой. */
internal object TorrentsSamples {

    private const val GIB = 1L shl 30
    private val server = TorrServer(address = "http://192.168.1.178:8092", version = "MatriX.150")

    private val uhd = TorrentRelease(
        title = "Сёгун / Shogun / S1E1-10 of 10 (2024) WEB-DL [H.265/2160p] [4K, HDR10, Dolby Vision]",
        tracker = "NoNaMe Club",
        link = "uhd",
        sizeBytes = 40 * GIB,
        seeders = 120,
        peers = 14,
        publishedDate = "2025-01-08",
        traits = ReleaseTraits(
            quality = VideoQuality.ULTRA_HD,
            dynamicRange = setOf(DynamicRange.HDR10, DynamicRange.DOLBY_VISION),
            source = ReleaseSource.WEB_DL,
            codec = VideoCodec.HEVC,
            voices = listOf("Red Head Sound", "LostFilm", "HDrezka"),
            seasons = 1..1,
        ),
    )

    private val fullHd = TorrentRelease(
        title = "Сёгун / Shogun, S1E1-10 of 10 (2024) WEB-DL 1080p | HDrezka Studio, LostFilm",
        tracker = "BigFANGroup",
        link = "fhd",
        sizeBytes = 23 * GIB / 2,
        seeders = 48,
        peers = 3,
        publishedDate = "2024-04-23",
        traits = ReleaseTraits(
            quality = VideoQuality.FULL_HD,
            source = ReleaseSource.WEB_DL,
            voices = listOf("HDrezka", "LostFilm"),
            seasons = 1..1,
        ),
    )

    private val rip = TorrentRelease(
        title = "Сёгун / Shôgun / Shogun [S1] (2024) WEB-DLRip | D, P, P2, A",
        tracker = "MegaPeer",
        link = "rip",
        sizeBytes = 7 * GIB,
        seeders = 0,
        peers = 0,
        publishedDate = "2024-04-24",
        traits = ReleaseTraits(quality = VideoQuality.SD, source = ReleaseSource.WEB_RIP, seasons = 1..1),
    )

    private val base = TorrentsState(
        title = "Сёгун",
        subtitle = "2024 · запустится S1E3",
        sourceName = "kino.pub",
        server = server,
    )

    val all: List<TorrentsState> = listOf(
        base.copy(stage = TorrentsStage.NO_SERVER, server = null, address = "192.168.1.178:8092"),
        base.copy(stage = TorrentsStage.SEARCHING),
        base.copy(
            stage = TorrentsStage.READY,
            releases = listOf(uhd, fullHd, rip),
            chosen = ChosenRelease(
                link = "fhd",
                key = fullHd.key,
                title = fullHd.title,
                quality = VideoQuality.FULL_HD,
                seasons = 1..1,
            ),
            opening = "uhd",
        ),
        base.copy(
            stage = TorrentsStage.FAILED,
            error = "Поиск не ответил. Проверьте, что в TorrServer настроен Jackett.",
        ),
    )
}
