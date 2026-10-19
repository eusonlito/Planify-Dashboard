package com.lito.planify.dashboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.GeoResult
import com.lito.planify.dashboard.data.Schedule
import com.lito.planify.dashboard.data.WifiStatus
import com.lito.planify.dashboard.data.readWifiStatus
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class SettingsCallbacks(
    val onSearch: (String) -> Unit,
    val onSelectLocation: (GeoResult) -> Unit,
    val onSchedule: (Schedule) -> Unit,
    val onBrightness: (Float?) -> Unit,
    val onLogout: () -> Unit
)

/**
 * Tres columnas que caben enteras en pantalla, sin desplazamiento; la de la derecha, en dos cajas (brillo y conexión, y
 * los botones). Todos los botones, a lo ancho.
 * Dentro de las cajas (blancas) los controles van en crema ([Control]) para que se distingan.
 */
@Composable
fun SettingsScreen(state: UiState, actions: DashboardActions, callbacks: SettingsCallbacks) {
    ScreenPadding {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                LocationSection(state, callbacks)
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                ScheduleSection(state.schedule, callbacks.onSchedule)
            }
            // La columna de la derecha, en dos cajas: brillo y conexión arriba, y los botones abajo
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Panel(Modifier.fillMaxWidth().weight(1f)) {
                    BrightnessSection(state.brightness, callbacks.onBrightness)
                    Section("Conexión") { WifiCard() }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
                        PillButton("Wi-Fi", Modifier.fillMaxWidth(), background = Control, onClick = actions.openWifi)
                        PillButton("Ajustes de Android", Modifier.fillMaxWidth(), background = Control, onClick = actions.openSettings)
                        PillButton("Cerrar sesión", Modifier.fillMaxWidth(), background = Control, onClick = callbacks.onLogout)
                        PillButton("Apagar o reiniciar", Modifier.fillMaxWidth(), background = Control, onClick = actions.powerMenu)
                    }
                }
            }
        }
    }
}

private val Control = Palette.Background
private val ButtonGap = 16.dp

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SectionLabel(title, Modifier.padding(top = 8.dp, bottom = 20.dp))
    content()
    Spacer(Modifier.height(48.dp))
}

@Composable
private fun LocationSection(state: UiState, callbacks: SettingsCallbacks) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val search = {
        keyboard?.hide()
        callbacks.onSearch(query)
    }
    Section("Ubicación del tiempo") {
        Text(
            state.location?.let { "${it.name} · ${it.detail}" } ?: "Sin ubicación",
            style = Type.Body,
            maxLines = 2,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
            Field(query, { query = it }, "Buscar ciudad", Modifier.fillMaxWidth(), background = Control, imeAction = ImeAction.Search, onAction = search)
            PillButton(if (state.geoSearching) "Buscando…" else "Buscar", Modifier.fillMaxWidth(), primary = true, enabled = query.isNotBlank(), onClick = search)
        }
        state.geoResults.take(5).forEach { result ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        query = ""
                        callbacks.onSelectLocation(result)
                    }
                    .padding(vertical = 16.dp)
            ) {
                Text(result.name, style = Type.Body, maxLines = 1)
                Text(
                    listOfNotNull(result.admin1, result.country).joinToString(", "),
                    style = Type.Small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 18.dp)
                )
            }
            Hairline()
        }
    }
}

@Composable
private fun ScheduleSection(schedule: Schedule, onChange: (Schedule) -> Unit) {
    Section("Horario de pantalla") {
        Toggle("Horario de pantalla", schedule.enabled) { onChange(schedule.copy(enabled = it)) }
        if (schedule.enabled) {
            Toggle("Reloj nocturno", !schedule.screenOff) { onChange(schedule.copy(screenOff = !it)) }
            WindowRow("Lunes a viernes", schedule.weekdayOn, schedule.weekdayOff) { on, off -> onChange(schedule.copy(weekdayOn = on, weekdayOff = off)) }
            WindowRow("Sábado y domingo", schedule.weekendOn, schedule.weekendOff) { on, off -> onChange(schedule.copy(weekendOn = on, weekendOff = off)) }
        }
    }
}

/** Encendido y apagado de un tipo de día, en pasos de 15 minutos (siempre encendido < apagado). */
@Composable
private fun WindowRow(label: String, on: Int, off: Int, onChange: (Int, Int) -> Unit) {
    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = Type.Body.copy(fontWeight = FontWeight.SemiBold))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enciende", style = Type.Small, modifier = Modifier.weight(1f))
            Stepper(on, min = 0, max = off - 15) { onChange(it, off) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apaga", style = Type.Small, modifier = Modifier.weight(1f))
            Stepper(off, min = on + 15, max = 24 * 60 - 15) { onChange(on, it) }
        }
    }
}

@Composable
private fun Stepper(minutes: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton("−", enabled = minutes - 15 >= min) { onChange(minutes - 15) }
        Text(LocalTime.MIN.plusMinutes(minutes.toLong()).format(HourMinute), style = Type.Body, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
        RoundButton("+", enabled = minutes + 15 <= max) { onChange(minutes + 15) }
    }
}

@Composable
private fun BrightnessSection(brightness: Float?, onChange: (Float?) -> Unit) {
    // Último brillo manual, para volver a él al quitar el automático
    var manual by remember { mutableFloatStateOf(brightness ?: 0.8f) }
    val auto = brightness == null
    // El valor elegido va en el título, para no quitarle ancho al deslizador
    Section(if (auto) "Brillo · automático" else "Brillo · ${(manual * 100).roundToInt()}%") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LevelSlider(manual, enabled = !auto, modifier = Modifier.weight(1f)) {
                manual = it
                onChange(it)
            }
            // (A): brillo automático del sistema; con él, el deslizador queda desactivado
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (auto) Palette.Ink else Control, CircleShape)
                    .clickable { onChange(if (auto) manual else null) }
            ) { Text("A", style = Type.Body.copy(color = if (auto) Palette.Card else Palette.Ink, fontWeight = FontWeight.SemiBold)) }
        }
    }
}

