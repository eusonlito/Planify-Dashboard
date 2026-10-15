package com.lito.planify.dashboard.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiskCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private data class Sample(val name: String, val items: List<Int>)

    private val dir get() = File(folder.root, "cache")
    private val cache by lazy { DiskCache(dir) }

    @Test
    fun writesAndReadsBack() {
        cache.write("sample", Cached(Sample("a", listOf(1, 2)), 42L), Sample::class.java)
        assertEquals(Cached(Sample("a", listOf(1, 2)), 42L), cache.read<Sample>("sample", Sample::class.java))
    }

    @Test
    fun survivesANewInstance() {
        cache.write("sample", Cached(Sample("a", emptyList()), 1L), Sample::class.java)
        assertEquals("a", DiskCache(dir).read<Sample>("sample", Sample::class.java)?.value?.name)
    }

    @Test
    fun missingKeyIsNull() {
        assertNull(cache.read<Sample>("nothing", Sample::class.java))
    }

    @Test
    fun corruptFileIsTreatedAsEmpty() {
        dir.mkdirs()
        File(dir, "sample.json").writeText("{ esto no es json")
        assertNull(cache.read<Sample>("sample", Sample::class.java))
        File(dir, "sample.json").writeText("""{"fetchedAt": 3}""")
        assertNull(cache.read<Sample>("sample", Sample::class.java))
    }

    @Test
    fun writeReplacesWithoutLeavingTemporaryFiles() {
        cache.write("sample", Cached(Sample("old", emptyList()), 1L), Sample::class.java)
        cache.write("sample", Cached(Sample("new", emptyList()), 2L), Sample::class.java)
        assertEquals("new", cache.read<Sample>("sample", Sample::class.java)?.value?.name)
        assertEquals(listOf("sample.json"), dir.list()!!.toList())
    }

    @Test
    fun listsKeysByPrefixAndRemoves() {
        listOf("week-2026-10-12", "week-2026-10-19", "content").forEach {
            cache.write(it, Cached(Sample(it, emptyList()), 1L), Sample::class.java)
        }
        assertEquals(setOf("week-2026-10-12", "week-2026-10-19"), cache.keys("week-").toSet())
        cache.remove("week-2026-10-12")
        assertEquals(listOf("week-2026-10-19"), cache.keys("week-"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsKeysThatAreNotPlainFileNames() {
        cache.remove("../fuera")
    }

    @Test
    fun keysOfAnEmptyCacheAreEmpty() {
        assertTrue(cache.keys().isEmpty())
    }
}
