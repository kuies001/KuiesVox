package tw.kuies.voiceime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tw.kuies.voiceime.AppThemeId
import tw.kuies.voiceime.ThemeContrast
import tw.kuies.voiceime.ThemeMix
import tw.kuies.voiceime.ThemePalette

/**
 * Theme Token → Compose ColorScheme 的映射。
 *
 * 只有新主題走這條路徑；Legacy Warm 直接沿用既有的 [legacyLightColorScheme] /
 * [legacyDarkColorScheme]，確保 v0.15.5 的設定畫面外觀完全不變。
 *
 * 這些函式都不是 @Composable，因此可以在 JVM 單元測試中驗證映射一致性。
 */
internal fun colorSchemeFor(palette: ThemePalette): ColorScheme {
    val surface = Color(palette.surface)
    val background = Color(palette.background)
    val onBackground = Color(palette.onBackground)
    val outlineVariant = Color(ThemeMix.blend(palette.surface, palette.outline, 0.45f))
    val errorContainer = Color(ThemeMix.blend(palette.error, palette.surface, 0.78f))
    val onErrorContainer = Color(ThemeContrast.bestOnColor(errorContainer.value.toInt()))

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = Color(palette.primary),
            onPrimary = Color(palette.recorderForeground),
            primaryContainer = Color(palette.primaryBright),
            onPrimaryContainer = Color(palette.recorderForeground),
            inversePrimary = Color(palette.primaryBright),
            secondary = Color(palette.secondary),
            onSecondary = Color(ThemeContrast.bestOnColor(palette.secondary)),
            secondaryContainer = Color(ThemeMix.blend(palette.secondary, palette.surface, 0.72f)),
            onSecondaryContainer = Color(palette.onBackground),
            tertiary = Color(palette.tertiary),
            onTertiary = Color(ThemeContrast.bestOnColor(palette.tertiary)),
            tertiaryContainer = Color(ThemeMix.blend(palette.tertiary, palette.surface, 0.72f)),
            onTertiaryContainer = Color(palette.onBackground),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = Color(palette.onSurface),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurfaceVariant = Color(palette.onSurfaceVariant),
            surfaceTint = Color(palette.primary),
            inverseSurface = Color(palette.onBackground),
            inverseOnSurface = Color(palette.background),
            outline = Color(palette.outline),
            outlineVariant = outlineVariant,
            scrim = Color(0x99000000),
            error = Color(palette.error),
            onError = Color(ThemeContrast.bestOnColor(palette.error)),
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    } else {
        lightColorScheme(
            primary = Color(palette.primary),
            onPrimary = Color(palette.recorderForeground),
            primaryContainer = Color(palette.primaryBright),
            onPrimaryContainer = Color(palette.recorderForeground),
            inversePrimary = Color(palette.primary),
            secondary = Color(palette.secondary),
            onSecondary = Color(ThemeContrast.bestOnColor(palette.secondary)),
            secondaryContainer = Color(ThemeMix.blend(palette.secondary, palette.surface, 0.72f)),
            onSecondaryContainer = Color(palette.onBackground),
            tertiary = Color(palette.tertiary),
            onTertiary = Color(ThemeContrast.bestOnColor(palette.tertiary)),
            tertiaryContainer = Color(ThemeMix.blend(palette.tertiary, palette.surface, 0.72f)),
            onTertiaryContainer = Color(palette.onBackground),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = Color(palette.onSurface),
            surfaceVariant = Color(palette.surfaceVariant),
            onSurfaceVariant = Color(palette.onSurfaceVariant),
            inverseSurface = Color(palette.onBackground),
            inverseOnSurface = Color(palette.background),
            outline = Color(palette.outline),
            outlineVariant = outlineVariant,
            error = Color(palette.error),
            onError = Color(ThemeContrast.bestOnColor(palette.error)),
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    }
    return scheme
}

/**
 * 依主題調整圓角形狀。圓角不參與測量，因此不會改變任何元件的尺寸或位置。
 * Legacy Warm 必須維持 v0.15.5 的原始形狀。
 */
internal fun shapesFor(themeId: AppThemeId, palette: ThemePalette): Shapes {
    if (themeId == AppThemeId.LEGACY_WARM) return LegacyVoiceImeShapes
    val card = palette.cardRadiusDp
    return Shapes(
        extraSmall = RoundedCornerShape((card * 0.5f).toInt().dp),
        small = RoundedCornerShape((card * 0.75f).toInt().dp),
        medium = RoundedCornerShape(card.dp),
        large = RoundedCornerShape((card * 1.25f).toInt().dp),
        extraLarge = RoundedCornerShape((card * 1.75f).toInt().dp)
    )
}
