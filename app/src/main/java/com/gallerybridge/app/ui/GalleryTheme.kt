package com.gallerybridge.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val SearchButtonBlue = Color(0xFF2563EB)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85BCFF),
    onPrimary = Color(0xFF002E60),
    primaryContainer = Color(0xFF223F65),
    onPrimaryContainer = Color(0xFFD9EAFF),
    secondary = Color(0xFF53D9DA),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF153E46),
    onSecondaryContainer = Color(0xFFADF1EF),
    background = Color(0xFF0B1420),
    onBackground = Color(0xFFF2F6FD),
    surface = Color(0xFF0F1927),
    onSurface = Color(0xFFF2F6FD),
    surfaceVariant = Color(0xFF26364C),
    onSurfaceVariant = Color(0xFFBBC9DB),
    surfaceTint = Color(0xFF85BCFF),
    surfaceContainerLowest = Color(0xFF080F19),
    surfaceContainerLow = Color(0xFF141F30),
    surfaceContainer = Color(0xFF192639),
    surfaceContainerHigh = Color(0xFF21324A),
    surfaceContainerHighest = Color(0xFF293C57),
    outline = Color(0xFF8395AB),
    outlineVariant = Color(0xFF34465E)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1755B3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8FF),
    onPrimaryContainer = Color(0xFF0A346B),
    secondary = Color(0xFF006A72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F1F2),
    onSecondaryContainer = Color(0xFF0A434A),
    background = Color(0xFFF3F7FD),
    onBackground = Color(0xFF17253A),
    surface = Color(0xFFF9FBFF),
    onSurface = Color(0xFF17253A),
    surfaceVariant = Color(0xFFDAE5F4),
    onSurfaceVariant = Color(0xFF475A73),
    surfaceTint = Color(0xFF1755B3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEAF1FC),
    surfaceContainerHigh = Color(0xFFE2EBF8),
    surfaceContainerHighest = Color(0xFFD7E3F5),
    outline = Color(0xFF62758D),
    outlineVariant = Color(0xFFBDCCDF)
)

private val GalleryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp)
)

@Composable
internal fun GalleryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = GalleryShapes,
        content = content
    )
}
