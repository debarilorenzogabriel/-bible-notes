package it.lgdb.biblenotes.ui.settings

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.R
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.BackupPayload
import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.HomeLayoutMode
import it.lgdb.biblenotes.model.ImportMode
import it.lgdb.biblenotes.model.ImportSummary
import it.lgdb.biblenotes.model.NoteTextSize
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
    keepScreenOnInReadMode: Boolean,
    bibleRefDisplayStyle: BibleRefDisplayStyle,
    smartListsEnabled: Boolean,
    homeLayoutMode: HomeLayoutMode,
    noteTextSize: NoteTextSize,
    activeNotesCount: Int,
    trashedNotesCount: Int,
    labelsCount: Int,
    savedRefsCount: Int,
    hasSafetyBackup: Boolean,
    importSummary: ImportSummary?,
    strings: AppStrings,
    onToggleJwLibrary: (Boolean) -> Unit,
    onSelectTheme: (ThemeOption) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleKeepScreenOnInReadMode: (Boolean) -> Unit,
    onSelectBibleRefDisplayStyle: (BibleRefDisplayStyle) -> Unit,
    onToggleSmartLists: (Boolean) -> Unit,
    onSelectHomeLayoutMode: (HomeLayoutMode) -> Unit,
    onSelectNoteTextSize: (NoteTextSize) -> Unit,
    onPrepareBackupJson: suspend () -> String?,
    onConsumePendingExportJson: () -> String?,
    onClearPendingExportJson: () -> Unit,
    onImportBackup: (BackupPayload, ImportMode) -> Unit,
    onDismissImportSummary: () -> Unit,
    onRestoreSafetyBackup: () -> Unit,
    onNotifyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pendingImportPayload by remember { mutableStateOf<BackupPayload?>(null) }
    var showSafetyRestoreConfirm by remember { mutableStateOf(false) }

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
            SettingsSwitchRow(
                title = strings.settingsJwTitle,
                description = strings.settingsJwDesc,
                checked = openWithJwLibrary,
                onCheckedChange = onToggleJwLibrary,
                testTag = "jw_library_switch"
            )
        }

        // Scripture Chip Display Style
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = strings.settingsBibleRefStyleTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsBibleRefStyleDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsOptionChip(
                        label = strings.bibleRefStyleAbbreviated,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        selected = bibleRefDisplayStyle == BibleRefDisplayStyle.ABBREVIATED,
                        onClick = { onSelectBibleRefDisplayStyle(BibleRefDisplayStyle.ABBREVIATED) },
                        testTag = "bible_ref_style_abbreviated_option"
                    )
                    SettingsOptionChip(
                        label = strings.bibleRefStyleFull,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        selected = bibleRefDisplayStyle == BibleRefDisplayStyle.FULL,
                        onClick = { onSelectBibleRefDisplayStyle(BibleRefDisplayStyle.FULL) },
                        testTag = "bible_ref_style_full_option"
                    )
                }
            }
        }

        // Keep Screen On in Read Mode Toggle
        SettingsCard {
            SettingsSwitchRow(
                title = strings.settingsKeepScreenOnTitle,
                description = strings.settingsKeepScreenOnDesc,
                checked = keepScreenOnInReadMode,
                onCheckedChange = onToggleKeepScreenOnInReadMode,
                testTag = "keep_screen_on_switch",
                leadingIcon = Icons.Filled.Lightbulb
            )
        }

        // Smart Lists Toggle
        SettingsCard {
            SettingsSwitchRow(
                title = strings.settingsSmartListsTitle,
                description = strings.settingsSmartListsDesc,
                checked = smartListsEnabled,
                onCheckedChange = onToggleSmartLists,
                testTag = "smart_lists_switch",
                leadingIcon = Icons.AutoMirrored.Filled.FormatListBulleted
            )
        }

        // Home Layout Mode Picker
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = strings.settingsHomeLayoutTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = strings.settingsHomeLayoutDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsOptionChip(
                        label = strings.homeLayoutGrid,
                        icon = Icons.Filled.GridView,
                        selected = homeLayoutMode == HomeLayoutMode.GRID,
                        onClick = { onSelectHomeLayoutMode(HomeLayoutMode.GRID) },
                        testTag = "home_layout_grid_option"
                    )
                    SettingsOptionChip(
                        label = strings.homeLayoutList,
                        icon = Icons.Filled.ViewAgenda,
                        selected = homeLayoutMode == HomeLayoutMode.LIST,
                        onClick = { onSelectHomeLayoutMode(HomeLayoutMode.LIST) },
                        testTag = "home_layout_list_option"
                    )
                }
            }
        }

        // Note Text Size Picker
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp)
                    )
                    Column {
                        Text(
                            text = strings.settingsTextSizeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.settingsTextSizeDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsOptionChip(
                        label = strings.textSizeSmall,
                        selected = noteTextSize == NoteTextSize.SMALL,
                        onClick = { onSelectNoteTextSize(NoteTextSize.SMALL) },
                        testTag = "text_size_small_option"
                    )
                    SettingsOptionChip(
                        label = strings.textSizeNormal,
                        selected = noteTextSize == NoteTextSize.NORMAL,
                        onClick = { onSelectNoteTextSize(NoteTextSize.NORMAL) },
                        testTag = "text_size_normal_option"
                    )
                    SettingsOptionChip(
                        label = strings.textSizeLarge,
                        selected = noteTextSize == NoteTextSize.LARGE,
                        onClick = { onSelectNoteTextSize(NoteTextSize.LARGE) },
                        testTag = "text_size_large_option"
                    )
                    SettingsOptionChip(
                        label = strings.textSizeExtraLarge,
                        selected = noteTextSize == NoteTextSize.EXTRA_LARGE,
                        onClick = { onSelectNoteTextSize(NoteTextSize.EXTRA_LARGE) },
                        testTag = "text_size_extra_large_option"
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (language == AppLanguage.EN) {
                            "Preview: Happy are those conscious of their spiritual need. (Mt 5:3)"
                        } else {
                            "Anteprima: Felici quelli che sono consapevoli del loro bisogno spirituale. (Mt 5:3)"
                        },
                        fontSize = noteTextSize.bodySp.sp,
                        lineHeight = noteTextSize.lineHeightDp.dp.value.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
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

                if (hasSafetyBackup) {
                    OutlinedButton(
                        onClick = { showSafetyRestoreConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("restore_safety_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.backupRestoreSafetyAction)
                    }
                }
            }
        }

        // App Info & Statistics Card
        val versionLabel = remember(context) {
            runCatching {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                pkgInfo.versionName ?: "2.0"
            }.getOrDefault("2.0")
        }
        val appDisplayName = stringResource(id = R.string.app_name)

        SettingsCard(modifier = Modifier.testTag("settings_app_info_card")) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = strings.settingsAppInfoTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.settingsAppInfoDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "$appDisplayName v$versionLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsMetricTile(
                            icon = Icons.AutoMirrored.Filled.Notes,
                            label = strings.statsActiveNotesLabel,
                            value = activeNotesCount.toString(),
                            testTag = "stats_active_notes",
                            modifier = Modifier.weight(1f)
                        )
                        StatsMetricTile(
                            icon = Icons.Filled.DeleteOutline,
                            label = strings.statsTrashedNotesLabel,
                            value = trashedNotesCount.toString(),
                            testTag = "stats_trashed_notes",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsMetricTile(
                            icon = Icons.AutoMirrored.Filled.Label,
                            label = strings.statsLabelsCountLabel,
                            value = labelsCount.toString(),
                            testTag = "stats_labels_count",
                            modifier = Modifier.weight(1f)
                        )
                        StatsMetricTile(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = strings.statsSavedRefsLabel,
                            value = savedRefsCount.toString(),
                            testTag = "stats_saved_refs",
                            modifier = Modifier.weight(1f)
                        )
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.backupPreviewSummary
                            .replace("{notes}", payload.notes.size.toString())
                            .replace("{labels}", payload.allLabels.size.toString())
                    )
                    Button(
                        onClick = {
                            pendingImportPayload = null
                            onImportBackup(payload, ImportMode.MERGE)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_merge_button")
                    ) {
                        Text(strings.backupMergeAction)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingImportPayload = null
                            onImportBackup(payload, ImportMode.REPLACE_ALL)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_replace_button")
                    ) {
                        Text(strings.backupReplaceAllAction)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pendingImportPayload = null },
                    modifier = Modifier.testTag("cancel_import_button")
                ) {
                    Text(strings.cancelAction)
                }
            }
        )
    }

    importSummary?.let { summary ->
        val body = buildString {
            append(
                strings.backupResultBody
                    .replace("{imported}", summary.importedCount.toString())
                    .replace("{ignored}", summary.ignoredCount.toString())
                    .replace("{invalid}", summary.invalidCount.toString())
                    .replace("{labels}", summary.labelsCount.toString())
            )
            if (summary.safetyBackupCreated) {
                append(strings.backupSafetyBackupNote)
            }
        }
        AlertDialog(
            onDismissRequest = onDismissImportSummary,
            title = { Text(strings.backupResultTitle) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = onDismissImportSummary,
                    modifier = Modifier.testTag("dismiss_import_summary_button")
                ) {
                    Text(strings.closeAction)
                }
            }
        )
    }

    if (showSafetyRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showSafetyRestoreConfirm = false },
            title = { Text(strings.backupRestoreSafetyAction) },
            text = { Text(strings.backupRestoreSafetyConfirmBody) },
            dismissButton = {
                TextButton(onClick = { showSafetyRestoreConfirm = false }) {
                    Text(strings.cancelAction)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSafetyRestoreConfirm = false
                        onRestoreSafetyBackup()
                    }
                ) {
                    Text(strings.confirmAction)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingsOptionChip(
    label: String,
    icon: ImageVector? = null,
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
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
private fun StatsMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
