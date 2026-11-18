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
 * Graphite and violet visual identity. Keep light and dark themes explicit so stored user
 * preference continues to select the same appearance after an APK update.
 */
// Blue remains a secondary highlight; violet is the approved primary UI accent.
internal val GalleryAccentBlue = Color(0xFF64AEFF)
internal val GalleryAccentCyan = Color(0xFF80BFFF)
internal val SearchButtonBlue = Color(0xFF8040C4)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC58BFF),
    onPrimary = Color(0xFF271136),
    primaryContainer = Color(0xFF42245C),
    onPrimaryContainer = Color(0xFFF4DFFF),
    secondary = GalleryAccentCyan,
    onSecondary = Color(0xFF082C4F),
    secondaryContainer = Color(0xFF23374E),
    onSecondaryContainer = Color(0xFFCCE5FF),
    background = Color(0xFF0C0B12),
    onBackground = Color(0xFFF7F4FF),
    surface = Color(0xFF15131D),
    onSurface = Color(0xFFF7F4FF),
    surfaceVariant = Color(0xFF2A2734),
    onSurfaceVariant = Color(0xFFBFC0D2),
    surfaceTint = Color(0xFFC58BFF),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF1B1925),
    surfaceContainer = Color(0xFF221F2D),
    surfaceContainerHigh = Color(0xFF2B2738),
    surfaceContainerHighest = Color(0xFF373044),
    outline = Color(0xFF8E849E),
    outlineVariant = Color(0xFF413A4C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7939B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFDCFF),
    onPrimaryContainer = Color(0xFF34144E),
    secondary = Color(0xFF255BA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBFF),
    onSecondaryContainer = Color(0xFF15385D),
    background = Color(0xFFFAF8FD),
    onBackground = Color(0xFF221C2D),
    surface = Color.White,
    onSurface = Color(0xFF221C2D),
    surfaceVariant = Color(0xFFEBE5F3),
    onSurfaceVariant = Color(0xFF5C5269),
    surfaceTint = Color(0xFF7939B8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFAFF),
    surfaceContainer = Color(0xFFF4EFFA),
    surfaceContainerHigh = Color(0xFFEDE5F5),
    surfaceContainerHighest = Color(0xFFE5D9F0),
    outline = Color(0xFF776B86),
    outlineVariant = Color(0xFFD6CBDD)
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
