package com.filmax.data.hub

import com.filmax.core.domain.common.RequestResult
import com.filmax.core.domain.common.safeRequest
import com.filmax.core.domain.source.ContentSource
import com.filmax.core.domain.source.HomeServer
import com.filmax.core.domain.source.HomeServerRepository
import com.filmax.core.domain.source.QuickConnectCode
import com.filmax.core.domain.torrent.TorrServerRepository
import com.filmax.data.hub.remote.HubApi
import com.filmax.hub.protocol.HUB_DEFAULT_PORT
import com.filmax.hub.protocol.HUB_PROTOCOL_VERSION

/**
 * Подключение к хабу на шаге «Домашний сервер»: найти в сети или ввести адрес. Учётных записей у
 * хаба нет, поэтому подключиться — уже войти; заодно подключается TorrServer хаба, и его адрес
 * набирать не нужно.
 */
internal class HubServerRepository(
    private val api: HubApi,
    private val session: HubSession,
    private val discovery: HubDiscovery,
    private val torrServers: TorrServerRepository,
) : HomeServerRepository {

    override val source: ContentSource = ContentSource.HUB

    override suspend fun discover(): List<HomeServer> =
        discovery.find().map { found -> HomeServer(address = found.address, name = found.name, accounts = false) }

    override suspend fun connect(address: String): RequestResult<HomeServer> = safeRequest {
        val normalized = hubAddress(address)
        val info = api.info(normalized)
        // Хаб старше приложения не знает его путей и полей: лучше сказать сразу, чем показать пустые ряды.
        check(info.protocol >= HUB_PROTOCOL_VERSION) { "Хаб устарел — обновите его на компьютере" }
        HomeServer(address = normalized, name = info.name, version = info.version, accounts = false)
    }

    override suspend fun login(server: HomeServer, username: String, password: String): RequestResult<Unit> =
        safeRequest {
            val info = api.info(server.address)
            session.save(StoredHub(address = server.address, name = info.name, torrServer = info.torrServer))
            // TorrServer — удобство: не ответил сейчас — экран торрентов попросит адрес сам.
            info.torrServer?.let { torrServers.connect(it) }
            Unit
        }

    override suspend fun startQuickConnect(server: HomeServer): RequestResult<QuickConnectCode> =
        RequestResult.Error(NO_CODE)

    override suspend fun checkQuickConnect(server: HomeServer, code: QuickConnectCode): RequestResult<Boolean> =
        RequestResult.Error(NO_CODE)

    private companion object {
        const val NO_CODE = "У хаба нет входа по коду"
    }
}

/**
 * Адрес, как его вводят («192.168.1.20», «mac.local:9000»), — в базовый адрес API. Без схемы —
 * http и, если порта нет, порт хаба по умолчанию; со схемой адрес берётся как есть.
 */
internal fun hubAddress(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    require(trimmed.isNotEmpty()) { "Введите адрес хаба" }
    if (trimmed.contains("://")) return trimmed
    val host = trimmed.substringBefore('/')
    val withPort = if (host.contains(':')) host else "$host:$HUB_DEFAULT_PORT"
    return "http://$withPort${trimmed.removePrefix(host)}"
}
