package it.lgdb.biblenotes.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.ThemeOption

@Composable
fun SettingsScreen(
    openWithJwLibrary: Boolean,
    themeOption: ThemeOption,
    language: AppLanguage,
    strings: AppStrings,
    onToggleJwLibrary: (Boolean) -> Unit,
    onSelectTheme: (ThemeOption) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = strings.settingsTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // JW Library Toggle
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = strings.settingsJwTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsJwDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = openWithJwLibrary,
                    onCheckedChange = onToggleJwLibrary
                )
            }
        }

        // Theme Picker
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = strings.settingsThemeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsThemeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeDotOption(
                        fill = Color(0xFFFFFFFF),
                        selected = themeOption == ThemeOption.LIGHT,
                        onClick = { onSelectTheme(ThemeOption.LIGHT) }
                    )
                    ThemeDotOption(
                        fill = Color(0xFF121212),
                        selected = themeOption == ThemeOption.DARK,
                        onClick = { onSelectTheme(ThemeOption.DARK) }
                    )
                    ThemeDotOption(
                        fill = Color(0xFFF2E4D0),
                        selected = themeOption == ThemeOption.SEPIA,
                        onClick = { onSelectTheme(ThemeOption.SEPIA) }
                    )
                    ThemeDotOption(
                        fill = Color(0xFFD9E4FF),
                        selected = themeOption == ThemeOption.BLUE,
                        onClick = { onSelectTheme(ThemeOption.BLUE) }
                    )
                }
            }
        }

        // Language Picker
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = strings.settingsLanguageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsLanguageDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageOptionChip(
                        label = "🇮🇹 IT",
                        selected = language == AppLanguage.IT,
                        onClick = { onSelectLanguage(AppLanguage.IT) }
                    )
                    LanguageOptionChip(
                        label = "🇬🇧 EN",
                        selected = language == AppLanguage.EN,
                        onClick = { onSelectLanguage(AppLanguage.EN) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun ThemeDotOption(
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

@Composable
private fun LanguageOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
