package com.filmax.data.hub

import com.filmax.core.domain.watchlist.WatchlistRepository
import com.filmax.core.domain.watchlist.model.WatchlistItem
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * «Буду смотреть» хаба — пока на устройстве, как и прогресс: своей базы у хаба ещё нет. Список
 * живёт в настройках и переживает перезапуск; с телефоном он не общий — это следующий этап.
 */
internal class HubWatchlistRepository(private val settings: Settings) : WatchlistRepository {

    private val state = MutableStateFlow(load())

    override val items: Flow<List<WatchlistItem>> = state.asStateFlow()

    override fun contains(itemId: Int): Flow<Boolean> = state.map { list -> list.any { it.id == itemId } }

    /** Отметка для карточки тайтла: её ставят без подписки на поток. */
    fun has(itemId: Int): Boolean = state.value.any { it.id == itemId }

    override suspend fun add(item: WatchlistItem) = adopt(item)

    override suspend fun remove(itemId: Int) = change { list -> list.filterNot { it.id == itemId } }

    override suspend fun adopt(item: WatchlistItem) = change { list -> list.filterNot { it.id == item.id } + item }

    private fun change(transform: (List<WatchlistItem>) -> List<WatchlistItem>) {
        state.update(transform)
        settings.putString(KEY, json.encodeToString(serializer, state.value.map(StoredItem::of)))
    }

    private fun load(): List<WatchlistItem> = settings.getStringOrNull(KEY)
        ?.let { stored -> runCatching { json.decodeFromString(serializer, stored) }.getOrNull() }
        .orEmpty()
        .map(StoredItem::toDomain)

    /** Свой формат хранения: доменная модель без аннотаций сериализации. */
    @Serializable
    private data class StoredItem(
        val id: Int,
        val title: String,
        val poster: String,
        val year: Int,
        val minutes: Int,
        val rating: Double? = null,
    ) {
        fun toDomain() = WatchlistItem(id, title, poster, year, minutes, rating)

        companion object {
            fun of(item: WatchlistItem) =
                StoredItem(item.id, item.title, item.posterSmall, item.year, item.durationMinutes, item.rating)
        }
    }

    private companion object {
        const val KEY = "hub_watchlist"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(StoredItem.serializer())
    }
}
