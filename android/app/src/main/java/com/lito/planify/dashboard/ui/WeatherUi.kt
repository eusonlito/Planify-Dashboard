package com.lito.planify.dashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.R
import com.lito.planify.dashboard.data.Forecast
import com.lito.planify.dashboard.data.Hourly
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// --- Códigos WMO de Open-Meteo ---

fun weatherText(code: Int?): String = when (code) {
    0 -> "Despejado"
    1 -> "Poco nuboso"
    2 -> "Parcialmente nuboso"
    3 -> "Cubierto"
    45, 48 -> "Niebla"
    51, 53, 55 -> "Llovizna"
    56, 57 -> "Llovizna helada"
    61 -> "Lluvia débil"
    63 -> "Lluvia"
    65 -> "Lluvia fuerte"
    66, 67 -> "Lluvia helada"
    71, 73, 75, 77 -> "Nieve"
    80 -> "Chubascos débiles"
    81 -> "Chubascos"
    82 -> "Chubascos fuertes"
    85, 86 -> "Chubascos de nieve"
    95 -> "Tormenta"
    96, 99 -> "Tormenta con granizo"
    else -> "—"
}

fun Double?.degrees() = if (this == null) "—" else "${roundToInt()}°"

/** Un decimal con coma, sea cual sea el idioma del sistema. */
fun Double.decimal(): String = String.format(Spanish, "%.1f", this)

/** Lluvia de un día: "24 mm", con un decimal por debajo de 10 ("3,5 mm"); null si no llega a 0,1 mm. */
fun Double?.rainTotal(): String? = this?.takeIf { it >= 0.1 }?.let { if (it < 10) "${it.decimal()} mm" else "${it.roundToInt()} mm" }

private val IsoMinute: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun Forecast.hourIndex(at: LocalDateTime): Int =
    hourly.time.indexOfFirst { LocalDateTime.parse(it, IsoMinute) >= at.withMinute(0).withSecond(0).withNano(0) }.coerceAtLeast(0)

fun Forecast.dayIndex(date: LocalDate): Int = daily.time.indexOf(date.toString())

/** Si hay previsión por horas para [date] (para poder abrir su detalle). */
fun Forecast.hasHours(date: LocalDate): Boolean = "${date}T".let { prefix -> hourly.time.count { it.startsWith(prefix) } >= 2 }

fun Forecast.sunset(date: LocalDate): String? = dayIndex(date).takeIf { it >= 0 }?.let { daily.sunset[it].substringAfter('T') }
fun Forecast.sunrise(date: LocalDate): String? = dayIndex(date).takeIf { it >= 0 }?.let { daily.sunrise[it].substringAfter('T') }

/**
 * Añade una curva suave desde el punto actual del trazo (que debe ser el primero) por el resto de puntos.
 * Es una spline cúbica monótona (Fritsch–Carlson): la pendiente en cada punto se reparte con sus vecinos,
 * así que la línea fluye sin escalones, y nunca sube ni baja más allá de los valores reales. En los extremos
 * la pendiente es cero para enlazar con los tramos horizontales hasta los bordes.
 */
fun Path.smoothThrough(xs: List<Float>, ys: List<Float>) {
    val n = xs.size
    if (n < 2) return
    val slopes = (0 until n - 1).map { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val tangents = FloatArray(n)
    for (k in 1 until n - 1) {
        tangents[k] = if (slopes[k - 1] * slopes[k] <= 0f) 0f else (slopes[k - 1] + slopes[k]) / 2
    }
    for (k in 0 until n - 1) {
        if (slopes[k] == 0f) {
            tangents[k] = 0f
            tangents[k + 1] = 0f
            continue
        }
        val a = tangents[k] / slopes[k]
        val b = tangents[k + 1] / slopes[k]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            tangents[k] = t * a * slopes[k]
            tangents[k + 1] = t * b * slopes[k]
        }
    }
    for (k in 0 until n - 1) {
        val third = (xs[k + 1] - xs[k]) / 3
        cubicTo(
            xs[k] + third, ys[k] + tangents[k] * third,
            xs[k + 1] - third, ys[k + 1] - tangents[k + 1] * third,
            xs[k + 1], ys[k + 1]
        )
    }
}

