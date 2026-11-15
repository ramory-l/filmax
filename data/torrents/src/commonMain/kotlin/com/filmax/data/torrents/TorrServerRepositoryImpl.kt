package com.filmax.data.torrents

import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.torrent.TorrServer
import com.filmax.core.domain.torrent.TorrServerRepository
import com.filmax.data.torrents.remote.TorrServerApi
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Сохранённый TorrServer — в настройках устройства, рядом с сессией домашнего сервера. */
internal class TorrServerStore(private val settings: Settings) {

    private val state = MutableStateFlow(load())

    val server: StateFlow<TorrServer?> = state.asStateFlow()

    /** Адрес для запроса; без настроенного сервера — ошибка, её экран покажет как «нет TorrServer». */
    fun requireAddress(): String = state.value?.address ?: throw IllegalStateException(NOT_CONFIGURED)

    fun save(server: TorrServer) {
        val stored = StoredServer(address = server.address, version = server.version)
        settings.putString(KEY, Json.encodeToString(StoredServer.serializer(), stored))
        state.value = server
    }

    fun clear() {
        settings.remove(KEY)
        state.value = null
    }

    private fun load(): TorrServer? = settings.getStringOrNull(KEY)
        ?.let { stored -> runCatching { Json.decodeFromString(StoredServer.serializer(), stored) }.getOrNull() }
        ?.let { TorrServer(address = it.address, version = it.version) }

    @Serializable
    private data class StoredServer(val address: String, val version: String)

    private companion object {
        const val KEY = "torrserver"
        const val NOT_CONFIGURED = "TorrServer не настроен"
    }
}

internal class TorrServerRepositoryImpl(
    private val api: TorrServerApi,
    private val store: TorrServerStore,
) : TorrServerRepository {

    override val server: StateFlow<TorrServer?> = store.server

    override suspend fun connect(address: String): RequestResult<TorrServer> = safeRequest {
        val normalized = normalizeTorrServerAddress(address)
        val version = api.echo(normalized)
        check(version.isNotEmpty()) { "По адресу $normalized отвечает не TorrServer" }
        TorrServer(address = normalized, version = version).also(store::save)
    }

    override fun forget() = store.clear()
}

/**
 * Адрес как его набрали пультом — «192.168.1.20» или «nas.local:8092» — в базовый URL: схема
 * http (TorrServer в своей сети без TLS) и порт по умолчанию 8090, если не указан.
 */
internal fun normalizeTorrServerAddress(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    require(trimmed.isNotEmpty()) { "Введите адрес TorrServer" }
    if (trimmed.contains("://")) return trimmed
    val host = trimmed.substringBefore('/')
    val path = trimmed.removePrefix(host)
    val hostWithPort = if (host.contains(':')) host else "$host:$DEFAULT_PORT"
    return "http://$hostWithPort$path"
}

private const val DEFAULT_PORT = 8090
