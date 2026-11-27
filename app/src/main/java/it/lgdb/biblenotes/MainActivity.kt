package it.lgdb.biblenotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import it.lgdb.biblenotes.ui.trash.TrashScreen
import it.lgdb.biblenotes.widget.PinnedNotesWidgetProvider
import kotlinx.coroutines.flow.collectLatest

private sealed interface RootScreen {
    data object Main : RootScreen
    data object Trash : RootScreen
    data class Editor(val noteId: String?) : RootScreen
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: NotesRepository
    private var pendingWidgetIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NotesRepository(applicationContext)
        pendingWidgetIntent = intent
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

            LaunchedEffect(pendingWidgetIntent) {
                val widgetIntent = pendingWidgetIntent ?: return@LaunchedEffect
                when (widgetIntent.action) {
                    PinnedNotesWidgetProvider.ACTION_WIDGET_NEW_NOTE -> {
                        appViewModel.openNewNoteEditor()
                    }
                    PinnedNotesWidgetProvider.ACTION_WIDGET_OPEN_NOTE -> {
                        val noteId = widgetIntent.getStringExtra(PinnedNotesWidgetProvider.EXTRA_NOTE_ID)
                        if (!noteId.isNullOrBlank()) {
                            appViewModel.openNoteEditor(noteId)
                        } else {
                            appViewModel.openHomeFromWidget()
                        }
                    }
                    PinnedNotesWidgetProvider.ACTION_WIDGET_OPEN_HOME -> {
                        appViewModel.openHomeFromWidget()
                    }
                }
                pendingWidgetIntent = null
            }

            LaunchedEffect(Unit) {
                appViewModel.events.collectLatest { event ->
                    when (event) {
                        is AppMessageEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                    }
                }
            }

