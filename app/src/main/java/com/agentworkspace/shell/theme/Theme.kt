package com.agentworkspace.shell.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

data class ExtendedColors(
    val success: Color = Success,
    val successGlow: Color = SuccessGlow,
    val warning: Color = Warning,
    val error: Color = Error,
    val info: Color = Info,
    val diffAddBg: Color = Color(0xFF151515),
    val diffAddText: Color = Color(0xFFF7F7F7),
    val diffRemoveBg: Color = Color(0xFF0C0C0C),
    val diffRemoveText: Color = Color(0xFFA3A3A3),
    val border: Color = BorderColor,
    val borderStrong: Color = BorderStrongColor,
    val surfaceVariant: Color = SurfaceVariantColor,
    val elevated: Color = ElevatedColor,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textDisabled: Color = TextDisabled,
    val textOnBrand: Color = TextOnBrand,
    val primaryLight: Color = PrimaryLight,
    val primaryDark: Color = PrimaryDark,
    val primaryGlow: Color = AccentGlow,
    val accent: Color = Accent,
    val accentSoft: Color = AccentSoft,
    val accentGlow: Color = AccentGlow,
)

object AppGradients {
    val brandStops: List<Color> get() = listOf(BrandStart, BrandMid, BrandEnd)
    fun horizontal() = SolidColor(Primary)
    fun vertical() = SolidColor(Primary)
}

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

private val WorkspaceColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextOnBrand,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = BackgroundColor,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = BackgroundColor,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundColor,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    outline = BorderColor,
    outlineVariant = BorderStrongColor,
    error = Error,
    onError = BackgroundColor,
    errorContainer = ElevatedColor,
    onErrorContainer = TextPrimary,
)

@Composable
fun AgentWorkspaceTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors()) {
        MaterialTheme(
            colorScheme = WorkspaceColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

object AppTheme {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

data class AiColors(
    val aiPrimary: Color,
    val aiSecondary: Color,
    val aiWarning: Color,
    val aiError: Color,
    val aiSuccess: Color,
    val glassBase: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceBorder: Color,
)

object AiTheme {
    val colors: AiColors
        @Composable get() = AiColors(
            aiPrimary = Accent,
            aiSecondary = TextSecondary,
            aiWarning = AppTheme.colors.warning,
            aiError = AppTheme.colors.error,
            aiSuccess = AppTheme.colors.success,
            glassBase = AppTheme.colors.surfaceVariant,
            textPrimary = AppTheme.colors.textPrimary,
            textSecondary = AppTheme.colors.textSecondary,
            textTertiary = AppTheme.colors.textDisabled,
            surfaceBorder = AppTheme.colors.border,
        )
}
