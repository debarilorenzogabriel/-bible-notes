package it.lgdb.biblenotes.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.BackupPayload
import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    openWithJwLibrary: Boolean,
    themeOption: ThemeOption,
    language: AppLanguage,
    strings: AppStrings,
    onToggleJwLibrary: (Boolean) -> Unit,
    onSelectTheme: (ThemeOption) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onPrepareBackupJson: suspend () -> String?,
    onConsumePendingExportJson: () -> String?,
    onClearPendingExportJson: () -> Unit,
    onImportBackup: (BackupPayload) -> Unit,
    onNotifyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pendingImportPayload by remember { mutableStateOf<BackupPayload?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val jsonContent = onConsumePendingExportJson()
        if (uri != null && jsonContent != null) {
            coroutineScope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                            stream.flush()
                        } ?: error("Cannot open output stream")
                    }.isSuccess
                }
                onNotifyMessage(
                    if (success) strings.backupExportSuccess else strings.backupExportError
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        } ?: error("Cannot open input stream")
                    }
                }
                readResult.onFailure {
                    onNotifyMessage(strings.backupImportError)
                }.onSuccess { rawText ->
                    val decoded = BackupSerializer.decode(rawText)
                    decoded.onFailure {
                        onNotifyMessage(strings.backupInvalidFile)
                    }.onSuccess { payload ->
                        pendingImportPayload = payload
                    }
                }
            }
        }
    }

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
                    onCheckedChange = onToggleJwLibrary,
                    modifier = Modifier.testTag("jw_library_switch")
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionChip(
                        label = strings.themeLight,
                        swatchColor = Color(0xFFFFFFFF),
                        selected = themeOption == ThemeOption.LIGHT,
                        onClick = { onSelectTheme(ThemeOption.LIGHT) },
                        testTag = "theme_light_option"
                    )
                    ThemeOptionChip(
                        label = strings.themeDark,
                        swatchColor = Color(0xFF121212),
                        selected = themeOption == ThemeOption.DARK,
                        onClick = { onSelectTheme(ThemeOption.DARK) },
                        testTag = "theme_dark_option"
                    )
                    ThemeOptionChip(
                        label = strings.themeSepia,
                        swatchColor = Color(0xFFF2E4D0),
                        selected = themeOption == ThemeOption.SEPIA,
                        onClick = { onSelectTheme(ThemeOption.SEPIA) },
                        testTag = "theme_sepia_option"
                    )
                    ThemeOptionChip(
                        label = strings.themeBlue,
                        swatchColor = Color(0xFFD9E4FF),
                        selected = themeOption == ThemeOption.BLUE,
                        onClick = { onSelectTheme(ThemeOption.BLUE) },
                        testTag = "theme_blue_option"
                    )
                    ThemeOptionChip(
                        label = strings.themeDynamic,
                        swatchColor = null,
                        selected = themeOption == ThemeOption.SYSTEM_DYNAMIC,
                        onClick = { onSelectTheme(ThemeOption.SYSTEM_DYNAMIC) },
                        testTag = "theme_dynamic_option"
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
                        onClick = { onSelectLanguage(AppLanguage.IT) },
                        testTag = "language_it_option"
                    )
                    LanguageOptionChip(
                        label = "🇬🇧 EN",
                        selected = language == AppLanguage.EN,
                        onClick = { onSelectLanguage(AppLanguage.EN) },
                        testTag = "language_en_option"
                    )
                }
            }
        }

        // Backup Export / Import
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        text = strings.settingsBackupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsBackupDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("import_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.settingsImport)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val json = onPrepareBackupJson()
                                if (json == null) {
                                    onClearPendingExportJson()
                                    onNotifyMessage(strings.backupNothingToExport)
                                } else {
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    exportLauncher.launch("bible-notes-backup-$dateStr.json")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.settingsExport)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }

    pendingImportPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImportPayload = null },
            title = { Text(strings.backupConfirmTitle) },
            text = {
                Text(
                    strings.backupConfirmBody
                        .replace("{notes}", payload.notes.size.toString())
                        .replace("{labels}", payload.allLabels.size.toString())
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingImportPayload = null },
                    modifier = Modifier.testTag("cancel_import_button")
                ) {
                    Text(strings.cancel)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportPayload = null
                        onImportBackup(payload)
                    },
                    modifier = Modifier.testTag("confirm_import_button")
                ) {
                    Text(strings.confirmAction)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    swatchColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LanguageOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
