package com.lito.planify.dashboard.ui

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lito.planify.dashboard.R
import com.lito.planify.dashboard.data.Forecast
import kotlinx.coroutines.delay
import java.time.LocalDate

// Iconos del tiempo: Meteocons (estilo "line", Lottie) de Bas Milius, licencia MIT (third_party/meteocons).

/** Icono de Meteocons para un código WMO de Open-Meteo, de día o de noche. */
@RawRes
fun meteocon(code: Int?, day: Boolean = true): Int = when (code) {
    0 -> if (day) R.raw.meteocons_clear_day else R.raw.meteocons_clear_night
    1 -> if (day) R.raw.meteocons_mostly_clear_day else R.raw.meteocons_mostly_clear_night
    2 -> if (day) R.raw.meteocons_partly_cloudy_day else R.raw.meteocons_partly_cloudy_night
    3 -> R.raw.meteocons_overcast
    45, 48 -> if (day) R.raw.meteocons_fog_day else R.raw.meteocons_fog_night
    51, 53, 55 -> R.raw.meteocons_drizzle
    56, 57, 66, 67 -> R.raw.meteocons_sleet
    61, 63, 65 -> R.raw.meteocons_rain
    71, 73, 75, 77 -> R.raw.meteocons_snow
    80, 81, 82 -> if (day) R.raw.meteocons_partly_cloudy_day_rain else R.raw.meteocons_partly_cloudy_night_rain
    85, 86 -> if (day) R.raw.meteocons_partly_cloudy_day_snow else R.raw.meteocons_partly_cloudy_night_snow
    95 -> if (day) R.raw.meteocons_thunderstorms_day else R.raw.meteocons_thunderstorms_night
    96, 99 -> if (day) R.raw.meteocons_thunderstorms_day_hail else R.raw.meteocons_thunderstorms_night_hail
    else -> R.raw.meteocons_overcast
}

/**
 * Fotograma actual de las animaciones de los iconos, compartido por todos: avanza a [FPS] por segundo dentro del bucle
 * de [LOOP_MS] (lo que dura cada animación de Meteocons). Así los iconos van sincronizados y cada fotograma es un único
 * cambio de estado. Solo corre con la actividad visible: con la pantalla apagada no se dibuja nada.
 */
private val LocalIconClock = staticCompositionLocalOf<IntState> { mutableIntStateOf(0) }

@Composable
fun ProvideIconClock(content: @Composable () -> Unit) {
    val frame = remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val step = LOOP_MS / MeteoconFrames.FRAMES
            while (true) {
                val now = System.currentTimeMillis()
                frame.intValue = ((now % LOOP_MS) / step).toInt()
                delay(step - now % step)
            }
        }
    }
    CompositionLocalProvider(LocalIconClock provides frame) { content() }
}

private const val LOOP_MS = 6_000L

/**
 * Un icono de Meteocons, de fotogramas pre-renderizados ([MeteoconFrames]). [animated] lo mueve con el reloj
 * compartido; si no, es su primer fotograma. [colorFilter] cambia sus colores al pintarlo (fondo negro).
 */
@Composable
fun Meteocon(@RawRes icon: Int, size: Int, animated: Boolean = false, description: String? = null, colorFilter: ColorFilter? = null) {
    val context = LocalContext.current
    // Los Meteocons dejan mucho margen en su lienzo de 128: se dibujan más grandes que su hueco, centrados, para que
    // el dibujo ocupe lo mismo que un icono normal de ese tamaño.
    val art = (size * ART_SCALE).dp
    val px = with(LocalDensity.current) { art.roundToPx() }
    val count = if (animated) MeteoconFrames.FRAMES else 1
    val frames by produceState(emptyList<ImageBitmap>(), icon, px, count) {
        value = MeteoconFrames.get(context, icon, px, count)
    }
    val clock = LocalIconClock.current
    // Capa propia: cada fotograma del icono solo vuelve a dibujar el icono, no la tarjeta ni la gráfica de alrededor.
    Box(
        Modifier.size(size.dp).graphicsLayer().semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.requiredSize(art)) {
            if (frames.isNotEmpty()) drawImage(frames[clock.intValue % frames.size], colorFilter = colorFilter)
        }
    }
}

private const val ART_SCALE = 1.6f

/** Icono estático de Meteocons (VectorDrawable generado por scripts/meteocons.py), con la misma escala del dibujo. */
@Composable
fun StaticMeteocon(@DrawableRes icon: Int, size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.requiredSize((size * ART_SCALE).dp))
    }
}

@Composable
fun WeatherIcon(code: Int?, day: Boolean = true, size: Int = 40, animated: Boolean = false, colorFilter: ColorFilter? = null) {
    Meteocon(meteocon(code, day), size, animated, weatherText(code), colorFilter)
}

/**
 * Icono de un día, animado. El código diario de Open-Meteo es el tiempo más significativo de todo el día (si hay
 * niebla por la mañana, el día entero sale con niebla), así que hoy se pinta con el tiempo de ahora, igual que la
 * cabecera.
 */
@Composable
fun DayWeatherIcon(forecast: Forecast, date: LocalDate, today: LocalDate?, size: Int) {
    if (date == today) {
        WeatherIcon(forecast.current.weather_code, forecast.current.is_day == 1, size, animated = true)
    } else {
        val index = forecast.dayIndex(date)
        if (index >= 0) WeatherIcon(forecast.daily.weather_code[index], size = size, animated = true)
    }
}
