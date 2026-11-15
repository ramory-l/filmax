package com.filmax.app.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Релизы Filmax и «Плёнки» лежат в одном репозитории: каждое приложение находит свой по префиксу
 * тега и не путает его с чужим, черновиком или пре-релизом.
 */
class LatestReleaseTest {

    private val releases = listOf(
        ReleaseDto(tagName = "plenka-v0.0.2", prerelease = true),
        ReleaseDto(tagName = "plenka-v0.0.1"),
        ReleaseDto(tagName = "v2.10.0"),
        ReleaseDto(tagName = "v2.9.0"),
    )

    @Test
    fun `filmax skips releases of the other app`() {
        assertEquals("v2.10.0", releases.latestFor("v")?.tagName)
    }

    @Test
    fun `plenka takes its own published release`() {
        assertEquals("plenka-v0.0.1", releases.latestFor("plenka-v")?.tagName)
    }

    @Test
    fun `drafts and foreign prefixes are not updates`() {
        val noise = listOf(ReleaseDto(tagName = "v3.0.0", draft = true), ReleaseDto(tagName = "video-1"))

        assertNull(noise.latestFor("v"))
    }
}
