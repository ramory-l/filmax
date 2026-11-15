package com.filmax.core.domain.torrent

import com.filmax.core.domain.catalog.model.Item

/**
 * Раздача тайтла с трекера. [link] — то, что принимает TorrServer: магнит или ссылка на .torrent.
 * Ищет сам TorrServer (его Torznab-мост к Jackett), и ссылки Jackett ведут на адрес, видный только
 * TorrServer'у, поэтому клиент ссылку не открывает, а передаёт обратно как есть.
 */
data class TorrentRelease(
    val title: String,
    val tracker: String,
    val link: String,
    val sizeBytes: Long?,
    val seeders: Int?,
    val peers: Int?,
    /** День публикации «yyyy-MM-dd»; null — трекер не прислал. Строка сортируется как дата. */
    val publishedDate: String?,
    val traits: ReleaseTraits,
) {
    /**
     * Ключ раздачи между поисками. Ссылка им быть не может: Jackett шифрует путь к .torrent со
     * случайной солью, и одна раздача в каждой выдаче приходит с новой ссылкой — даже в двух
     * запросах одного поиска. Трекер, название и размер у неё те же.
     */
    val key: String get() = "$tracker|$title|${sizeBytes ?: 0}"
}

/**
 * Что раздача обещает по названию. Трекеры пишут это в свободной форме («WEB-DL [H.265/2160p]
 * [HDR10] Red Head Sound, LostFilm»), поэтому любое поле может не найтись — null или пусто.
 */
data class ReleaseTraits(
    val quality: VideoQuality? = null,
    val dynamicRange: Set<DynamicRange> = emptySet(),
    val source: ReleaseSource? = null,
    val codec: VideoCodec? = null,
    val voiceKinds: Set<VoiceKind> = emptySet(),
    /** Студии озвучки в порядке названия: «LostFilm», «HDrezka», «Red Head Sound». */
    val voices: List<String> = emptyList(),
    /** Сезоны раздачи; null — сезон не указан (фильм или раздача без пометок). */
    val seasons: IntRange? = null,
)

/** Разрешение по названию раздачи. [height] — по нему качество сравнивают и упорядочивают. */
enum class VideoQuality(val height: Int) {
    SD(height = 480),
    HD(height = 720),
    FULL_HD(height = 1080),
    ULTRA_HD(height = 2160),
}

enum class DynamicRange { HDR10, HDR10_PLUS, DOLBY_VISION }

/** Откуда видео: от ремукса диска до записи в кинотеатре — по убыванию качества. */
enum class ReleaseSource { REMUX, BLU_RAY, WEB_DL, WEB_RIP, HD_RIP, HDTV, DVD, CAM }

/** Кодек важен для ТВ: HEVC нужен для 4K, AV1 аппаратно декодируют не все приставки. */
enum class VideoCodec { AVC, HEVC, AV1 }

enum class VoiceKind { DUBBING, MULTI_VOICE, TWO_VOICE, SINGLE_VOICE, ORIGINAL, SUBTITLES }

/**
 * Что искать: названия тайтла (русское и оригинальное — трекеры пишут оба), год выхода и сериал
 * ли это. По году и названию отсеиваются однофамильцы: «Сёгун» 1980 года при поиске сериала 2024-го.
 */
data class TorrentQuery(
    val names: List<String>,
    val year: Int?,
    val episodic: Boolean,
)

/**
 * Запрос к трекерам: kino.pub и хаб пишут названия через « / » — русское и оригинальное, и
 * трекеры пишут оба, поэтому ищется каждое.
 */
fun Item.torrentQuery(): TorrentQuery = TorrentQuery(
    names = title.split(" / ").map(String::trim).filter(String::isNotEmpty),
    year = year.takeIf { it > 0 },
    episodic = type.episodic,
)

/** Раздача как выбор тайтла: то, что нужно, чтобы открыть её снова и узнать в новой выдаче. */
fun TorrentRelease.toChosen(): ChosenRelease = ChosenRelease(
    link = link,
    key = key,
    title = title,
    quality = traits.quality,
    seasons = traits.seasons,
)
