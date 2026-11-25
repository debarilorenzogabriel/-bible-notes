package it.lgdb.biblenotes.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.domain.BibleBook
import it.lgdb.biblenotes.domain.EditOperationKind
import it.lgdb.biblenotes.domain.OpenBibleResult
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.domain.UndoHistoryManager
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
    val draftSessionNoteId: String = "",
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
    val isSaving: Boolean = false,
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
    private val repository: NotesRepository,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() }
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
    private val undoHistory = UndoHistoryManager<EditorSnapshot>(
        maxSteps = MAX_UNDO_STEPS,
        pauseCoalesceMs = TYPING_COALESCE_MS,
        maxGroupDurationMs = MAX_TYPING_GROUP_MS
    )
    private var autosaveJob: Job? = null
    private var draftFlushJob: Job? = null
    private var isSessionActive: Boolean = false

    fun loadSession(
        noteId: String?,
        openWithJwLibrary: Boolean,
        themeOption: ThemeOption,
        language: AppLanguage
    ) {
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        undoHistory.clear()
        isSessionActive = true

        val stableNewId = noteId ?: "${currentTimeMs()}-${UUID.randomUUID().toString().take(8)}"

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
                        draftSessionNoteId = stableNewId,
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
                        isSaving = false,
                        draftStatus = DraftStatus.SAVED
                    )
                    emitEvent(EditorEvent.ShowMessage(strings.draftRecoveredMessage))
                } else {
                    _uiState.value = NoteEditorUiState(
                        draftSessionNoteId = stableNewId,
                        availableTags = allTags,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language,
                        isSaving = false
                    )
                }
                return@launch
            }

            val noteResult = runCatching { repository.getNoteById(noteId) }
            noteResult.onFailure { err ->
                _uiState.value = NoteEditorUiState(
                    draftSessionNoteId = stableNewId,
                    availableTags = allTags,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    isReadOnlyMode = true,
                    isSaving = false
                )
                emitEvent(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                return@launch
            }

            val note = noteResult.getOrNull()
            if (note == null) {
                baselineSnapshot = EditorSnapshot("", RichNoteContent(), TextRange.Zero, emptyList())
                _uiState.value = NoteEditorUiState(
                    draftSessionNoteId = "${currentTimeMs()}-${UUID.randomUUID().toString().take(8)}",
                    availableTags = allTags,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    isSaving = false
                )
                return@launch
            }

            if (note.isCorrupted) {
                baselineSnapshot = EditorSnapshot(note.title, note.content, TextRange.Zero, note.tags)
                _uiState.value = NoteEditorUiState(
                    activeNoteId = note.id,
                    draftSessionNoteId = note.id,
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
                    hasUnsavedChanges = false,
                    isSaving = false
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
                    draftSessionNoteId = note.id,
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
                    isSaving = false,
                    draftStatus = DraftStatus.SAVED
                )
                emitEvent(EditorEvent.ShowMessage(strings.draftRecoveredMessage))
            } else {
                _uiState.value = NoteEditorUiState(
                    activeNoteId = note.id,
                    draftSessionNoteId = note.id,
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
                    hasUnsavedChanges = false,
                    isSaving = false
                )
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted || current.title == newTitle) return

        val (kind, editOffset, nextExpected) = UndoHistoryManager.classifyTitleEdit(current.title, newTitle)
        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = kind,
            nowMs = currentTimeMs(),
            editOffset = editOffset,
            nextExpectedCursor = nextExpected
        )

        _uiState.update { state ->
            applyDirtyAndHistoryFlags(state.copy(title = newTitle))
        }
        scheduleDraftAutosave()
    }

    fun onBodyValueChanged(newValue: TextFieldValue) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return

        val textChanged = newValue.text != current.richContent.text
        if (!textChanged) {
            if (newValue.selection != current.textFieldValue.selection) {
                undoHistory.onCursorMoved()
            }
        } else {
            val (kind, editOffset, nextExpected) = UndoHistoryManager.classifyBodyEdit(
                oldValue = current.textFieldValue,
                newValue = newValue
            )
            undoHistory.recordBeforeEdit(
                snapshotBeforeEdit = current.toSnapshot(),
                kind = kind,
                nowMs = currentTimeMs(),
                editOffset = editOffset,
                nextExpectedCursor = nextExpected
            )
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
        if (state.isSaving || state.isReadOnlyMode || state.isCorrupted) return

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

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = state.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
        _uiState.update {
            applyDirtyAndHistoryFlags(it.copy(richContent = updatedContent))
        }
        scheduleDraftAutosave()
    }

    fun insertBibleReference(book: BibleBook, chapter: Int, verseSpec: String?) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
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
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return

        val cleaned = rawTag.trim()
        if (cleaned.isEmpty()) return
        if (current.tags.any { it.equals(cleaned, ignoreCase = true) }) return

        val canonical = current.availableTags.firstOrNull {
            it.equals(cleaned, ignoreCase = true)
        } ?: cleaned

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
        _uiState.update { state ->
            applyDirtyAndHistoryFlags(state.copy(tags = state.tags + canonical))
        }
        scheduleDraftAutosave()
    }

    fun removeTag(tag: String) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return
        if (current.tags.none { it.equals(tag, ignoreCase = true) }) return

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
        _uiState.update { state ->
            applyDirtyAndHistoryFlags(
                state.copy(tags = state.tags.filterNot { it.equals(tag, ignoreCase = true) })
            )
        }
        scheduleDraftAutosave()
    }

    fun undo() {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return
        val previous = undoHistory.undo(current.toSnapshot()) ?: return

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
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return
        val next = undoHistory.redo(current.toSnapshot()) ?: return

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
        if (state.isSaving || state.isCorrupted) return
        undoHistory.onCursorMoved()
        _uiState.update { it.copy(isReadOnlyMode = !it.isReadOnlyMode) }
    }

    fun enterEditMode() {
        val state = _uiState.value
        if (state.isSaving || state.isCorrupted) return
        undoHistory.onCursorMoved()
        _uiState.update { it.copy(isReadOnlyMode = false) }
    }

    fun saveAndClose(silentIfEmpty: Boolean) {
        val state = _uiState.value
        if (state.isSaving) return
        val strings = editorStrings(state.language)

        if (state.isCorrupted) {
            isSessionActive = false
            autosaveJob?.cancel()
            draftFlushJob?.cancel()
            emitEvent(EditorEvent.CloseEditor)
            return
        }

        val trimmedTitle = state.title.trim()
        val hasBody = state.richContent.text.isNotBlank()
        val hasTags = state.tags.isNotEmpty()
        val draftKey = currentDraftKey(state)

        if (!hasBody && trimmedTitle.isEmpty() && !hasTags) {
            _uiState.update { it.copy(isSaving = true) }
            autosaveJob?.cancel()
            draftFlushJob?.cancel()
            repository.invalidateDraftEpoch(draftKey)
            viewModelScope.launch {
                runCatching { repository.deleteDraft(draftKey) }
                if (silentIfEmpty) {
                    isSessionActive = false
                    _events.emit(EditorEvent.CloseEditor)
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(EditorEvent.ShowMessage(strings.emptyNoteError))
                }
            }
            return
        }

        // No changes on an existing note -> close immediately without rewriting updatedAt
        if (state.activeNoteId != null && !state.hasUnsavedChanges) {
            _uiState.update { it.copy(isSaving = true) }
            autosaveJob?.cancel()
            draftFlushJob?.cancel()
            repository.invalidateDraftEpoch(draftKey)
            viewModelScope.launch {
                runCatching { repository.deleteDraft(state.activeNoteId) }
                isSessionActive = false
                _events.emit(EditorEvent.CloseEditor)
            }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        repository.invalidateDraftEpoch(draftKey)

        val now = currentTimeMs()
        val stableId = state.activeNoteId ?: state.draftSessionNoteId.ifBlank {
            "${now}-${UUID.randomUUID().toString().take(8)}"
        }
        val record = NoteRecord(
            id = stableId,
            title = trimmedTitle,
            content = state.richContent,
            tags = state.tags,
            createdAt = state.createdAt ?: now,
            updatedAt = now,
            pinned = state.pinned
        )

        viewModelScope.launch {
            runCatching {
                repository.saveNoteAndDeleteDraft(record, draftKey)
            }.onSuccess {
                isSessionActive = false
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    fun discardAndClose() {
        val state = _uiState.value
        if (state.isSaving) return
        val draftKey = currentDraftKey(state)

        _uiState.update { it.copy(isSaving = true) }
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        repository.invalidateDraftEpoch(draftKey)

        viewModelScope.launch {
            runCatching { repository.deleteDraft(draftKey) }
            isSessionActive = false
            _events.emit(EditorEvent.CloseEditor)
        }
    }

    fun deleteAndClose() {
        val state = _uiState.value
        if (state.isSaving) return
        val strings = editorStrings(state.language)
        val id = state.activeNoteId ?: run {
            discardAndClose()
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        repository.invalidateDraftEpoch(id)

        viewModelScope.launch {
            runCatching {
                repository.moveNoteToTrash(id)
            }.onSuccess {
                isSessionActive = false
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    fun flushDraftOnBackground() {
        val latest = _uiState.value
        if (!isSessionActive || latest.isCorrupted || latest.isReadOnlyMode || latest.isSaving || !latest.hasUnsavedChanges) {
            return
        }

        autosaveJob?.cancel()
        val draftKey = currentDraftKey(latest)
        val expectedEpoch = repository.currentDraftEpoch(draftKey)
        val draft = DraftRecord(
            noteKey = draftKey,
            title = latest.title,
            content = latest.richContent,
            tags = latest.tags,
            updatedAt = currentTimeMs()
        )
        if (!isDraftNonEmpty(draft) && latest.activeNoteId == null) return

        _uiState.update { it.copy(draftStatus = DraftStatus.SAVING) }
        draftFlushJob?.cancel()
        draftFlushJob = viewModelScope.launch {
            val strings = editorStrings(latest.language)
            runCatching {
                repository.saveDraft(draft, expectedEpoch)
            }.onSuccess { written ->
                if (written && isSessionActive && !_uiState.value.isSaving) {
                    _uiState.update { it.copy(draftStatus = DraftStatus.SAVED) }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(draftStatus = DraftStatus.IDLE) }
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

    private fun scheduleDraftAutosave() {
        val snapshotState = _uiState.value
        if (!isSessionActive || snapshotState.isSaving || snapshotState.isCorrupted || snapshotState.isReadOnlyMode) return

        autosaveJob?.cancel()

        val draftKey = currentDraftKey(snapshotState)
        val expectedEpoch = repository.currentDraftEpoch(draftKey)
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
            if (!isSessionActive || latest.isCorrupted || latest.isSaving) return@launch
            if (repository.currentDraftEpoch(draftKey) != expectedEpoch) return@launch

            val draft = DraftRecord(
                noteKey = draftKey,
                title = latest.title,
                content = latest.richContent,
                tags = latest.tags,
                updatedAt = currentTimeMs()
            )
            val strings = editorStrings(latest.language)
            runCatching {
                if (!isDraftNonEmpty(draft) && latest.activeNoteId == null) {
                    repository.deleteDraft(draft.noteKey)
                    DraftStatus.IDLE
                } else {
                    val written = repository.saveDraft(draft, expectedEpoch)
                    if (written) DraftStatus.SAVED else latest.draftStatus
                }
            }.onSuccess { status ->
                if (isSessionActive && !_uiState.value.isSaving) {
                    _uiState.update { it.copy(draftStatus = status) }
                }
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
            canUndo = undoHistory.canUndo,
            canRedo = undoHistory.canRedo
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
        private const val MAX_TYPING_GROUP_MS = 4000L
        private const val MAX_UNDO_STEPS = 60
    }
}
