package com.lito.planify.dashboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReferenceClockTest {
    private val utc = ZoneId.of("UTC")
    private val build = millis(2026, 10, 1, 0, 0)
    private val factory = millis(2026, 1, 30, 4, 0)

    private var saved = 0L
    private var saves = 0
    private var now = 0L

    private val clock = ReferenceClock(build, { saved }, { saved = it; saves++ }, { now }, { utc })

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun validTimeIsUsedAndSaved() {
        now = millis(2026, 10, 14, 20, 30)
        val time = clock.current()
        assertTrue(time.valid)
        assertEquals(LocalDateTime.of(2026, 10, 14, 20, 30), time.now)
        assertEquals(now, saved)
    }

    @Test
    fun savesAtMostOncePerMinute() {
        now = millis(2026, 10, 14, 20, 30)
        clock.current()
        now += 30_000
        clock.current()
        assertEquals(1, saves)
        now += 30_000
        clock.current()
        assertEquals(2, saves)
    }

    @Test
    fun factoryClockFallsBackToLastValidTime() {
        saved = millis(2026, 10, 14, 23, 10)
        now = factory
        val time = clock.current()
        assertFalse(time.valid)
        assertEquals(LocalDateTime.of(2026, 10, 14, 23, 10), time.now)
        assertEquals(0, saves)
    }

    @Test
    fun factoryClockWithoutHistoryKeepsTheDeviceTime() {
        now = factory
        val time = clock.current()
        assertFalse(time.valid)
        assertEquals(LocalDateTime.of(2026, 1, 30, 4, 0), time.now)
    }

    @Test
    fun aValidTimeBehindTheSavedOneIsSaved() {
        saved = millis(2026, 10, 20, 0, 0)
        now = millis(2026, 10, 14, 20, 30)
        clock.current()
        assertEquals(now, saved)
    }
}
