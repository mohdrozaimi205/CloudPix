package com.mohdrozaimi.cloudpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Sky = Color(0xFF38BDF8)
val Blue = Color(0xFF2563EB)
val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF8B5CF6)
val Rose = Color(0xFFFB7185)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)
val Ok = Color(0xFF10B981)
val Grad = Brush.linearGradient(listOf(Sky, Blue, Indigo))

data class GlassColors(val glass: Color, val border: Color, val bgTop: Color, val bgBottom: Color, val glow1: Color, val glow2: Color, val muted: Color)
val LocalGlass = staticCompositionLocalOf { GlassColors(Color.White, Color.Gray, Color.White, Color.White, Color.Blue, Color.Blue, Color.Gray) }

@Composable
fun CloudPixTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF6C9BFF), onPrimary = Color.White, secondary = Sky,
        background = Color(0xFF060C1E), surface = Color(0xFF0E1A3A), surfaceVariant = Color(0xFF16244A),
        onBackground = Color(0xFFE8EEFC), onSurface = Color(0xFFE8EEFC), onSurfaceVariant = Color(0xFF8F9DBD),
        error = Danger, outline = Color(0x33A0BEFF),
    ) else lightColorScheme(
        primary = Blue, onPrimary = Color.White, secondary = Sky,
        background = Color(0xFFEEF3FB), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6EEFA),
        onBackground = Color(0xFF0B1530), onSurface = Color(0xFF0B1530), onSurfaceVariant = Color(0xFF5B6B8A),
        error = Danger, outline = Color(0x1A0F285A),
    )
    val glass = if (dark) GlassColors(
        Color(0x8C121E3E), Color(0x1AA0BEFF), Color(0xFF071230), Color(0xFF060A18), Color(0x5538BDF8), Color(0x448B5CF6), Color(0xFF8F9DBD),
    ) else GlassColors(
        Color(0xB3FFFFFF), Color(0x170F285A), Color(0xFFF3F7FF), Color(0xFFE7EEFA), Color(0x5538BDF8), Color(0x308B5CF6), Color(0xFF5B6B8A),
    )
    val type = Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
        headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        bodyMedium = TextStyle(fontSize = 15.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    )
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(
            colorScheme = scheme,
            typography = type,
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
            content = content,
        )
    }
}

/** Latar "Aurora": gradien malam dengan cahaya biru & ungu. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(g.bgTop, g.bgBottom)))
            .background(Brush.radialGradient(listOf(g.glow1, Color.Transparent), center = Offset(80f, 120f), radius = 1100f))
            .background(Brush.radialGradient(listOf(g.glow2, Color.Transparent), center = Offset(1400f, 2600f), radius = 1300f)),
        content = content,
    )
}
