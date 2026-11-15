package com.filmax.hub.server.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TtlCacheTest {

    private var now = 0L
    private var loads = 0

    private suspend fun TtlCache<String>.load(key: String): String = get(key) {
        loads++
        "$key#$loads"
    }

    @Test
    fun `value is loaded once while fresh`() = runTest {
        val cache = TtlCache<String>(ttlMillis = 1000, clock = { now })

        cache.load("hot")
        now = 999
        val value = cache.load("hot")

        assertEquals("hot#1", value)
        assertEquals(1, loads)
    }

    @Test
    fun `expired value is loaded again`() = runTest {
        val cache = TtlCache<String>(ttlMillis = 1000, clock = { now })

        cache.load("hot")
        now = 1000

        assertEquals("hot#2", cache.load("hot"))
    }

    @Test
    fun `full cache drops the entry that expires first`() = runTest {
        val cache = TtlCache<String>(ttlMillis = 1000, capacity = 2, clock = { now })

        cache.load("first")
        now = 10
        cache.load("second")
        cache.load("third")
        cache.load("second")
        cache.load("first")

        assertEquals(4, loads)
    }
}