/** Sin [day] (si la previsión no trae las horas de hoy): [HOURS_BEFORE] antes de la actual y las siguientes, hasta 24. */
const val HOURS_BEFORE = 4
const val HOURS_SHOWN = 24

/** Lluvia que llena la barra entera: 15 mm en una hora es el paso de lluvia moderada a fuerte. */
private const val RAIN_FULL_MM = 15.0

/** Lo mismo en un día entero: 30 mm ya es un día de mucha lluvia. */
private const val RAIN_DAY_FULL_MM = 30.0

/**
 * Barra de lluvia sobre [baseline], centrada en [x], solo si llueve algo (0,1 mm). El alto es la cantidad, en raíz
 * cuadrada para que se vea la floja y no se dispare la fuerte, llena a [full] mm (a partir de ahí, un azul más
 * intenso). El relleno es la probabilidad, en tres niveles que se leen sin número: maciza desde el 70 % (segura), a
 * rayas del 40 al 69 % (probable) y solo el contorno por debajo (posible). Encima, [amount].
 */
private fun DrawScope.drawRain(
    measurer: TextMeasurer, x: Float, baseline: Float, maxHeight: Float, mm: Double, chance: Int, full: Double,
    width: Float, amount: String, amountStyle: TextStyle
) {
    if (mm < 0.1) return
    val height = maxHeight * kotlin.math.sqrt(minOf(mm, full) / full).toFloat()
    val color = if (mm >= full) RainHeavy else Palette.Rain
    val topLeft = Offset(x - width / 2, baseline - height)
    // Esquinas redondeadas solo arriba: la barra sale recta de la línea base
    val corner = CornerRadius(width * 0.3f)
    val shape = Path().apply {
        addRoundRect(RoundRect(Rect(topLeft, Size(width, height)), topLeft = corner, topRight = corner, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero))
    }
    when {
        chance >= 70 -> drawPath(shape, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.6f)), startY = topLeft.y, endY = baseline))
        chance >= 40 -> {
            clipPath(shape) {
                drawRect(color.copy(alpha = 0.08f), topLeft, Size(width, height))
                // Rayas a 45°, de abajo a la izquierda hacia arriba a la derecha
                var start = topLeft.x - height
                while (start < topLeft.x + width) {
                    drawLine(color.copy(alpha = 0.45f), Offset(start, baseline), Offset(start + height, topLeft.y), strokeWidth = 3f)
                    start += 9f
                }
            }
            drawPath(shape, color.copy(alpha = 0.45f), style = Stroke(width = 2f))
        }
        else -> {
            // Posible: solo un contorno suave
            drawPath(shape, color.copy(alpha = 0.06f))
            drawPath(shape, color.copy(alpha = 0.35f), style = Stroke(width = 2f))
        }
    }
    val label = measurer.measure(amount, amountStyle)
    drawText(label, topLeft = Offset(x - label.size.width / 2, baseline - height - 6 - label.size.height))
}

private val RainHeavy = Color(0xFF1D4ED8)

/**
 * Lluvia de la hora que empieza en la columna [i]: Open-Meteo da la de la hora anterior (la de las 06 es de 05 a 06),
 * así que se toma la de la hora siguiente, para que la columna de las 05 diga lo que llueve de 05 a 06.
 */
private fun Hourly.rainAt(i: Int): Pair<Double, Int> =
    (precipitation.getOrNull(i + 1) ?: 0.0) to (precipitation_probability.getOrNull(i + 1) ?: 0)

/**
 * Previsión hora a hora: las [HOURS_BEFORE] anteriores, la actual (marcada con una línea) y las siguientes hasta
 * [HOURS_SHOWN]: curva de temperatura con relleno y su valor; barras de lluvia con sus mm, solo donde llueve (alto
 * por cantidad y relleno por probabilidad, ver [drawRain]). Debajo, el icono del tiempo de cada hora
 * ([iconSize], estático) y, abajo del todo, la hora con dos dígitos.
 * Con [day], que es lo normal (las tarjetas pasan hoy o el día tocado), las 24 horas de ese día, de 00 a 23.
 */
