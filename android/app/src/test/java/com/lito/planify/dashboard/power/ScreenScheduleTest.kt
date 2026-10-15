package com.lito.planify.dashboard.power

import com.lito.planify.dashboard.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScreenScheduleTest {
    // Laborables 07:00–23:00, fin de semana 08:30–23:30
    private val schedule = Schedule(enabled = true)
    private val wednesday = LocalDateTime.of(2026, 10, 14, 0, 0)
    private val saturday = LocalDateTime.of(2026, 10, 17, 0, 0)

    @Test
    fun disabledScheduleIsAlwaysOn() {
        assertTrue(ScreenSchedule.isOn(Schedule(enabled = false), wednesday.withHour(3)))
    }

    @Test
    fun windowIncludesOnAndExcludesOff() {
        assertFalse(ScreenSchedule.isOn(schedule, wednesday.withHour(6).withMinute(59)))
        assertTrue(ScreenSchedule.isOn(schedule, wednesday.withHour(7)))
        assertTrue(ScreenSchedule.isOn(schedule, wednesday.withHour(22).withMinute(59)))
        assertFalse(ScreenSchedule.isOn(schedule, wednesday.withHour(23)))
    }

    @Test
    fun weekendUsesItsOwnWindow() {
        assertFalse(ScreenSchedule.isOn(schedule, saturday.withHour(8)))
        assertTrue(ScreenSchedule.isOn(schedule, saturday.withHour(23).withMinute(15)))
    }

    @Test
    fun nextChangeIsTheNextBoundary() {
        assertEquals(wednesday.withHour(7), ScreenSchedule.nextChange(schedule, wednesday.withHour(3)))
        assertEquals(wednesday.withHour(23), ScreenSchedule.nextChange(schedule, wednesday.withHour(7)))
        // Viernes noche → sábado 08:30
        assertEquals(saturday.withHour(8).withMinute(30), ScreenSchedule.nextChange(schedule, saturday.minusDays(1).withHour(23)))
    }

    @Test
    fun nightClockReplacesTheScreenOffOutsideTheWindow() {
        assertTrue(ScreenSchedule.isNight(schedule, wednesday.withHour(23)))
        assertFalse(ScreenSchedule.isNight(schedule, wednesday.withHour(12)))
        assertFalse(ScreenSchedule.isNight(schedule.copy(screenOff = true), wednesday.withHour(23)))
        assertFalse(ScreenSchedule.isNight(Schedule(enabled = false), wednesday.withHour(3)))
    }
}
