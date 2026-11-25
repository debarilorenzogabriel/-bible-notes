package it.lgdb.biblenotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.data.PinToggleResult
import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.NoteRecord
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
    val editorTargetNoteId: String? = null,
    val allNotes: List<NoteRecord> = emptyList(),
    val homeSearchQuery: String = "",
    val allLabels: List<String> = emptyList(),
    val labelSearchQuery: String = "",
    val openWithJwLibrary: Boolean = false,
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val language: AppLanguage = AppLanguage.IT
) {
    val filteredNotes: List<NoteRecord>
        get() = NoteSorter.filterNotes(allNotes, homeSearchQuery)

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
            language = repository.getLanguage()
        )
    )
    val uiState: StateFlow<MainAppUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppMessageEvent>()
    val events: SharedFlow<AppMessageEvent> = _events.asSharedFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            val notes = repository.getAllNotes()
            val labels = repository.getAllTags()
            _uiState.update {
                it.copy(
                    allNotes = notes,
                    allLabels = labels
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == MainTab.LABELS || tab == MainTab.HOME) {
            refreshData()
        }
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

    fun onLabelSearchQueryChanged(query: String) {
        _uiState.update { it.copy(labelSearchQuery = query) }
    }

    fun togglePinNote(noteId: String) {
        viewModelScope.launch {
            when (repository.togglePinNote(noteId)) {
                is PinToggleResult.MaxLimitReached -> {
                    emitMessage(appStrings(_uiState.value.language).maxPinnedReached)
                }
                is PinToggleResult.Toggled,
                PinToggleResult.NotFound -> {
                    refreshData()
                }
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            refreshData()
            emitMessage(appStrings(_uiState.value.language).noteDeletedMessage)
        }
    }

    fun createLabel(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val created = repository.createLabel(trimmed)
            refreshData()
            if (!created) {
                emitMessage(appStrings(_uiState.value.language).labelAlreadyExists)
            }
        }
    }

    fun renameLabel(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        viewModelScope.launch {
            repository.renameLabel(oldName, trimmed)
            refreshData()
        }
    }

    fun deleteLabel(name: String) {
        viewModelScope.launch {
            repository.deleteLabel(name)
            refreshData()
        }
    }

    fun filterNotesByLabel(label: String) {
        _uiState.update {
            it.copy(
                homeSearchQuery = label,
                currentTab = MainTab.HOME
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
