package com.lito.planify.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.ui.theme.GeistMono
import kotlinx.coroutines.withContext

/**
 * Reloj de paletas (split-flap), como los paneles de los aeropuertos: cada cifra en una placa oscura partida por la
 * mitad (sin ranura). Al cambiar, la mitad de arriba cae y descubre la cifra nueva. Solo se anima el minuto que cambia.
 */
@Composable
fun FlipClock(text: String, modifier: Modifier = Modifier, tile: Dp = 110.dp, height: Dp = 184.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            key(index) {
                if (char == ':') Colon(height) else FlipDigit(char, tile, height)
            }
        }
    }
}

private val TileTop = Color(0xFF2E2823)
private val TileBottom = Color(0xFF221D18)
private val Digit = Color.White
private const val FLIP_MS = 520

@Composable
private fun Colon(height: Dp) {
    Column(Modifier.height(height), verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically)) {
        repeat(2) { Box(Modifier.size(16.dp).background(TileBottom, RoundedCornerShape(4.dp))) }
    }
}

@Composable
private fun FlipDigit(char: Char, width: Dp, height: Dp) {
    var current by remember { mutableStateOf(char) }
    var previous by remember { mutableStateOf<Char?>(null) }
    // 0 → 1: la primera mitad cae la paleta de arriba (cifra anterior) y la segunda baja la de abajo (cifra nueva)
    val top = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    LaunchedEffect(char) {
        if (char == current) return@LaunchedEffect
        previous = current
        current = char
        top.snapTo(0f)
        bottom.snapTo(90f)
        withContext(RealTimeMotion) {
            top.animateTo(-90f, tween(FLIP_MS / 2, easing = FastOutLinearInEasing))
            bottom.animateTo(0f, tween(FLIP_MS / 2, easing = LinearOutSlowInEasing))
        }
        previous = null
    }
    val half = height / 2
    val style = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 144.sp,
        lineHeight = 144.sp,
        color = Digit,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    )
    val old = previous
    Box(Modifier.size(width, height)) {
        // Placas fijas: arriba la cifra nueva; abajo la anterior hasta que la tapa la paleta que baja
        Column {
            Half(current, upper = true, width, half, height, style)
            Half(old ?: current, upper = false, width, half, height, style)
        }
        if (old != null) {
            Column {
                Half(
                    old, upper = true, width, half, height, style,
                    Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationX = top.value
                        cameraDistance = 12f * density
                        alpha = if (top.value <= -89f) 0f else 1f
                    }
                )
                Half(
                    current, upper = false, width, half, height, style,
                    Modifier.graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        rotationX = bottom.value
                        cameraDistance = 12f * density
                        alpha = if (bottom.value >= 89f) 0f else 1f
                    }
                )
            }
        }
    }
}

/** Media placa: la cifra entera, del alto de la placa completa, recortada a su mitad de arriba o de abajo. */
@Composable
private fun Half(char: Char, upper: Boolean, width: Dp, half: Dp, full: Dp, style: TextStyle, modifier: Modifier = Modifier) {
    val corner = 14.dp
    val shape = if (upper) RoundedCornerShape(topStart = corner, topEnd = corner) else RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
    Box(
        modifier
            .size(width, half)
            .clip(shape)
            .background(Brush.verticalGradient(if (upper) listOf(TileTop, TileBottom) else listOf(TileBottom, TileTop.copy(alpha = 0.9f))))
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(if (upper) Alignment.Top else Alignment.Bottom, unbounded = true)
                .height(full),
            contentAlignment = Alignment.Center
        ) {
            Text(char.toString(), style = style, maxLines = 1, modifier = Modifier.width(width))
        }
    }
}
