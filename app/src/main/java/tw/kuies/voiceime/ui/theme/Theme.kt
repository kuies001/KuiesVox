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
import tw.kuies.voiceime.AppThemeId
import tw.kuies.voiceime.ThemeAppearance
import tw.kuies.voiceime.ThemePaletteRegistry

internal val legacyDarkColorScheme = darkColorScheme(
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

internal val legacyLightColorScheme = lightColorScheme(
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

/** v0.15.5 的原始圓角；Legacy Warm 必須維持不變。 */
internal val LegacyVoiceImeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * App 設定畫面主題入口。
 *
 * - [AppThemeId.LEGACY_WARM] 直接使用 v0.15.5 的既有 ColorScheme 與 Shapes，升級後外觀不變。
 * - 其餘主題由 [colorSchemeFor] 從 Theme Tokens 產生，因此與 IME 面板共用同一套配色來源。
 * - [ThemeAppearance.SYSTEM] 依 Android 系統深淺色決定；LIGHT／DARK 可手動指定。
 */
@Composable
internal fun VoiceIMETheme(
    themeId: AppThemeId = AppThemeId.LEGACY_WARM,
    appearance: ThemeAppearance = ThemeAppearance.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val useDark = when (appearance) {
        ThemeAppearance.SYSTEM -> darkTheme
        ThemeAppearance.LIGHT -> false
        ThemeAppearance.DARK -> true
    }
    if (themeId == AppThemeId.LEGACY_WARM) {
        MaterialTheme(
            colorScheme = if (useDark) legacyDarkColorScheme else legacyLightColorScheme,
            typography = Typography,
            shapes = LegacyVoiceImeShapes,
            content = content
        )
        return
    }
    val palette = ThemePaletteRegistry.paletteFor(themeId, appearance, darkTheme)
    MaterialTheme(
        colorScheme = colorSchemeFor(palette),
        typography = Typography,
        shapes = shapesFor(themeId, palette),
        content = content
    )
}
