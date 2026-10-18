package com.lito.planify.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * El tiempo a pantalla completa, en dos páginas que se deslizan: esta semana (24 horas, de 4 antes de la
 * actual a 19 después, con iconos, y 7 días) y la siguiente (gráfica diaria de los días 8 a 14).
 */
@Composable
fun WeatherScreen(state: UiState) {
    val forecast = state.forecast
    val location = state.location
    ScreenPadding {
        if (forecast == null || location == null) {
            Text(if (location == null) "Elige una ubicación en Ajustes" else "Cargando previsión…", style = Type.Large.copy(color = Palette.InkMuted))
            return@ScreenPadding
        }
        val pager = rememberPagerState { 2 }
        val scope = rememberCoroutineScope()
        val today = state.now.toLocalDate()
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), pageSpacing = 40.dp) { page ->
                if (page == 0) {
                    // Al pasar del último día de la gráfica sigue la semana siguiente, y antes de la semana vuelve al día
                    WeatherCard(forecast, location.name, state.now, days = 7, modifier = Modifier.fillMaxSize()) { edge ->
                        if (edge > 0) scope.launch { pager.animateScrollToPage(1) }
                    }
                } else {
                    WeekWeatherCard(forecast, location.name, "Próxima semana", today.plusDays(7), state.now, Modifier.fillMaxSize()) { edge ->
                        if (edge < 0) scope.launch { pager.animateScrollToPage(0) }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                repeat(2) { Dot(if (pager.currentPage == it) Palette.Ink else Palette.Line, 14) }
            }
        }
    }
}
