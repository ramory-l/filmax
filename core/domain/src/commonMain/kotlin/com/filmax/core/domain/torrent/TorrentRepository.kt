package com.filmax.core.domain.torrent

import com.filmax.core.domain.catalog.model.MediaTrack
import com.filmax.core.domain.common.RequestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** TorrServer в своей сети: [address] — нормализованный адрес со схемой и портом, [version] — сборка. */
data class TorrServer(val address: String, val version: String)

/**
 * Адрес TorrServer на устройстве. Раздачи качает и отдаёт потоком он, а клиент только выбирает
 * раздачу и файл: без сервера раздел торрентов скрыт.
 */
interface TorrServerRepository {

    /** Сохранённый сервер; null — не настроен. */
    val server: StateFlow<TorrServer?>

    /** Проверяет, что по адресу отвечает TorrServer, и запоминает его. */
    suspend fun connect(address: String): RequestResult<TorrServer>

    fun forget()
}

/** Раздача, открытая в TorrServer: [hash] — инфо-хеш, по нему идёт поток; [videos] — её видеофайлы. */
data class TorrentContents(
    val hash: String,
    val name: String,
    val videos: List<TorrentVideo>,
)

/**
 * Видеофайл раздачи. [index] — номер файла в TorrServer (с единицы), [season] и [episode] —
 * разобранные из пути; у фильма и у файла без номера — null.
 */
data class TorrentVideo(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    val season: Int?,
    val episode: Int?,
)

interface TorrentRepository {

    /**
     * Раздачи тайтла, уже отобранные по названию и году и без того, что не сыграть (образы дисков,
     * музыка, игры). Порядок — по сидам: живые раздачи стартуют быстрее.
     */
    suspend fun search(query: TorrentQuery): RequestResult<List<TorrentRelease>>

    /**
     * Добавляет раздачу в TorrServer (повторное добавление той же — не ошибка) и ждёт список
     * файлов: у ссылки на .torrent он сразу, у магнита — когда найдутся пиры. Названия нет: если
     * раздача уже лежит в TorrServer, у неё остаётся своё.
     */
    suspend fun open(link: String, poster: String?): RequestResult<TorrentContents>
}

/**
 * Раздача, выбранная для тайтла: по [link] её можно открыть заново, если TorrServer перезапустился,
 * а по [key] — узнать в новой выдаче (см. [TorrentRelease.key]).
 */
data class ChosenRelease(
    val link: String,
    val key: String,
    val title: String,
    /** Разрешение раздачи — подпись качества в плеере; null — по названию не понять. */
    val quality: VideoQuality?,
    val seasons: IntRange?,
)

/**
 * Выбор раздачи на тайтл. Запоминается, чтобы следующая серия шла из той же раздачи, — как в
 * Lampa. Хранится на устройстве: у kino.pub и домашнего сервера такого понятия нет.
 */
interface TorrentChoices {

    fun chosen(itemId: Int): ChosenRelease?

    /** Выбор тайтла [itemId] и его смены: панель торрентов показывает, что сейчас играет. */
    fun observe(itemId: Int): Flow<ChosenRelease?>

    fun choose(itemId: Int, release: ChosenRelease)

    fun forget(itemId: Int)
}

/**
 * Потоки из выбранной раздачи вместо потоков источника. Прогресс при этом пишется в трек
 * источника: серия та же, другой только файл.
 */
interface TorrentStreams {

    /**
     * Трек [track] тайтла [itemId] с потоком из выбранной раздачи. null — раздача не выбрана или
     * этой серии в ней нет: тогда играет источник.
     */
    suspend fun playable(itemId: Int, track: MediaTrack): RequestResult<MediaTrack>?
}
