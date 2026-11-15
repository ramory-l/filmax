package com.filmax.hub.server.images

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Картинки TMDB через хаб: телевизор ходит только к хабу, а хаб скачивает кадр один раз и дальше
 * отдаёт с диска. image.tmdb.org из домашней сети медленный, а постеры Главной — сотня файлов на
 * каждый запуск.
 *
 * [http] — клиент без токена: CDN картинок TMDB его не просит.
 */
internal class ImageProxy(
    private val http: HttpClient,
    private val cacheDir: File,
    private val io: CoroutineDispatcher,
) {
    /** Файл картинки на диске, скачанный при первом запросе. Неизвестный размер или имя — ошибка запроса. */
    suspend fun file(size: String, name: String): File {
        require(size in SIZES) { "Размер $size не поддерживается" }
        require(FILE_NAME.matches(name)) { "Имя файла $name не похоже на картинку TMDB" }
        val cached = File(cacheDir, "$size/$name")
        if (withContext(io) { cached.isFile }) return cached
        val bytes = http.get("$TMDB_IMAGES$size/$name").bodyAsBytes()
        withContext(io) { store(cached, bytes) }
        return cached
    }

    /**
     * Запись через временный файл и атомарный перенос: два одновременных запроса одного постера не
     * отдадут недописанный файл, а оборванная загрузка не оставит битый.
     */
    private fun store(target: File, bytes: ByteArray) {
        val folder = target.parentFile
        folder.mkdirs()
        val temporary = File.createTempFile(target.name, TEMP_SUFFIX, folder)
        try {
            temporary.writeBytes(bytes)
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        const val TMDB_IMAGES = "https://image.tmdb.org/t/p/"
        const val TEMP_SUFFIX = ".part"

        /** Размеры, которые TMDB отдаёт для постеров, фонов и кадров серий. */
        val SIZES = setOf("w92", "w154", "w185", "w300", "w342", "w500", "w780", "w1280", "original")

        /**
         * Имя файла TMDB: буквы и цифры с расширением. Ни слэшей, ни точек в начале — запрос не
         * выведет путь за пределы кеша.
         */
        val FILE_NAME = Regex("[A-Za-z0-9_-]+\\.(jpg|jpeg|png|svg|webp)")
    }
}

/** Клиент CDN картинок: без токена и языка, но с таймаутом — зависший кадр не должен держать запрос. */
internal fun imageHttpClient(engine: HttpClient): HttpClient = engine.config {
    install(HttpTimeout) { requestTimeoutMillis = IMAGE_TIMEOUT_MS }
    expectSuccess = true
}

private const val IMAGE_TIMEOUT_MS = 20_000L
