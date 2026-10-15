package com.lito.planify.dashboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.R

// Medidas en píxeles del panel (1920×1200): la app usa Density(1f), así que 1 dp = 1 sp = 1 px.

object Palette {
    val Background = Color(0xFFF7EEE2)
    val Rail = Color(0xFFF0E3D2)
    val Card = Color(0xFFFFFDF9)
    val CardBorder = Color(0xFFEFE2D0)
    val Line = Color(0xFFEADBC6)
    val Ink = Color(0xFF221D18)
    val InkSoft = Color(0xFF6E6358)
    val InkMuted = Color(0xFFA6998A)
    /** Títulos de sección (SectionLabel). */
    val Accent = Color(0xFFC8243B)
    val Rain = Color(0xFF2F80ED)
    val Warm = Color(0xFFF5A623)
    val Night = Color(0xFF5B6B9A)
    val Highlight = Color(0xFFF6ECDD)
    val Offline = Color(0xFFB5A99A)
    val Online = Color(0xFF3BA55C)
}

val Geist = FontFamily(
    Font(R.font.geist_thin, FontWeight.Thin),
    Font(R.font.geist_extralight, FontWeight.ExtraLight),
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold)
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium)
)

object Type {
    /** Etiquetas de sección en mayúsculas: SIGUIENTE, PRÓXIMAS HORAS… */
    val Label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 2.5.sp, color = Palette.Accent)
    val Mono = TextStyle(fontFamily = GeistMono, fontSize = 24.sp, color = Palette.InkSoft)
    val Clock = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Light, fontSize = 196.sp, lineHeight = 196.sp, letterSpacing = (-8).sp, color = Palette.Ink)
    val Huge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 140.sp, lineHeight = 140.sp, letterSpacing = (-5).sp, color = Palette.Ink)
    val Title = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 46.sp, lineHeight = 54.sp, color = Palette.Ink)
    val Large = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 42.sp, color = Palette.Ink)
    val Body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 29.sp, lineHeight = 37.sp, color = Palette.Ink)
    val Small = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp, color = Palette.InkSoft)
    val Tiny = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp, color = Palette.InkSoft)
}
