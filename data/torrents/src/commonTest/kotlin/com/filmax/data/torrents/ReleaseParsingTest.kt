package com.filmax.data.torrents

import com.filmax.core.domain.torrent.DynamicRange
import com.filmax.core.domain.torrent.ReleaseSource
import com.filmax.core.domain.torrent.VideoCodec
import com.filmax.core.domain.torrent.VideoQuality
import com.filmax.core.domain.torrent.VoiceKind
import com.filmax.data.torrents.parse.ReleaseNames
import com.filmax.data.torrents.parse.ReleaseTraitsParser
import com.filmax.data.torrents.parse.SizeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Разбор названий — на настоящих раздачах из выдачи BigFANGroup, NNM-Club, MegaPeer и Rutracker. */
class ReleaseParsingTest {

    @Test
    fun `bigfangroup lists voices after a bar`() {
        val traits = ReleaseTraitsParser.parse(BIGFAN_SHOGUN)

        assertEquals(VideoQuality.FULL_HD, traits.quality)
        assertEquals(ReleaseSource.WEB_DL, traits.source)
        assertEquals(1..1, traits.seasons)
        assertEquals(listOf("HDrezka", "LostFilm"), traits.voices)
    }

    @Test
    fun `nnm club keeps codec and hdr in brackets and voices after them`() {
        val traits = ReleaseTraitsParser.parse(NNM_SHOGUN_4K)

        assertEquals(VideoQuality.ULTRA_HD, traits.quality)
        assertEquals(VideoCodec.HEVC, traits.codec)
        assertEquals(setOf(DynamicRange.HDR10, DynamicRange.DOLBY_VISION), traits.dynamicRange)
        assertEquals(listOf("Red Head Sound", "LostFilm", "HDrezka"), traits.voices)
    }

    @Test
    fun `voice codes are kinds, not studios`() {
        val traits = ReleaseTraitsParser.parse(MEGAPEER_SHOGUN)

        assertEquals(
            setOf(VoiceKind.DUBBING, VoiceKind.MULTI_VOICE, VoiceKind.TWO_VOICE, VoiceKind.SINGLE_VOICE),
            traits.voiceKinds,
        )
        assertEquals(emptyList(), traits.voices)
        assertEquals(VideoCodec.AVC, traits.codec)
        assertNull(traits.quality)
    }

    @Test
    fun `rutracker spells kinds after the bracket`() {
        val traits = ReleaseTraitsParser.parse(RUTRACKER_DUNE)

        assertEquals(
            setOf(VoiceKind.DUBBING, VoiceKind.MULTI_VOICE, VoiceKind.ORIGINAL, VoiceKind.SUBTITLES),
            traits.voiceKinds,
        )
        assertEquals(emptyList(), traits.voices)
    }

    @Test
    fun `studio glued to the source is a voice, codec glued to it is not`() {
        val glued = ReleaseTraitsParser.parse("Сёгун / Shogun, S1E1-10 of 10 (2024) WEB-DL-LostFilm")
        assertEquals(listOf("LostFilm"), glued.voices)
        assertEquals(emptyList(), ReleaseTraitsParser.parse("Сёгун / Shogun (2024) WEB-DL-AVC").voices)
    }

    @Test
    fun `nnm club without brackets keeps voices after a double space`() {
        val title = "Дюна: Пророчество / Dune: Prophecy  / S1E1-6 of 6 (2024) WEB-DL  LostFilm"

        assertEquals(listOf("LostFilm"), ReleaseTraitsParser.parse(title).voices)
        assertEquals(
            listOf("ViruseProject"),
            ReleaseTraitsParser.parse("Сёгун / Shogun  / S1E1-10 of 10 (2024) WEB-DL  ViruseProject [AD]").voices,
        )
    }

    @Test
    fun `notes after the bar are not voices`() {
        listOf(
            "Дюна / Dune (1984) BDRip 1080p | Расширенная версия",
            "Дюна / Dune (2021) WEB-DL 1080p | Чистый звук",
            "Интерстеллар / Interstellar (2014) BDRip 1080p | IMAX Edition | 60 fps-Лицензия",
            "Дюна / Dune: Part One (2021) WEB-DL-iTunes",
        ).forEach { title -> assertEquals(emptyList(), ReleaseTraitsParser.parse(title).voices, title) }
    }

    @Test
    fun `one studio has one spelling`() {
        val title = "Тьма / Dark (2017-2020) WEBRip [H.264/2160p]  RHS, HDRezka, Kubik, Red Head Sound, HDrezka"

        assertEquals(listOf("Red Head Sound", "HDrezka", "Кубик в Кубе"), ReleaseTraitsParser.parse(title).voices)
    }

    @Test
    fun `uhd rip with av1 and imax notes has no voices`() {
        val traits = ReleaseTraitsParser.parse(NNM_INTERSTELLAR)

        assertEquals(VideoQuality.ULTRA_HD, traits.quality)
        assertEquals(ReleaseSource.BLU_RAY, traits.source)
        assertEquals(VideoCodec.AV1, traits.codec)
        assertEquals(setOf(DynamicRange.HDR10), traits.dynamicRange)
        assertEquals(emptyList(), traits.voices)
        assertNull(traits.seasons)
    }

    @Test
    fun `explicit height beats 4k of the master`() {
        val masterRip = ReleaseTraitsParser.parse("Синтел / Sintel (2010) 4K DMRip 1080p")
        val cinemaHeight = ReleaseTraitsParser.parse("Синтел / Sintel (2010) 4K Ultra HD 1744p")

        assertEquals(VideoQuality.FULL_HD, masterRip.quality)
        assertEquals(VideoQuality.ULTRA_HD, cinemaHeight.quality, "1744p не из списка высот — решает «4K»")
    }

