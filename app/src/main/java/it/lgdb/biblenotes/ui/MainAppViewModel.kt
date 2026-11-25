package it.lgdb.biblenotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.data.PinToggleResult
import it.lgdb.biblenotes.domain.BackupPayload
import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.ImportMode
import it.lgdb.biblenotes.model.ImportSummary
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteSortOption
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    LABELS,
    SETTINGS
}

data class MainAppUiState(
    val currentTab: MainTab = MainTab.HOME,
    val isEditorOpen: Boolean = false,
    val isTrashOpen: Boolean = false,
    val editorTargetNoteId: String? = null,
    val allNotes: List<NoteRecord> = emptyList(),
    val trashedNotes: List<NoteRecord> = emptyList(),
    val homeSearchQuery: String = "",
    val selectedLabelFilter: String? = null,
    val sortOption: NoteSortOption = NoteSortOption.UPDATED_DESC,
    val allLabels: List<String> = emptyList(),
    val labelSearchQuery: String = "",
    val openWithJwLibrary: Boolean = false,
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val language: AppLanguage = AppLanguage.IT,
    val hasSafetyBackup: Boolean = false,
    val lastImportSummary: ImportSummary? = null
) {
    val filteredNotes: List<NoteRecord>
        get() = NoteSorter.filterNotes(
            notes = allNotes,
            query = homeSearchQuery,
            labelFilter = selectedLabelFilter
        )

    val filteredLabels: List<String>
        get() {
            val q = labelSearchQuery.trim().lowercase()
            if (q.isEmpty()) return allLabels
            return allLabels.filter { it.lowercase().contains(q) }
        }
}

sealed interface AppMessageEvent {
    data class ShowMessage(val message: String) : AppMessageEvent
}

class MainAppViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainAppUiState(
            openWithJwLibrary = repository.getOpenWithJwLibrary(),
            themeOption = repository.getTheme(),
            language = repository.getLanguage(),
            sortOption = repository.getSortOption(),
            hasSafetyBackup = repository.hasSafetyBackup()
        )
    )
    val uiState: StateFlow<MainAppUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppMessageEvent>()
    val events: SharedFlow<AppMessageEvent> = _events.asSharedFlow()

    private var pendingExportJson: String? = null

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching {
                val sortOpt = _uiState.value.sortOption
                val notes = repository.getAllNotes(sortOpt)
                val trashed = repository.getTrashedNotes()
                val labels = repository.getAllTags()
                val safety = repository.hasSafetyBackup()
                Triple(notes, trashed, labels) to safety
            }.onSuccess { (triple, safety) ->
                val (notes, trashed, labels) = triple
                val currentFilter = _uiState.value.selectedLabelFilter
                val resolvedFilter = if (currentFilter != null) {
                    labels.firstOrNull { it.equals(currentFilter, ignoreCase = true) }
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        allNotes = notes,
                        trashedNotes = trashed,
                        allLabels = labels,
                        selectedLabelFilter = resolvedFilter,
                        hasSafetyBackup = safety
                    )
                }
            }.onFailure { err ->
                emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab, isTrashOpen = false) }
        if (tab == MainTab.LABELS || tab == MainTab.HOME || tab == MainTab.SETTINGS) {
            refreshData()
        }
    }

    fun openTrash() {
        _uiState.update { it.copy(isTrashOpen = true) }
        refreshData()
    }

    fun closeTrash() {
        _uiState.update { it.copy(isTrashOpen = false) }
        refreshData()
    }

    fun openNewNoteEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editorTargetNoteId = null
            )
        }
    }

    fun openNoteEditor(noteId: String) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editorTargetNoteId = noteId
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                isEditorOpen = false,
                editorTargetNoteId = null
            )
        }
        refreshData()
    }

    fun onHomeSearchQueryChanged(query: String) {
        _uiState.update { it.copy(homeSearchQuery = query) }
    }

    fun clearLabelFilter() {
        _uiState.update { it.copy(selectedLabelFilter = null) }
    }

    fun onLabelSearchQueryChanged(query: String) {
        _uiState.update { it.copy(labelSearchQuery = query) }
    }

    fun setSortOption(option: NoteSortOption) {
        repository.setSortOption(option)
        _uiState.update { it.copy(sortOption = option) }
        refreshData()
    }

    fun togglePinNote(noteId: String) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.togglePinNote(noteId) }
                .onSuccess { result ->
                    when (result) {
                        is PinToggleResult.MaxLimitReached -> emitMessage(strings.maxPinnedReached)
                        is PinToggleResult.Toggled,
                        PinToggleResult.NotFound -> refreshData()
                    }
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.moveNoteToTrash(noteId) }
                .onSuccess {
                    refreshData()
                    emitMessage(strings.noteMovedToTrashMessage)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun restoreTrashedNote(noteId: String) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.restoreNoteFromTrash(noteId) }
                .onSuccess {
                    refreshData()
                    emitMessage(strings.noteRestoredMessage)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun permanentlyDeleteNote(noteId: String) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.permanentlyDeleteNote(noteId) }
                .onSuccess {
                    refreshData()
                    emitMessage(strings.noteDeletedMessage)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.emptyTrash() }
                .onSuccess {
                    refreshData()
                    emitMessage(strings.trashEmptiedMessage)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun createLabel(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.createLabel(trimmed) }
                .onSuccess { created ->
                    refreshData()
                    if (!created) emitMessage(strings.labelAlreadyExists)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun renameLabel(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.renameLabel(oldName, trimmed) }
                .onSuccess { renamed ->
                    if (!renamed) {
                        emitMessage(strings.labelAlreadyExists)
                    } else {
                        _uiState.update { state ->
                            val updatedFilter = if (state.selectedLabelFilter?.equals(oldName, ignoreCase = true) == true) {
                                trimmed
                            } else {
                                state.selectedLabelFilter
                            }
                            state.copy(selectedLabelFilter = updatedFilter)
                        }
                        refreshData()
                    }
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun deleteLabel(name: String) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching { repository.deleteLabel(name) }
                .onSuccess {
                    _uiState.update { state ->
                        val updatedFilter = if (state.selectedLabelFilter?.equals(name, ignoreCase = true) == true) {
                            null
                        } else {
                            state.selectedLabelFilter
                        }
                        state.copy(selectedLabelFilter = updatedFilter)
                    }
                    refreshData()
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
        }
    }

    fun filterNotesByLabel(label: String) {
        _uiState.update {
            it.copy(
                selectedLabelFilter = label,
                currentTab = MainTab.HOME,
                isTrashOpen = false
            )
        }
    }

    fun setOpenWithJwLibrary(enabled: Boolean) {
        repository.setOpenWithJwLibrary(enabled)
        _uiState.update { it.copy(openWithJwLibrary = enabled) }
    }

    fun setTheme(theme: ThemeOption) {
        repository.setTheme(theme)
        _uiState.update { it.copy(themeOption = theme) }
    }

    fun setLanguage(language: AppLanguage) {
        repository.setLanguage(language)
        _uiState.update { it.copy(language = language) }
    }

    fun notifyMessage(message: String) {
        emitMessage(message)
    }

    suspend fun prepareBackupJson(): String? {
        val strings = appStrings(_uiState.value.language)
        return runCatching {
            val notes = repository.getAllNotes()
            val labels = repository.getAllTags()
            if (notes.isEmpty() && labels.isEmpty()) {
                pendingExportJson = null
                null
            } else {
                BackupSerializer.encode(notes = notes, labels = labels).also {
                    pendingExportJson = it
                }
            }
        }.onFailure { err ->
            emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
        }.getOrNull()
    }

    fun consumePendingExportJson(): String? {
        val current = pendingExportJson
        pendingExportJson = null
        return current
    }

    fun clearPendingExportJson() {
        pendingExportJson = null
    }

    fun importBackup(payload: BackupPayload, mode: ImportMode) {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            runCatching {
                repository.importBackup(payload = payload, mode = mode)
            }.onSuccess { summary ->
                refreshData()
                _uiState.update { it.copy(lastImportSummary = summary) }
            }.onFailure { err ->
                emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
            }
        }
    }

    fun dismissImportSummary() {
        _uiState.update { it.copy(lastImportSummary = null) }
    }

    fun restoreSafetyBackup() {
        viewModelScope.launch {
            val strings = appStrings(_uiState.value.language)
            val payloadResult = repository.readSafetyBackup()
            payloadResult.onFailure {
                emitMessage(strings.backupImportError)
            }.onSuccess { payload ->
                runCatching {
                    repository.importBackup(payload = payload, mode = ImportMode.REPLACE_ALL)
                }.onSuccess {
                    refreshData()
                    emitMessage(strings.backupSafetyRestoredMessage)
                }.onFailure { err ->
                    emitMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}")
                }
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(AppMessageEvent.ShowMessage(message))
        }
    }

    class Factory(private val repository: NotesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainAppViewModel(repository) as T
        }
    }
}
