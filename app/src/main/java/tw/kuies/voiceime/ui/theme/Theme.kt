package tw.kuies.voiceime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = WarmPrimaryDark,
    onPrimary = WarmOnPrimaryDark,
    primaryContainer = WarmPrimaryContainerDark,
    onPrimaryContainer = WarmTextPrimaryDark,
    inversePrimary = WarmPrimaryLight,
    secondary = WarmSecondaryDark,
    onSecondary = WarmOnPrimaryDark,
    secondaryContainer = WarmSecondaryContainerDark,
    onSecondaryContainer = WarmSecondaryDark,
    tertiary = WarmTertiaryDark,
    onTertiary = Color(0xFF243627),
    tertiaryContainer = WarmTertiaryContainerDark,
    onTertiaryContainer = WarmTertiaryDark,
    background = WarmBgDark,
    onBackground = WarmTextPrimaryDark,
    surface = WarmSurfaceDark,
    onSurface = WarmTextPrimaryDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmTextSecondaryDark,
    surfaceTint = WarmPrimaryDark,
    inverseSurface = WarmSurfaceLight,
    inverseOnSurface = WarmTextPrimaryLight,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    scrim = Color(0x99000000),
    error = WarmErrorDark,
    onError = Color(0xFF4A1818),
    errorContainer = WarmErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = WarmPrimaryLight,
    onPrimary = WarmOnPrimaryLight,
    primaryContainer = WarmPrimaryContainerLight,
    onPrimaryContainer = WarmOnPrimaryLight,
    inversePrimary = WarmPrimaryDark,
    secondary = WarmSecondaryLight,
    onSecondary = WarmOnPrimaryLight,
    secondaryContainer = WarmSecondaryContainerLight,
    onSecondaryContainer = Color(0xFF5A2A28),
    tertiary = Color(0xFF5A7E5E),
    onTertiary = Color.White,
    tertiaryContainer = WarmTertiaryContainerLight,
    onTertiaryContainer = Color(0xFF263D29),
    background = WarmBgLight,
    onBackground = WarmTextPrimaryLight,
    surface = WarmSurfaceLight,
    onSurface = WarmTextPrimaryLight,
    surfaceVariant = WarmSurfaceVariantLight,
    onSurfaceVariant = WarmTextSecondaryLight,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    inverseSurface = WarmSurfaceDark,
    inverseOnSurface = WarmTextPrimaryDark,
    error = WarmErrorLight,
    onError = Color.White,
    errorContainer = WarmErrorContainerLight,
    onErrorContainer = Color(0xFF410E0B)
)

private val VoiceIMEShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoiceIMETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = VoiceIMEShapes,
        content = content
    )
}
