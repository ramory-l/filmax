package com.filmax.data.hub.mapper

import com.filmax.core.domain.catalog.model.Duration
import com.filmax.core.domain.catalog.model.Genre
import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.ItemRating
import com.filmax.core.domain.catalog.model.ItemType
import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.catalog.model.Posters
import com.filmax.core.domain.catalog.model.Rating
import com.filmax.core.domain.catalog.model.RatingSource
import com.filmax.data.hub.HubSession
import com.filmax.data.hub.local.HubProgress
import com.filmax.hub.protocol.HubGenre
import com.filmax.hub.protocol.HubItem
import com.filmax.hub.protocol.HubType
import kotlin.math.roundToInt

/**
 * Тайтлы хаба → доменные модели. Картинки — адреса на хабе (`/img/{size}/{file}`): хаб кеширует
 * TMDB, и телевизор не ходит к image.tmdb.org сам. Оценка — зрителей TMDB, своя оценка витрины.
 */
internal class HubMapper(
    private val session: HubSession,
    private val progress: HubProgress,
    private val inWatchlist: (Int) -> Boolean,
) {

    fun item(dto: HubItem): Item {
        val posters = posters(dto)
        return Item(
            id = dto.id,
            title = dto.title,
            type = itemType(dto.type),
            year = dto.year,
            plot = dto.overview,
            director = dto.directors.joinToString(", "),
            cast = dto.cast.joinToString(", "),
            country = dto.countries.joinToString(", "),
            genres = dto.genres.map(::genre),
            rating = rating(dto.rating),
            posters = posters,
            duration = Duration(averageMinutes = dto.runtimeMinutes?.toDouble(), totalMinutes = null),
            tracklist = tracks(dto, posters),
            // Трейлеры хаба — ролики YouTube, а плеер трейлеров играет только прямой поток.
            trailer = null,
            inWatchlist = inWatchlist(dto.id),
            finished = false,
            imdbId = dto.imdbId,
        )
    }

    fun genre(dto: HubGenre): Genre = Genre(id = dto.id, title = dto.title)

    /**
     * Видео тайтла. Фильм — одно видео с номером 1 без сезона (так его адресует маршрут плеера),
     * сериал — серии по сезонам, номер серии — внутри сезона. Ссылок нет: их даёт раздача.
     */
    private fun tracks(dto: HubItem, posters: Posters): List<MediaTrack> {
        val cover = posters.wide ?: posters.medium
        if (!itemType(dto.type).episodic) {
            return listOf(video(dto.title, cover, dto.runtimeMinutes).withProgress(dto.id))
        }
        return dto.seasons.flatMap { season ->
            season.episodes.map { episode ->
                val still = image(episode.still, STILL) ?: cover
                video(episode.title, still, episode.runtimeMinutes ?: dto.runtimeMinutes)
                    .copy(id = episode.number, number = episode.number, seasonNumber = season.number)
                    .withProgress(dto.id)
            }
        }
    }

    /** Видео без ссылок и дорожек — номер 1 без сезона, серия переписывает их своими. */
    private fun video(title: String, thumbnail: String, minutes: Int?) = MediaTrack(
        id = 1,
        number = 1,
        seasonNumber = 0,
        title = title,
        thumbnail = thumbnail,
        durationSeconds = (minutes ?: 0) * SECONDS_PER_MINUTE,
        files = emptyList(),
        audios = emptyList(),
        subtitles = emptyList(),
    )

    private fun MediaTrack.withProgress(itemId: Int): MediaTrack {
        val stored = progress.of(itemId, this) ?: return this
        return copy(watchedSeconds = stored.seconds, watchStatus = stored.status)
    }

    private fun rating(value: Double?): ItemRating =
        ItemRating(listOfNotNull(value?.takeIf { it > 0 }?.let { Rating(RatingSource.COMMUNITY, rounded(it)) }))

    private fun posters(dto: HubItem) = Posters(
        small = image(dto.poster, POSTER_SMALL).orEmpty(),
        medium = image(dto.poster, POSTER_MEDIUM).orEmpty(),
        big = image(dto.poster, POSTER_BIG).orEmpty(),
        wide = image(dto.backdrop, BACKDROP),
    )

    private fun image(file: String?, size: String): String? {
        val address = session.current?.address ?: return null
        return file?.let { "$address/img/$size/$it" }
    }

    companion object {
        private const val POSTER_SMALL = "w185"
        private const val POSTER_MEDIUM = "w342"
        private const val POSTER_BIG = "w500"
        private const val BACKDROP = "w1280"
        private const val STILL = "w300"
        private const val SECONDS_PER_MINUTE = 60
        private const val RATING_PRECISION = 10.0

        private fun rounded(rating: Double): Double = (rating * RATING_PRECISION).roundToInt() / RATING_PRECISION

        fun itemType(type: HubType): ItemType = when (type) {
            HubType.MOVIE -> ItemType.MOVIE
            HubType.SERIES -> ItemType.SERIES
            HubType.ANIME -> ItemType.ANIME
            HubType.DOCUMENTARY -> ItemType.DOCUMENTARY
            HubType.DOCUMENTARY_MOVIE -> ItemType.DOCUMENTARY_MOVIE
            HubType.TV_SHOW -> ItemType.TV_SHOW
        }

        /** Тип каталога приложения в терминах хаба; концертов и ТВ-каналов у хаба нет. */
        fun hubType(type: ItemType): HubType? = when (type) {
            ItemType.MOVIE -> HubType.MOVIE
            ItemType.SERIES -> HubType.SERIES
            ItemType.ANIME -> HubType.ANIME
            ItemType.DOCUMENTARY -> HubType.DOCUMENTARY
            ItemType.DOCUMENTARY_MOVIE -> HubType.DOCUMENTARY_MOVIE
            ItemType.TV_SHOW -> HubType.TV_SHOW
            ItemType.CONCERT, ItemType.TV -> null
        }
    }
}
