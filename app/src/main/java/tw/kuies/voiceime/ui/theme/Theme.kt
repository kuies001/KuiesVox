package tw.kuies.voiceime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VoiceLavender,
    onPrimary = Color(0xFF252342),
    primaryContainer = VoiceLavenderDeep,
    onPrimaryContainer = Color(0xFFE7E4FF),
    inversePrimary = Color(0xFF5E5A92),
    secondary = VoiceMint,
    onSecondary = Color(0xFF18372E),
    secondaryContainer = VoiceMintDeep,
    onSecondaryContainer = Color(0xFFC6F4E7),
    tertiary = VoicePink,
    onTertiary = Color(0xFF48283B),
    tertiaryContainer = VoicePinkDeep,
    onTertiaryContainer = Color(0xFFFFD9E8),
    background = VoiceBackground,
    onBackground = VoiceText,
    surface = VoiceSurface,
    onSurface = VoiceText,
    surfaceVariant = VoiceSurfaceVariant,
    onSurfaceVariant = VoiceTextMuted,
    surfaceTint = VoiceLavender,
    inverseSurface = Color(0xFFE0E5EF),
    inverseOnSurface = Color(0xFF252D3B),
    outline = Color(0xFF8490A5),
    outlineVariant = Color(0xFF3B475B),
    scrim = Color(0x99000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF56538A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E1FF),
    onPrimaryContainer = Color(0xFF15123F),
    secondary = Color(0xFF37665A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC0F0E1),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFF81506A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E9),
    onTertiaryContainer = Color(0xFF321027),
    background = Color(0xFFF5F3FA),
    onBackground = Color(0xFF1B1B25),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1B1B25),
    surfaceVariant = Color(0xFFE7E4EF),
    onSurfaceVariant = Color(0xFF494854),
    outline = Color(0xFF797783),
    outlineVariant = Color(0xFFC9C6D3),
    inverseSurface = Color(0xFF30303A),
    inverseOnSurface = Color(0xFFF2F0FA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val VoiceIMEShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VoiceIMETheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = VoiceIMEShapes,
        content = content
    )
}
