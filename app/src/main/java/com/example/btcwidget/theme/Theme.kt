package com.example.btcwidget.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Bitcoin orange: Block's one accent colour. */
val BitcoinOrange = Color(0xFFF7931A)

/**
 * Block's colours for the Material components it uses (menus, dialogs, sheets, text fields,
 * switches, the bottom bar). Fixed and dark, the same as the app's own screens (the Classic
 * palette in [com.example.btcwidget.Palette]): no wallpaper colours (Material You), no light
 * mode, no Material purple. Widget themes are separate and don't touch this.
 */
private val BlockColors = darkColorScheme(
    primary = BitcoinOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2306),
    onPrimaryContainer = Color(0xFFFFD9A8),
    inversePrimary = BitcoinOrange,
    secondary = Color(0xFFD7DCDF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2C31),
    onSecondaryContainer = Color(0xFFD7DCDF),
    tertiary = BitcoinOrange,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A2306),
    onTertiaryContainer = Color(0xFFFFD9A8),
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFD7DCDF),
    surface = Color(0xFF0E0F11),
    onSurface = Color(0xFFD7DCDF),
    surfaceVariant = Color(0xFF1A1B1F),
    onSurfaceVariant = Color(0xFF8C9196),
    surfaceTint = Color.Transparent, // no tinting of raised surfaces
    inverseSurface = Color(0xFFD7DCDF),
    inverseOnSurface = Color(0xFF0E0F11),
    error = Color(0xFFE5534B),
    onError = Color.White,
    errorContainer = Color(0xFF3D1513),
    onErrorContainer = Color(0xFFFFB4AE),
    outline = Color(0xFF34363B),
    outlineVariant = Color(0xFF2A2C31),
    scrim = Color.Black,
    // Raised surfaces: menus, dialogs and sheets sit a step above the black background.
    surfaceBright = Color(0xFF26282D),
    surfaceDim = Color(0xFF0E0F11),
    surfaceContainerLowest = Color(0xFF0A0B0C),
    surfaceContainerLow = Color(0xFF141518),
    surfaceContainer = Color(0xFF1A1B1F),
    surfaceContainerHigh = Color(0xFF1E2024),
    surfaceContainerHighest = Color(0xFF26282D),
)

private val BlockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The app's Material theme: always Block's own dark colours. */
@Composable
fun BTCWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlockColors, shapes = BlockShapes, typography = Typography, content = content)
}
