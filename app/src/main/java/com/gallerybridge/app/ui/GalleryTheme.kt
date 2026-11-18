package com.gallerybridge.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Photo First palette. Keep light and dark themes explicit so stored user
 * preference continues to select the same appearance after an APK update.
 */
internal val GalleryAccentBlue = Color(0xFF3478F6)
internal val GalleryAccentCyan = Color(0xFF55C9DD)
internal val SearchButtonBlue = Color(0xFF245DD4)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83B6FF),
    onPrimary = Color(0xFF0C2850),
    primaryContainer = Color(0xFF233B63),
    onPrimaryContainer = Color(0xFFDEE9FF),
    secondary = GalleryAccentCyan,
    onSecondary = Color(0xFF072F39),
    secondaryContainer = Color(0xFF1A3D48),
    onSecondaryContainer = Color(0xFFB7EBF1),
    background = Color(0xFF11151C),
    onBackground = Color(0xFFF4F6FA),
    surface = Color(0xFF171D27),
    onSurface = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFF303E50),
    onSurfaceVariant = Color(0xFFB8C6D7),
    surfaceTint = GalleryAccentBlue,
    surfaceContainerLowest = Color(0xFF0D1118),
    surfaceContainerLow = Color(0xFF1D2632),
    surfaceContainer = Color(0xFF253140),
    surfaceContainerHigh = Color(0xFF2C3A4B),
    surfaceContainerHighest = Color(0xFF364558),
    outline = Color(0xFF91A4B9),
    outlineVariant = Color(0xFF3A495C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF245DD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF143A7A),
    secondary = Color(0xFF006D7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F3F5),
    onSecondaryContainer = Color(0xFF154D55),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF182438),
    surface = Color.White,
    onSurface = Color(0xFF182438),
    surfaceVariant = Color(0xFFE1EAF4),
    onSurfaceVariant = Color(0xFF4A5D74),
    surfaceTint = GalleryAccentBlue,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF2F8),
    surfaceContainerHigh = Color(0xFFE5ECF5),
    surfaceContainerHighest = Color(0xFFDCE5F1),
    outline = Color(0xFF687C94),
    outlineVariant = Color(0xFFC7D3E1)
)

private val GalleryShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

private val GalleryTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
internal fun GalleryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = GalleryShapes,
        typography = GalleryTypography,
        content = content
    )
}
