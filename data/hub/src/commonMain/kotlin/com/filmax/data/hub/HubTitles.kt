package com.filmax.data.hub

import com.filmax.core.domain.catalog.model.Item
import com.filmax.data.hub.mapper.HubMapper
import com.filmax.data.hub.remote.HubApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Карточки тайтлов с сезонами — те, что открывали. Прогресс и подбор раздачи знают о тайтле
 * только id, а им нужны название, год и серии: карточка берётся отсюда, без второго запроса к хабу.
 */
internal class HubTitles(private val api: HubApi, private val mapper: HubMapper) {

    private val cards = LinkedHashMap<Int, Item>()
    private val lock = Mutex()

    /** Свежая карточка с хаба: детали открыли — сезоны и отметки должны быть актуальны. */
    suspend fun load(id: Int): Item {
        val item = mapper.item(api.item(id))
        lock.withLock {
            cards.remove(id)
            cards[id] = item
            if (cards.size > MAX_CARDS) cards.remove(cards.keys.first())
        }
        return item
    }

    /** Карточка из памяти, а нет её (приложение перезапускали) — с хаба. */
    suspend fun get(id: Int): Item = lock.withLock { cards[id] } ?: load(id)

    private companion object {
        /** Карточка сериала с сериями — десятки килобайт; держим последние открытые. */
        const val MAX_CARDS = 30
    }
}
