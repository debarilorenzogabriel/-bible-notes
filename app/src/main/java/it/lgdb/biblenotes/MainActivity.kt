package it.lgdb.biblenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.ui.AppMessageEvent
import it.lgdb.biblenotes.ui.MainAppViewModel
import it.lgdb.biblenotes.ui.MainTab
import it.lgdb.biblenotes.ui.editor.NoteEditorScreen
import it.lgdb.biblenotes.ui.editor.NoteEditorViewModel
import it.lgdb.biblenotes.ui.home.HomeScreen
import it.lgdb.biblenotes.ui.labels.LabelsScreen
import it.lgdb.biblenotes.ui.settings.SettingsScreen
import it.lgdb.biblenotes.ui.theme.BibleNotesTheme
import it.lgdb.biblenotes.ui.theme.LocalBibleNotesExtraColors
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private lateinit var repository: NotesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NotesRepository(applicationContext)
        enableEdgeToEdge()

        setContent {
            val appViewModel: MainAppViewModel = viewModel(
                factory = MainAppViewModel.Factory(repository)
            )
            val editorViewModel: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.Factory(repository)
            )

            val appState by appViewModel.uiState.collectAsStateWithLifecycle()
            val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
            val strings = remember(appState.language) { appStrings(appState.language) }
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                appViewModel.events.collectLatest { event ->
                    when (event) {
                        is AppMessageEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                    }
                }
            }

            LaunchedEffect(appState.isEditorOpen, appState.editorTargetNoteId) {
                if (appState.isEditorOpen) {
                    editorViewModel.loadSession(
                        noteId = appState.editorTargetNoteId,
                        openWithJwLibrary = appState.openWithJwLibrary,
                        themeOption = appState.themeOption,
                        language = appState.language
                    )
                }
            }

            BackHandler(enabled = appState.isEditorOpen) {
                editorViewModel.saveAndClose(silentIfEmpty = true)
            }

            BackHandler(enabled = !appState.isEditorOpen && appState.currentTab != MainTab.HOME) {
                appViewModel.selectTab(MainTab.HOME)
            }

            BibleNotesTheme(themeOption = appState.themeOption) {
                if (appState.isEditorOpen) {
                    NoteEditorScreen(
                        state = editorState,
                        viewModel = editorViewModel,
                        onCloseEditor = { appViewModel.closeEditor() }
                    )
                } else {
                    val extraColors = LocalBibleNotesExtraColors.current
                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            NavigationBar(
                                containerColor = extraColors.footerBg
                            ) {
                                NavigationBarItem(
                                    selected = appState.currentTab == MainTab.HOME,
                                    onClick = { appViewModel.selectTab(MainTab.HOME) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Notes,
                                            contentDescription = strings.navNotes
                                        )
                                    },
                                    label = { Text(strings.navNotes) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = extraColors.footerActiveBg
                                    )
                                )
                                NavigationBarItem(
                                    selected = appState.currentTab == MainTab.LABELS,
                                    onClick = { appViewModel.selectTab(MainTab.LABELS) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Label,
                                            contentDescription = strings.navLabels
                                        )
                                    },
                                    label = { Text(strings.navLabels) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = extraColors.footerActiveBg
                                    )
                                )
                                NavigationBarItem(
                                    selected = appState.currentTab == MainTab.SETTINGS,
                                    onClick = { appViewModel.selectTab(MainTab.SETTINGS) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = strings.navSettings
                                        )
                                    },
                                    label = { Text(strings.navSettings) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = extraColors.footerActiveBg
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (appState.currentTab) {
                                MainTab.HOME -> {
                                    HomeScreen(
                                        notes = appState.filteredNotes,
                                        searchQuery = appState.homeSearchQuery,
                                        strings = strings,
                                        onSearchQueryChange = appViewModel::onHomeSearchQueryChanged,
                                        onNewNoteClick = appViewModel::openNewNoteEditor,
                                        onNoteClick = appViewModel::openNoteEditor,
                                        onTogglePin = appViewModel::togglePinNote,
                                        onDeleteNote = appViewModel::deleteNote,
                                        onNoteCopied = {
                                            appViewModel.notifyMessage(strings.copiedToClipboard)
                                        }
                                    )
                                }

                                MainTab.LABELS -> {
                                    LabelsScreen(
                                        labels = appState.filteredLabels,
                                        searchQuery = appState.labelSearchQuery,
                                        strings = strings,
                                        onSearchQueryChange = appViewModel::onLabelSearchQueryChanged,
                                        onCreateLabel = appViewModel::createLabel,
                                        onSearchByLabel = appViewModel::filterNotesByLabel,
                                        onRenameLabel = appViewModel::renameLabel,
                                        onDeleteLabel = appViewModel::deleteLabel
                                    )
                                }

                                MainTab.SETTINGS -> {
                                    SettingsScreen(
                                        openWithJwLibrary = appState.openWithJwLibrary,
                                        themeOption = appState.themeOption,
                                        language = appState.language,
                                        strings = strings,
                                        onToggleJwLibrary = appViewModel::setOpenWithJwLibrary,
                                        onSelectTheme = appViewModel::setTheme,
                                        onSelectLanguage = appViewModel::setLanguage,
                                        onPrepareBackupJson = appViewModel::prepareBackupJson,
                                        onConsumePendingExportJson = appViewModel::consumePendingExportJson,
                                        onClearPendingExportJson = appViewModel::clearPendingExportJson,
                                        onImportBackup = appViewModel::importBackup,
                                        onNotifyMessage = appViewModel::notifyMessage
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
