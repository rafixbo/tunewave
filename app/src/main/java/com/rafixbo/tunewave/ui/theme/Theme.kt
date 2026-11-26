package com.rafixbo.tunewave.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.expressive.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** TuneWave original branding — deep-space violet + pink + cyan shine.
 *  NOT YouTube Music / ACS branding: original colours, original name. */
private val TuneViolet = Color(0xFF8B5CF6)
private val TuneVioletBright = Color(0xFFA78BFA)
private val TunePink = Color(0xFFEC4899)
private val TuneCyan = Color(0xFF22D3EE)
private val TuneBackgroundDark = Color(0xFF0A0A12)
private val TuneSurfaceDark = Color(0xFF14141F)
private val TuneSurfaceVariantDark = Color(0xFF1E1E2E)

private val DarkScheme = darkColorScheme(
    primary = TuneVioletBright,
    onPrimary = Color(0xFF1E1040),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = TunePink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF831843),
    tertiary = TuneCyan,
    background = TuneBackgroundDark,
    onBackground = Color(0xFFF4F4F8),
    surface = TuneSurfaceDark,
    onSurface = Color(0xFFF4F4F8),
    surfaceVariant = TuneSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB9B9CC),
    outline = Color(0xFF3A3A52),
)

private val LightScheme = lightColorScheme(
    primary = TuneViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    secondary = TunePink,
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF12121C),
    surface = Color.White,
    onSurface = Color(0xFF12121C),
    surfaceVariant = Color(0xFFEDEDF4),
)

/** Material 3 Expressive theme (Android 16 style) — springy motion,
 *  big rounded shapes, emphasized type. Dark is the default TuneWave look. */
@Composable
fun TuneWaveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

@Composable
fun tuneWaveDark(): Boolean = true // TuneWave ships dark-first; system toggle in Settings.