@Composable
fun HourlyForecast(forecast: Forecast, now: LocalDateTime, modifier: Modifier = Modifier, iconSize: Int = 36, day: LocalDate? = null, compact: Boolean = false) {
    val step = 1
    val prefix = day?.let { "${it}T" }
    // La previsión incluye el día anterior (past_days=1), así que de madrugada también hay horas previas.
    val first = if (prefix == null) (forecast.hourIndex(now) - HOURS_BEFORE).coerceAtLeast(0) else forecast.hourly.time.indexOfFirst { it.startsWith(prefix) }
    if (first < 0) return
    val points = (0 until HOURS_SHOWN).map { first + it }
        .filter { it < forecast.hourly.time.size && (prefix == null || forecast.hourly.time[it].startsWith(prefix)) }
    if (points.size < 2) return
    val hourly = forecast.hourly
    val temps = points.map { hourly.temperature_2m[it] ?: 0.0 }
    val rain = points.map { hourly.rainAt(it) }
    val measurer = rememberTextMeasurer()
    val tempStyle = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.Medium)
    // Con columnas estrechas (Hoy), la temperatura de todas las horas, más pequeña y sin "°", para que se vea el pico
    val narrowTempStyle = tempStyle.copy(fontSize = 17.sp)
    // Los mm, pequeños para no saturar la gráfica; en compacto (Hoy), aún más
    val mmStyle = Type.Tiny.copy(color = Palette.Rain, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 14.sp else 16.sp)
    val hourStyle = Type.Mono.copy(fontSize = Type.Mono.fontSize * 0.85f)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val column = size.width / points.size
            fun x(i: Int) = column * i + column / 2
            val baseline = size.height - 2f
            val curveTop = 52f
            // Con poco alto, la lluvia cede espacio para que la curva no se monte sobre las barras.
            val rainHeight = ((baseline - curveTop - 40f) * 0.42f).coerceIn(0f, 130f)
            val curveBottom = baseline - rainHeight - 40f
            val minT = temps.min()
            val maxT = maxOf(temps.max(), minT + 1)
            fun y(t: Double) = curveBottom - (curveBottom - curveTop) * ((t - minT) / (maxT - minT)).toFloat()

            // Hora actual: una banda azul translúcida sobre la columna de la hora en punto (13:40 → las 13), detrás de la curva
            val current = Math.floorDiv(Duration.between(LocalDateTime.parse(hourly.time[points.first()], IsoMinute), now).toMinutes(), 60L * step).toInt()
            if (current in points.indices) {
                val nowX = x(current)
                val band = (column * 0.6f).coerceAtMost(36f)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Palette.Rain.copy(alpha = 0.18f), Palette.Rain.copy(alpha = 0.06f)), startY = 0f, endY = baseline),
                    topLeft = Offset(nowX - band / 2, 0f),
                    size = Size(band, baseline),
                    cornerRadius = CornerRadius(6f)
                )
            }

            // Curva suave de borde a borde, con relleno degradado hasta la base
            val line = Path().apply {
                moveTo(0f, y(temps.first()))
                lineTo(x(0), y(temps.first()))
                smoothThrough(temps.indices.map(::x), temps.map(::y))
                lineTo(size.width, y(temps.last()))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, baseline)
                lineTo(0f, baseline)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(Palette.Warm.copy(alpha = 0.30f), Palette.Warm.copy(alpha = 0.03f)), startY = curveTop, endY = baseline))
            drawPath(line, Palette.Warm, style = Stroke(width = 5f, cap = StrokeCap.Round))

            points.indices.forEach { i ->
                val center = Offset(x(i), y(temps[i]))
                drawCircle(Color.White, 10f, center)
                drawCircle(Palette.Warm, 7f, center)
                val narrow = column < 60f
                val label = measurer.measure(if (narrow) "${temps[i].roundToInt()}" else "${temps[i].roundToInt()}°", if (narrow) narrowTempStyle else tempStyle)
                drawText(label, topLeft = Offset(center.x - label.size.width / 2, center.y - label.size.height - 16))

                val (mm, chance) = rain[i]
                drawRain(measurer, center.x, baseline, rainHeight, mm, chance, RAIN_FULL_MM, 26f, if (column >= 90f) "${mm.decimal()} mm" else mm.decimal(), mmStyle)
            }
            drawLine(Palette.Line, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
        }
        // Debajo de la gráfica, el tiempo de cada hora (estático) y, abajo del todo, la hora
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            points.forEach { i ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    WeatherIcon(hourly.weather_code[i], hourly.is_day[i] == 1, iconSize)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEach { i ->
                Text(hourly.time[i].substring(11, 13), style = hourStyle, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Tarjeta del tiempo: icono, temperatura y estado; lluvia, humedad y viento; la previsión hora a hora
 * ([HourlyForecast]) y los próximos [days] días en columnas. [compact] reduce la cabecera y los
 * días para dejar sitio a la gráfica cuando la tarjeta es baja (Hoy). La gráfica enseña el día de hoy entero.
 * Al tocar otro día, la cabecera y la gráfica pasan a ese día por horas; tocarlo de nuevo, o hoy, vuelve a
 * ahora, y también vuelve solo a los 2 minutos.
 */
@Composable
fun WeatherCard(
    forecast: Forecast,
    location: String,
    now: LocalDateTime,
    days: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val today = now.toLocalDate()
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(120_000)
            picked = null
        }
    }
    val selected = picked?.takeIf { it > today && forecast.dayIndex(it) >= 0 && forecast.hasHours(it) }
    val onDay: (LocalDate) -> Unit = { date -> picked = if (date == today || date == selected) null else date }
    Column(
        modifier
            .background(Palette.Card, RoundedCornerShape(32.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp)
    ) {
        WeatherHeader(forecast, location, today, selected, compact)
        // La gráfica es siempre un día entero (de 00 a 23): hoy, con la banda de la hora actual avanzando por ella
        val hours = selected ?: today.takeIf { forecast.hasHours(it) }
        HourlyForecast(forecast, now, Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), iconSize = if (compact) 28 else 36, day = hours, compact = compact)
        // La gráfica del día y los días quedan separados por una línea
        Hairline(Modifier.padding(top = if (compact) 12.dp else 18.dp, bottom = if (compact) 12.dp else 18.dp))
        if (compact) {
            DaysPager(forecast, today, days, selected ?: today, onDay)
        } else {
            DaysRow(forecast, (0 until days).map { today.plusDays(it.toLong()) }, highlight = selected ?: today, today = today, modifier = Modifier.fillMaxWidth(), showDate = true, onClick = onDay)
        }
    }
}

/**
 * Cabecera del tiempo: icono grande, temperatura y estado, ubicación con máxima y mínima, y lluvia, humedad y viento.
 * Sin [selected] es el tiempo de ahora (con la máxima y la mínima de hoy); con un día, su previsión.
 */
@Composable
fun WeatherHeader(forecast: Forecast, location: String, today: LocalDate, selected: LocalDate?, compact: Boolean = false) {
    val current = forecast.current
    val day = forecast.dayIndex(selected ?: today)
    val daily = forecast.daily
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selected != null) WeatherIcon(daily.weather_code[day], size = if (compact) 64 else 110, animated = true)
        else WeatherIcon(current.weather_code, current.is_day == 1, if (compact) 64 else 110, animated = true)
        Text(
            if (selected != null) daily.temperature_2m_max[day].degrees() else current.temperature_2m.degrees(),
            style = if (compact) Type.Huge.copy(fontSize = 80.sp, lineHeight = 80.sp, letterSpacing = (-3).sp) else Type.Huge,
            modifier = Modifier.padding(start = if (compact) 18.dp else 24.dp)
        )
        Column(Modifier.padding(start = if (compact) 22.dp else 28.dp).weight(1f)) {
            Text(
                if (selected != null) "${selected.weekdayName()} ${selected.dayOfMonth} · ${weatherText(daily.weather_code[day])}" else weatherText(current.weather_code),
                style = if (compact) Type.Body else Type.Large,
                maxLines = 1
            )
            Text(
                listOfNotNull(location, day.takeIf { it >= 0 }?.let { "Máx ${daily.temperature_2m_max[it].degrees()} · Mín ${daily.temperature_2m_min[it].degrees()}" }).joinToString("  ·  "),
                style = Type.Small,
                maxLines = 1
            )
        }
        // Lluvia del día (probabilidad y total), humedad y viento: icono y valor, sin etiquetas. De otro día: lluvia y
        // viento máximo.
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            if (day >= 0) {
                // Primero los mm y después el %, como en los días; sin lluvia apreciable, solo el %
                val chance = "${daily.precipitation_probability_max[day] ?: 0}"
                val total = daily.precipitation_sum[day].rainTotal()?.removeSuffix(" mm")
                if (total != null) Stat(R.drawable.meteocons_rain, total, "mm", compact, extra = chance to "%")
                else Stat(R.drawable.meteocons_rain, chance, "%", compact)
            }
            if (selected != null) {
                Stat(R.drawable.meteocons_wind, "${daily.wind_speed_10m_max[day]?.roundToInt() ?: 0}", "km/h", compact)
            } else {
                current.relative_humidity_2m?.let { Stat(R.drawable.meteocons_raindrop, "$it", "%", compact) }
                Stat(R.drawable.meteocons_wind, "${current.wind_speed_10m.roundToInt()}", "km/h", compact)
            }
        }
    }
}

