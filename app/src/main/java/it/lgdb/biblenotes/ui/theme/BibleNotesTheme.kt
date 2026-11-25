package it.lgdb.biblenotes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import it.lgdb.biblenotes.model.ThemeOption

data class BibleNotesExtraColors(
    val noteCardGradientTop: Color,
    val noteCardGradientBottom: Color,
    val pinColor: Color,
    val chipBg: Color,
    val chipText: Color,
    val footerBg: Color,
    val footerActiveBg: Color
)

val LocalBibleNotesExtraColors = staticCompositionLocalOf {
    BibleNotesExtraColors(
        noteCardGradientTop = Color(0xFFF9EFAF),
        noteCardGradientBottom = Color(0xFFF7E98D),
        pinColor = Color(0xFFC0392B),
        chipBg = Color(0xFFE1ECFF),
        chipText = Color(0xFF003087),
        footerBg = Color(0xFFFFFFFF),
        footerActiveBg = Color(0x142F7ED8)
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A90E2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1ECFF),
    onPrimaryContainer = Color(0xFF003087),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3F6FB),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFE1E5E9),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFDEAEA),
    onErrorContainer = Color(0xFFB3261E)
)

private val LightExtra = BibleNotesExtraColors(
    noteCardGradientTop = Color(0xFFFCF5C9),
    noteCardGradientBottom = Color(0xFFF9EEA8),
    pinColor = Color(0xFFC0392B),
    chipBg = Color(0xFFE1ECFF),
    chipText = Color(0xFF003087),
    footerBg = Color(0xFFFFFFFF),
    footerActiveBg = Color(0x142F7ED8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B2245),
    primaryContainer = Color(0xFF2B3B5C),
    onPrimaryContainer = Color(0xFFE5ECFF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF22252C),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF5C1E1C),
    onErrorContainer = Color(0xFFFDEAEA)
)

private val DarkExtra = BibleNotesExtraColors(
    noteCardGradientTop = Color(0xFF272C35),
    noteCardGradientBottom = Color(0xFF21252D),
    pinColor = Color(0xFFE06C65),
    chipBg = Color(0xFF2B3B5C),
    chipText = Color(0xFFE5ECFF),
    footerBg = Color(0xFF1E1E1E),
    footerActiveBg = Color(0x298AB4F8)
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFFB5732C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0D8B0),
    onPrimaryContainer = Color(0xFF5B3A1A),
    background = Color(0xFFF8F2E6),
    onBackground = Color(0xFF3B2B1A),
    surface = Color(0xFFF8F2E6),
    onSurface = Color(0xFF3B2B1A),
    surfaceVariant = Color(0xFFF2E4D0),
    onSurfaceVariant = Color(0xFF6B4B2A),
    outline = Color(0xFFE0C8A0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFDEAEA),
    onErrorContainer = Color(0xFFB3261E)
)

private val SepiaExtra = BibleNotesExtraColors(
    noteCardGradientTop = Color(0xFFF7E8C6),
    noteCardGradientBottom = Color(0xFFF2DCB0),
    pinColor = Color(0xFFC0392B),
    chipBg = Color(0xFFF0D8B0),
    chipText = Color(0xFF5B3A1A),
    footerBg = Color(0xFFF8F2E6),
    footerActiveBg = Color(0x1FB5732C)
)

private val BlueColors = lightColorScheme(
    primary = Color(0xFF003087),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FF),
    onPrimaryContainer = Color(0xFF003087),
    background = Color(0xFFE8F0FF),
    onBackground = Color(0xFF102040),
    surface = Color(0xFFE8F0FF),
    onSurface = Color(0xFF102040),
    surfaceVariant = Color(0xFFD9E4FF),
    onSurfaceVariant = Color(0xFF4A5A80),
    outline = Color(0xFFBFD0FF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFDEAEA),
    onErrorContainer = Color(0xFFB3261E)
)

private val BlueExtra = BibleNotesExtraColors(
    noteCardGradientTop = Color(0xFFF2F7FF),
    noteCardGradientBottom = Color(0xFFE4EEFF),
    pinColor = Color(0xFFC0392B),
    chipBg = Color(0xFFD3E3FF),
    chipText = Color(0xFF003087),
    footerBg = Color(0xFFE8F0FF),
    footerActiveBg = Color(0x1F003087)
)

private fun dynamicExtraColors(scheme: ColorScheme): BibleNotesExtraColors = BibleNotesExtraColors(
    noteCardGradientTop = scheme.surfaceContainerHigh,
    noteCardGradientBottom = scheme.secondaryContainer.copy(alpha = 0.72f),
    pinColor = scheme.error,
    chipBg = scheme.primaryContainer,
    chipText = scheme.onPrimaryContainer,
    footerBg = scheme.surfaceContainer,
    footerActiveBg = scheme.secondaryContainer
)

private val ThemeColorSpec = tween<Color>(durationMillis = 300)

@Composable
private fun animColor(target: Color): Color {
    val color by animateColorAsState(targetValue = target, animationSpec = ThemeColorSpec, label = "theme_color")
    return color
}

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme = target.copy(
    primary = animColor(target.primary),
    onPrimary = animColor(target.onPrimary),
    primaryContainer = animColor(target.primaryContainer),
    onPrimaryContainer = animColor(target.onPrimaryContainer),
    secondary = animColor(target.secondary),
    onSecondary = animColor(target.onSecondary),
    secondaryContainer = animColor(target.secondaryContainer),
    onSecondaryContainer = animColor(target.onSecondaryContainer),
    background = animColor(target.background),
    onBackground = animColor(target.onBackground),
    surface = animColor(target.surface),
    onSurface = animColor(target.onSurface),
    surfaceVariant = animColor(target.surfaceVariant),
    onSurfaceVariant = animColor(target.onSurfaceVariant),
    surfaceContainer = animColor(target.surfaceContainer),
    surfaceContainerHigh = animColor(target.surfaceContainerHigh),
    outline = animColor(target.outline),
    error = animColor(target.error),
    errorContainer = animColor(target.errorContainer),
    onErrorContainer = animColor(target.onErrorContainer)
)

@Composable
private fun animatedExtraColors(target: BibleNotesExtraColors): BibleNotesExtraColors = BibleNotesExtraColors(
    noteCardGradientTop = animColor(target.noteCardGradientTop),
    noteCardGradientBottom = animColor(target.noteCardGradientBottom),
    pinColor = animColor(target.pinColor),
    chipBg = animColor(target.chipBg),
    chipText = animColor(target.chipText),
    footerBg = animColor(target.footerBg),
    footerActiveBg = animColor(target.footerActiveBg)
)

@Composable
fun BibleNotesTheme(
    themeOption: ThemeOption,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val (targetScheme, targetExtra, isDarkTheme) = when (themeOption) {
        ThemeOption.LIGHT -> Triple(LightColors, LightExtra, false)
        ThemeOption.DARK -> Triple(DarkColors, DarkExtra, true)
        ThemeOption.SEPIA -> Triple(SepiaColors, SepiaExtra, false)
        ThemeOption.BLUE -> Triple(BlueColors, BlueExtra, false)
        ThemeOption.SYSTEM_DYNAMIC -> {
            val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColors else LightColors
            }
            Triple(scheme, dynamicExtraColors(scheme), systemDark)
        }
    }

    val colorScheme = animatedColorScheme(targetScheme)
    val extraColors = animatedExtraColors(targetExtra)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalBibleNotesExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
