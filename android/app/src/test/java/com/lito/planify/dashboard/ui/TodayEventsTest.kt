package com.lito.planify.dashboard.ui

import com.lito.planify.dashboard.data.Event
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayEventsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val morning = Event(1, 1, null, null, "Mañana", "2026-11-19 08:00:00")
    private val evening = morning.copy(id = 2, date_at = "2026-11-20 18:00:00")
    private val routine = morning.copy(id = 3, routine_id = 5, date_at = "2026-11-20 09:00:00")
    private val events = listOf(morning, evening)

    @Test fun calendarHighlightAppearsOnce() {
        assertEquals(events, eventsForList(events, evening, today, null))
    }

    @Test fun highlightedRoutineIsInsertedChronologicallyAndRespectsTheSelectedDay() {
        assertEquals(listOf(morning, routine, evening), eventsForList(events, routine, today, null))
        assertEquals(listOf(morning), eventsForList(events, routine, today, today))
        assertEquals(listOf(routine, evening), eventsForList(events, routine, today, today.plusDays(1)))
    }
}
