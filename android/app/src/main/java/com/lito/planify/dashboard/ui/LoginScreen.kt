package com.lito.planify.dashboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.R
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/**
 * Inicio de sesión con la cuenta de Planify. El dashboard solo lee: nunca modifica contenido.
 * Sin Internet no tiene sentido el formulario: se espera a la red, con acceso a los ajustes de Wi-Fi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(state: UiState, onLogin: (String, String) -> Unit, onWifi: () -> Unit) {
    if (!state.connected) {
        WaitingForNetwork(onWifi)
        return
    }
    var email by remember { mutableStateOf(state.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !state.loggingIn

    val focusManager = LocalFocusManager.current
    // Con el teclado abierto el formulario sube por encima de él y el icono se oculta para que quepa entero.
    val keyboardOpen = WindowInsets.isImeVisible
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            // Sin barra de navegación no hay "atrás": tocar fuera de los campos cierra el teclado.
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (!keyboardOpen) {
                Image(
                    painterResource(R.drawable.planify_icon),
                    contentDescription = "Planify",
                    modifier = Modifier.size(144.dp).align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )
            }
            Text(
                "Inicia sesión",
                style = Type.Huge.copy(fontSize = Type.Title.fontSize * if (keyboardOpen) 1.4f else 2f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = if (keyboardOpen) 4.dp else 24.dp)
            )
            Field(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            Field(password, { password = it }, "Contraseña", Modifier.fillMaxWidth(), password = true) {
                if (canSubmit) onLogin(email, password)
            }
            state.loginError?.let { Text(it, style = Type.Body.copy(color = Palette.Accent)) }
            PillButton(if (state.loggingIn) "Entrando…" else "Entrar", Modifier.fillMaxWidth(), primary = true, enabled = canSubmit) {
                onLogin(email, password)
            }
        }
    }
}

/**
 * Arco que gira una vez por segundo. El marco tiene las escalas de animación del sistema a 0 (install.sh) y Compose
 * las respeta, así que el indicador indeterminado de Material se quedaría quieto: el giro va con el reloj de fotogramas.
 */
@Composable
private fun Spinner() {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { angle = (it % 1000L) * 0.36f }
    }
    Canvas(Modifier.size(96.dp).graphicsLayer { rotationZ = angle }) {
        val stroke = 8.dp.toPx()
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(Palette.Line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
        drawArc(Palette.Ink, -90f, 110f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun WaitingForNetwork(onWifi: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Spinner()
            Text("Esperando conexión a Internet…", style = Type.Title, textAlign = TextAlign.Center)
            PillButton("Ir a Ajustes", Modifier.width(520.dp), onClick = onWifi)
        }
    }
}
