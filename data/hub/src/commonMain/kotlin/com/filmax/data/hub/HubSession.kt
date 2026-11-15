package com.filmax.data.hub

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** К какому хабу подключились: адрес API, имя для Профиля и TorrServer, которым хаб качает. */
@Serializable
internal data class StoredHub(
    val address: String,
    val name: String,
    val torrServer: String? = null,
)

/**
 * Подключение к хабу — в настройках устройства. Учётной записи у хаба нет: адрес и есть вход, и
 * выход — забыть адрес.
 */
internal class HubSession(private val settings: Settings) {

    private val state = MutableStateFlow(load())

    val current: StoredHub? get() = state.value

    val signedIn: Flow<Boolean> = state.map { it != null }

    /** Текущий хаб; без подключения — ошибка с 401, её экраны покажут как «сессия истекла». */
    fun require(): StoredHub = current ?: throw IllegalStateException(NOT_CONNECTED)

    fun save(hub: StoredHub) {
        settings.putString(KEY, json.encodeToString(StoredHub.serializer(), hub))
        state.value = hub
    }

    fun clear() {
        settings.remove(KEY)
        state.value = null
    }

    private fun load(): StoredHub? = settings.getStringOrNull(KEY)
        ?.let { stored -> runCatching { json.decodeFromString(StoredHub.serializer(), stored) }.getOrNull() }

    private companion object {
        const val KEY = "hub_session"
        const val NOT_CONNECTED = "Нет подключения к хабу (401)"
        val json = Json { ignoreUnknownKeys = true }
    }
}