/** Brillo mínimo del deslizador: más bajo, la pantalla apenas se ve de día. */
private const val MIN_BRIGHTNESS = 0.05f

/** Salto del deslizador: de 5 en 5 %. */
private const val BRIGHTNESS_STEP = 0.05f

/**
 * Deslizador a tamaño de este panel (el de Material está pensado para dp de móvil): pista gruesa, la parte llena en
 * tinta y un tirador grande. Se toca o se arrastra; desactivado, se ve translúcido y no responde.
 */
@Composable
private fun LevelSlider(value: Float, enabled: Boolean, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val thumb = 26f
    fun valueAt(x: Float, width: Int): Float {
        val raw = MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS) * ((x - thumb) / (width - thumb * 2))
        return ((raw / BRIGHTNESS_STEP).roundToInt() * BRIGHTNESS_STEP).coerceIn(MIN_BRIGHTNESS, 1f)
    }
    Canvas(
        modifier
            .height(84.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pointerInput(enabled) { if (enabled) detectTapGestures { change(valueAt(it.x, size.width)) } }
            .pointerInput(enabled) { if (enabled) detectHorizontalDragGestures { pointer, _ -> change(valueAt(pointer.position.x, size.width)) } }
    ) {
        val y = size.height / 2
        val x = thumb + (size.width - thumb * 2) * ((value - MIN_BRIGHTNESS) / (1f - MIN_BRIGHTNESS))
        drawLine(Control, Offset(thumb, y), Offset(size.width - thumb, y), strokeWidth = 16f, cap = StrokeCap.Round)
        drawLine(Palette.Ink, Offset(thumb, y), Offset(x, y), strokeWidth = 16f, cap = StrokeCap.Round)
        drawCircle(Palette.Ink, thumb, Offset(x, y))
        drawCircle(Palette.Card, thumb - 8f, Offset(x, y))
    }
}

/** Red Wi-Fi conectada, con una barra de calidad según la señal. Se actualiza cada 5 s mientras se ve. */
@Composable
private fun WifiCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<WifiStatus?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            status = withContext(Dispatchers.IO) { runCatching { readWifiStatus(context) }.getOrNull() }
            loaded = true
            delay(5_000)
        }
    }
    val current = status
    val rssi = current?.rssi
    val (quality, color) = when {
        rssi == null -> "" to Palette.InkMuted
        rssi >= -60 -> "Excelente" to Palette.Online
        rssi >= -67 -> "Buena" to Palette.Online
        rssi >= -75 -> "Regular" to Palette.Warm
        else -> "Débil" to Palette.Accent
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Control, RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (current == null) Icons.Outlined.WifiOff else Icons.Outlined.Wifi,
                contentDescription = null,
                tint = if (current == null) Palette.InkMuted else color,
                modifier = Modifier.size(40.dp)
            )
            Text(
                when {
                    current != null -> current.ssid ?: "Conectado"
                    loaded -> "Sin conexión Wi-Fi"
                    else -> "Wi-Fi…"
                },
                style = Type.Body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 10.dp).weight(1f)
            )
            Text(quality, style = Type.Small.copy(color = color, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
        if (current != null) {
            // De -90 dBm (sin señal útil) a -50 dBm (llena)
            val level = rssi?.let { ((it + 90) / 40f).coerceIn(0.04f, 1f) } ?: 0f
            Box(Modifier.fillMaxWidth().height(14.dp).background(Palette.Line, CircleShape)) {
                if (level > 0f) Box(Modifier.fillMaxWidth(level).fillMaxHeight().background(color, CircleShape))
            }
            Text(
                listOfNotNull(rssi?.let { "$it dBm" } ?: "Señal desconocida", current.ip).joinToString("  ·  "),
                style = Type.Small,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Sin efecto al pulsar (sería un recuadro sobre toda la fila): el propio interruptor ya cambia
        modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }.padding(vertical = 8.dp)
    ) {
        Text(label, style = Type.Body, modifier = Modifier.weight(1f))
        Box(
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            modifier = Modifier
                .size(width = 112.dp, height = 60.dp)
                .background(if (checked) Palette.Ink else Palette.Line, CircleShape)
                .padding(7.dp)
        ) {
            Box(Modifier.size(46.dp).background(Palette.Card, CircleShape))
        }
    }
}

@Composable
private fun RoundButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) Control else Palette.Card, CircleShape)
            .border(2.dp, if (enabled) Control else Palette.CardBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) { Text(label, style = Type.Body.copy(color = if (enabled) Palette.Ink else Palette.InkMuted)) }
}

/** Campo de texto a tamaño de este panel (los de Material están pensados para dp de móvil). */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    background: Color = Palette.Card,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.Body,
        cursorBrush = SolidColor(Palette.Ink),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
        // Solo las acciones que usan los campos: "Siguiente" pasa al campo de abajo; el resto ejecuta onAction.
        // (KeyboardActions { … } sustituiría también "Siguiente" y el foco se quedaría atascado.)
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = {
                focusManager.clearFocus()
                onAction()
            },
            onSearch = { onAction() }
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(84.dp)
                    .background(background, RoundedCornerShape(24.dp))
                    .border(2.dp, Palette.CardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 30.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Palette.InkMuted))
                inner()
            }
        }
    )
}
