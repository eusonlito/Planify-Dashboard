package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.data.Person
import com.lito.planify.dashboard.data.Task
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle

private val Gap = 28.dp

/**
 * Rejilla de 3 columnas en dos filas (55 % / 45 % del alto): arriba la hora (1/3) y el tiempo (2/3); abajo
 * eventos, rutinas y tareas.
 */
@Composable
fun TodayScreen(state: UiState) {
    val content = state.content

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
        val column: Dp = (maxWidth - Gap * 2) / 3
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Gap)) {
            Row(Modifier.fillMaxWidth().weight(0.55f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                ClockColumn(state, Modifier.width(column).fillMaxHeight())
                val forecast = state.forecast
                val location = state.location
                if (forecast != null && location != null) {
                    WeatherCard(forecast, location.name, state.now, days = 7, modifier = Modifier.width(column * 2 + Gap).fillMaxHeight(), compact = true)
                } else {
                    Card(Modifier.width(column * 2 + Gap).fillMaxHeight()) {
                        CardHeader("Tiempo", Icons.Outlined.Thermostat, Palette.Warm)
                        Text(if (location == null) "Elige una ubicación en Ajustes" else "Cargando previsión…", style = Type.Body.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(0.45f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                EventsCard(state, Modifier.width(column).fillMaxHeight())
                RoutinesCard(state, Modifier.width(column).fillMaxHeight())
                TasksCard(content?.tasks.orEmpty(), Modifier.width(column).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Palette.Card, RoundedCornerShape(32.dp))
            .padding(horizontal = 34.dp, vertical = 28.dp),
        content = content
    )
}

// --- Hora y siguiente evento ---

@Composable
private fun ClockColumn(state: UiState, modifier: Modifier) {
    val now = state.now
    val today = now.toLocalDate()
    val next = nextEvent(state.content?.events.orEmpty(), now)
    Column(modifier) {
        // Hora y fecha, centradas en el espacio que deja la tarjeta del siguiente evento
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FlipClock(if (state.clockValid) now.format(HourMinute) else "--:--")
            Text(
                if (state.clockValid) "${today.weekdayName()} · ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Spanish)}".uppercase(Spanish) else "SINCRONIZANDO HORA…",
                style = Type.Mono.copy(fontSize = 26.sp, letterSpacing = 6.sp, color = Palette.InkSoft),
                maxLines = 1,
                modifier = Modifier.padding(top = 34.dp)
            )
        }
        if (next != null) NextCard(next, now)
    }
}

/** El siguiente evento: el primero que aún no ha empezado. Va en su tarjeta y no se repite en la lista de próximos. */
private fun nextEvent(events: List<Event>, now: LocalDateTime): Event? = events.firstOrNull { !it.at.isBefore(now) }

/**
 * Próximo evento en una tarjeta del color de su calendario. Arriba, sus personas y, a la derecha, lo que falta en una
 * píldora con el mismo formato que la lista de próximos eventos; debajo, el título y el día y la hora.
 */
@Composable
private fun NextCard(event: Event, now: LocalDateTime) {
    val color = parseColor(event.calendar_color, Palette.Accent)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(32.dp))
            .padding(horizontal = 34.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.persons.orEmpty().joinToString(", ") { it.name }.uppercase(Spanish),
                style = Type.Label.copy(color = Color.White.copy(alpha = 0.9f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                relativeBadge(now, event.at),
                style = Type.Tiny.copy(color = Color.White, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        Text(event.title, style = Type.Large.copy(color = Color.White, fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "${relativeDay(event.at.toLocalDate(), now.toLocalDate())} · ${event.at.format(HourMinute)}",
            style = Type.Small.copy(color = Color.White.copy(alpha = 0.85f)),
            maxLines = 1
        )
    }
}

// --- Tarjetas inferiores ---

/**
 * Próximos eventos con el formato del widget de la app: barra del color del calendario, "MIÉ 14 OCT · 16:00"
 * con el tiempo que falta en una píldora, y el título debajo. Los ya pasados de hoy, en gris. Sin el siguiente, que ya
 * está en su tarjeta.
 */
@Composable
private fun EventsCard(state: UiState, modifier: Modifier) {
    val now = state.now
    val all = state.content?.events.orEmpty()
    val next = nextEvent(all, now)
    val events = all.filter { it !== next }
    Card(modifier) {
        CardHeader("Próximos eventos", Icons.Outlined.Event, Palette.Rain, count = events.count { !it.at.isBefore(now) })
        if (events.isEmpty()) {
            EmptyState(Icons.Outlined.EventAvailable, Palette.Rain, "Sin eventos próximos", "Nada pendiente en los calendarios", Modifier.weight(1f).fillMaxWidth())
            return@Card
        }
        Column(Modifier.fadingScroll(rememberScrollState())) {
            events.forEach { event ->
                EventRow(event, now)
                Hairline()
            }
        }
    }
}

@Composable
private fun EventRow(event: Event, now: LocalDateTime) {
    val past = event.at.isBefore(now)
    val color = if (past) Palette.Line else parseColor(event.calendar_color)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(7.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(start = 18.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${event.at.toLocalDate().shortLabel()} · ${event.at.format(HourMinute)}",
                    style = Type.Tiny.copy(color = if (past) Palette.InkMuted else Palette.InkSoft, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (!past) {
                    Text(
                        relativeBadge(now, event.at),
                        style = Type.Tiny.copy(color = Palette.InkSoft, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                        maxLines = 1,
                        modifier = Modifier.background(Palette.Highlight, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    style = Type.Body.copy(color = if (past) Palette.InkMuted else Palette.Ink, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!past) PersonPills(event.persons, Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** Rutinas de hoy: la lista del día, con la siguiente sesión resaltada y las pasadas atenuadas. */
@Composable
private fun RoutinesCard(state: UiState, modifier: Modifier) {
    val now = state.now
    val sessions = state.content?.sessions.orEmpty().filter { it.at.toLocalDate() == now.toLocalDate() }
    val upcoming = sessions.firstOrNull { !it.at.isBefore(now) }
    Card(modifier) {
        CardHeader("Rutinas de hoy", Icons.Outlined.Autorenew, Palette.Warm, count = sessions.count { !it.at.isBefore(now) })
        if (sessions.isEmpty()) {
            EmptyState(Icons.Outlined.Weekend, Palette.Warm, "Hoy no hay rutinas", "Día libre", Modifier.weight(1f).fillMaxWidth())
            return@Card
        }
        Column(Modifier.padding(top = 4.dp).fadingScroll(rememberScrollState())) {
            sessions.forEach { session ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).alpha(if (session.at.isBefore(now)) 0.45f else 1f)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(session.title, style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // La siguiente sesión, con la hora resaltada
                        Text(session.at.format(HourMinute), style = Type.Small.copy(color = if (session == upcoming) Palette.Accent else Palette.InkSoft))
                    }
                    PersonPills(session.persons)
                }
                Hairline()
            }
        }
    }
}

@Composable
private fun TasksCard(tasks: List<Task>, modifier: Modifier) {
    val byList = tasks.groupBy { it.task_list_id }
    Card(modifier) {
        CardHeader("Tareas", Icons.Outlined.Checklist, Palette.Online, count = tasks.size)
        if (tasks.isEmpty()) {
            EmptyState(Icons.Outlined.TaskAlt, Palette.Online, "Todo hecho", "No quedan tareas pendientes", Modifier.weight(1f).fillMaxWidth())
            return@Card
        }
        Column(Modifier.padding(top = 4.dp).fadingScroll(rememberScrollState())) {
            byList.values.forEach { list ->
                val color = parseColor(list.first().task_list_color)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)) {
                    Dot(color, 12)
                    Text(list.first().task_list_name.orEmpty(), style = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 12.dp))
                }
                list.forEach { TaskRow(it) }
            }
        }
    }
}

@Composable
fun TaskRow(task: Task) {
    // Sin casilla: el dashboard es de solo lectura y no se pueden marcar.
    Text(task.title, style = Type.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

/** Nombres en píldoras del color de cada persona. */
@Composable
private fun PersonPills(persons: List<Person>?, modifier: Modifier = Modifier) {
    if (persons.isNullOrEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        persons.forEach { person ->
            Text(
                person.name,
                style = Type.Tiny.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.background(parseColor(person.color), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}
