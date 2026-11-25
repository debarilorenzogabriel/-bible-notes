package it.lgdb.biblenotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    noteCardGradientTop = Color(0xFFF9EFAF),
    noteCardGradientBottom = Color(0xFFF7E98D),
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
    noteCardGradientTop = Color(0xFF2A2F38),
    noteCardGradientBottom = Color(0xFF22252C),
    pinColor = Color(0xFFC0392B),
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
    noteCardGradientTop = Color(0xFFF5E1B5),
    noteCardGradientBottom = Color(0xFFF1D39A),
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
    noteCardGradientTop = Color(0xFFF0F6FF),
    noteCardGradientBottom = Color(0xFFE0EBFF),
    pinColor = Color(0xFFC0392B),
    chipBg = Color(0xFFD3E3FF),
    chipText = Color(0xFF003087),
    footerBg = Color(0xFFE8F0FF),
    footerActiveBg = Color(0x1F003087)
)

@Composable
fun BibleNotesTheme(
    themeOption: ThemeOption,
    content: @Composable () -> Unit
) {
    val (colorScheme, extraColors) = when (themeOption) {
        ThemeOption.LIGHT -> LightColors to LightExtra
        ThemeOption.DARK -> DarkColors to DarkExtra
        ThemeOption.SEPIA -> SepiaColors to SepiaExtra
        ThemeOption.BLUE -> BlueColors to BlueExtra
    }

    CompositionLocalProvider(LocalBibleNotesExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