    @Test
    fun `old rips without height are sd`() {
        val traits = ReleaseTraitsParser.parse("Сёгун Маэда / Kabuto / Shogun Mayeda (1991) DVDRip [VO]")

        assertEquals(VideoQuality.SD, traits.quality)
        assertEquals(ReleaseSource.DVD, traits.source)
        assertEquals(setOf(VoiceKind.SINGLE_VOICE), traits.voiceKinds)
    }

    @Test
    fun `web dl rip is a rip, not web dl`() {
        assertEquals(ReleaseSource.WEB_RIP, ReleaseTraitsParser.parse("Сёгун / Shogun (2024) WEB-DLRip").source)
    }

    @Test
    fun `names are the parts before the year without the season tail`() {
        assertEquals(listOf("Сёгун", "Shogun"), ReleaseNames.names(BIGFAN_SHOGUN))
        assertEquals(listOf("Сёгун", "Shogun"), ReleaseNames.names(NNM_SHOGUN_4K))
        assertEquals(listOf("Сёгун", "Shôgun", "Shogun"), ReleaseNames.names(MEGAPEER_SHOGUN))
        assertEquals(listOf("Сёгун", "Shogun"), ReleaseNames.names("Сёгун / Shogun,E1-3 of 3 (1980) BDRip 1080p"))
        assertEquals(listOf("Дюна", "Dune"), ReleaseNames.names(RUTRACKER_DUNE))
    }

    @Test
    fun `a year inside the name stays in the name`() {
        assertEquals(
            listOf("Бегущий по лезвию 2049", "Blade Runner 2049"),
            ReleaseNames.names("Бегущий по лезвию 2049 / Blade Runner 2049 (2017) BDRip 1080p"),
        )
        assertEquals(2017..2017, ReleaseNames.years("Бегущий по лезвию 2049 / Blade Runner 2049 (2017) BDRip 1080p"))
        assertEquals(listOf("1917", "1917"), ReleaseNames.names("1917 / 1917 (2019) BDRip"))
    }

    @Test
    fun `scene release name ends at the year`() {
        val title = "Shogun.2024.S01.1080p.WEB-DL.H264"
        val traits = ReleaseTraitsParser.parse(title)

        assertEquals(listOf("Shogun"), ReleaseNames.names(title))
        assertEquals(2024..2024, ReleaseNames.years(title))
        assertEquals(1..1, traits.seasons)
        assertEquals(VideoCodec.AVC, traits.codec)
        assertEquals(ReleaseSource.WEB_DL, traits.source)
    }

    @Test
    fun `long running series has a range of seasons and years`() {
        val title = "Южный Парк / South Park, S1-20E1-277 of 277 (1997-2016) WEB-DL-HEVC 1080p"
        val traits = ReleaseTraitsParser.parse(title)

        assertEquals(1..20, traits.seasons)
        assertEquals(1997..2016, ReleaseNames.years(title))
        assertEquals(VideoCodec.HEVC, traits.codec)
        assertEquals(emptyList(), traits.voices)
    }

    @Test
    fun `russian season spellings`() {
        assertEquals(2..2, ReleaseNames.seasons("Охотники за мифами. Сезон №2. Серия №13"))
        assertEquals(1..3, ReleaseNames.seasons("Тьма / Dark (2017) 1-3 сезоны WEB-DL"))
        assertEquals(2..2, ReleaseNames.seasons("Тьма / Dark (2019) 2-й сезон"))
    }

    @Test
    fun `names compare without case, yo and diacritics`() {
        assertEquals(ReleaseNames.normalize("Сёгун"), ReleaseNames.normalize("СЕГУН"))
        assertEquals(ReleaseNames.normalize("Shōgun"), ReleaseNames.normalize("Shogun"))
        assertEquals("dune part two", ReleaseNames.normalize("Dune: Part Two"))
    }

    @Test
    fun `sizes come as formatted text`() {
        assertEquals(42_949_672_960, SizeText.bytes("40.0 GiB"))
        assertEquals(619_813_273, SizeText.bytes("591.1 MiB"))
        assertEquals(1_610_612_736, SizeText.bytes("1,5 ГБ"))
        assertNull(SizeText.bytes("много"))
        assertNull(SizeText.bytes(null))
    }
}

internal const val BIGFAN_SHOGUN = "Сёгун / Shogun, S1E1-10 of 10 (2024) WEB-DL 1080p | HDrezka Studio, LostFilm,  …"
internal const val NNM_SHOGUN_4K = "Сёгун / Shogun  / S1E1-10 of 10 (2024) WEB-DL [H.265/2160p] " +
    "[4K, HDR10, Dolby Vision Profile 8, 10-bit]  Red Head Sound, LostFilm, HDrezka, "
internal const val MEGAPEER_SHOGUN = "Сёгун / Shôgun / Shogun [S1] (2024) WEB-DL-AVC | D, P, P2, A"
internal const val NNM_INTERSTELLAR = "Интерстеллар / Interstellar (2014) UHD BDRip [AV1/2160p] " +
    "[4K, HDR10, 10-bit] [IMAX Edition] [handmade AI]"
internal const val RUTRACKER_DUNE = "Дюна / Dune (Дени Вильнёв) [2021, США, фантастика, WEB-DL 1080p] " +
    "Dub + MVO + Original (Eng) + Sub"
