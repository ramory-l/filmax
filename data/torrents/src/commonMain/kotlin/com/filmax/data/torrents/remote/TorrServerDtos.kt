package com.filmax.data.torrents.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Результат поиска `/torznab/search/` — то, что Jackett нашёл на трекерах, в разметке TorrServer.
 * Поля с большой буквы — так их пишет Go-сервер. Всё nullable: пустые поля сервер то пропускает,
 * то шлёт нулём («Year»: 0), и одно обязательное поле уронило бы разбор всей выдачи.
 */
@Serializable
internal data class TorznabResultDto(
    @SerialName("Title") val title: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Size") val size: String? = null,
    @SerialName("CreateDate") val createDate: String? = null,
    @SerialName("Tracker") val tracker: String? = null,
    /** Ссылка Jackett на .torrent — на адрес, видный только самому TorrServer. */
    @SerialName("Link") val link: String? = null,
    @SerialName("Magnet") val magnet: String? = null,
    @SerialName("Peer") val peers: Double? = null,
    @SerialName("Seed") val seeders: Double? = null,
)

/** Раздача в TorrServer (`/torrents`): `file_stats` пуст, пока не получены метаданные. */
@Serializable
internal data class TorrentStatusDto(
    val hash: String? = null,
    val name: String? = null,
    val title: String? = null,
    val stat: Int? = null,
    @SerialName("file_stats") val files: List<TorrentFileDto>? = null,
)

/** Файл раздачи: [id] — номер для потока (`index=`), с единицы. */
@Serializable
internal data class TorrentFileDto(
    val id: Int? = null,
    val path: String? = null,
    val length: Long? = null,
)

/**
 * Тело `/torrents`: одно на все действия, нужные поля — у каждого своё. `save_to_db` выключен:
 * раздачу, выбранную для просмотра, клиент держит у себя и при нужде добавит заново, а база
 * TorrServer — список раздач, которые человек добавил сам.
 */
@Serializable
internal data class TorrentsRequest(
    val action: String,
    val link: String? = null,
    val hash: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val data: String? = null,
    @SerialName("save_to_db") val saveToDb: Boolean? = null,
)
