package it.lgdb.biblenotes.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.lgdb.biblenotes.data.NotesRepository
import it.lgdb.biblenotes.domain.BibleBook
import it.lgdb.biblenotes.domain.BibleReferenceDetector
import it.lgdb.biblenotes.domain.DetectedBibleRef
import it.lgdb.biblenotes.domain.EditOperationKind
import it.lgdb.biblenotes.domain.InNoteSearchHelper
import it.lgdb.biblenotes.domain.InNoteSearchMatch
import it.lgdb.biblenotes.domain.ListFormattingHelper
import it.lgdb.biblenotes.domain.OpenBibleResult
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.domain.UndoHistoryManager
import it.lgdb.biblenotes.domain.editorStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.DraftRecord
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.LinkSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteTextSize
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
import kotlinx.coroutines.isActive
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
    val isLocked: Boolean = false,
    val isCorrupted: Boolean = false,
    val rawCorruptedJson: String? = null,
    val openWithJwLibrary: Boolean = false,
    val themeOption: ThemeOption = ThemeOption.LIGHT,
    val language: AppLanguage = AppLanguage.IT,
    val keepScreenOnInReadMode: Boolean = false,
    val bibleRefDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED,
    val smartListsEnabled: Boolean = true,
    val noteTextSize: NoteTextSize = NoteTextSize.NORMAL,
    val isInNoteSearchOpen: Boolean = false,
    val inNoteSearchQuery: String = "",
    val inNoteSearchMatches: List<InNoteSearchMatch> = emptyList(),
    val activeInNoteSearchIndex: Int = -1,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val draftStatus: DraftStatus = DraftStatus.IDLE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val detectedPlainRefs: List<DetectedBibleRef> = emptyList(),
    val readTimerElapsedMs: Long = 0L,
    val readTimerTargetMs: Long? = null,
    val isReadTimerRunning: Boolean = false
) {
    val activeRefAtCursor: BibleRefSpan?
        get() = SpanAdjuster.findRefAtOffset(richContent, textFieldValue.selection.start)

    val activeLinkAtCursor: LinkSpan?
        get() = SpanAdjuster.findLinkAtOffset(richContent, textFieldValue.selection.start)

    val isSelectionBold: Boolean
        get() = !textFieldValue.selection.collapsed &&
            SpanAdjuster.isRangeCoveredBySpans(
                richContent.bolds,
                textFieldValue.selection.start,
                textFieldValue.selection.end
            )

    val isSelectionItalic: Boolean
        get() = !textFieldValue.selection.collapsed &&
            SpanAdjuster.isRangeCoveredBySpans(
                richContent.italics,
                textFieldValue.selection.start,
                textFieldValue.selection.end
            )

    val detectedRefInSelection: DetectedBibleRef?
        get() = if (textFieldValue.selection.collapsed) {
            null
        } else {
            BibleReferenceDetector.findOverlappingRange(
                detectedPlainRefs,
                textFieldValue.selection.start,
                textFieldValue.selection.end
            )
        }

    val primaryDetectedRef: DetectedBibleRef?
        get() = BibleReferenceDetector.findNearestToOffset(
            detectedPlainRefs,
            textFieldValue.selection.start
        )
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
            language = repository.getLanguage(),
            keepScreenOnInReadMode = repository.getKeepScreenOnInReadMode(),
            bibleRefDisplayStyle = repository.getBibleRefDisplayStyle(),
            smartListsEnabled = repository.getSmartListsEnabled(),
            noteTextSize = repository.getNoteTextSize()
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
    private var timerTickerJob: Job? = null
    private var timerAccumulatedMs: Long = 0L
    private var timerStartedAtMs: Long? = null
    private var isSessionActive: Boolean = false

    fun loadSession(
        noteId: String?,
        openWithJwLibrary: Boolean,
        themeOption: ThemeOption,
        language: AppLanguage,
        keepScreenOnInReadMode: Boolean = repository.getKeepScreenOnInReadMode(),
        bibleRefDisplayStyle: BibleRefDisplayStyle = repository.getBibleRefDisplayStyle(),
        smartListsEnabled: Boolean = repository.getSmartListsEnabled(),
        noteTextSize: NoteTextSize = repository.getNoteTextSize()
    ) {
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        stopAndResetTimerState(clearTarget = true)
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
                        keepScreenOnInReadMode = keepScreenOnInReadMode,
                        bibleRefDisplayStyle = bibleRefDisplayStyle,
                        smartListsEnabled = smartListsEnabled,
                        noteTextSize = noteTextSize,
                        hasUnsavedChanges = true,
                        isSaving = false,
                        draftStatus = DraftStatus.SAVED,
                        detectedPlainRefs = BibleReferenceDetector.findAll(draft.content)
                    )
                    emitEvent(EditorEvent.ShowMessage(strings.draftRecoveredMessage))
                } else {
                    _uiState.value = NoteEditorUiState(
                        draftSessionNoteId = stableNewId,
                        availableTags = allTags,
                        openWithJwLibrary = openWithJwLibrary,
                        themeOption = themeOption,
                        language = language,
                        keepScreenOnInReadMode = keepScreenOnInReadMode,
                        bibleRefDisplayStyle = bibleRefDisplayStyle,
                        smartListsEnabled = smartListsEnabled,
                        noteTextSize = noteTextSize,
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
                    keepScreenOnInReadMode = keepScreenOnInReadMode,
                    bibleRefDisplayStyle = bibleRefDisplayStyle,
                    smartListsEnabled = smartListsEnabled,
                    noteTextSize = noteTextSize,
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
                    keepScreenOnInReadMode = keepScreenOnInReadMode,
                    bibleRefDisplayStyle = bibleRefDisplayStyle,
                    smartListsEnabled = smartListsEnabled,
                    noteTextSize = noteTextSize,
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
                    keepScreenOnInReadMode = keepScreenOnInReadMode,
                    bibleRefDisplayStyle = bibleRefDisplayStyle,
                    smartListsEnabled = smartListsEnabled,
                    noteTextSize = noteTextSize,
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

            val draftMatchesChange = !note.locked &&
                draft != null &&
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
                    isLocked = false,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    keepScreenOnInReadMode = keepScreenOnInReadMode,
                    bibleRefDisplayStyle = bibleRefDisplayStyle,
                    smartListsEnabled = smartListsEnabled,
                    noteTextSize = noteTextSize,
                    hasUnsavedChanges = true,
                    isSaving = false,
                    draftStatus = DraftStatus.SAVED,
                    detectedPlainRefs = BibleReferenceDetector.findAll(draft.content)
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
                        selection = TextRange.Zero
                    ),
                    tags = note.tags,
                    availableTags = allTags,
                    isReadOnlyMode = true,
                    isLocked = note.locked,
                    openWithJwLibrary = openWithJwLibrary,
                    themeOption = themeOption,
                    language = language,
                    keepScreenOnInReadMode = keepScreenOnInReadMode,
                    bibleRefDisplayStyle = bibleRefDisplayStyle,
                    smartListsEnabled = smartListsEnabled,
                    noteTextSize = noteTextSize,
                    hasUnsavedChanges = false,
                    isSaving = false,
                    detectedPlainRefs = BibleReferenceDetector.findAll(note.content)
                )
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isLocked || current.isCorrupted || current.title == newTitle) return

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
        if (current.isSaving || current.isReadOnlyMode || current.isLocked || current.isCorrupted) return

        val effectiveValue = if (current.smartListsEnabled && newValue.text != current.richContent.text) {
            ListFormattingHelper.processTextEdit(
                oldText = current.textFieldValue.text,
                oldSelection = current.textFieldValue.selection,
                newValue = newValue,
                bibleRefs = current.richContent.bibleRefs
            ) ?: newValue
        } else {
            newValue
        }

        val textChanged = effectiveValue.text != current.richContent.text
        if (!textChanged) {
            if (effectiveValue.selection != current.textFieldValue.selection) {
                undoHistory.onCursorMoved()
            }
        } else {
            val (kind, editOffset, nextExpected) = UndoHistoryManager.classifyBodyEdit(
                oldValue = current.textFieldValue,
                newValue = effectiveValue
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
                SpanAdjuster.onTextChanged(state.richContent, effectiveValue.text)
            } else {
                state.richContent
            }
            val updated = state.copy(
                richContent = updatedRich,
                textFieldValue = effectiveValue
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

    fun toggleBold() {
        val state = _uiState.value
        if (state.isSaving || state.isReadOnlyMode || state.isCorrupted) return
        val sel = state.textFieldValue.selection
        if (sel.collapsed) return

        val updatedContent = SpanAdjuster.toggleBold(
            content = state.richContent,
            selectionStart = sel.start,
            selectionEnd = sel.end
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

    fun toggleItalic() {
        val state = _uiState.value
        if (state.isSaving || state.isReadOnlyMode || state.isCorrupted) return
        val sel = state.textFieldValue.selection
        if (sel.collapsed) return

        val updatedContent = SpanAdjuster.toggleItalic(
            content = state.richContent,
            selectionStart = sel.start,
            selectionEnd = sel.end
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

    fun convertDetectedBibleRef(detected: DetectedBibleRef) {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
        _uiState.update { state ->
            val (updatedContent, newCursor) = SpanAdjuster.convertDetectedBibleRef(
                content = state.richContent,
                detected = detected,
                language = state.language,
                currentCursor = state.textFieldValue.selection.end
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

    fun convertAllDetectedBibleRefs() {
        val current = _uiState.value
        if (current.isSaving || current.isReadOnlyMode || current.isCorrupted) return
        val refs = current.detectedPlainRefs
        if (refs.isEmpty()) return

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = current.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )
        _uiState.update { state ->
            val (updatedContent, newCursor) = SpanAdjuster.convertAllDetectedBibleRefs(
                content = state.richContent,
                detectedRefs = state.detectedPlainRefs,
                language = state.language,
                currentCursor = state.textFieldValue.selection.end
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

    fun applyLink(
        selectionStart: Int,
        selectionEnd: Int,
        url: String?,
        displayText: String? = null
    ) {
        val state = _uiState.value
        if (state.isSaving || state.isReadOnlyMode || state.isCorrupted) return
        val strings = editorStrings(state.language)

        val textLength = state.richContent.text.length
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, textLength)

        if (url.isNullOrBlank()) {
            val targetRange = if (start < end) {
                start to end
            } else {
                val active = SpanAdjuster.findLinkAtOffset(state.richContent, start) ?: return
                active.start to active.end
            }
            val clearedContent = SpanAdjuster.applyLink(
                content = state.richContent,
                selectionStart = targetRange.first,
                selectionEnd = targetRange.second,
                url = null
            )
            if (clearedContent == state.richContent) return
            undoHistory.recordBeforeEdit(
                snapshotBeforeEdit = state.toSnapshot(),
                kind = EditOperationKind.DISCRETE,
                nowMs = currentTimeMs()
            )
            _uiState.update {
                applyDirtyAndHistoryFlags(it.copy(richContent = clearedContent))
            }
            scheduleDraftAutosave()
            return
        }

        if (!SpanAdjuster.isValidLinkUrl(url)) {
            emitEvent(EditorEvent.ShowMessage(strings.invalidLinkUrlError))
            return
        }

        val normalizedUrl = SpanAdjuster.normalizeLinkUrl(url)
        val selectedText = if (start < end) state.richContent.text.substring(start, end) else ""
        val requestedText = displayText?.trim()?.takeIf { it.isNotEmpty() }

        undoHistory.recordBeforeEdit(
            snapshotBeforeEdit = state.toSnapshot(),
            kind = EditOperationKind.DISCRETE,
            nowMs = currentTimeMs()
        )

        if (start == end || (requestedText != null && requestedText != selectedText)) {
            val effectiveText = requestedText ?: normalizedUrl
            val (updatedContent, newCursor) = SpanAdjuster.insertLink(
                content = state.richContent,
                selectionStart = start,
                selectionEnd = end,
                displayText = effectiveText,
                url = normalizedUrl
            )
            _uiState.update {
                applyDirtyAndHistoryFlags(
                    it.copy(
                        richContent = updatedContent,
                        textFieldValue = TextFieldValue(
                            text = updatedContent.text,
                            selection = TextRange(newCursor)
                        )
                    )
                )
            }
        } else {
            val updatedContent = SpanAdjuster.applyLink(
                content = state.richContent,
                selectionStart = start,
                selectionEnd = end,
                url = normalizedUrl
            )
            _uiState.update {
                applyDirtyAndHistoryFlags(it.copy(richContent = updatedContent))
            }
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
            val localizedBook = book.localizedAbbr(state.language)
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
        if (state.isSaving || state.isCorrupted || state.isLocked) return
        undoHistory.onCursorMoved()
        _uiState.update { it.copy(isReadOnlyMode = !it.isReadOnlyMode) }
    }

    fun enterEditMode(cursorOffset: Int? = null) {
        val state = _uiState.value
        if (state.isSaving || state.isCorrupted || state.isLocked) return
        undoHistory.onCursorMoved()
        _uiState.update { current ->
            val nextSelection = if (cursorOffset != null) {
                TextRange(cursorOffset.coerceIn(0, current.richContent.text.length))
            } else {
                current.textFieldValue.selection
            }
            current.copy(
                isReadOnlyMode = false,
                textFieldValue = current.textFieldValue.copy(selection = nextSelection)
            )
        }
    }

    fun toggleLock() {
        val state = _uiState.value
        if (state.isSaving || state.isCorrupted) return
        val nextLocked = !state.isLocked
        val strings = editorStrings(state.language)
        undoHistory.onCursorMoved()
        _uiState.update { current ->
            current.copy(
                isLocked = nextLocked,
                isReadOnlyMode = if (nextLocked) true else current.isReadOnlyMode
            )
        }
        val activeId = state.activeNoteId
        if (activeId != null) {
            viewModelScope.launch {
                runCatching { repository.setNoteLocked(activeId, nextLocked) }
                    .onFailure { err ->
                        _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                    }
            }
        }
        emitEvent(
            EditorEvent.ShowMessage(
                if (nextLocked) strings.noteLockedMessage else strings.noteUnlockedMessage
            )
        )
    }

    fun saveAndClose(silentIfEmpty: Boolean) {
        val state = _uiState.value
        if (state.isSaving) return
        val strings = editorStrings(state.language)

        if (state.isCorrupted) {
            isSessionActive = false
            stopAndResetTimerState(clearTarget = true)
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
                    .onSuccess {
                        if (silentIfEmpty) {
                            isSessionActive = false
                            stopAndResetTimerState(clearTarget = true)
                            _events.emit(EditorEvent.CloseEditor)
                        } else {
                            _uiState.update { it.copy(isSaving = false, draftStatus = DraftStatus.IDLE) }
                            _events.emit(EditorEvent.ShowMessage(strings.emptyNoteError))
                        }
                    }.onFailure { err ->
                        _uiState.update { it.copy(isSaving = false) }
                        _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                    }
            }
            return
        }

        // No changes on an existing note -> close only after ensuring any stale draft is removed
        if (state.activeNoteId != null && !state.hasUnsavedChanges) {
            _uiState.update { it.copy(isSaving = true) }
            autosaveJob?.cancel()
            draftFlushJob?.cancel()
            repository.invalidateDraftEpoch(draftKey)
            viewModelScope.launch {
                runCatching { repository.deleteDraft(state.activeNoteId) }
                    .onSuccess {
                        isSessionActive = false
                        stopAndResetTimerState(clearTarget = true)
                        _events.emit(EditorEvent.CloseEditor)
                    }.onFailure { err ->
                        _uiState.update { it.copy(isSaving = false) }
                        _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                    }
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
            pinned = state.pinned,
            locked = state.isLocked
        )

        viewModelScope.launch {
            runCatching {
                repository.saveNoteAndDeleteDraft(record, draftKey)
            }.onSuccess {
                isSessionActive = false
                stopAndResetTimerState(clearTarget = true)
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
        val strings = editorStrings(state.language)
        val draftKey = currentDraftKey(state)

        _uiState.update { it.copy(isSaving = true) }
        autosaveJob?.cancel()
        draftFlushJob?.cancel()
        repository.invalidateDraftEpoch(draftKey)

        viewModelScope.launch {
            runCatching {
                repository.deleteDraft(draftKey)
            }.onSuccess {
                isSessionActive = false
                stopAndResetTimerState(clearTarget = true)
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
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
                stopAndResetTimerState(clearTarget = true)
                _events.emit(EditorEvent.CloseEditor)
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    fun flushDraftOnBackground() {
        val latest = _uiState.value
        if (!isSessionActive || latest.isCorrupted || latest.isSaving || !latest.hasUnsavedChanges) {
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

    fun onExternalLinkOpenFailed() {
        val strings = editorStrings(_uiState.value.language)
        emitEvent(EditorEvent.ShowMessage(strings.cannotOpenLinkMessage))
    }

    private fun scheduleDraftAutosave() {
        val snapshotState = _uiState.value
        if (!isSessionActive || snapshotState.isSaving || snapshotState.isCorrupted) return

        autosaveJob?.cancel()

        val draftKey = currentDraftKey(snapshotState)
        val expectedEpoch = repository.currentDraftEpoch(draftKey)
        val strings = editorStrings(snapshotState.language)
        if (!snapshotState.hasUnsavedChanges) {
            _uiState.update { it.copy(draftStatus = DraftStatus.IDLE) }
            autosaveJob = viewModelScope.launch {
                runCatching { repository.deleteDraft(draftKey) }
                    .onFailure { err ->
                        _events.emit(EditorEvent.ShowMessage("${strings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
                    }
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
            val latestStrings = editorStrings(latest.language)
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
                _events.emit(EditorEvent.ShowMessage("${latestStrings.databaseErrorPrefix} ${err.localizedMessage.orEmpty()}"))
            }
        }
    }

    fun setNoteTextSize(size: NoteTextSize) {
        repository.setNoteTextSize(size)
        _uiState.update { it.copy(noteTextSize = size) }
    }

    fun toggleInNoteSearch() {
        _uiState.update { state ->
            if (state.isInNoteSearchOpen) {
                state.copy(
                    isInNoteSearchOpen = false,
                    inNoteSearchQuery = "",
                    inNoteSearchMatches = emptyList(),
                    activeInNoteSearchIndex = -1
                )
            } else {
                state.copy(isInNoteSearchOpen = true)
            }
        }
    }

    fun closeInNoteSearch() {
        _uiState.update {
            it.copy(
                isInNoteSearchOpen = false,
                inNoteSearchQuery = "",
                inNoteSearchMatches = emptyList(),
                activeInNoteSearchIndex = -1
            )
        }
    }

    fun onInNoteSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val matches = InNoteSearchHelper.findMatches(
                content = state.richContent,
                query = query,
                language = state.language,
                refDisplayStyle = state.bibleRefDisplayStyle
            )
            val nextIdx = if (matches.isEmpty()) -1 else 0
            state.copy(
                inNoteSearchQuery = query,
                inNoteSearchMatches = matches,
                activeInNoteSearchIndex = nextIdx
            )
        }
    }

    fun nextInNoteSearchMatch() {
        _uiState.update { state ->
            val count = state.inNoteSearchMatches.size
            if (count == 0) return@update state
            val nextIdx = if (state.activeInNoteSearchIndex in 0 until count) {
                (state.activeInNoteSearchIndex + 1) % count
            } else {
                0
            }
            state.copy(activeInNoteSearchIndex = nextIdx)
        }
    }

    fun prevInNoteSearchMatch() {
        _uiState.update { state ->
            val count = state.inNoteSearchMatches.size
            if (count == 0) return@update state
            val prevIdx = if (state.activeInNoteSearchIndex in 0 until count) {
                (state.activeInNoteSearchIndex - 1 + count) % count
            } else {
                count - 1
            }
            state.copy(activeInNoteSearchIndex = prevIdx)
        }
    }

    fun toggleReadTimer() {
        val state = _uiState.value
        if (state.isCorrupted) return
        if (state.isReadTimerRunning) {
            timerAccumulatedMs = currentTimerElapsedMs()
            timerStartedAtMs = null
            timerTickerJob?.cancel()
            timerTickerJob = null
            _uiState.update {
                it.copy(
                    isReadTimerRunning = false,
                    readTimerElapsedMs = timerAccumulatedMs
                )
            }
        } else {
            timerStartedAtMs = currentTimeMs()
            _uiState.update {
                it.copy(
                    isReadTimerRunning = true,
                    readTimerElapsedMs = timerAccumulatedMs
                )
            }
            timerTickerJob?.cancel()
            timerTickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(TIMER_TICK_MS)
                    val elapsed = currentTimerElapsedMs()
                    _uiState.update { current ->
                        if (!current.isReadTimerRunning) return@update current
                        current.copy(readTimerElapsedMs = elapsed)
                    }
                }
            }
        }
    }

    fun resetReadTimer() {
        stopAndResetTimerState(clearTarget = false)
    }

    fun setReadTimerStopwatchMode() {
        stopAndResetTimerState(clearTarget = true)
    }

    fun setReadTimerCountdownMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        timerTickerJob?.cancel()
        timerTickerJob = null
        timerAccumulatedMs = 0L
        timerStartedAtMs = null
        _uiState.update {
            it.copy(
                isReadTimerRunning = false,
                readTimerElapsedMs = 0L,
                readTimerTargetMs = clamped * 60_000L
            )
        }
    }

    private fun currentTimerElapsedMs(): Long {
        val startedAt = timerStartedAtMs ?: return timerAccumulatedMs
        val delta = (currentTimeMs() - startedAt).coerceAtLeast(0L)
        return timerAccumulatedMs + delta
    }

    private fun stopAndResetTimerState(clearTarget: Boolean) {
        timerTickerJob?.cancel()
        timerTickerJob = null
        timerAccumulatedMs = 0L
        timerStartedAtMs = null
        _uiState.update { current ->
            current.copy(
                isReadTimerRunning = false,
                readTimerElapsedMs = 0L,
                readTimerTargetMs = if (clearTarget) null else current.readTimerTargetMs
            )
        }
    }

    override fun onCleared() {
        timerTickerJob?.cancel()
        super.onCleared()
    }

    private fun applyDirtyAndHistoryFlags(state: NoteEditorUiState): NoteEditorUiState {
        val isDirty = state.title != baselineSnapshot.title ||
            state.richContent != baselineSnapshot.richContent ||
            state.tags != baselineSnapshot.tags
        val matches = if (state.isInNoteSearchOpen && state.inNoteSearchQuery.isNotBlank()) {
            InNoteSearchHelper.findMatches(
                content = state.richContent,
                query = state.inNoteSearchQuery,
                language = state.language,
                refDisplayStyle = state.bibleRefDisplayStyle
            )
        } else {
            emptyList()
        }
        val clampedMatchIndex = when {
            matches.isEmpty() -> -1
            state.activeInNoteSearchIndex in matches.indices -> state.activeInNoteSearchIndex
            else -> 0
        }
        return state.copy(
            hasUnsavedChanges = isDirty,
            canUndo = undoHistory.canUndo,
            canRedo = undoHistory.canRedo,
            detectedPlainRefs = BibleReferenceDetector.findAll(state.richContent),
            inNoteSearchMatches = matches,
            activeInNoteSearchIndex = clampedMatchIndex
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
            draft.content.links.isNotEmpty() ||
            draft.content.bolds.isNotEmpty() ||
            draft.content.italics.isNotEmpty() ||
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
        private const val TIMER_TICK_MS = 200L
    }
}