            LaunchedEffect(appState.isEditorOpen, appState.editorTargetNoteId, appState.editorSessionNonce) {
                if (appState.isEditorOpen) {
                    editorViewModel.loadSession(
                        noteId = appState.editorTargetNoteId,
                        openWithJwLibrary = appState.openWithJwLibrary,
                        themeOption = appState.themeOption,
                        language = appState.language,
                        keepScreenOnInReadMode = appState.keepScreenOnInReadMode,
                        bibleRefDisplayStyle = appState.bibleRefDisplayStyle,
                        smartListsEnabled = appState.smartListsEnabled,
                        noteTextSize = appState.noteTextSize
                    )
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        editorViewModel.flushDraftOnBackground()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            BackHandler(enabled = appState.isEditorOpen) {
                editorViewModel.saveAndClose(silentIfEmpty = true)
            }

            BackHandler(enabled = !appState.isEditorOpen && appState.isTrashOpen) {
                appViewModel.closeTrash()
            }

            BackHandler(enabled = !appState.isEditorOpen && !appState.isTrashOpen && appState.currentTab != MainTab.HOME) {
                appViewModel.selectTab(MainTab.HOME)
            }

            BibleNotesTheme(themeOption = appState.themeOption) {
                val rootScreen = when {
                    appState.isEditorOpen -> RootScreen.Editor(appState.editorTargetNoteId)
                    appState.isTrashOpen -> RootScreen.Trash
                    else -> RootScreen.Main
                }

                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    val sharedTransitionScope = this
                    AnimatedContent(
                        targetState = rootScreen,
                        contentKey = { screen ->
                            when (screen) {
                                RootScreen.Main -> "main"
                                RootScreen.Trash -> "trash"
                                is RootScreen.Editor -> "editor_${screen.noteId ?: "new"}"
                            }
                        },
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 260)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 200))
                        },
                        label = "root_screen_transition"
                    ) { screen ->
                        val animatedVisibilityScope = this
                        when (screen) {
                            is RootScreen.Editor -> {
                                NoteEditorScreen(
                                    state = editorState,
                                    viewModel = editorViewModel,
                                    onCloseEditor = { appViewModel.closeEditor() },
                                    onTextSizeChanged = appViewModel::setNoteTextSize,
                                    sharedNoteId = screen.noteId,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }

                            RootScreen.Trash -> {
                                TrashScreen(
                                    trashedNotes = appState.trashedNotes,
                                    strings = strings,
                                    onBack = appViewModel::closeTrash,
                                    onRestoreNote = appViewModel::restoreTrashedNote,
                                    onDeleteForever = appViewModel::permanentlyDeleteNote,
                                    onEmptyTrash = appViewModel::emptyTrash
                                )
                            }

                            RootScreen.Main -> {
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
                                        Crossfade(
                                            targetState = appState.currentTab,
                                            animationSpec = tween(durationMillis = 150),
                                            label = "main_tab_crossfade"
                                        ) { tab ->
                                            when (tab) {
                                                MainTab.HOME -> {
                                                    HomeScreen(
                                                        notes = appState.filteredNotes,
                                                        searchQuery = appState.homeSearchQuery,
                                                        selectedLabelFilter = appState.selectedLabelFilter,
                                                        sortOption = appState.sortOption,
                                                        trashedCount = appState.trashedNotes.size,
                                                        homeLayoutMode = appState.homeLayoutMode,
                                                        bibleRefStyle = appState.bibleRefDisplayStyle,
                                                        strings = strings,
                                                        onSearchQueryChange = appViewModel::onHomeSearchQueryChanged,
                                                        onClearLabelFilter = appViewModel::clearLabelFilter,
                                                        onSelectSortOption = appViewModel::setSortOption,
                                                        onOpenTrash = appViewModel::openTrash,
                                                        onNewNoteClick = appViewModel::openNewNoteEditor,
                                                        onNoteClick = appViewModel::openNoteEditor,
                                                        onTogglePin = appViewModel::togglePinNote,
                                                        onDeleteNote = appViewModel::deleteNote,
                                                        onNoteCopied = {
                                                            appViewModel.notifyMessage(strings.copiedToClipboard)
                                                        },
                                                        onNotifyMessage = appViewModel::notifyMessage,
                                                        sharedTransitionScope = sharedTransitionScope,
                                                        animatedVisibilityScope = animatedVisibilityScope
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
                                                        keepScreenOnInReadMode = appState.keepScreenOnInReadMode,
                                                        bibleRefDisplayStyle = appState.bibleRefDisplayStyle,
                                                        smartListsEnabled = appState.smartListsEnabled,
                                                        homeLayoutMode = appState.homeLayoutMode,
                                                        noteTextSize = appState.noteTextSize,
                                                        activeNotesCount = appState.allNotes.size,
                                                        trashedNotesCount = appState.trashedNotes.size,
                                                        labelsCount = appState.allLabels.size,
                                                        savedRefsCount = appState.totalSavedRefsCount,
                                                        hasSafetyBackup = appState.hasSafetyBackup,
                                                        importSummary = appState.lastImportSummary,
                                                        strings = strings,
                                                        onToggleJwLibrary = appViewModel::setOpenWithJwLibrary,
                                                        onSelectTheme = appViewModel::setTheme,
                                                        onSelectLanguage = appViewModel::setLanguage,
                                                        onToggleKeepScreenOnInReadMode = appViewModel::setKeepScreenOnInReadMode,
                                                        onSelectBibleRefDisplayStyle = appViewModel::setBibleRefDisplayStyle,
                                                        onToggleSmartLists = appViewModel::setSmartListsEnabled,
                                                        onSelectHomeLayoutMode = appViewModel::setHomeLayoutMode,
                                                        onSelectNoteTextSize = appViewModel::setNoteTextSize,
                                                        onPrepareBackupJson = appViewModel::prepareBackupJson,
                                                        onConsumePendingExportJson = appViewModel::consumePendingExportJson,
                                                        onClearPendingExportJson = appViewModel::clearPendingExportJson,
                                                        onImportBackup = appViewModel::importBackup,
                                                        onDismissImportSummary = appViewModel::dismissImportSummary,
                                                        onRestoreSafetyBackup = appViewModel::restoreSafetyBackup,
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
            }
        }
    }
}
