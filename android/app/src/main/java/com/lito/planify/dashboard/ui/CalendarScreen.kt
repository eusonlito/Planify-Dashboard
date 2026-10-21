package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.R
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

/** Separación entre días y entre semanas. */
private val GAP = 14.dp

/**
 * Calendario en dos filas, la semana actual y la siguiente, de lunes a domingo. Se desliza en vertical de semana en
 * semana. Cada semana se descarga al mostrarla (y las vecinas, para que el deslizamiento no espere); mientras tanto
 * se usa lo que haya del contenido de Hoy.
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
        listOf(monday, monday.plusWeeks(1), monday.plusWeeks(2), monday.minusWeeks(1)).forEach(onLoadWeek)
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        // Dos semanas en pantalla: cada página mide la mitad del alto
        val row = (maxHeight - GAP) / 2
        VerticalPager(pager, Modifier.fillMaxSize(), pageSize = PageSize.Fixed(row), pageSpacing = GAP, beyondViewportPageCount = 1) { page ->
            val monday = mondayOf(page)
            val (events, sessions) = weekContent(state, monday)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                (0 until 7).forEach { offset ->
                    val date = monday.plusDays(offset.toLong())
                    DayCard(
                        date = date,
                        state = state,
                        events = events.filter { it.at.toLocalDate() == date },
                        sessions = sessions.filter { it.at.toLocalDate() == date },
                        modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (date < today) 0.6f else 1f)
                    )
                }
            }
        }
        if (pager.currentPage != CURRENT) {
            PillButton("Volver a hoy", Modifier.align(Alignment.BottomEnd).padding(24.dp), primary = true) {
                scope.launch { pager.animateScrollToPage(CURRENT) }
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
private fun DayCard(date: LocalDate, state: UiState, events: List<Event>, sessions: List<Event>, modifier: Modifier) {
    val isToday = date == state.now.toLocalDate()
    val forecast = state.forecast
    val day = forecast?.dayIndex(date) ?: -1

    Column(
        modifier
            .background(if (isToday) Palette.Card else Palette.Card.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
    ) {
        // Día de la semana (o "Hoy") a la izquierda y el número a la derecha; el día 1, también el mes
        Row(verticalAlignment = Alignment.CenterVertically) {
            val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.').replaceFirstChar { it.uppercaseChar() }
            val month = if (date.dayOfMonth == 1) " · " + date.month.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.') else ""
            Text(
                if (isToday) "Hoy" else weekday + month,
                style = Type.Body.copy(color = if (isToday) Palette.Accent else Palette.InkSoft, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text("${date.dayOfMonth}", style = Type.Large.copy(color = if (isToday) Palette.Accent else Palette.Ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
        // El tiempo en una línea, de alto fijo haya previsión o no: icono, máxima y mínima, y la lluvia a la derecha
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
            if (forecast != null && day >= 0) {
                DayWeatherIcon(forecast, date, today = date.takeIf { isToday }, size = 40)
                Text(
                    forecast.daily.temperature_2m_max[day].degrees(),
                    style = Type.Small.copy(color = Palette.Ink),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Text(
                    " " + forecast.daily.temperature_2m_min[day].degrees(),
                    style = Type.Small.copy(color = Palette.InkMuted),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                StaticMeteocon(R.drawable.meteocons_raindrop, 26)
                Text("${forecast.daily.precipitation_probability_max[day] ?: 0}%", style = Type.Tiny.copy(color = Palette.Rain), maxLines = 1)
            } else {
                Text("—", style = Type.Small.copy(color = Palette.InkMuted))
            }
        }
        Box(Modifier.padding(top = 8.dp, bottom = 12.dp)) { Hairline() }

        Column(Modifier.weight(1f).fadingScroll(rememberScrollState(), edge = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            events.forEach { WeekEvent(it, past = it.at.isBefore(state.now)) }
        }

        // Las rutinas del día, en una caja abajo: hora, color de cada persona y nombre, que sigue en la línea de debajo
        if (sessions.isNotEmpty()) {
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(Palette.Highlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sessions.forEach { session ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Palette.InkMuted)) { append(session.at.format(HourMinute)) }
                            session.persons.orEmpty().forEach { withStyle(SpanStyle(color = parseColor(it.color))) { append(" ●") } }
                            append(" ")
                            append(session.title)
                        },
                        style = Type.Tiny.copy(color = Palette.Ink),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(if (session.at.isBefore(state.now)) 0.4f else 1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekEvent(event: Event, past: Boolean) {
    Row(Modifier.fillMaxWidth().alpha(if (past) 0.4f else 1f)) {
        Box(Modifier.width(5.dp).height(56.dp).background(parseColor(event.calendar_color), RoundedCornerShape(3.dp)))
        Column(Modifier.padding(start = 10.dp)) {
            Text(event.at.format(HourMinute), style = Type.Tiny.copy(color = Palette.InkMuted))
            Text(event.title, style = Type.Small.copy(color = Palette.Ink), maxLines = 3, overflow = TextOverflow.Ellipsis)
            PersonInitials(event.persons, Modifier.padding(top = 4.dp), size = 26)
        }
    }
}
