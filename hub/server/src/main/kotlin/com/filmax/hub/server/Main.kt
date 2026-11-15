package com.filmax.hub.server

import com.filmax.hub.server.catalog.HubCatalog
import com.filmax.hub.server.images.ImageProxy
import com.filmax.hub.server.images.imageHttpClient
import com.filmax.hub.server.routes.hubModule
import com.filmax.hub.server.tmdb.TmdbClient
import com.filmax.hub.server.tmdb.tmdbHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File

fun main() = runHub(HubConfig.fromEnvironment())

/**
 * Композиционный корень хаба: здесь собираются клиенты и кеши, а диспетчер IO приходит
 * параметром, как в DI-модулях приложения. Слушает все интерфейсы — телевизор приходит из
 * домашней сети, а не с самого Мака.
 */
internal fun runHub(config: HubConfig, io: CoroutineDispatcher = Dispatchers.IO) {
    val log = LoggerFactory.getLogger("filmax-hub")
    val engine = HttpClient(OkHttp)
    val catalog = config.tmdbToken?.let { token -> HubCatalog(TmdbClient(tmdbHttpClient(token, engine))) }
    if (catalog == null) log.warn("TMDB_TOKEN не задан: каталог будет отвечать 503")
    val images = ImageProxy(imageHttpClient(engine), File(config.cacheDir, IMAGES_DIR), io)

    // Поиск в сети — удобство, а не условие работы: занят UDP-порт — хаб живёт, адрес вводят руками.
    val discoveryFailed = CoroutineExceptionHandler { _, failure ->
        log.warn("Поиск хаба в сети не запустился", failure)
    }
    val background = CoroutineScope(SupervisorJob() + io + discoveryFailed)
    background.launch { HubDiscoveryResponder(config.name, config.port, io).run() }

    embeddedServer(CIO, port = config.port, host = ANY_HOST) { hubModule(config, catalog, images) }
        .start(wait = true)
}

private const val ANY_HOST = "0.0.0.0"
private const val IMAGES_DIR = "images"
