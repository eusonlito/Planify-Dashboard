package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.GeistMono
import com.lito.planify.dashboard.ui.theme.Type
import java.time.format.TextStyle

/**
 * Reloj nocturno: fuera de horario, en vez de apagar la pantalla, la hora, la fecha y el tiempo de ahora en el centro,
 * en blanco sobre negro (con el brillo al mínimo hace falta todo el contraste) y la hora en negrita. El brillo lo pone
 * MainActivity. Todo estático: solo cambia una vez por minuto.
 * Tocar en cualquier sitio enseña el dashboard unos minutos.
 */
@Composable
fun NightScreen(state: UiState, onWake: () -> Unit) {
    val now = state.now
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onWake() } }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                now.format(HourMinute),
                style = Type.Clock.copy(fontSize = 300.sp, lineHeight = 300.sp, letterSpacing = (-10).sp, fontWeight = FontWeight.Bold, color = Color.White, fontFeatureSettings = "tnum")
            )
            Text(
                "${now.toLocalDate().weekdayName()} · ${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, Spanish)}".uppercase(Spanish),
                style = Type.Mono.copy(fontFamily = GeistMono, fontSize = 30.sp, letterSpacing = 8.sp, color = Color.White),
                maxLines = 1,
                modifier = Modifier.padding(top = 20.dp)
            )
            state.forecast?.current?.let { current ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(44.dp),
                    modifier = Modifier.padding(top = 56.dp)
                ) {
                    WeatherIcon(current.weather_code, current.is_day == 1, size = 80)
                    Text(
                        current.temperature_2m.degrees(),
                        style = Type.Huge.copy(fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Light, color = Color.White)
                    )
                }
            }
        }
    }
}
