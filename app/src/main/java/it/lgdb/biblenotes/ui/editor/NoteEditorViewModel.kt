package it.lgdb.biblenotes.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.domain.BibleBook
import it.lgdb.biblenotes.domain.OpenBibleResult
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.domain.editorStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class NoteEditorUiState(
    val activeNoteId: String? = null,
    val createdAt: Long? = null,
    val pinned: Boolean = false,
    val title: String = "",
    val richContent: RichNoteContent = RichNoteContent(),
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val tags: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isReadOnlyMode: Boolean = false,
    val openWithJwLibrary: Boolean = false,
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val language: AppLanguage = AppLanguage.IT,
    val hasUnsavedChanges: Boolean = false
) {
    val activeRefAtCursor: BibleRefSpan?
        get() = SpanAdjuster.findRefAtOffset(richContent, textFieldValue.selection.start)
}

sealed interface EditorEvent {
    data class ShowMessage(val message: String) : EditorEvent
    data object CloseEditor : EditorEvent
}

class NoteEditorViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            openWithJwLibrary = repository.getOpenWithJwLibrary(),
            themeOption = repository.getTheme(),
            language = repository.getLanguage()
        )
    )
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    fun loadSession(
        noteId: String?,
        openWithJwLibrary: Boolean,
        themeOption: ThemeOption,
        language: AppLanguage
    ) {
        viewModelScope.launch {
            val allTags = repository.getAllTags()
            if (noteId == null) {
                _uiState.value = NoteEditorUiState(
                    availableTags = allTags,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language
                )
            } else {
                val note = repository.getNoteById(noteId)
                if (note != null) {
                    _uiState.value = NoteEditorUiState(
                        activeNoteId = note.id,
                        createdAt = note.createdAt,
                        pinned = note.pinned,
                        title = note.title,
                        richContent = note.content,
                        textFieldValue = TextFieldValue(
                            text = note.content.text,
                            selection = TextRange(note.content.text.length)
                        ),
                        tags = note.tags,
                        availableTags = allTags,
                        isReadOnlyMode = true,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language,
                        hasUnsavedChanges = false
                    )
                } else {
                    _uiState.value = NoteEditorUiState(
                        availableTags = allTags,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language
                    )
                }
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle, hasUnsavedChanges = true) }
    }

    fun onBodyValueChanged(newValue: TextFieldValue) {
        _uiState.update { state ->
            val textChanged = newValue.text != state.richContent.text
            val updatedRich = if (textChanged) {
                SpanAdjuster.onTextChanged(state.richContent, newValue.text)
            } else {
                state.richContent
            }
            state.copy(
                richContent = updatedRich,
                textFieldValue = newValue,
                hasUnsavedChanges = state.hasUnsavedChanges || textChanged
            )
        }
    }

    fun applyHighlight(color: HighlightColor?) {
        val state = _uiState.value
        val sel = state.textFieldValue.selection
        if (sel.collapsed) {
            emitEvent(EditorEvent.ShowMessage(editorStrings(state.language).selectTextFirstHint))
            return
        }
        val updatedContent = SpanAdjuster.applyHighlight(
            content = state.richContent,
            selectionStart = sel.start,
            selectionEnd = sel.end,
            color = color
        )
        _uiState.update {
            it.copy(
                richContent = updatedContent,
                hasUnsavedChanges = true
            )
        }
    }

    fun insertBibleReference(book: BibleBook, chapter: Int, verseSpec: String?) {
        _uiState.update { state ->
            val sel = state.textFieldValue.selection
            val localizedBook = book.localizedName(state.language)
            val (updatedContent, newCursor) = SpanAdjuster.insertBibleRef(
                content = state.richContent,
                selectionStart = sel.start,
                selectionEnd = sel.end,
                book = book,
                chapter = chapter,
                verseSpec = verseSpec,
                bookDisplayName = localizedBook
            )
            state.copy(
                richContent = updatedContent,
                textFieldValue = TextFieldValue(
                    text = updatedContent.text,
                    selection = TextRange(newCursor)
                ),
                hasUnsavedChanges = true
            )
        }
    }

    fun addTag(rawTag: String) {
        val cleaned = rawTag.trim()
        if (cleaned.isEmpty()) return
        _uiState.update { state ->
            if (state.tags.any { it.equals(cleaned, ignoreCase = true) }) {
                state
            } else {
                state.copy(
                    tags = state.tags + cleaned,
                    hasUnsavedChanges = true
                )
            }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(
                tags = state.tags.filterNot { it == tag },
                hasUnsavedChanges = true
            )
        }
    }

    fun toggleReadOnlyMode() {
        _uiState.update { it.copy(isReadOnlyMode = !it.isReadOnlyMode) }
    }

    fun enterEditMode() {
        _uiState.update { it.copy(isReadOnlyMode = false) }
    }

    fun saveAndClose(silentIfEmpty: Boolean) {
        val state = _uiState.value
        val strings = editorStrings(state.language)
        val trimmedTitle = state.title.trim()
        val hasBody = state.richContent.text.isNotBlank()
        val hasTags = state.tags.isNotEmpty()

        if (!hasBody && trimmedTitle.isEmpty() && !hasTags) {
            if (silentIfEmpty) {
                emitEvent(EditorEvent.CloseEditor)
            } else {
                emitEvent(EditorEvent.ShowMessage(strings.emptyNoteError))
            }
            return
        }

        val now = System.currentTimeMillis()
        val noteId = state.activeNoteId ?: "${now}-${UUID.randomUUID().toString().take(8)}"
        val record = NoteRecord(
            id = noteId,
            title = trimmedTitle,
            content = state.richContent,
            tags = state.tags,
            createdAt = state.createdAt ?: now,
            updatedAt = now,
            pinned = state.pinned
        )

        viewModelScope.launch {
            repository.saveNote(record)
            _events.emit(EditorEvent.CloseEditor)
        }
    }

    fun discardAndClose() {
        emitEvent(EditorEvent.CloseEditor)
    }

    fun deleteAndClose() {
        val id = _uiState.value.activeNoteId ?: run {
            emitEvent(EditorEvent.CloseEditor)
            return
        }
        viewModelScope.launch {
            repository.deleteNote(id)
            _events.emit(EditorEvent.CloseEditor)
        }
    }

    fun onBibleReferenceOpenResult(result: OpenBibleResult) {
        val strings = editorStrings(_uiState.value.language)
        when (result) {
            OpenBibleResult.FallbackToJwOrg -> emitEvent(EditorEvent.ShowMessage(strings.fallbackToJwOrgMessage))
            OpenBibleResult.NoAppAvailable -> emitEvent(EditorEvent.ShowMessage(strings.noAppAvailableMessage))
            OpenBibleResult.OpenedInJwLibrary,
            OpenBibleResult.OpenedInBrowser -> Unit
        }
    }

    private fun emitEvent(event: EditorEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    class Factory(private val repository: NotesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoteEditorViewModel(repository) as T
        }
    }
}
