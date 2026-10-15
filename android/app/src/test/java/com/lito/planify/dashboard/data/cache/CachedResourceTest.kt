package com.lito.planify.dashboard.data.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CachedResourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private val cache by lazy { DiskCache(folder.root) }

    private fun resource() = CachedResource<String>(cache, "value", String::class.java, maxAgeMs = 60_000L, now = { clock })

    @Test
    fun startsEmptyAndStaleWithoutCopy() {
        val resource = resource()
        assertNull(resource.data.value)
        assertTrue(resource.isStale())
    }

    @Test
    fun successIsPublishedAndPersisted() = runBlocking {
        val result = resource().refresh { "fresh" }
        assertEquals("fresh", result.getOrNull())
        // Un arranque nuevo (otra instancia) lo encuentra en disco
        assertEquals(Cached("fresh", clock), resource().data.value)
    }

    @Test
    fun failureKeepsTheLastGoodCopy() = runBlocking {
        val resource = resource()
        resource.refresh { "good" }
        clock += 120_000L
        val result = resource.refresh { throw IOException("sin red") }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("good", resource.data.value?.value)
        assertEquals("good", resource().data.value?.value)
    }

    @Test
    fun staleOnlyAfterMaxAge() = runBlocking {
        val resource = resource()
        resource.refresh { "x" }
        clock += 59_999L
        assertFalse(resource.isStale())
        clock += 1L
        assertTrue(resource.isStale())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotSwallowed(): Unit = runBlocking {
        resource().refresh { throw CancellationException("cancelado") }
    }

    @Test
    fun clearRemovesMemoryAndDisk() = runBlocking {
        val resource = resource()
        resource.refresh { "x" }
        resource.clear()
        assertNull(resource.data.value)
        assertNull(resource().data.value)
    }
}
