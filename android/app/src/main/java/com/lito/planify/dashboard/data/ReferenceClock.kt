package com.lito.planify.dashboard.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** La hora con la que se pinta todo, y si es la real. */
data class DisplayTime(val now: LocalDateTime, val valid: Boolean)

/**
 * El RTC del marco no guarda la hora: hasta que entra NTP marca la fecha de fábrica, anterior a [validFrom] (la
 * compilación de la app). Mientras tanto se usa la última hora válida, para que la caché (que es de esa fecha) se
 * pinte igual que la última vez que hubo red. La hora válida se guarda como mucho una vez por minuto.
 */
class ReferenceClock(
    private val validFrom: Long,
    private val loadLastValid: () -> Long,
    private val saveLastValid: (Long) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    fun current(): DisplayTime {
        val millis = nowMillis()
        if (millis >= validFrom) {
            val last = loadLastValid()
            // También si la hora válida ha retrocedido (corrección de NTP)
            if (millis - last >= SAVE_EVERY_MS || millis < last) saveLastValid(millis)
            return DisplayTime(at(millis), valid = true)
        }
        val last = loadLastValid()
        return DisplayTime(at(if (last > 0) last else millis), valid = false)
    }

    private fun at(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    private companion object {
        const val SAVE_EVERY_MS = 60_000L
    }
}
