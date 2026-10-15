package com.lito.planify.dashboard.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --- Planify (solo lo que lee el dashboard) ---

data class AuthRequest(val email: String, val password: String)

data class UserResponse(val id: Int, val name: String, val email: String, val token: String?)

data class Person(val id: Int, val name: String, val color: String)

/** Evento o sesión de rutina (`routine_id` no nulo). `date_at` es hora local flotante. */
data class Event(
    val id: Int,
    val calendar_id: Int,
    val calendar_name: String?,
    val calendar_color: String?,
    val title: String,
    val date_at: String,
    val routine_id: Int? = null,
    val persons: List<Person>? = null
) {
    val at: LocalDateTime get() = LocalDateTime.parse(date_at, SQL_DATE_TIME)
}

data class Task(
    val id: Int,
    val task_list_id: Int,
    val task_list_name: String?,
    val task_list_color: String?,
    val title: String,
    val order: Int,
    val completed_at: String?
)

/** Contenido de Hoy (ver ContentRepository). */
data class Content(
    val events: List<Event>,
    val sessions: List<Event>,
    val tasks: List<Task>
)

/** Una semana del calendario (lunes a domingo), descargada al navegar hasta ella. */
data class Week(val events: List<Event>, val sessions: List<Event>)

val SQL_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

// --- Open-Meteo ---

data class Location(val name: String, val detail: String, val latitude: Double, val longitude: Double)

data class GeoResponse(val results: List<GeoResult>? = null)

data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
)

data class Forecast(
    val current: Current,
    val hourly: Hourly,
    val daily: Daily
)

/**
 * La máxima y la mínima de cada día, sacadas de sus horas. Open-Meteo calcula las diarias con más detalle que la
 * hora en punto (cada 15 minutos los primeros días, y a partir del octavo con otro modelo), así que no coincidían
 * con la gráfica por horas: 28,5 en la gráfica y 28,6 en el resumen (28° y 29° una vez redondeados), o hasta 2–3°
 * de diferencia la segunda semana. Se hace una vez al recibir la previsión, antes de guardarla.
 * Los días sin todas sus horas (23 el día del cambio de hora) se quedan con los valores de Open-Meteo.
 */
fun Forecast.withDailyFromHourly(): Forecast {
    val max = HashMap<String, Double>()
    val min = HashMap<String, Double>()
    val hours = HashMap<String, Int>()
    hourly.time.forEachIndexed { i, time ->
        val t = hourly.temperature_2m.getOrNull(i) ?: return@forEachIndexed
        val date = time.substring(0, 10)
        max[date] = maxOf(max[date] ?: t, t)
        min[date] = minOf(min[date] ?: t, t)
        hours[date] = (hours[date] ?: 0) + 1
    }
    fun complete(date: String) = (hours[date] ?: 0) >= 23
    return copy(
        daily = daily.copy(
            temperature_2m_max = daily.time.mapIndexed { i, date -> if (complete(date)) max[date] else daily.temperature_2m_max.getOrNull(i) },
            temperature_2m_min = daily.time.mapIndexed { i, date -> if (complete(date)) min[date] else daily.temperature_2m_min.getOrNull(i) }
        )
    )
}

data class Current(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int?,
    val weather_code: Int,
    val wind_speed_10m: Double,
    val wind_direction_10m: Double,
    val is_day: Int
)

data class Hourly(
    val time: List<String>,
    val temperature_2m: List<Double?>,
    val precipitation_probability: List<Int?>,
    val precipitation: List<Double?>,
    val weather_code: List<Int?>,
    val is_day: List<Int?>
)

data class Daily(
    val time: List<String>,
    val weather_code: List<Int?>,
    val temperature_2m_max: List<Double?>,
    val temperature_2m_min: List<Double?>,
    val precipitation_probability_max: List<Int?>,
    val precipitation_sum: List<Double?>,
    val wind_speed_10m_max: List<Double?>,
    val sunrise: List<String>,
    val sunset: List<String>
)
