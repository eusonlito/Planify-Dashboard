package com.lito.planify.dashboard.power

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.lito.planify.dashboard.BuildConfig
import com.lito.planify.dashboard.MainActivity
import com.lito.planify.dashboard.admin.Kiosk
import com.lito.planify.dashboard.data.Schedule
import com.lito.planify.dashboard.data.Store
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Enciende y apaga la pantalla según el horario. Apagar es `lockNow()` (Device Owner, sin keyguard);
 * encender, un wakelock que despierta la pantalla y trae la actividad al frente.
 * Si alguien la enciende con el botón fuera de horario, se vuelve a apagar a los [GRACE_MINUTES].
 * Mientras el reloj no sea válido (el RTC del marco no guarda la hora: arranca en la fecha de fábrica hasta que
 * llega la hora por NTP) no se apaga nunca; al cambiar la hora (TIME_SET) se vuelve a programar.
 */
object ScreenSchedule {
    const val GRACE_MINUTES = 5L

    /** La hora es de fiar si no es anterior a la compilación de la app. */
    fun clockValid() = System.currentTimeMillis() >= BuildConfig.BUILD_TIME

    fun window(schedule: Schedule, day: DayOfWeek): Pair<Int, Int> =
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) schedule.weekendOn to schedule.weekendOff
        else schedule.weekdayOn to schedule.weekdayOff

    fun isOn(schedule: Schedule, at: LocalDateTime): Boolean {
        if (!schedule.enabled) return true
        val (on, off) = window(schedule, at.dayOfWeek)
        val minute = at.hour * 60 + at.minute
        return minute in on until off
    }

    /** Siguiente encendido o apagado posterior a [now]. */
    fun nextChange(schedule: Schedule, now: LocalDateTime): LocalDateTime =
        (0L..7L).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            val (on, off) = window(schedule, date.dayOfWeek)
            listOf(date.atStartOfDay().plusMinutes(on.toLong()), date.atStartOfDay().plusMinutes(off.toLong()))
        }.filter { it.isAfter(now) }.min()

    /** Programa la próxima alarma. Se llama al arrancar, al cambiar el horario y tras cada alarma. */
    fun reschedule(context: Context) {
        val schedule = Store(context).schedule
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        if (!schedule.enabled || !clockValid()) return

        val now = LocalDateTime.now()
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        val next = if (screenOn && !isOn(schedule, now)) now.plusMinutes(GRACE_MINUTES) else nextChange(schedule, now)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Aplica el estado que toca ahora mismo. */
    fun apply(context: Context) {
        val schedule = Store(context).schedule
        if (!clockValid() || isOn(schedule, LocalDateTime.now())) wake(context) else Kiosk.sleep(context)
    }

    @Suppress("DEPRECATION") // SCREEN_BRIGHT + ACQUIRE_CAUSES_WAKEUP es la única forma de encender la pantalla desde una alarma.
    fun wake(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isInteractive) {
            power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "dashboard:wake")
                .acquire(10_000)
        }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ScheduleReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ScreenSchedule.apply(context)
        ScreenSchedule.reschedule(context)
    }
}

/** Al arrancar y cuando cambia la hora (llega por NTP tras arrancar, o cambia la zona horaria). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ScreenSchedule.reschedule(context)
        }
    }
}