/**
 * Días en páginas de [perPage] que se deslizan para ver las semanas siguientes (hasta donde llegue la previsión).
 * Si se deja en otra página, vuelve a la primera a los 2 minutos.
 */
@Composable
private fun DaysPager(forecast: Forecast, today: LocalDate, perPage: Int, highlight: LocalDate, onClick: (LocalDate) -> Unit) {
    val available = forecast.daily.time.count { LocalDate.parse(it) >= today }
    val pages = ((available + perPage - 1) / perPage).coerceAtLeast(1)
    val pager = rememberPagerState { pages }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != 0) {
            delay(120_000)
            pager.animateScrollToPage(0)
        }
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 24.dp) { page ->
        val dates = (0 until perPage).map { today.plusDays((page * perPage + it).toLong()) }
        DaysRow(forecast, dates, highlight = highlight, today = today, modifier = Modifier.fillMaxWidth(), showDate = true, compact = true, keepSlots = true, onClick = onClick)
    }
}

/**
 * Una columna por día: nombre, icono, máx/mín y lluvia (probabilidad y, si no es [compact], total). [highlight] resalta un día (hoy o el elegido).
 * [keepSlots] reserva el hueco de los días sin previsión para que las columnas no cambien de ancho.
 * Con [onClick], se pueden tocar los días que tienen previsión por horas.
 */
