package com.filmax.data.torrents

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.torrent.TorrentQuery
import com.filmax.core.domain.torrent.TorrentVideo
import com.filmax.data.torrents.parse.EpisodeNumber
import com.filmax.data.torrents.parse.EpisodeNumbers
import com.filmax.data.torrents.parse.ReleaseMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Какие раздачи достаются тайтлу и какой файл раздачи играет серию. */
class ReleaseMatchingTest {

    private val shogun = TorrentQuery(names = listOf("Сёгун", "Shōgun"), year = 2024, episodic = true)
    private val interstellar =
        TorrentQuery(names = listOf("Интерстеллар", "Interstellar"), year = 2014, episodic = false)

    @Test
    fun `releases of the title match whatever the tracker`() {
        listOf(BIGFAN_SHOGUN, NNM_SHOGUN_4K, MEGAPEER_SHOGUN).forEach { title ->
            assertTrue(ReleaseMatcher.matches(title, shogun), title)
        }
    }

    @Test
    fun `namesakes of another year or with a longer name do not match`() {
        listOf(
            "Сёгун / Shogun  / S1E1-3 of 3 (1980) BDRip [H.264/1080p]  ОРТ",
            "Fuuun Ishin Dai Shougun / Dai-Shogun: Great Revolution / Грозный Сёгун: Великая революция [2014, TV]",
            "Сёгун Маэда / Kabuto / Shogun Mayeda (1991) DVDRip [VO]",
            "Охотники за мифами. Утраченные сокровища сёгуна. Сезон №2. Серия №13 / Raiders Of The Lost Past",
        ).forEach { title -> assertFalse(ReleaseMatcher.matches(title, shogun), title) }
    }

    @Test
    fun `later seasons of a series still match`() {
        assertTrue(ReleaseMatcher.matches("Сёгун / Shogun, S2E1-10 of 10 (2026) WEB-DL 1080p", shogun))
    }

    @Test
    fun `games, music and disc images are not offered`() {
        listOf(
            "Total War - Shogun 2 - Complete Edition [v. 1.0.0 Build 87380] (2026) PC | RePack-FitGirl",
            "Интерстеллар / Interstellar (2014) OST MP3",
            "Интерстеллар / Interstellar (2014) Blu-Ray Disc 1080p",
        ).forEach { title ->
            assertFalse(ReleaseMatcher.matches(title, interstellar.copy(names = interstellar.names + "Shogun")), title)
        }
    }

    @Test
    fun `a movie does not get series releases`() {
        assertTrue(ReleaseMatcher.matches(NNM_INTERSTELLAR, interstellar))
        assertFalse(ReleaseMatcher.matches("Интерстеллар / Interstellar, S1E1-3 of 3 (2014) WEB-DL", interstellar))
        val openSeason = TorrentQuery(listOf("Сезон охоты"), year = 2006, episodic = false)
        assertTrue(ReleaseMatcher.matches("Сезон охоты / Open Season (2006) BDRip", openSeason))
    }

    @Test
    fun `a movie does not get collections, 3d or teasers`() {
        val avatar = TorrentQuery(listOf("Аватар", "Avatar"), 2009, episodic = false)

        assertTrue(ReleaseMatcher.matches("Аватар / Avatar (2009) UHD BDRip [H.265/2160p] [4K, HDR, 10-bit]", avatar))
        listOf(
            "Аватар / Avatar (2009-2025) BDRemux [H.264/1080p] [Коллекция]",
            "Дюна / Dune (2021) BDRip [H.264/1080p] [Дилогия]",
            "Аватар / Avatar (2009) BDRip 3D [H.264/1080p] [Вертикальная анаморфная стереопара]",
            "Интерстеллар / Interstellar (2014) DCPrip-Тизер",
            "Интерстеллар / Interstellar (2014) AC3 5.1 [Proper]",
            "Интерстеллар / Interstellar (2014) UHD Blu-ray [H.265/2160p] [4K, HDR, 10-bit] [EUR]",
        ).forEach { title ->
            val anyOfThem = avatar.copy(names = avatar.names + "Дюна" + "Интерстеллар")
            assertFalse(ReleaseMatcher.matches(title, anyOfThem), title)
        }
    }

