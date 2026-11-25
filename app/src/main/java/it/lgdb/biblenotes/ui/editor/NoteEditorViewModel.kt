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
import it.lgdb.biblenotes.model.DraftRecord
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class DraftStatus {
    IDLE,
    SAVING,
    SAVED
}

data class EditorSnapshot(
    val title: String,
    val richContent: RichNoteContent,
    val selection: TextRange,
    val tags: List<String>
)

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
    val isCorrupted: Boolean = false,
    val rawCorruptedJson: String? = null,
    val openWithJwLibrary: Boolean = false,
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val language: AppLanguage = AppLanguage.IT,
    val hasUnsavedChanges: Boolean = false,
    val draftStatus: DraftStatus = DraftStatus.IDLE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
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

    private var baselineSnapshot: EditorSnapshot = EditorSnapshot("", RichNoteContent(), TextRange.Zero, emptyList())
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var lastTypingPushTimeMs: Long = 0L
    private var autosaveJob: Job? = null

    fun loadSession(
        noteId: String?,
        openWithJwLibrary: Boolean,
        themeOption: ThemeOption,
        language: AppLanguage
    ) {
        autosaveJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        lastTypingPushTimeMs = 0L

        viewModelScope.launch {
            val strings = editorStrings(language)
            val allTags = runCatching { repository.getAllTags() }
                .getOrElse { err ->
                    emitEvent(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                    emptyList()
                }

            val draftKey = noteId ?: NotesRepository.NEW_NOTE_DRAFT_KEY
            val draft = runCatching { repository.getDraft(draftKey) }.getOrNull()

            if (noteId == null) {
                baselineSnapshot = EditorSnapshot("", RichNoteContent(), TextRange.Zero, emptyList())
                if (draft != null && isDraftNonEmpty(draft)) {
                    _uiState.value = NoteEditorUiState(
                        title = draft.title,
                        richContent = draft.content,
                        textFieldValue = TextFieldValue(
                            text = draft.content.text,
                            selection = TextRange(draft.content.text.length)
                        ),
                        tags = draft.tags,
                        availableTags = allTags,
                        isReadOnlyMode = false,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language,
                        hasUnsavedChanges = true,
                        draftStatus = DraftStatus.SAVED
                    )
                    emitEvent(EditorEvent.ShowMessage(strings.draftRecoveredMessage))
                } else {
                    _uiState.value = NoteEditorUiState(
                        availableTags = allTags,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language
                    )
                }
                return@launch
            }

            val noteResult = runCatching { repository.getNoteById(noteId) }
            noteResult.onFailure { err ->
                _uiState.value = NoteEditorUiState(
                    availableTags = allTags,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    isReadOnlyMode = true
                )
                emitEvent(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                return@launch
            }

            val note = noteResult.getOrNull()
            if (note == null) {
                baselineSnapshot = EditorSnapshot("", RichNoteContent(), TextRange.Zero, emptyList())
                _uiState.value = NoteEditorUiState(
                    availableTags = allTags,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language
                )
                return@launch
            }

            if (note.isCorrupted) {
                baselineSnapshot = EditorSnapshot(note.title, note.content, TextRange.Zero, note.tags)
                _uiState.value = NoteEditorUiState(
                    activeNoteId = note.id,
                    createdAt = note.createdAt,
                    pinned = note.pinned,
                    title = note.title,
                    richContent = note.content,
                    textFieldValue = TextFieldValue(note.content.text),
                    tags = note.tags,
                    availableTags = allTags,
                    isReadOnlyMode = true,
                    isCorrupted = true,
                    rawCorruptedJson = note.rawCorruptedJson,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    hasUnsavedChanges = false
                )
                emitEvent(EditorEvent.ShowMessage(strings.corruptedNoteTitle))
                return@launch
            }

            baselineSnapshot = EditorSnapshot(
                title = note.title,
                richContent = note.content,
                selection = TextRange(note.content.text.length),
                tags = note.tags
            )

            val draftMatchesChange = draft != null &&
                draft.updatedAt >= note.updatedAt &&
                (draft.title.trim() != note.title.trim() ||
                    draft.content != note.content ||
                    draft.tags != note.tags)

            if (draft != null && draftMatchesChange) {
                _uiState.value = NoteEditorUiState(
                    activeNoteId = note.id,
                    createdAt = note.createdAt,
                    pinned = note.pinned,
                    title = draft.title,
                    richContent = draft.content,
                    textFieldValue = TextFieldValue(
                        text = draft.content.text,
                        selection = TextRange(draft.content.text.length)
                    ),
                    tags = draft.tags,
                    availableTags = allTags,
                    isReadOnlyMode = false,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    hasUnsavedChanges = true,
                    draftStatus = DraftStatus.SAVED
                )
                emitEvent(EditorEvent.ShowMessage(strings.draftRecoveredMessage))
            } else {
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
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted || current.title == newTitle) return

        pushUndoSnapshot(coalesceTyping = true)
        _uiState.update { state ->
            val updated = state.copy(title = newTitle)
            applyDirtyAndHistoryFlags(updated)
        }
        scheduleDraftAutosave()
    }

    fun onBodyValueChanged(newValue: TextFieldValue) {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted) return

        val textChanged = newValue.text != current.richContent.text
        if (textChanged) {
            val largeJump = kotlin.math.abs(newValue.text.length - current.richContent.text.length) > 1
            pushUndoSnapshot(coalesceTyping = !largeJump)
        }

        _uiState.update { state ->
            val updatedRich = if (textChanged) {
                SpanAdjuster.onTextChanged(state.richContent, newValue.text)
            } else {
                state.richContent
            }
            val updated = state.copy(
                richContent = updatedRich,
                textFieldValue = newValue
            )
            if (textChanged) applyDirtyAndHistoryFlags(updated) else updated
        }

        if (textChanged) {
            scheduleDraftAutosave()
        }
    }

    fun applyHighlight(color: HighlightColor?) {
        val state = _uiState.value
        if (state.isReadOnlyMode || state.isCorrupted) return

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
        if (updatedContent == state.richContent) return

        pushUndoSnapshot(coalesceTyping = false)
        _uiState.update {
            applyDirtyAndHistoryFlags(it.copy(richContent = updatedContent))
        }
        scheduleDraftAutosave()
    }

    fun insertBibleReference(book: BibleBook, chapter: Int, verseSpec: String?) {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted) return

        pushUndoSnapshot(coalesceTyping = false)
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
            applyDirtyAndHistoryFlags(
                state.copy(
                    richContent = updatedContent,
                    textFieldValue = TextFieldValue(
                        text = updatedContent.text,
                        selection = TextRange(newCursor)
                    )
                )
            )
        }
        scheduleDraftAutosave()
    }

    fun addTag(rawTag: String) {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted) return

        val cleaned = rawTag.trim()
        if (cleaned.isEmpty()) return
        if (current.tags.any { it.equals(cleaned, ignoreCase = true) }) return

        val canonical = current.availableTags.firstOrNull {
            it.equals(cleaned, ignoreCase = true)
        } ?: cleaned

        pushUndoSnapshot(coalesceTyping = false)
        _uiState.update { state ->
            applyDirtyAndHistoryFlags(state.copy(tags = state.tags + canonical))
        }
        scheduleDraftAutosave()
    }

    fun removeTag(tag: String) {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted) return
        if (current.tags.none { it.equals(tag, ignoreCase = true) }) return

        pushUndoSnapshot(coalesceTyping = false)
        _uiState.update { state ->
            applyDirtyAndHistoryFlags(
                state.copy(tags = state.tags.filterNot { it.equals(tag, ignoreCase = true) })
            )
        }
        scheduleDraftAutosave()
    }

    fun undo() {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted || undoStack.isEmpty()) return

        redoStack.addLast(current.toSnapshot())
        val previous = undoStack.removeLast()
        lastTypingPushTimeMs = 0L

        _uiState.update { state ->
            applyDirtyAndHistoryFlags(
                state.copy(
                    title = previous.title,
                    richContent = previous.richContent,
                    textFieldValue = TextFieldValue(
                        text = previous.richContent.text,
                        selection = TextRange(
                            previous.selection.start.coerceIn(0, previous.richContent.text.length),
                            previous.selection.end.coerceIn(0, previous.richContent.text.length)
                        )
                    ),
                    tags = previous.tags
                )
            )
        }
        scheduleDraftAutosave()
    }

    fun redo() {
        val current = _uiState.value
        if (current.isReadOnlyMode || current.isCorrupted || redoStack.isEmpty()) return

        undoStack.addLast(current.toSnapshot())
        val next = redoStack.removeLast()
        lastTypingPushTimeMs = 0L

        _uiState.update { state ->
            applyDirtyAndHistoryFlags(
                state.copy(
                    title = next.title,
                    richContent = next.richContent,
                    textFieldValue = TextFieldValue(
                        text = next.richContent.text,
                        selection = TextRange(
                            next.selection.start.coerceIn(0, next.richContent.text.length),
                            next.selection.end.coerceIn(0, next.richContent.text.length)
                        )
                    ),
                    tags = next.tags
                )
            )
        }
        scheduleDraftAutosave()
    }

    fun toggleReadOnlyMode() {
        val state = _uiState.value
        if (state.isCorrupted) return
        _uiState.update { it.copy(isReadOnlyMode = !it.isReadOnlyMode) }
    }

    fun enterEditMode() {
        val state = _uiState.value
        if (state.isCorrupted) return
        _uiState.update { it.copy(isReadOnlyMode = false) }
    }

    fun saveAndClose(silentIfEmpty: Boolean) {
        val state = _uiState.value
        val strings = editorStrings(state.language)

        if (state.isCorrupted) {
            emitEvent(EditorEvent.CloseEditor)
            return
        }

        val trimmedTitle = state.title.trim()
        val hasBody = state.richContent.text.isNotBlank()
        val hasTags = state.tags.isNotEmpty()

        if (!hasBody && trimmedTitle.isEmpty() && !hasTags) {
            autosaveJob?.cancel()
            viewModelScope.launch {
                runCatching { repository.deleteDraft(currentDraftKey(state)) }
                if (silentIfEmpty) {
                    _events.emit(EditorEvent.CloseEditor)
                } else {
                    _events.emit(EditorEvent.ShowMessage(strings.emptyNoteError))
                }
            }
            return
        }

        // No changes on an existing note -> close immediately without rewriting updatedAt
        if (state.activeNoteId != null && !state.hasUnsavedChanges) {
            autosaveJob?.cancel()
            viewModelScope.launch {
                runCatching { repository.deleteDraft(state.activeNoteId) }
                _events.emit(EditorEvent.CloseEditor)
            }
            return
        }

        autosaveJob?.cancel()
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
            runCatching {
                repository.saveNote(record)
                repository.deleteDraft(currentDraftKey(state))
            }.onSuccess {
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    fun discardAndClose() {
        val state = _uiState.value
        autosaveJob?.cancel()
        viewModelScope.launch {
            runCatching { repository.deleteDraft(currentDraftKey(state)) }
            _events.emit(EditorEvent.CloseEditor)
        }
    }

    fun deleteAndClose() {
        val state = _uiState.value
        val strings = editorStrings(state.language)
        autosaveJob?.cancel()
        val id = state.activeNoteId ?: run {
            discardAndClose()
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.moveNoteToTrash(id)
            }.onSuccess {
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
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

    private fun pushUndoSnapshot(coalesceTyping: Boolean) {
        val now = System.currentTimeMillis()
        redoStack.clear()
        if (coalesceTyping && undoStack.isNotEmpty() && (now - lastTypingPushTimeMs) < TYPING_COALESCE_MS) {
            lastTypingPushTimeMs = now
            return
        }
        undoStack.addLast(_uiState.value.toSnapshot())
        while (undoStack.size > MAX_UNDO_STEPS) {
            undoStack.removeFirst()
        }
        lastTypingPushTimeMs = if (coalesceTyping) now else 0L
    }

    private fun scheduleDraftAutosave() {
        val snapshotState = _uiState.value
        if (snapshotState.isCorrupted || snapshotState.isReadOnlyMode) return

        autosaveJob?.cancel()

        val draftKey = currentDraftKey(snapshotState)
        if (!snapshotState.hasUnsavedChanges) {
            _uiState.update { it.copy(draftStatus = DraftStatus.IDLE) }
            autosaveJob = viewModelScope.launch {
                runCatching { repository.deleteDraft(draftKey) }
            }
            return
        }

        _uiState.update { it.copy(draftStatus = DraftStatus.SAVING) }
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            val latest = _uiState.value
            if (latest.isCorrupted) return@launch
            val draft = DraftRecord(
                noteKey = currentDraftKey(latest),
                title = latest.title,
                content = latest.richContent,
                tags = latest.tags,
                updatedAt = System.currentTimeMillis()
            )
            val strings = editorStrings(latest.language)
            runCatching {
                if (!isDraftNonEmpty(draft) && latest.activeNoteId == null) {
                    repository.deleteDraft(draft.noteKey)
                    DraftStatus.IDLE
                } else {
                    repository.saveDraft(draft)
                    DraftStatus.SAVED
                }
            }.onSuccess { status ->
                _uiState.update { it.copy(draftStatus = status) }
            }.onFailure { err ->
                _uiState.update { it.copy(draftStatus = DraftStatus.IDLE) }
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    private fun applyDirtyAndHistoryFlags(state: NoteEditorUiState): NoteEditorUiState {
        val isDirty = state.title != baselineSnapshot.title ||
            state.richContent != baselineSnapshot.richContent ||
            state.tags != baselineSnapshot.tags
        return state.copy(
            hasUnsavedChanges = isDirty,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
    }

    private fun NoteEditorUiState.toSnapshot(): EditorSnapshot = EditorSnapshot(
        title = title,
        richContent = richContent,
        selection = textFieldValue.selection,
        tags = tags
    )

    private fun currentDraftKey(state: NoteEditorUiState): String =
        state.activeNoteId ?: NotesRepository.NEW_NOTE_DRAFT_KEY

    private fun isDraftNonEmpty(draft: DraftRecord): Boolean =
        draft.title.isNotBlank() ||
            draft.content.text.isNotBlank() ||
            draft.content.bibleRefs.isNotEmpty() ||
            draft.tags.isNotEmpty()

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

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 700L
        private const val TYPING_COALESCE_MS = 700L
        private const val MAX_UNDO_STEPS = 60
    }
}
