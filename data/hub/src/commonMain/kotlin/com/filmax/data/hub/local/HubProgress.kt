package com.filmax.data.hub.local

import com.filmax.core.domain.catalog.model.Item
import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.catalog.model.VideoRef
import com.filmax.core.domain.catalog.model.ref
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Позиция в одном видео: секунды, длительность (для полосы «Продолжить») и статус в шкале
 * kino.pub: −1 — не начат, 0 — начат, 1 — досмотрен.
 */
@Serializable
internal data class StoredVideo(val seconds: Int, val status: Int, val duration: Int = 0)

/**
 * Что смотрели в тайтле: снимок названия и картинок для истории (без похода на хаб) и позиции
 * по видео. Ключ видео — «сезон:номер»: номер серии уникален только внутри сезона.
 */
@Serializable
internal data class StoredTitle(
    val itemId: Int,
    val title: String,
    val episodic: Boolean,
    val poster: String,
    val wide: String? = null,
    val lastSeenAt: Long,
    val last: String,
    val videos: Map<String, StoredVideo> = emptyMap(),
    /** Отмечен досмотренным целиком — из деталей, а не по позиции. */
    val watched: Boolean = false,
)

/**
 * Прогресс просмотра хаба — пока на устройстве: у хаба своей базы ещё нет, и это первый этап. Шкала
 * та же, что у kino.pub, поэтому «Продолжить» и отметки серий работают без изменений в экранах.
 */
internal class HubProgress(private val settings: Settings, private val clock: () -> Long) {

    private var titles: Map<Int, StoredTitle> = load()

    fun title(itemId: Int): StoredTitle? = titles[itemId]

    /** История — по последнему просмотру; самые свежие сверху. */
    fun history(): List<StoredTitle> = titles.values.sortedByDescending { it.lastSeenAt }

    /** Позиция [seconds] в видео [video] тайтла [item]; досмотренным считается последний [WATCHED_SHARE]. */
    fun save(item: Item, video: VideoRef, seconds: Int) {
        val key = key(video)
        val duration = item.tracklist.firstOrNull { it.ref == video }?.durationSeconds ?: 0
        val status = if (duration > 0 && seconds >= duration * WATCHED_SHARE) WATCHED else STARTED
        val previous = titles[item.id]
        val updated = StoredTitle(
            itemId = item.id,
            title = item.title,
            episodic = item.type.episodic,
            poster = item.posters.medium,
            wide = item.posters.wide,
            lastSeenAt = clock(),
            last = key,
            videos = previous?.videos.orEmpty() + (key to StoredVideo(seconds, status, duration)),
            watched = previous?.watched == true,
        )
        store(titles + (item.id to updated))
    }

    /** Отметка «досмотрен» целиком: тайтла ещё нет в истории — появляется в ней с первым видео. */
    fun toggleWatched(item: Item) {
        val previous = titles[item.id]
        val first = item.tracklist.firstOrNull()?.let { key(it.ref) } ?: MOVIE_KEY
        val updated = previous?.copy(watched = !previous.watched, lastSeenAt = clock()) ?: StoredTitle(
            itemId = item.id,
            title = item.title,
            episodic = item.type.episodic,
            poster = item.posters.medium,
            wide = item.posters.wide,
            lastSeenAt = clock(),
            last = first,
            watched = true,
        )
        store(titles + (item.id to updated))
    }

    fun clear(itemId: Int) = store(titles - itemId)

    /** Позиция и статус видео [track]: отметка тайтла целиком важнее позиции серии. */
    fun of(itemId: Int, track: MediaTrack): StoredVideo? {
        val title = titles[itemId] ?: return null
        val video = title.videos[key(track.ref)]
        return if (title.watched) StoredVideo(video?.seconds ?: 0, WATCHED, video?.duration ?: 0) else video
    }

    private fun store(updated: Map<Int, StoredTitle>) {
        // Старые записи вытесняются: история дальше сотни тайтлов никому не нужна, а настройки не резиновые.
        titles = updated.values.sortedByDescending { it.lastSeenAt }.take(MAX_TITLES).associateBy { it.itemId }
        settings.putString(KEY, json.encodeToString(serializer, titles.values.toList()))
    }

    private fun load(): Map<Int, StoredTitle> = settings.getStringOrNull(KEY)
        ?.let { stored -> runCatching { json.decodeFromString(serializer, stored) }.getOrNull() }
        .orEmpty()
        .associateBy { it.itemId }

    companion object {
        const val STARTED = 0
        const val WATCHED = 1
        private const val MOVIE_KEY = "0:1"
        private const val KEY = "hub_progress"
        private const val MAX_TITLES = 100

        /** Титры: последние 10 % видео — уже «досмотрен», как у официальных клиентов. */
        private const val WATCHED_SHARE = 0.9
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(StoredTitle.serializer())

        fun key(video: VideoRef): String = when (video) {
            is VideoRef.Episode -> "${video.season}:${video.number}"
            is VideoRef.Movie -> "0:${video.number}"
        }

        /** Обратно из ключа: «2:5» — пятая серия второго сезона, «0:1» — фильм. */
        fun video(key: String): VideoRef? {
            val season = key.substringBefore(':').toIntOrNull() ?: return null
            return VideoRef.of(season, key.substringAfter(':').toIntOrNull() ?: VideoRef.NONE)
        }
    }
}
