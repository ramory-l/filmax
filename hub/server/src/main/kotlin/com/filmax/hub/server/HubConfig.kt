package com.filmax.hub.server

import com.filmax.hub.protocol.HUB_DEFAULT_PORT
import java.io.File

/**
 * Настройки хаба из переменных окружения: их задаёт LaunchAgent на Маке, а токен TMDB живёт
 * только там — ни в репозитории, ни в APK его нет.
 */
internal data class HubConfig(
    val port: Int,
    val name: String,
    /** Токен чтения TMDB. Без него хаб отвечает на `/info`, а каталог — 503 «не настроен». */
    val tmdbToken: String?,
    val cacheDir: File,
    val torrServer: String?,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): HubConfig = HubConfig(
            port = environment["FILMAX_HUB_PORT"]?.toIntOrNull() ?: HUB_DEFAULT_PORT,
            name = environment["FILMAX_HUB_NAME"]?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            tmdbToken = environment["TMDB_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() },
            cacheDir = environment["FILMAX_HUB_CACHE"]?.let(::File)
                ?: File(System.getProperty("user.home"), DEFAULT_CACHE),
            torrServer = environment["TORRSERVER_URL"]?.trim()?.takeIf { it.isNotEmpty() },
        )

        private const val DEFAULT_NAME = "Домашний хаб"
        private const val DEFAULT_CACHE = "Library/Caches/filmax-hub"
    }
}
