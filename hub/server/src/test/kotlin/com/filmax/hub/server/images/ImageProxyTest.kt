package com.filmax.hub.server.images

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageProxyTest {

    private val cacheDir: File = createTempDirectory("hub-images").toFile()
    private val downloads = mutableListOf<String>()
    private val cdn = MockEngine { request ->
        downloads += request.url.toString()
        respond(POSTER, HttpStatusCode.OK)
    }
    private val proxy = ImageProxy(imageHttpClient(HttpClient(cdn)), cacheDir, UnconfinedTestDispatcher())

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `poster is downloaded once and then served from disk`() = runTest {
        proxy.file("w342", "poster.jpg")
        val file = proxy.file("w342", "poster.jpg")

        assertContentEquals(POSTER, file.readBytes())
        assertEquals(listOf("https://image.tmdb.org/t/p/w342/poster.jpg"), downloads)
        assertEquals(listOf("poster.jpg"), File(cacheDir, "w342").list()?.toList())
    }

    @Test
    fun `name with a path is rejected before any download`() = runTest {
        assertFailsWith<IllegalArgumentException> { proxy.file("w342", "../secret.jpg") }
        assertFailsWith<IllegalArgumentException> { proxy.file("w342", ".hidden.jpg") }
        assertEquals(emptyList(), downloads)
    }

    @Test
    fun `unknown size is rejected`() = runTest {
        assertFailsWith<IllegalArgumentException> { proxy.file("w9999", "poster.jpg") }
    }

    private companion object {
        val POSTER = byteArrayOf(1, 2, 3, 4)
    }
}