@Composable
fun DaysRow(
    forecast: Forecast,
    dates: List<LocalDate>,
    highlight: LocalDate?,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    showDate: Boolean = false,
    compact: Boolean = false,
    keepSlots: Boolean = false,
    onClick: ((LocalDate) -> Unit)? = null
) {
    val text = if (compact) Type.Small.copy(color = Palette.Ink) else Type.Body
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        dates.forEach { date ->
            val index = forecast.dayIndex(date)
            if (index < 0) {
                if (keepSlots) Spacer(Modifier.weight(1f))
                return@forEach
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (date == highlight) Palette.Highlight else Palette.Card)
                    .then(if (onClick != null && forecast.hasHours(date)) Modifier.clickable { onClick(date) } else Modifier)
                    .padding(vertical = if (compact) 6.dp else 10.dp)
            ) {
                val name = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Spanish).trimEnd('.')
                // Lluvia: primero los mm y después el %; con un 0 %, nada
                val rain = (forecast.daily.precipitation_probability_max[index] ?: 0).takeIf { it > 0 }?.let { "$it%" }
                val total = forecast.daily.precipitation_sum[index].rainTotal()
                val rainText = listOfNotNull(total, rain).joinToString(" · ")
                Text(if (showDate) "$name ${date.dayOfMonth}" else name, style = text)
                DayWeatherIcon(forecast, date, today, size = if (compact) 36 else 44)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(forecast.daily.temperature_2m_max[index].degrees(), style = text.copy(fontWeight = FontWeight.SemiBold))
                    Text(forecast.daily.temperature_2m_min[index].degrees(), style = text.copy(color = Palette.InkMuted), modifier = Modifier.padding(start = if (compact) 6.dp else 8.dp))
                    // En compacto la lluvia va en la misma línea
                    // (solo el %, sin icono: la fila de Hoy ya va justa)
                    if (compact && rain != null) Text(rain, style = Type.Tiny.copy(color = Palette.Rain, fontSize = 16.sp), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
                }
                // Sin lluvia, la línea queda invisible para que todas las columnas midan lo mismo
                if (!compact) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (rainText.isEmpty()) 0f else 1f)) {
                    StaticMeteocon(R.drawable.meteocons_rain, 24)
                    Text(rainText.ifEmpty { "0%" }, style = Type.Tiny.copy(color = Palette.Rain, fontWeight = FontWeight.Normal), maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/**
 * Previsión de varios días como gráfica: curvas de máximas (ámbar) y mínimas (azul grisáceo) con sus
 * valores, y barras de lluvia con sus mm (ver [drawRain]). Debajo, el viento máximo de cada día.
 */
@Composable
fun DailyForecast(forecast: Forecast, dates: List<LocalDate>, modifier: Modifier = Modifier) {
    val daily = forecast.daily
    val indexes = dates.map { forecast.dayIndex(it) }.filter { it >= 0 }
    if (indexes.size < 2) return
    val highs = indexes.map { daily.temperature_2m_max[it] ?: 0.0 }
    val lows = indexes.map { daily.temperature_2m_min[it] ?: 0.0 }
    val rain = indexes.map { daily.precipitation_probability_max[it] ?: 0 }
    val mm = indexes.map { daily.precipitation_sum[it] ?: 0.0 }
    val wind = indexes.map { daily.wind_speed_10m_max[it]?.roundToInt() ?: 0 }
    val measurer = rememberTextMeasurer()
    val highStyle = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.SemiBold)
    val lowStyle = Type.Small.copy(color = Palette.Night)
    val mmStyle = Type.Tiny.copy(color = Palette.Rain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val windStyle = Type.Tiny.copy(color = Palette.InkMuted)

    Canvas(modifier) {
        val column = size.width / indexes.size
        fun x(i: Int) = column * i + column / 2
        val windHeight = 34f
        val baseline = size.height - windHeight - 8f
        val rainHeight = (baseline * 0.32f).coerceAtMost(150f)
        val curveTop = 50f
        val curveBottom = baseline - rainHeight - 60f
        val minT = lows.min()
        val maxT = maxOf(highs.max(), minT + 1)
        fun y(t: Double) = curveBottom - (curveBottom - curveTop) * ((t - minT) / (maxT - minT)).toFloat()

        fun curve(values: List<Double>) = Path().apply {
            moveTo(0f, y(values.first()))
            lineTo(x(0), y(values.first()))
            smoothThrough(values.indices.map(::x), values.map(::y))
            lineTo(size.width, y(values.last()))
        }
        // Banda entre mínimas y máximas
        val high = curve(highs)
        val band = Path().apply {
            addPath(high)
            lineTo(size.width, y(lows.last()))
            lineTo(x(lows.lastIndex), y(lows.last()))
            smoothThrough(lows.indices.reversed().map(::x), lows.reversed().map(::y))
            lineTo(0f, y(lows.first()))
            close()
        }
        drawPath(band, Brush.verticalGradient(listOf(Palette.Warm.copy(alpha = 0.22f), Palette.Night.copy(alpha = 0.10f)), startY = curveTop, endY = curveBottom))
        drawPath(high, Palette.Warm, style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawPath(curve(lows), Palette.Night, style = Stroke(width = 4f, cap = StrokeCap.Round))

        indexes.indices.forEach { i ->
            val top = Offset(x(i), y(highs[i]))
            drawCircle(Color.White, 10f, top)
            drawCircle(Palette.Warm, 7f, top)
            val highLabel = measurer.measure("${highs[i].roundToInt()}°", highStyle)
            drawText(highLabel, topLeft = Offset(top.x - highLabel.size.width / 2, top.y - highLabel.size.height - 12))

            val bottom = Offset(x(i), y(lows[i]))
            drawCircle(Color.White, 9f, bottom)
            drawCircle(Palette.Night, 6f, bottom)
            val lowLabel = measurer.measure("${lows[i].roundToInt()}°", lowStyle)
            drawText(lowLabel, topLeft = Offset(bottom.x - lowLabel.size.width / 2, bottom.y + 12))

            drawRain(measurer, x(i), baseline, rainHeight, mm[i], rain[i], RAIN_DAY_FULL_MM, 36f, "${mm[i].decimal()} mm", mmStyle)

            val windLabel = measurer.measure("${wind[i]} km/h", windStyle)
            drawText(windLabel, topLeft = Offset(x(i) - windLabel.size.width / 2, size.height - windLabel.size.height))
        }
        drawLine(Palette.Line, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
    }
}

/**
 * Los 7 días que empiezan en [from]: gráfica diaria y columnas por día. Para deslizar a la semana siguiente.
 * Al tocar un día, la cabecera pasa a ser la de ese día (como en [WeatherCard]) y la gráfica sus 24 horas; tocarlo de
 * nuevo vuelve a la semana (y sola a los 2 minutos).
 */
@Composable
fun WeekWeatherCard(forecast: Forecast, location: String, title: String, from: LocalDate, now: LocalDateTime, modifier: Modifier = Modifier) {
    val dates = (0 until 7).map { from.plusDays(it.toLong()) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(120_000)
            picked = null
        }
    }
    val selected = picked?.takeIf { it in dates && forecast.hasHours(it) }
    Column(
        modifier
            .background(Palette.Card, RoundedCornerShape(32.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp)
    ) {
        if (selected != null) {
            WeatherHeader(forecast, location, now.toLocalDate(), selected)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
                Text(weekRange(from), style = Type.Large.copy(color = Palette.InkSoft), modifier = Modifier.padding(start = 24.dp, bottom = 2.dp))
            }
        }
        if (dates.none { forecast.dayIndex(it) >= 0 }) {
            Text("Cargando previsión…", style = Type.Body.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        if (selected != null) {
            HourlyForecast(forecast, now, Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), day = selected)
        } else {
            DailyForecast(forecast, dates, Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp))
        }
        DaysRow(
            forecast, dates, highlight = selected, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), showDate = true,
            onClick = { date -> picked = if (date == selected) null else date }
        )
    }
}

@Composable
/**
 * Icono y valor de la cabecera; la unidad, más pequeña, para que el bloque no se ensanche ("km/h"). [extra] es un
 * segundo valor con su unidad detrás ("29 mm · 85%"), con los mismos tamaños.
 */
private fun Stat(@DrawableRes icon: Int, value: String, unit: String, compact: Boolean, extra: Pair<String, String>? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Palette.Highlight, RoundedCornerShape(18.dp))
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 8.dp else 10.dp)
    ) {
        StaticMeteocon(icon, if (compact) 30 else 40)
        val style = (if (compact) Type.Tiny else Type.Small).copy(color = Palette.Ink, fontWeight = FontWeight.SemiBold)
        Text(
            buildAnnotatedString {
                fun valueWithUnit(value: String, unit: String) {
                    append(value)
                    withStyle(SpanStyle(fontSize = style.fontSize * UNIT_SCALE, fontWeight = FontWeight.Medium, color = Palette.InkSoft)) {
                        append(if (unit == "%") unit else " $unit")
                    }
                }
                valueWithUnit(value, unit)
                if (extra != null) {
                    append(" · ")
                    valueWithUnit(extra.first, extra.second)
                }
            },
            style = style,
            maxLines = 1
        )
    }
}

private const val UNIT_SCALE = 0.6f
