package com.lito.planify.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import kotlinx.coroutines.delay

enum class Page(val label: String, val icon: ImageVector) {
    Today("Hoy", Icons.Outlined.Home),
    Calendar("Calendario", Icons.Outlined.CalendarMonth),
    Weather("Tiempo", Icons.Outlined.Thermostat),
    Tasks("Tareas", Icons.Outlined.Checklist),
    Settings("Ajustes", Icons.Outlined.Settings)
}

/** Acciones que dependen de la actividad (pantalla, quiosco, ajustes de Android). */
class DashboardActions(
    val sleep: () -> Unit,
    val openWifi: () -> Unit,
    val openSettings: () -> Unit,
    val powerMenu: () -> Unit,
)

private const val IDLE_MILLIS = 120_000L

/** Solape entre las líneas del reloj lateral: deja la distancia entre las cifras en el interlineado (52). */
private val RAIL_CLOCK_OVERLAP = (-30).dp

@Composable
fun DashboardApp(
    state: UiState,
    actions: DashboardActions,
    onLogin: (String, String) -> Unit,
    onLoadWeek: (LocalDate) -> Unit,
    onSync: () -> Unit,
    settings: SettingsCallbacks,
    onPeek: () -> Unit
) {
    var page by remember { mutableStateOf(Page.Today) }
    var lastTouch by remember { mutableLongStateOf(0L) }

    // Tras 2 minutos sin tocar se vuelve a Hoy.
    LaunchedEffect(page, lastTouch) {
        if (page == Page.Today) return@LaunchedEffect
        delay(IDLE_MILLIS)
        page = Page.Today
    }
    // Es el launcher: "atrás" vuelve a Hoy y nunca cierra la actividad.
    BackHandler { page = Page.Today }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            }
    ) {
        if (state.token == null) {
            LoginScreen(state, onLogin, actions.openWifi)
            return@Box
        }
        if (state.night) {
            NightScreen(state, onWake = {
                page = Page.Today
                onPeek()
            })
            return@Box
        }
        Row(Modifier.fillMaxSize()) {
            Rail(page, state, onSelect = { page = it }, onSync = onSync, onSleep = actions.sleep)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (page) {
                    Page.Today -> TodayScreen(state, lastTouch)
                    Page.Calendar -> CalendarScreen(state, onLoadWeek)
                    Page.Weather -> WeatherScreen(state)
                    Page.Tasks -> TasksScreen(state)
                    Page.Settings -> SettingsScreen(state, actions, settings)
                }
            }
        }
    }
}

@Composable
private fun Rail(current: Page, state: UiState, onSelect: (Page) -> Unit, onSync: () -> Unit, onSleep: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(136.dp)
            .fillMaxHeight()
            .background(Palette.Rail)
            .padding(vertical = 48.dp)
    ) {
        // Todos los botones juntos arriba y el reloj abajo del todo
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Page.entries.filter { it != Page.Settings }.forEach { page -> RailItem(page.label, page.icon, page == current) { onSelect(page) } }
            SyncItem(state.syncing, onSync)
            RailItem(Page.Settings.label, Page.Settings.icon, current == Page.Settings) { onSelect(Page.Settings) }
            RailItem("Apagar pantalla", Icons.Outlined.Bedtime, false, onClick = onSleep)
        }
        Spacer(Modifier.weight(1f))
        RailClock(state)
    }
}

/**
 * Sincronizar ahora. Gira mientras dura; el giro va con el reloj de fotogramas porque el marco tiene las animaciones
 * del sistema a 0 y Compose las respeta.
 */
@Composable
private fun SyncItem(syncing: Boolean, onSync: () -> Unit) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(syncing) {
        angle = 0f
        if (!syncing) return@LaunchedEffect
        val start = withFrameMillis { it }
        // Hacia la izquierda, como indica la flecha del icono
        while (true) withFrameMillis { angle = -((it - start) % 1000L) * 0.36f }
    }
    RailItem("Sincronizar", Icons.Outlined.Sync, syncing, rotation = angle) { onSync() }
}

/** Botón de la barra lateral: solo el icono (el nombre queda como descripción de accesibilidad). */
@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, rotation: Float = 0f, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(104.dp)
            .background(if (selected) Palette.Card else Palette.Rail, RoundedCornerShape(30.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Palette.Ink else Palette.InkMuted, modifier = Modifier.size(52.dp).rotate(rotation))
    }
}

/** La hora, con horas y minutos en dos líneas. Debajo, solo si falla la sincronización, un aviso. */
@Composable
private fun RailClock(state: UiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Horas y minutos en dos líneas con las mismas métricas, cifras tabulares e interlineado ajustado a la
        // altura de las cifras para que queden juntas. Las cifras que cambian se deslizan (RollingText).
        val hour = if (state.clockValid) "%02d".format(state.now.hour) else "--"
        val minute = if (state.clockValid) "%02d".format(state.now.minute) else "--"
        val style = Type.Title.copy(
            fontSize = 64.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-2).sp,
            fontFeatureSettings = "tnum",
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
        )
        // Cada línea es su propio texto (para animar sus cifras): se solapan para dejar el mismo interlineado de 52.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(RAIL_CLOCK_OVERLAP)) {
            RollingText(hour, style)
            RollingText(minute, style.copy(color = Palette.InkSoft))
        }
        if (!state.online || !state.connected) {
            Text("Sin conexión", style = Type.Tiny.copy(color = Palette.Accent), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Botón en píldora. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    background: Color = Palette.Card,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(84.dp)
            // Recortado a su forma, para que el efecto al pulsar no salga cuadrado
            .clip(RoundedCornerShape(42.dp))
            .background(
                when {
                    !enabled -> Palette.Line
                    primary -> Palette.Ink
                    else -> background
                },
                RoundedCornerShape(42.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 40.dp)
    ) {
        Text(text, style = Type.Body.copy(color = if (primary) Palette.Card else Palette.Ink), maxLines = 1)
    }
}

@Composable
fun ScreenPadding(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 60.dp, bottom = 52.dp)) { content() }
}