    @Test
    fun `the original title tells namesakes apart but allows a subtitle`() {
        val dark = TorrentQuery(listOf("Тьма", "Dark"), 2017, episodic = true)
        val dune = TorrentQuery(listOf("Дюна", "Dune"), 2021, episodic = false)

        assertTrue(ReleaseMatcher.matches("Тьма / Dark, S1-3E1-26 of 26 (2017-2020) WEBRip 1080p-NewStudio", dark))
        assertFalse(ReleaseMatcher.matches("Тьма / The Dark  / S1E1-6 of 6 (2026) WEBRip  Contentica", dark))
        assertTrue(ReleaseMatcher.matches("Дюна / Dune: Part One (2021) BDRip 1080p", dune))
        assertTrue(ReleaseMatcher.matches("Тьма (2017) WEBRip", dark.copy(names = listOf("Тьма"))))
    }

    @Test
    fun `episode numbers come from the file name or its folder`() {
        assertEquals(
            EpisodeNumber(season = 1, episode = 3),
            EpisodeNumbers.parse("Shogun.S01.WEB-DLRip.LF/Shogun.S01E03.WEB-DLRip.RGzsRutracker.avi"),
        )
        val southPark = EpisodeNumbers.parse("South Park/Season 05/05x12 - A.mkv")
        assertEquals(EpisodeNumber(season = 5, episode = 12), southPark)
        assertEquals(EpisodeNumber(season = 2, episode = 5), EpisodeNumbers.parse("Тьма/Сезон 2/Серия 05.mkv"))
        assertEquals(EpisodeNumber(season = null, episode = 3), EpisodeNumbers.parse("Show/03. Название.mkv"))
        assertNull(EpisodeNumbers.parse("Interstellar.2014.mkv"))
    }

    @Test
    fun `only videos that are not samples`() {
        assertTrue(EpisodeNumbers.isVideo("Show/Episode.MKV"))
        assertFalse(EpisodeNumbers.isVideo("Show/Episode.srt"))
        assertTrue(EpisodeNumbers.isSample("Show/Sample/sample.mkv", sizeBytes = 50L * 1024 * 1024))
        assertFalse(EpisodeNumbers.isSample("Show/Show.S01E01.mkv", sizeBytes = 50L * 1024 * 1024))
    }

    @Test
    fun `a movie plays the biggest file`() {
        val videos = listOf(video(index = 1, size = 200), video(index = 2, size = 9_000), video(index = 3, size = 40))

        assertEquals(2, TorrentVideoPicker.pick(videos, track(season = 0, number = 1), releaseSeasons = null)?.index)
    }

    @Test
    fun `an episode plays the file with its season and number`() {
        val videos = listOf(video(index = 1, season = 1, episode = 1), video(index = 2, season = 1, episode = 2))

        assertEquals(2, TorrentVideoPicker.pick(videos, track(season = 1, number = 2), releaseSeasons = 1..1)?.index)
        assertNull(TorrentVideoPicker.pick(videos, track(season = 1, number = 3), releaseSeasons = 1..1))
        assertNull(TorrentVideoPicker.pick(videos, track(season = 2, number = 1), releaseSeasons = 1..1))
    }

    @Test
    fun `a file without a season takes the season of the release`() {
        val videos = listOf(video(index = 7, season = null, episode = 1))

        assertEquals(7, TorrentVideoPicker.pick(videos, track(season = 2, number = 1), releaseSeasons = 2..2)?.index)
        assertNull(TorrentVideoPicker.pick(videos, track(season = 2, number = 1), releaseSeasons = 1..3))
        assertEquals(7, TorrentVideoPicker.pick(videos, track(season = 1, number = 1), releaseSeasons = null)?.index)
    }

    private fun video(index: Int, size: Long = 1_000, season: Int? = null, episode: Int? = null) =
        TorrentVideo(index = index, path = "Show/file$index.mkv", sizeBytes = size, season = season, episode = episode)
}

internal fun track(season: Int, number: Int) = MediaTrack(
    id = season * 100 + number,
    number = number,
    seasonNumber = season,
    title = "Серия $number",
    thumbnail = "",
    durationSeconds = 3_000,
    files = emptyList(),
    audios = emptyList(),
    subtitles = emptyList(),
)
