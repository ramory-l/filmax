package com.filmax.hub.server.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * Кеш ответов в памяти со сроком жизни. TMDB отвечает за доли секунды, но Главная — это десяток
 * рядов разом на каждый запуск приложения, а лимит TMDB — около 50 запросов в секунду. Ряды
 * меняются раз в сутки, поэтому повтор отдаётся из памяти.
 *
 * Ограничен по числу записей ([capacity]): память Мака занята медиасервером и TorrServer.
 */
internal class TtlCache<V : Any>(
    private val ttlMillis: Long,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry<V>>()

    /**
     * Значение по [key] или загруженное [load]. Два одновременных промаха загрузят дважды —
     * дешевле, чем держать блокировку на время сетевого запроса.
     */
    suspend fun get(key: String, load: suspend () -> V): V {
        val now = clock()
        entries[key]?.takeIf { it.expiresAt > now }?.let { return it.value }
        val value = load()
        if (entries.size >= capacity) evict(now)
        entries[key] = Entry(value, now + ttlMillis)
        return value
    }

    /** Сначала протухшие; если места всё равно нет — та запись, что протухнет раньше всех. */
    private fun evict(now: Long) {
        entries.entries.removeIf { it.value.expiresAt <= now }
        if (entries.size < capacity) return
        entries.entries.minByOrNull { it.value.expiresAt }?.let { entries.remove(it.key) }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
