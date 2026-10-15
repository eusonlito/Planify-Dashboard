package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

/** Semanas navegables hacia atrás y hacia delante desde la actual. */
private const val WEEKS = 105
private const val CURRENT = WEEKS / 2

/**
 * Calendario por semanas de lunes a domingo, deslizando a izquierda y derecha. Cada semana se descarga
 * al mostrarla (y las vecinas, para que el deslizamiento no espere); mientras tanto se usa lo que haya
 * del contenido de Hoy.
 */
@Composable
fun CalendarScreen(state: UiState, onLoadWeek: (LocalDate) -> Unit) {
    val today = state.now.toLocalDate()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val pager = rememberPagerState(initialPage = CURRENT) { WEEKS }
    val scope = rememberCoroutineScope()
    fun mondayOf(page: Int): LocalDate = thisMonday.plusWeeks((page - CURRENT).toLong())

    LaunchedEffect(pager.settledPage) {
        val monday = mondayOf(pager.settledPage)
        listOf(monday, monday.plusWeeks(1), monday.minusWeeks(1)).forEach(onLoadWeek)
    }

    val shown = mondayOf(pager.currentPage)
    val shownWeek = weekContent(state, shown)
    val persons = (shownWeek.first + shownWeek.second).flatMap { it.persons.orEmpty() }.distinctBy { it.id }

    ScreenPadding {
        Column(Modifier.fillMaxSize()) {
            // Alto fijo: el botón "Volver a hoy" aparece y desaparece sin mover las columnas.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(84.dp)) {
                Text("Semana ${shown.weekNumber()}", style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
                Text(weekRange(shown), style = Type.Large.copy(color = Palette.InkSoft), modifier = Modifier.padding(start = 24.dp).weight(1f))
                if (pager.currentPage != CURRENT) {
                    PillButton("Volver a hoy") { scope.launch { pager.animateScrollToPage(CURRENT) } }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp), pageSpacing = 40.dp) { page ->
                val monday = mondayOf(page)
                val (events, sessions) = weekContent(state, monday)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    (0 until 7).forEach { offset ->
                        val date = monday.plusDays(offset.toLong())
                        DayColumn(
                            date = date,
                            state = state,
                            events = events.filter { it.at.toLocalDate() == date },
                            sessions = sessions.filter { it.at.toLocalDate() == date },
                            modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (date < today) 0.6f else 1f)
                        )
                    }
                }
            }
            if (persons.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.padding(top = 28.dp)) {
                    persons.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Dot(parseColor(person.color))
                            Text(person.name, style = Type.Small)
                        }
                    }
                }
            }
        }
    }
}

/** Eventos y sesiones de la semana: los descargados para ella o, mientras llegan, los del contenido de Hoy. */
private fun weekContent(state: UiState, monday: LocalDate): Pair<List<Event>, List<Event>> {
    state.weeks[monday]?.let { return it.events to it.sessions }
    val sunday = monday.plusDays(6)
    fun inWeek(event: Event) = event.at.toLocalDate().let { it >= monday && it <= sunday }
    return state.content?.events.orEmpty().filter(::inWeek) to state.content?.sessions.orEmpty().filter(::inWeek)
}

@Composable
private fun DayColumn(date: LocalDate, state: UiState, events: List<Event>, sessions: List<Event>, modifier: Modifier) {
    val isToday = date == state.now.toLocalDate()
    val forecast = state.forecast
    val day = forecast?.dayIndex(date) ?: -1

    Column(
        modifier
            .background(if (isToday) Palette.Card else Palette.Background, RoundedCornerShape(30.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Cabecera de alto fijo, haya previsión o no: día y número con el icono a la derecha, y debajo mín, máx y lluvia.
        val hasWeather = forecast != null && day >= 0
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Column(Modifier.weight(1f)) {
                SectionLabel(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.'))
                Text("${date.dayOfMonth}", style = Type.Title.copy(color = if (isToday) Palette.Accent else Palette.Ink))
            }
            Box(Modifier.size(48.dp)) { if (hasWeather) DayWeatherIcon(forecast!!, date, today = date.takeIf { isToday }, size = 48) }
        }
        Row(Modifier.padding(start = 4.dp, top = 8.dp)) {
            if (hasWeather) {
                Text(
                    "${forecast!!.daily.temperature_2m_min[day].degrees()} / ${forecast.daily.temperature_2m_max[day].degrees()}",
                    style = Type.Small.copy(color = Palette.Ink),
                    maxLines = 1
                )
                Text(
                    "  ·  ${forecast.daily.precipitation_probability_max[day] ?: 0}%",
                    style = Type.Small.copy(color = Palette.Rain),
                    maxLines = 1
                )
            } else {
                Text("—", style = Type.Small.copy(color = Palette.InkMuted))
            }
        }
        Box(Modifier.padding(vertical = 16.dp)) { Hairline() }

        Column(Modifier.weight(1f).fadingScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            events.forEach { WeekEvent(it, past = it.at.isBefore(state.now)) }
        }

        if (sessions.isNotEmpty()) {
            Box(Modifier.padding(vertical = 14.dp)) { Hairline() }
            SectionLabel("Rutinas", Modifier.padding(bottom = 8.dp))
            Column(Modifier.height(300.dp).fadingScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sessions.forEach { session ->
                    // Arriba la hora y el color de cada persona; debajo, el nombre de la rutina
                    Column(Modifier.alpha(if (session.at.isBefore(state.now)) 0.4f else 1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.at.format(HourMinute), style = Type.Tiny.copy(color = Palette.InkMuted))
                            session.persons.orEmpty().forEach { Dot(parseColor(it.color), 10) }
                        }
                        Text(session.title, style = Type.Tiny.copy(color = Palette.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekEvent(event: Event, past: Boolean) {
    Row(Modifier.fillMaxWidth().alpha(if (past) 0.4f else 1f)) {
        Box(Modifier.width(5.dp).height(64.dp).background(parseColor(event.calendar_color), RoundedCornerShape(3.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(event.at.format(HourMinute), style = Type.Tiny.copy(color = Palette.InkMuted))
            Text(event.title, style = Type.Small.copy(color = Palette.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            PersonInitials(event.persons, Modifier.padding(top = 6.dp), size = 30)
        }
    }
}
