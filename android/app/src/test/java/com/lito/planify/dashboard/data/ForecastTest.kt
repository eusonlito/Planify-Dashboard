package com.lito.planify.dashboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastTest {
    private fun forecast(hours: Map<String, List<Double>>, days: List<String>) = Forecast(
        current = Current("2026-10-15T12:00", 20.0, 50, 0, 5.0, 0.0, 1),
        hourly = hours.flatMap { (date, temps) -> temps.mapIndexed { h, t -> "${date}T%02d:00".format(h) to t } }.let { list ->
            Hourly(list.map { it.first }, list.map { it.second }, list.map { 0 }, list.map { 0.0 }, list.map { 0 }, list.map { 1 })
        },
        daily = Daily(
            time = days,
            weather_code = days.map { 0 },
            temperature_2m_max = days.map { 99.0 },
            temperature_2m_min = days.map { -99.0 },
            precipitation_probability_max = days.map { 0 },
            precipitation_sum = days.map { 0.0 },
            wind_speed_10m_max = days.map { 0.0 },
            sunrise = days.map { "${it}T08:00" },
            sunset = days.map { "${it}T19:00" }
        )
    )

    @Test
    fun dailyExtremesComeFromTheHours() {
        val day = List(24) { 10.0 + it % 12 } // de 10 a 21
        val result = forecast(mapOf("2026-10-23" to day), listOf("2026-10-23")).withDailyFromHourly().daily
        assertEquals(21.0, result.temperature_2m_max[0]!!, 0.0)
        assertEquals(10.0, result.temperature_2m_min[0]!!, 0.0)
    }

    @Test
    fun dayChangingClockKeepsItsHours() {
        val result = forecast(mapOf("2026-10-25" to List(23) { 5.0 + it }), listOf("2026-10-25")).withDailyFromHourly().daily
        assertEquals(27.0, result.temperature_2m_max[0]!!, 0.0)
        assertEquals(5.0, result.temperature_2m_min[0]!!, 0.0)
    }

    @Test
    fun incompleteDayKeepsOpenMeteoValues() {
        val result = forecast(mapOf("2026-10-29" to List(6) { 15.0 }), listOf("2026-10-29", "2026-10-30")).withDailyFromHourly().daily
        assertEquals(listOf(99.0, 99.0), result.temperature_2m_max)
        assertEquals(listOf(-99.0, -99.0), result.temperature_2m_min)
    }
}
