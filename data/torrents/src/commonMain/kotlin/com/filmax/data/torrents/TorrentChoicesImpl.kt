package com.filmax.data.torrents

import com.filmax.core.domain.torrent.ChosenRelease
import com.filmax.core.domain.torrent.TorrentChoices
import com.filmax.core.domain.torrent.VideoQuality
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Выбор раздачи — в настройках устройства, ключ на тайтл. id тайтлов kino.pub и домашнего сервера
 * не пересекаются (у сервера они от 2³⁰), так что общий ключ не путает источники.
 */
internal class TorrentChoicesImpl(private val settings: Settings) : TorrentChoices {

    /** Счётчик правок: подписчики [observe] перечитывают свой тайтл, когда он сдвинулся. */
    private val revision = MutableStateFlow(0)

    override fun chosen(itemId: Int): ChosenRelease? = settings.getStringOrNull(key(itemId))
        ?.let { stored -> runCatching { json.decodeFromString(StoredChoice.serializer(), stored) }.getOrNull() }
        ?.toDomain()

    override fun observe(itemId: Int): Flow<ChosenRelease?> =
        revision.map { chosen(itemId) }.distinctUntilChanged()

    override fun choose(itemId: Int, release: ChosenRelease) {
        settings.putString(key(itemId), json.encodeToString(StoredChoice.serializer(), StoredChoice.of(release)))
        revision.value++
    }

    override fun forget(itemId: Int) {
        settings.remove(key(itemId))
        revision.value++
    }

    private fun key(itemId: Int) = "$KEY_PREFIX$itemId"

    /** Хранимая форма: качество — именем, сезоны — границами, чтобы пережить смену модели. */
    @Serializable
    private data class StoredChoice(
        val link: String,
        val title: String,
        val key: String? = null,
        val quality: String? = null,
        val firstSeason: Int? = null,
        val lastSeason: Int? = null,
    ) {
        // Без ключа выбор всё равно играет — по ссылке; не узнается только строка в новой выдаче.
        fun toDomain() = ChosenRelease(
            link = link,
            key = key ?: link,
            title = title,
            quality = VideoQuality.entries.firstOrNull { it.name == quality },
            seasons = firstSeason?.let { first -> first..(lastSeason ?: first) },
        )

        companion object {
            fun of(release: ChosenRelease) = StoredChoice(
                link = release.link,
                title = release.title,
                key = release.key,
                quality = release.quality?.name,
                firstSeason = release.seasons?.first,
                lastSeason = release.seasons?.last,
            )
        }
    }

    private companion object {
        const val KEY_PREFIX = "torrent_choice_"
        val json = Json { ignoreUnknownKeys = true }
    }
}
