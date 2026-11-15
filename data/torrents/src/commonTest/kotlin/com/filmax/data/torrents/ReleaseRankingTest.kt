package com.filmax.data.torrents

import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.ReleaseTraits
import com.filmax.core.domain.torrent.TorrentRelease
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.core.domain.torrent.VoiceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Какую раздачу приложение выбирает само, когда пользователь не выбрал её в «Торрентах». */
class ReleaseRankingTest {

    @Test
    fun `favourite studio wins over better quality`() {
        val lostFilm = release("LostFilm 720", ReleaseTraits(quality = VideoQuality.HD, voices = listOf("LostFilm")))
        val dubbing4k = release(
            "Дубляж 4K",
            ReleaseTraits(quality = VideoQuality.ULTRA_HD, voiceKinds = setOf(VoiceKind.DUBBING)),
        )

        assertEquals(lostFilm, ReleaseRanking.best(listOf(dubbing4k, lostFilm), season = null))
    }

    @Test
    fun `studios follow the chosen order, then dubbing, then multi voice`() {
        val redHead = release("RHS", ReleaseTraits(voices = listOf("Red Head Sound")))
        val hdRezka = release("Rezka", ReleaseTraits(voices = listOf("HDrezka")))
        val dubbing = release("Дубляж", ReleaseTraits(voiceKinds = setOf(VoiceKind.DUBBING)))
        val multiVoice = release("MVO", ReleaseTraits(voiceKinds = setOf(VoiceKind.MULTI_VOICE)))

        assertEquals(hdRezka, ReleaseRanking.best(listOf(redHead, dubbing, hdRezka, multiVoice), season = null))
        assertEquals(redHead, ReleaseRanking.best(listOf(redHead, dubbing, multiVoice), season = null))
        assertEquals(dubbing, ReleaseRanking.best(listOf(multiVoice, dubbing), season = null))
    }

    @Test
    fun `within one voice 1080p is preferred to 4K and 4K to 720p`() {
        val fullHd = release("1080", ReleaseTraits(quality = VideoQuality.FULL_HD))
        val ultraHd = release("2160", ReleaseTraits(quality = VideoQuality.ULTRA_HD))
        val hd = release("720", ReleaseTraits(quality = VideoQuality.HD))

        assertEquals(fullHd, ReleaseRanking.best(listOf(hd, ultraHd, fullHd), season = null))
        assertEquals(ultraHd, ReleaseRanking.best(listOf(hd, ultraHd), season = null))
    }

    @Test
    fun `more seeders decide between equal releases`() {
        val few = release("few", ReleaseTraits(), seeders = 5)
        val many = release("many", ReleaseTraits(), seeders = 50)

        assertEquals(many, ReleaseRanking.best(listOf(few, many), season = null))
    }

    @Test
    fun `camrips are never chosen`() {
        val cam = release("CAM", ReleaseTraits(source = ReleaseSource.CAM, voices = listOf("LostFilm")))

        assertNull(ReleaseRanking.best(listOf(cam), season = null))
    }

    @Test
    fun `dead releases are taken only when nothing alive is left`() {
        val dead = release("dead", ReleaseTraits(voices = listOf("LostFilm")), seeders = 1)
        val alive = release("alive", ReleaseTraits(), seeders = 10)

        assertEquals(alive, ReleaseRanking.best(listOf(dead, alive), season = null))
        assertEquals(dead, ReleaseRanking.best(listOf(dead), season = null))
    }

    @Test
    fun `release must contain the season, unless its seasons are unknown`() {
        val firstSeasons = release("S1-2", ReleaseTraits(voices = listOf("LostFilm"), seasons = 1..2))
        val wholeSeries = release("all", ReleaseTraits())

        assertEquals(wholeSeries, ReleaseRanking.best(listOf(firstSeasons, wholeSeries), season = 3))
        assertEquals(firstSeasons, ReleaseRanking.best(listOf(firstSeasons, wholeSeries), season = 2))
    }

    private fun release(title: String, traits: ReleaseTraits, seeders: Int = 20) = TorrentRelease(
        title = title,
        tracker = "tracker",
        link = "magnet:$title",
        sizeBytes = null,
        seeders = seeders,
        peers = null,
        publishedDate = null,
        traits = traits,
    )
}
