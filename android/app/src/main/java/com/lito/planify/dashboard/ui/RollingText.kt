package com.lito.planify.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.withContext

/**
 * Texto en el que cada carácter que cambia se desliza hacia abajo: el anterior sale por abajo y el nuevo entra por
 * arriba, como un contador mecánico. Pensado para el reloj (cifras tabulares, mismo ancho para todas).
 */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier) {
        text.forEachIndexed { index, char -> key(index) { RollingChar(char, style) } }
    }
}

@Composable
private fun RollingChar(char: Char, style: TextStyle) {
    var current by remember { mutableStateOf(char) }
    var previous by remember { mutableStateOf<Char?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(char) {
        if (char == current) return@LaunchedEffect
        previous = current
        current = char
        progress.snapTo(0f)
        withContext(RealTimeMotion) { progress.animateTo(1f, tween(ROLL_MS, easing = FastOutSlowInEasing)) }
        previous = null
    }
    Box(Modifier.clipToBounds()) {
        Text(current.toString(), style = style, maxLines = 1, modifier = Modifier.graphicsLayer { translationY = -(1f - progress.value) * size.height })
        previous?.let {
            Text(it.toString(), style = style, maxLines = 1, modifier = Modifier.graphicsLayer { translationY = progress.value * size.height })
        }
    }
}

private const val ROLL_MS = 600

/**
 * El marco tiene las escalas de animación del sistema a 0 (para que Android no anime nada), y Compose las respeta:
 * sin esto el cambio sería instantáneo. Esta animación sí se quiere, así que corre a velocidad real.
 */
internal object RealTimeMotion : MotionDurationScale {
    override val scaleFactor = 1f
}
