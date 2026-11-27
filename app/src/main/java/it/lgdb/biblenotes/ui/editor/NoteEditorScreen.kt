package it.lgdb.biblenotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.DetectedBibleRef
import it.lgdb.biblenotes.domain.InNoteSearchMatch
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.domain.JwLinkOpener
import it.lgdb.biblenotes.domain.ReadTimerFormatter
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.domain.TransformedRefRange
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.NoteTextSize
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.ui.components.BibleRefInlineText
import it.lgdb.biblenotes.ui.components.computeTextLayoutRangeRects
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    onCloseEditor: () -> Unit,
    onTextSizeChanged: (NoteTextSize) -> Unit = {},
    sharedNoteId: String? = state.activeNoteId,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val strings = remember(state.language) { appStrings(state.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    val keepScreenOnActive = state.keepScreenOnInReadMode && (state.isReadOnlyMode || state.isCorrupted)
    DisposableEffect(view, keepScreenOnActive) {
        view.keepScreenOn = keepScreenOnActive
        onDispose {
            view.keepScreenOn = false
        }
    }

    var showBookWizard by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTextSizeMenu by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
    var linkDialogDraft by remember { mutableStateOf<LinkDialogDraft?>(null) }
    val bodyScrollState = key(state.draftSessionNoteId) { rememberScrollState() }
    var editFocusRequestNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is EditorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                EditorEvent.CloseEditor -> onCloseEditor()
            }
        }
    }

    val openReference: (BibleRefSpan) -> Unit = { ref ->
        val res = JwLinkOpener.openReference(
            context = context,
            ref = ref,
            openWithJwLibrary = state.openWithJwLibrary,
            language = state.language
        )
        viewModel.onBibleReferenceOpenResult(res)
    }

    val openExternalUrl: (String) -> Unit = { url ->
        if (!JwLinkOpener.openExternalUrl(context, url)) {
            viewModel.onExternalLinkOpenFailed()
        }
    }

    val openLinkDialog: () -> Unit = {
        val sel = state.textFieldValue.selection
        val textLen = state.richContent.text.length
        val selStart = minOf(sel.start, sel.end).coerceIn(0, textLen)
        val selEnd = maxOf(sel.start, sel.end).coerceIn(0, textLen)
        val existingLink = SpanAdjuster.findLinkInRange(state.richContent, selStart, selEnd)
        linkDialogDraft = if (selStart == selEnd && existingLink != null) {
            LinkDialogDraft(
                selectionStart = existingLink.start,
                selectionEnd = existingLink.end,
                initialText = state.richContent.text.substring(existingLink.start, existingLink.end),
                initialUrl = existingLink.url,
                isEditingExisting = true
            )
        } else {
            val initialText = if (selStart < selEnd) {
                state.richContent.text.substring(selStart, selEnd)
            } else {
                ""
            }
            LinkDialogDraft(
                selectionStart = selStart,
                selectionEnd = selEnd,
                initialText = initialText,
                initialUrl = existingLink?.url.orEmpty(),
                isEditingExisting = existingLink != null
            )
        }
    }

    val sharedModifier = if (sharedNoteId != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note_card_$sharedNoteId"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        modifier = sharedModifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.saveAndClose(silentIfEmpty = true) },
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backAction,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = when {
                                state.activeNoteId == null -> strings.newNoteTitle
                                state.isReadOnlyMode -> strings.readNoteTitle
                                else -> strings.editNoteTitle
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!state.isCorrupted && state.draftStatus != DraftStatus.IDLE) {
                            Text(
                                text = when (state.draftStatus) {
                                    DraftStatus.SAVING -> strings.draftSaving
                                    DraftStatus.SAVED -> strings.draftSaved
                                    DraftStatus.IDLE -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (!state.isCorrupted) {
                        IconButton(
                            onClick = viewModel::toggleInNoteSearch,
                            enabled = !state.isSaving,
                            modifier = Modifier.testTag("editor_in_note_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = strings.inNoteSearchDesc,
                                tint = if (state.isInNoteSearchOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (state.isReadOnlyMode) {
                            Box {
                                IconButton(
                                    onClick = { showTextSizeMenu = true },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.testTag("editor_text_size_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FormatSize,
                                        contentDescription = strings.textSizeButtonDesc,
                                        tint = if (state.noteTextSize != NoteTextSize.NORMAL) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTextSizeMenu && !state.isSaving,
                                    onDismissRequest = { showTextSizeMenu = false }
                                ) {
                                    NoteTextSize.entries.forEach { sizeOption ->
                                        val label = when (sizeOption) {
                                            NoteTextSize.SMALL -> strings.textSizeSmall
                                            NoteTextSize.NORMAL -> strings.textSizeNormal
                                            NoteTextSize.LARGE -> strings.textSizeLarge
                                            NoteTextSize.EXTRA_LARGE -> strings.textSizeExtraLarge
                                        }
                                        val isSelected = state.noteTextSize == sizeOption
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            },
                                            trailingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                showTextSizeMenu = false
                                                viewModel.setNoteTextSize(sizeOption)
                                                onTextSizeChanged(sizeOption)
                                            },
                                            modifier = Modifier.testTag("editor_text_size_${sizeOption.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            if (state.isLocked) {
                                FilledTonalButton(
                                    onClick = { viewModel.toggleLock() },
                                    enabled = !state.isSaving,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .testTag("editor_topbar_unlock_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(strings.lockedBadgeLabel)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { viewModel.enterEditMode() },
                                    enabled = !state.isSaving,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .testTag("enter_edit_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(strings.editMode)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.toggleReadOnlyMode() },
                                enabled = !state.isSaving,
                                modifier = Modifier.testTag("toggle_read_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = strings.readMode
                                )
                            }
                            TextButton(
                                onClick = { viewModel.saveAndClose(silentIfEmpty = false) },
                                enabled = !state.isSaving,
                                modifier = Modifier.testTag("save_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.save, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            enabled = !state.isSaving,
                            modifier = Modifier.testTag("editor_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = strings.moreOptionsDesc
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu && !state.isSaving,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            if (!state.isCorrupted && !state.isReadOnlyMode) {
                                val activeLink = SpanAdjuster.findLinkInRange(
                                    state.richContent,
                                    state.textFieldValue.selection.start,
                                    state.textFieldValue.selection.end
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (activeLink != null) {
                                                strings.editLinkAction
                                            } else {
                                                strings.addLinkAction
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Link, contentDescription = null)
                                    },
                                    enabled = !state.isSaving,
                                    onClick = {
                                        showOverflowMenu = false
                                        openLinkDialog()
                                    },
                                    modifier = Modifier.testTag("overflow_add_link_item")
                                )
                            }
                            if (!state.isCorrupted) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.isLocked) {
                                                strings.unlockNoteAction
                                            } else {
                                                strings.lockNoteAction
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (state.isLocked) {
                                                Icons.Filled.LockOpen
                                            } else {
                                                Icons.Filled.Lock
                                            },
                                            contentDescription = null
                                        )
                                    },
                                    enabled = !state.isSaving,
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.toggleLock()
                                    },
                                    modifier = Modifier.testTag("overflow_toggle_lock_item")
                                )
                            }
                            if (!state.isCorrupted && (!state.isReadOnlyMode || state.hasUnsavedChanges)) {
                                DropdownMenuItem(
                                    text = { Text(strings.discardChangesAction) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                    },
                                    enabled = !state.isSaving,
                                    onClick = {
                                        showOverflowMenu = false
                                        if (state.hasUnsavedChanges) {
                                            showDiscardConfirm = true
                                        } else {
                                            viewModel.discardAndClose()
                                        }
                                    }
                                )
                            }
                            if (state.activeNoteId != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = strings.delete,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!state.isReadOnlyMode && !state.isCorrupted) {
                CompactEditorToolbar(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    detectedRefs = state.detectedPlainRefs,
                    primaryDetectedRef = state.primaryDetectedRef,
                    language = state.language,
                    refDisplayStyle = state.bibleRefDisplayStyle,
                    strings = strings,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onConvertDetectedRef = viewModel::convertDetectedBibleRef,
                    onConvertAllDetectedRefs = viewModel::convertAllDetectedBibleRefs,
                    onAddBibleRef = { showBookWizard = true },
                    onAddTagClick = { showTagInput = !showTagInput }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            AnimatedVisibility(
                visible = state.isInNoteSearchOpen && !state.isCorrupted,
                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
            ) {
                InNoteSearchBar(
                    query = state.inNoteSearchQuery,
                    matchesCount = state.inNoteSearchMatches.size,
                    activeIndex = state.activeInNoteSearchIndex,
                    strings = strings,
                    onQueryChange = viewModel::onInNoteSearchQueryChanged,
                    onPrevious = viewModel::prevInNoteSearchMatch,
                    onNext = viewModel::nextInNoteSearchMatch,
                    onClose = viewModel::closeInNoteSearch
                )
            }

            if (state.isCorrupted) {
                CorruptedNoteRecoveryBanner(
                    rawContent = state.rawCorruptedJson.orEmpty(),
                    strings = strings,
                    onCopyRaw = {
                        clipboardManager.setText(AnnotatedString(state.rawCorruptedJson.orEmpty()))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(strings.copiedToClipboard)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!state.isReadOnlyMode || state.title.isNotBlank()) {
                BasicTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChanged,
                    enabled = !state.isReadOnlyMode && !state.isLocked && !state.isCorrupted && !state.isSaving,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Column {
                            Box(modifier = Modifier.padding(vertical = 6.dp)) {
                                if (state.title.isEmpty() && !state.isReadOnlyMode) {
                                    Text(
                                        text = strings.titlePlaceholder,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                innerTextField()
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main body area occupies all remaining vertical space; tags live at the bottom inside scroll
            RichNoteBodyArea(
                content = state.richContent,
                textFieldValue = state.textFieldValue,
                tags = state.tags,
                language = state.language,
                refDisplayStyle = state.bibleRefDisplayStyle,
                textSize = state.noteTextSize,
                isInNoteSearchOpen = state.isInNoteSearchOpen,
                searchMatches = state.inNoteSearchMatches,
                activeSearchMatchIndex = state.activeInNoteSearchIndex,
                strings = strings,
                isReadOnly = state.isReadOnlyMode || state.isCorrupted,
                isEnabled = !state.isSaving,
                isSelectionBold = state.isSelectionBold,
                isSelectionItalic = state.isSelectionItalic,
                hasConvertibleBibleRef = state.detectedRefInSelection != null,
                placeholder = strings.bodyPlaceholder,
                bodyScrollState = bodyScrollState,
                editFocusRequestNonce = editFocusRequestNonce,
                onValueChange = viewModel::onBodyValueChanged,
                onApplyHighlight = viewModel::applyHighlight,
                onToggleBold = viewModel::toggleBold,
                onToggleItalic = viewModel::toggleItalic,
                onAddLink = openLinkDialog,
                onConvertSelectionRef = {
                    state.detectedRefInSelection?.let(viewModel::convertDetectedBibleRef)
                },
                onRemoveTag = viewModel::removeTag,
                onReferenceTap = openReference,
                onLinkTap = openExternalUrl,
                onEnterEditMode = if (state.isReadOnlyMode && !state.isLocked && !state.isCorrupted && !state.isSaving) {
                    { cursorOffset ->
                        viewModel.enterEditMode(cursorOffset)
                        editFocusRequestNonce++
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (state.isReadOnlyMode && !state.isCorrupted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = if (state.isLocked) strings.lockedReadOnlyHint else strings.tapToOpenHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isLocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    ReadTimerPill(
                        elapsedMs = state.readTimerElapsedMs,
                        targetDurationMs = state.readTimerTargetMs,
                        isRunning = state.isReadTimerRunning,
                        enabled = !state.isSaving,
                        strings = strings,
                        onToggleTimer = viewModel::toggleReadTimer,
                        onResetTimer = viewModel::resetReadTimer,
                        onSelectStopwatch = viewModel::setReadTimerStopwatchMode,
                        onSelectCountdownMinutes = viewModel::setReadTimerCountdownMinutes
                    )

                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(enabled = !state.isSaving) { viewModel.toggleLock() }
                            .testTag("editor_lock_pill")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (state.isLocked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (state.isLocked) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isLocked) {
                                        Icons.Filled.Lock
                                    } else {
                                        Icons.Filled.LockOpen
                                    },
                                    contentDescription = if (state.isLocked) {
                                        strings.unlockNoteAction
                                    } else {
                                        strings.lockNoteAction
                                    },
                                    tint = if (state.isLocked) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (state.isLocked) {
                                        strings.lockedBadgeLabel
                                    } else {
                                        strings.unlockedBadgeLabel
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (state.isLocked) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !state.isReadOnlyMode && !state.isCorrupted && showTagInput,
                enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TagInputWithSuggestions(
                        tagDraft = tagDraft,
                        onTagDraftChange = { tagDraft = it },
                        existingTags = state.tags,
                        availableTags = state.availableTags,
                        placeholder = strings.addTagPlaceholder,
                        addTagDesc = strings.addTag,
                        onAddTag = { chosen ->
                            viewModel.addTag(chosen)
                            tagDraft = ""
                            showTagInput = false
                        }
                    )
                }
            }
        }
    }

    if (showBookWizard) {
        BibleReferenceWizardDialog(
            language = state.language,
            strings = strings,
            onDismiss = { showBookWizard = false },
            onConfirm = { book, chapter, verseSpec ->
                showBookWizard = false
                viewModel.insertBibleReference(book, chapter, verseSpec)
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.confirmDeleteTitle) },
            text = { Text(strings.confirmDeleteBody) },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancelAction)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAndClose()
                    }
                ) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(strings.confirmDiscardTitle) },
            text = { Text(strings.confirmDiscardBody) },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(strings.cancelAction)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = {
                        showDiscardConfirm = false
                        viewModel.discardAndClose()
                    }
                ) {
                    Text(strings.confirmAction)
                }
            }
        )
    }

    linkDialogDraft?.let { draft ->
        LinkEditorDialog(
            draft = draft,
            strings = strings,
            onDismiss = { linkDialogDraft = null },
            onSave = { displayText, url ->
                linkDialogDraft = null
                viewModel.applyLink(
                    selectionStart = draft.selectionStart,
                    selectionEnd = draft.selectionEnd,
                    url = url,
                    displayText = displayText
                )
            },
            onRemove = {
                linkDialogDraft = null
                viewModel.applyLink(
                    selectionStart = draft.selectionStart,
                    selectionEnd = draft.selectionEnd,
                    url = null
                )
            },
            onOpenUrl = { url ->
                openExternalUrl(url)
            }
        )
    }
}

private data class LinkDialogDraft(
    val selectionStart: Int,
    val selectionEnd: Int,
    val initialText: String,
    val initialUrl: String,
    val isEditingExisting: Boolean
)

@Composable
private fun LinkEditorDialog(
    draft: LinkDialogDraft,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (displayText: String, url: String) -> Unit,
    onRemove: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var linkText by remember(draft) { mutableStateOf(draft.initialText) }
    var linkUrl by remember(draft) { mutableStateOf(draft.initialUrl) }
    var showValidationError by remember(draft) { mutableStateOf(false) }

    val tryConfirm = {
        if (SpanAdjuster.isValidLinkUrl(linkUrl)) {
            onSave(linkText, linkUrl)
        } else {
            showValidationError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (draft.isEditingExisting) strings.editLinkTitle else strings.addLinkTitle)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text(strings.linkTextLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_text_input")
                )
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = {
                        linkUrl = it
                        if (showValidationError) showValidationError = false
                    },
                    label = { Text(strings.linkUrlLabel) },
                    singleLine = true,
                    isError = showValidationError,
                    supportingText = if (showValidationError) {
                        { Text(strings.invalidLinkUrlError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { tryConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("link_url_input")
                )
                if (draft.isEditingExisting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                if (SpanAdjuster.isValidLinkUrl(linkUrl)) {
                                    onOpenUrl(linkUrl)
                                } else {
                                    showValidationError = true
                                }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("link_open_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.openLinkAction)
                        }
                        TextButton(
                            onClick = onRemove,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("link_remove_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.removeLinkAction,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
        confirmButton = {
            TextButton(
                onClick = tryConfirm,
                modifier = Modifier.testTag("link_save_button")
            ) {
                Text(strings.confirmAction)
            }
        }
    )
}

@Composable
private fun CorruptedNoteRecoveryBanner(
    rawContent: String,
    strings: AppStrings,
    onCopyRaw: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = strings.corruptedNoteTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = strings.corruptedNoteDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (rawContent.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Text(
                        text = rawContent,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    )
                }
                OutlinedButton(
                    onClick = onCopyRaw,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.copyRawContentAction)
                }
            }
        }
    }
}

private data class EditableChipBox(
    val ref: BibleRefSpan,
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val iconSpacePx: Float,
    val showIcon: Boolean
)

private class TextLayoutHolder {
    var value: TextLayoutResult? = null
}

private fun computeEditableChipBoxes(
    layout: TextLayoutResult,
    chipRanges: List<TransformedRefRange>,
    chipHeightPx: Float,
    minVerticalInsetPx: Float,
    desiredLeftGapPx: Float,
    desiredRightPadPx: Float
): List<EditableChipBox> {
    val layoutLength = layout.layoutInput.text.length
    if (layoutLength == 0 || chipRanges.isEmpty()) return emptyList()
    return buildList {
        chipRanges.forEach { range ->
            if (range.transformedStart >= 0 &&
                range.transformedEnd <= layoutLength &&
                range.transformedStart < range.transformedEnd
            ) {
                val startChar = range.transformedStart
                val endChar = range.transformedEnd - 1
                val startLine = layout.getLineForOffset(startChar)
                val endLine = layout.getLineForOffset(endChar)
                for (line in startLine..endLine) {
                    val segStart = if (line == startLine) startChar else layout.getLineStart(line)
                    val segEnd = if (line == endLine) {
                        endChar
                    } else {
                        (layout.getLineEnd(line, visibleEnd = true) - 1).coerceAtLeast(segStart)
                    }
                    if (segStart <= segEnd && segEnd < layoutLength) {
                        val startBox = layout.getBoundingBox(segStart)
                        val endBox = layout.getBoundingBox(segEnd)
                        val lineTop = layout.getLineTop(line)
                        val lineBottom = layout.getLineBottom(line)
                        val maxAllowedHeight = (lineBottom - lineTop - minVerticalInsetPx * 2f)
                            .coerceAtLeast(16f)
                        val effectiveHeight = minOf(chipHeightPx, maxAllowedHeight)
                        val lineCenterY = (lineTop + lineBottom) / 2f
                        val minTop = lineTop + minVerticalInsetPx
                        val maxTop = (lineBottom - minVerticalInsetPx - effectiveHeight)
                            .coerceAtLeast(minTop)
                        val topPx = (lineCenterY - effectiveHeight / 2f).coerceIn(minTop, maxTop)

                        val rawLeftPx = minOf(startBox.left, endBox.left)
                        val rawRightPx = maxOf(startBox.right, endBox.right)
                        val textStartInLine = range.textStart in segStart..segEnd &&
                            layout.getLineForOffset(range.textStart) == line
                        val textEndOffset = (range.textEnd - 1).coerceAtLeast(range.textStart)
                        val textEndInLine = textEndOffset in segStart..segEnd &&
                            layout.getLineForOffset(textEndOffset) == line

                        val leftPx = if (textStartInLine) {
                            val textStartX = layout.getBoundingBox(range.textStart).left
                            maxOf(rawLeftPx, textStartX - desiredLeftGapPx)
                        } else {
                            rawLeftPx
                        }
                        val rightPx = if (textEndInLine) {
                            val textEndX = layout.getBoundingBox(textEndOffset).right
                            minOf(rawRightPx, textEndX + desiredRightPadPx)
                        } else {
                            rawRightPx
                        }
                        val iconSpacePx = if (textStartInLine) {
                            (layout.getBoundingBox(range.textStart).left - leftPx).coerceAtLeast(0f)
                        } else {
                            0f
                        }
                        add(
                            EditableChipBox(
                                ref = range.ref,
                                leftPx = leftPx,
                                topPx = topPx,
                                widthPx = (rightPx - leftPx).coerceAtLeast(1f),
                                heightPx = effectiveHeight,
                                iconSpacePx = iconSpacePx,
                                showIcon = textStartInLine
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteTagsFooter(
    tags: List<String>,
    editable: Boolean,
    onRemoveTag: (String) -> Unit
) {
    if (tags.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 200))
            .testTag("note_tags_footer")
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            tags.forEach { tag ->
                key(tag) {
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text("#$tag") },
                        trailingIcon = if (editable) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveTag(tag) }
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InNoteSearchBar(
    query: String,
    matchesCount: Int,
    activeIndex: Int,
    strings: AppStrings,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        yield()
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("in_note_search_bar")
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = strings.inNoteSearchPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (matchesCount > 0) onNext()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("in_note_search_input")
                    )
                }

                if (query.isNotEmpty()) {
                    val countLabel = if (matchesCount > 0 && activeIndex >= 0) {
                        "${activeIndex + 1}/$matchesCount"
                    } else {
                        "0/0"
                    }
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (matchesCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .testTag("in_note_search_counter")
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    enabled = matchesCount > 0,
                    modifier = Modifier.testTag("in_note_search_prev")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = strings.inNoteSearchPrevDesc
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchesCount > 0,
                    modifier = Modifier.testTag("in_note_search_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = strings.inNoteSearchNextDesc
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("in_note_search_close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.closeAction
                    )
                }
            }
        }
    }
}

@Composable
private fun RichNoteBodyArea(
    content: RichNoteContent,
    textFieldValue: TextFieldValue,
    tags: List<String>,
    language: AppLanguage,
    refDisplayStyle: BibleRefDisplayStyle,
    textSize: NoteTextSize,
    isInNoteSearchOpen: Boolean,
    searchMatches: List<InNoteSearchMatch>,
    activeSearchMatchIndex: Int,
    strings: AppStrings,
    isReadOnly: Boolean,
    isEnabled: Boolean,
    isSelectionBold: Boolean,
    isSelectionItalic: Boolean,
    hasConvertibleBibleRef: Boolean,
    placeholder: String,
    bodyScrollState: ScrollState,
    editFocusRequestNonce: Int,
    onValueChange: (TextFieldValue) -> Unit,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onAddLink: () -> Unit,
    onConvertSelectionRef: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onReferenceTap: (BibleRefSpan) -> Unit,
    onLinkTap: (String) -> Unit,
    onEnterEditMode: ((cursorOffset: Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    val chipScale = textSize.chipFontSp / 13.5f
    val bodyLineHeight = with(density) {
        maxOf(textSize.lineHeightDp.sp.toPx(), textSize.lineHeightDp.dp.toPx()).toSp()
    }
    val bodyTextStyle = remember(textColor, bodyLineHeight, textSize) {
        TextStyle(
            color = textColor,
            fontSize = textSize.bodySp.sp,
            lineHeight = bodyLineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None
            )
        )
    }

    if (isReadOnly) {
        var readTextLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

        LaunchedEffect(
            isInNoteSearchOpen,
            searchMatches,
            activeSearchMatchIndex,
            readTextLayout,
            content,
            language,
            refDisplayStyle
        ) {
            if (!isInNoteSearchOpen || activeSearchMatchIndex !in searchMatches.indices) return@LaunchedEffect
            val layout = readTextLayout ?: return@LaunchedEffect
            val match = searchMatches[activeSearchMatchIndex]
            val range = SpanAdjuster.originalRangeToInlineAnnotatedRange(
                content = content,
                origStart = match.start,
                origEnd = match.end,
                language = language,
                refDisplayStyle = refDisplayStyle
            )
            if (range.isEmpty()) return@LaunchedEffect
            val rects = computeTextLayoutRangeRects(layout, range.first, range.last + 1)
            if (rects.isEmpty()) return@LaunchedEffect
            val padPx = with(density) { 12.dp.toPx() }
            val viewportHeight = bodyScrollState.viewportSize.toFloat()
            if (viewportHeight <= 0f) return@LaunchedEffect
            val matchTop = rects.minOf { it.top } + padPx
            val matchBottom = rects.maxOf { it.bottom } + padPx
            val margin = with(density) { 24.dp.toPx() }
            val currentScroll = bodyScrollState.value.toFloat()
            if (matchTop < currentScroll + margin || matchBottom > currentScroll + viewportHeight - margin) {
                val target = ((matchTop + matchBottom) / 2f - viewportHeight / 2f)
                    .roundToInt()
                    .coerceIn(0, bodyScrollState.maxValue)
                bodyScrollState.animateScrollTo(target)
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = modifier
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val minReadBodyHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
                val tapToEditModifier = if (onEnterEditMode != null) {
                    Modifier.pointerInput(onEnterEditMode, bodyScrollState, content, language, refDisplayStyle) {
                        detectTapGestures(
                            onTap = { tapPos ->
                                if (!bodyScrollState.isScrollInProgress) {
                                    val layout = readTextLayout
                                    val cursorOffset = if (layout != null && content.text.isNotEmpty()) {
                                        val annOffset = layout.getOffsetForPosition(tapPos)
                                        SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(
                                            content = content,
                                            language = language,
                                            annotatedOffset = annOffset,
                                            refDisplayStyle = refDisplayStyle
                                        )
                                    } else {
                                        0
                                    }
                                    onEnterEditMode(cursorOffset)
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(bodyScrollState)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(tapToEditModifier)
                    ) {
                        BibleRefInlineText(
                            content = content,
                            language = language,
                            refDisplayStyle = refDisplayStyle,
                            textSize = textSize,
                            placeholder = placeholder,
                            style = bodyTextStyle,
                            searchMatches = if (isInNoteSearchOpen) searchMatches else emptyList(),
                            activeSearchMatchIndex = if (isInNoteSearchOpen) activeSearchMatchIndex else -1,
                            onReferenceClick = onReferenceTap,
                            onLinkClick = onLinkTap,
                            onTextLayout = { readTextLayout = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(
                                    minHeight = if (tags.isEmpty()) minReadBodyHeight else 64.dp
                                )
                        )
                    }

                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        NoteTagsFooter(
                            tags = tags,
                            editable = false,
                            onRemoveTag = {}
                        )
                    }
                }
            }
        }
    } else {
        val refTextColor = MaterialTheme.colorScheme.onSurface
        val linkTextColor = MaterialTheme.colorScheme.primary
        val chipContainerColor = MaterialTheme.colorScheme.surface
        val chipOutlineColor = MaterialTheme.colorScheme.outline
        val chipIconTint = MaterialTheme.colorScheme.primary
        val bookIconPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.MenuBook)
        val bodyFocusRequester = remember { FocusRequester() }

        LaunchedEffect(editFocusRequestNonce) {
            if (editFocusRequestNonce > 0) {
                yield()
                bodyFocusRequester.requestFocus()
            }
        }

        val transformedNote = remember(content, refTextColor, linkTextColor, language, refDisplayStyle, textSize) {
            SpanAdjuster.buildEditableTransformedNote(
                content = content,
                refTextColor = refTextColor,
                language = language,
                linkTextColor = linkTextColor,
                refDisplayStyle = refDisplayStyle,
                textSize = textSize
            )
        }
        val visualTransformation = remember(transformedNote) {
            VisualTransformation {
                TransformedText(transformedNote.annotatedString, transformedNote.offsetMapping)
            }
        }
        val layoutHolder = remember { TextLayoutHolder() }
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        val legacyToolbarController = remember { EditorSelectionToolbarController() }
        var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var textFieldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var bodyBoundsInWindow by remember { mutableStateOf(Rect.Zero) }
        val contextMenuProvider = remember { EditorTextContextMenuProvider() }
        val scrollDismissConnection = remember(contextMenuProvider, legacyToolbarController) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (abs(available.y) > 0.5f || abs(available.x) > 0.5f) {
                        contextMenuProvider.cancel()
                        legacyToolbarController.hide()
                    }
                    return Offset.Zero
                }
            }
        }

        val isSelectionVisibleInViewport = remember(
            textFieldValue.selection,
            textLayoutResult,
            transformedNote,
            textFieldCoordinates,
            bodyBoundsInWindow,
            bodyScrollState.value
        ) {
            if (textFieldValue.selection.collapsed) return@remember false
            if (bodyBoundsInWindow.width <= 0f || bodyBoundsInWindow.height <= 0f) return@remember false
            val layout = textLayoutResult
            val coords = textFieldCoordinates
            if (layout == null || coords == null || !coords.isAttached) return@remember true
            if (layout.layoutInput.text.text != transformedNote.annotatedString.text) return@remember true

            val selStartOrig = minOf(textFieldValue.selection.start, textFieldValue.selection.end)
            val selEndOrig = maxOf(textFieldValue.selection.start, textFieldValue.selection.end)
            val transLen = layout.layoutInput.text.length
            if (transLen == 0) return@remember false

            val startTrans = transformedNote.offsetMapping
                .originalToTransformed(selStartOrig)
                .coerceIn(0, transLen - 1)
            val endTrans = (transformedNote.offsetMapping.originalToTransformed(selEndOrig) - 1)
                .coerceIn(startTrans, transLen - 1)

            val startBox = layout.getBoundingBox(startTrans)
            val endBox = layout.getBoundingBox(endTrans)
            val localRect = Rect(
                left = minOf(startBox.left, endBox.left),
                top = minOf(startBox.top, endBox.top),
                right = maxOf(startBox.right, endBox.right),
                bottom = maxOf(startBox.bottom, endBox.bottom)
            )
            val selectionInWindow = localRect.translate(coords.localToWindow(Offset.Zero))
            isSelectionRectVisibleInViewport(selectionInWindow, bodyBoundsInWindow)
        }

        LaunchedEffect(bodyScrollState) {
            var lastScrollValue = bodyScrollState.value
            snapshotFlow { bodyScrollState.isScrollInProgress to bodyScrollState.value }
                .collect { (scrolling, value) ->
                    if (scrolling || value != lastScrollValue) {
                        lastScrollValue = value
                        contextMenuProvider.cancel()
                        legacyToolbarController.hide()
                    }
                }
        }
        LaunchedEffect(isEnabled, isReadOnly) {
            if (!isEnabled || isReadOnly) {
                contextMenuProvider.cancel()
                legacyToolbarController.hide()
            }
        }
        LaunchedEffect(textFieldValue.selection.collapsed, isSelectionVisibleInViewport) {
            if (textFieldValue.selection.collapsed || !isSelectionVisibleInViewport) {
                contextMenuProvider.cancel()
                legacyToolbarController.hide()
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                contextMenuProvider.cancel()
                legacyToolbarController.hide()
            }
        }

        val chipBoxes = remember(transformedNote, textLayoutResult, density, textSize) {
            val layout = textLayoutResult ?: return@remember emptyList()
            if (layout.layoutInput.text.text != transformedNote.annotatedString.text) {
                return@remember emptyList()
            }
            with(density) {
                computeEditableChipBoxes(
                    layout = layout,
                    chipRanges = transformedNote.chipRanges,
                    chipHeightPx = textSize.chipHeightDp.dp.toPx(),
                    minVerticalInsetPx = 1.dp.toPx(),
                    desiredLeftGapPx = (29f * chipScale).dp.toPx(),
                    desiredRightPadPx = (9f * chipScale).dp.toPx()
                )
            }
        }

        LaunchedEffect(
            isInNoteSearchOpen,
            searchMatches,
            activeSearchMatchIndex,
            textLayoutResult,
            transformedNote,
            chipBoxes
        ) {
            if (!isInNoteSearchOpen || activeSearchMatchIndex !in searchMatches.indices) return@LaunchedEffect
            val layout = textLayoutResult ?: return@LaunchedEffect
            if (layout.layoutInput.text.text != transformedNote.annotatedString.text) return@LaunchedEffect
            val match = searchMatches[activeSearchMatchIndex]
            val viewportHeight = bodyScrollState.viewportSize.toFloat()
            if (viewportHeight <= 0f) return@LaunchedEffect
            val padPx = with(density) { 12.dp.toPx() }
            val matchBounds: Pair<Float, Float>? = if (match.isBibleRef) {
                val matchingBoxes = chipBoxes.filter { box ->
                    box.ref.start == match.start && box.ref.end == match.end
                }
                if (matchingBoxes.isNotEmpty()) {
                    matchingBoxes.minOf { it.topPx } to matchingBoxes.maxOf { it.topPx + it.heightPx }
                } else {
                    null
                }
            } else {
                val transLen = layout.layoutInput.text.length
                val ts = transformedNote.offsetMapping.originalToTransformed(match.start).coerceIn(0, transLen)
                val te = transformedNote.offsetMapping.originalToTransformed(match.end).coerceIn(ts, transLen)
                val rects = computeTextLayoutRangeRects(layout, ts, te)
                if (rects.isNotEmpty()) {
                    rects.minOf { it.top } to rects.maxOf { it.bottom }
                } else {
                    null
                }
            }
            if (matchBounds != null) {
                val matchTop = matchBounds.first + padPx
                val matchBottom = matchBounds.second + padPx
                val margin = with(density) { 24.dp.toPx() }
                val currentScroll = bodyScrollState.value.toFloat()
                if (matchTop < currentScroll + margin || matchBottom > currentScroll + viewportHeight - margin) {
                    val target = ((matchTop + matchBottom) / 2f - viewportHeight / 2f)
                        .roundToInt()
                        .coerceIn(0, bodyScrollState.maxValue)
                    bodyScrollState.animateScrollTo(target)
                }
            }
        }

        val activeSearchFillColor = Color(0xFFFFB74D).copy(alpha = 0.36f)
        val inactiveSearchFillColor = Color(0xFF4FC3F7).copy(alpha = 0.20f)
        val activeSearchBorderColor = Color(0xFFE65100)
        val inactiveSearchBorderColor = Color(0xFF0288D1).copy(alpha = 0.72f)
        val effectiveSearchMatches = if (isInNoteSearchOpen) searchMatches else emptyList()
        val effectiveActiveSearchIndex = if (isInNoteSearchOpen) activeSearchMatchIndex else -1

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = modifier
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollDismissConnection)
                    .onGloballyPositioned { coordinates ->
                        anchorCoordinates = coordinates
                        bodyBoundsInWindow = coordinates.boundsInWindow()
                    }
            ) {
                val minBodyHeight = (maxHeight - 24.dp).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(contextMenuProvider, legacyToolbarController) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val touchSlop = viewConfiguration.touchSlop
                                var dismissed = false
                                while (!dismissed) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val tracked = event.changes.firstOrNull { it.id == down.id }
                                    if (tracked == null || !tracked.pressed) {
                                        break
                                    }
                                    if ((tracked.position - down.position).getDistance() > touchSlop) {
                                        contextMenuProvider.cancel()
                                        legacyToolbarController.hide()
                                        dismissed = true
                                    }
                                }
                            }
                        }
                        .verticalScroll(bodyScrollState)
                        .padding(12.dp)
                ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = bodyTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }

                    // Editable BasicTextField draws chip surfaces, borders, and book icons synchronously
                    // during its Draw phase using the current frame's TextLayoutResult (zero flicker/lag).
                    // Outer drawWithContent overlays non-destructive search highlights without altering saved formatting.
                    CompositionLocalProvider(
                        LocalTextContextMenuToolbarProvider provides contextMenuProvider,
                        LocalTextContextMenuDropdownProvider provides contextMenuProvider,
                        LocalTextToolbar provides legacyToolbarController
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onValueChange,
                            enabled = isEnabled,
                            textStyle = bodyTextStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = visualTransformation,
                            onTextLayout = { layout ->
                                layoutHolder.value = layout
                                textLayoutResult = layout
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(
                                    minHeight = if (tags.isEmpty()) minBodyHeight else 120.dp
                                )
                                .focusRequester(bodyFocusRequester)
                                .onGloballyPositioned { coords ->
                                    textFieldCoordinates = coords
                                }
                                .drawWithContent {
                                    val layout = layoutHolder.value
                                    val layoutValid = layout != null &&
                                        layout.layoutInput.text.text == transformedNote.annotatedString.text
                                    val transLen = layout?.layoutInput?.text?.length ?: 0

                                    if (layoutValid && effectiveSearchMatches.isNotEmpty() && layout != null) {
                                        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        effectiveSearchMatches.forEachIndexed { idx, match ->
                                            if (!match.isBibleRef) {
                                                val ts = transformedNote.offsetMapping
                                                    .originalToTransformed(match.start)
                                                    .coerceIn(0, transLen)
                                                val te = transformedNote.offsetMapping
                                                    .originalToTransformed(match.end)
                                                    .coerceIn(ts, transLen)
                                                val rects = computeTextLayoutRangeRects(layout, ts, te)
                                                val fill = if (idx == effectiveActiveSearchIndex) {
                                                    activeSearchFillColor
                                                } else {
                                                    inactiveSearchFillColor
                                                }
                                                rects.forEach { r ->
                                                    drawRoundRect(
                                                        color = fill,
                                                        topLeft = Offset(r.left, r.top),
                                                        size = Size(r.width, r.height),
                                                        cornerRadius = corner
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    drawContent()

                                    if (layoutValid && effectiveSearchMatches.isNotEmpty() && layout != null) {
                                        val activeStrokePx = 1.8.dp.toPx()
                                        val inactiveStrokePx = 1.dp.toPx()
                                        val plainCorner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                        val chipCorner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                        val chipPadPx = 1.dp.toPx()

                                        val editChipRects = computeEditableChipBoxes(
                                            layout = layout,
                                            chipRanges = transformedNote.chipRanges,
                                            chipHeightPx = textSize.chipHeightDp.dp.toPx(),
                                            minVerticalInsetPx = 1.dp.toPx(),
                                            desiredLeftGapPx = (29f * chipScale).dp.toPx(),
                                            desiredRightPadPx = (9f * chipScale).dp.toPx()
                                        )

                                        effectiveSearchMatches.forEachIndexed { idx, match ->
                                            val isActive = idx == effectiveActiveSearchIndex
                                            val borderColor = if (isActive) {
                                                activeSearchBorderColor
                                            } else {
                                                inactiveSearchBorderColor
                                            }
                                            val strokeWidth = if (isActive) activeStrokePx else inactiveStrokePx
                                            if (match.isBibleRef) {
                                                editChipRects.forEach { box ->
                                                    if (box.ref.start == match.start && box.ref.end == match.end) {
                                                        drawRoundRect(
                                                            color = borderColor,
                                                            topLeft = Offset(box.leftPx - chipPadPx, box.topPx - chipPadPx),
                                                            size = Size(box.widthPx + chipPadPx * 2f, box.heightPx + chipPadPx * 2f),
                                                            cornerRadius = chipCorner,
                                                            style = Stroke(width = strokeWidth)
                                                        )
                                                    }
                                                }
                                            } else {
                                                val ts = transformedNote.offsetMapping
                                                    .originalToTransformed(match.start)
                                                    .coerceIn(0, transLen)
                                                val te = transformedNote.offsetMapping
                                                    .originalToTransformed(match.end)
                                                    .coerceIn(ts, transLen)
                                                val rects = computeTextLayoutRangeRects(layout, ts, te)
                                                rects.forEach { r ->
                                                    drawRoundRect(
                                                        color = borderColor,
                                                        topLeft = Offset(r.left, r.top),
                                                        size = Size(r.width, r.height),
                                                        cornerRadius = plainCorner,
                                                        style = Stroke(width = strokeWidth)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                .drawBehind {
                                    val layout = layoutHolder.value ?: return@drawBehind
                                    if (layout.layoutInput.text.text != transformedNote.annotatedString.text) {
                                        return@drawBehind
                                    }
                                    val rects = computeEditableChipBoxes(
                                        layout = layout,
                                        chipRanges = transformedNote.chipRanges,
                                        chipHeightPx = textSize.chipHeightDp.dp.toPx(),
                                        minVerticalInsetPx = 1.dp.toPx(),
                                        desiredLeftGapPx = (29f * chipScale).dp.toPx(),
                                        desiredRightPadPx = (9f * chipScale).dp.toPx()
                                    )
                                    val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    val strokeWidthPx = 1.dp.toPx()
                                    val padStartPx = (8f * chipScale).dp.toPx()
                                    val padAfterIconPx = (6f * chipScale).dp.toPx()
                                    val maxIconPx = (15f * chipScale).dp.toPx()
                                    val minIconPx = 8.dp.toPx()

                                    rects.forEach { box ->
                                        val topLeft = Offset(box.leftPx, box.topPx)
                                        val boxSize = Size(box.widthPx, box.heightPx)
                                        drawRoundRect(
                                            color = chipContainerColor,
                                            topLeft = topLeft,
                                            size = boxSize,
                                            cornerRadius = cornerRadius
                                        )
                                        drawRoundRect(
                                            color = chipOutlineColor,
                                            topLeft = topLeft,
                                            size = boxSize,
                                            cornerRadius = cornerRadius,
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                        if (box.showIcon) {
                                            val availableForIcon = (box.iconSpacePx - padStartPx - padAfterIconPx)
                                                .coerceAtLeast(minIconPx)
                                            val iconSizePx = minOf(
                                                maxIconPx,
                                                availableForIcon,
                                                (box.heightPx - 4.dp.toPx()).coerceAtLeast(minIconPx)
                                            )
                                            val iconLeft = (box.leftPx + box.iconSpacePx - padAfterIconPx - iconSizePx)
                                                .coerceAtLeast(box.leftPx + 4.dp.toPx())
                                            val iconTop = box.topPx + (box.heightPx - iconSizePx) / 2f
                                            translate(left = iconLeft, top = iconTop) {
                                                with(bookIconPainter) {
                                                    draw(
                                                        size = Size(iconSizePx, iconSizePx),
                                                        colorFilter = ColorFilter.tint(chipIconTint)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }

                    // Clickable tap overlays positioned on scripture boxes to open verses directly
                    chipBoxes.forEach { box ->
                        val widthDp = with(density) { box.widthPx.toDp() }
                        val heightDp = with(density) { box.heightPx.toDp() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(box.leftPx.roundToInt(), box.topPx.roundToInt()) }
                                .size(widthDp, heightDp)
                                .clip(RoundedCornerShape(8.dp))
                                .semantics {
                                    contentDescription = JwLinkBuilder.formatReference(
                                        box.ref,
                                        language,
                                        refDisplayStyle
                                    )
                                }
                                .clickable(enabled = isEnabled) { onReferenceTap(box.ref) }
                        )
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NoteTagsFooter(
                        tags = tags,
                        editable = true,
                        onRemoveTag = onRemoveTag
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 72.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                bodyFocusRequester.requestFocus()
                            }
                    )
                }
            }

            FloatingEditorContextMenu(
                provider = contextMenuProvider,
                anchorCoordinates = anchorCoordinates,
                bodyBoundsInWindow = bodyBoundsInWindow,
                hasTextSelection = !textFieldValue.selection.collapsed,
                isSelectionVisibleInViewport = isSelectionVisibleInViewport,
                isScrolling = bodyScrollState.isScrollInProgress,
                isSelectionBold = isSelectionBold,
                isSelectionItalic = isSelectionItalic,
                hasConvertibleBibleRef = hasConvertibleBibleRef,
                language = language,
                strings = strings,
                onApplyHighlight = onApplyHighlight,
                onToggleBold = onToggleBold,
                onToggleItalic = onToggleItalic,
                onAddLink = onAddLink,
                onConvertSelectionRef = onConvertSelectionRef
            )

            FloatingEditorSelectionMenu(
                controller = legacyToolbarController,
                bodyBoundsInWindow = bodyBoundsInWindow,
                hasTextSelection = !textFieldValue.selection.collapsed,
                isSelectionVisibleInViewport = isSelectionVisibleInViewport,
                isScrolling = bodyScrollState.isScrollInProgress,
                isSelectionBold = isSelectionBold,
                isSelectionItalic = isSelectionItalic,
                hasConvertibleBibleRef = hasConvertibleBibleRef,
                language = language,
                strings = strings,
                onApplyHighlight = onApplyHighlight,
                onToggleBold = onToggleBold,
                onToggleItalic = onToggleItalic,
                onAddLink = onAddLink,
                onConvertSelectionRef = onConvertSelectionRef
            )
            }
        }
    }
}

@Composable
private fun TagInputWithSuggestions(
    tagDraft: String,
    onTagDraftChange: (String) -> Unit,
    existingTags: List<String>,
    availableTags: List<String>,
    placeholder: String,
    addTagDesc: String,
    onAddTag: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        yield()
        focusRequester.requestFocus()
    }

    val suggestions = remember(tagDraft, existingTags, availableTags) {
        val q = tagDraft.trim().lowercase()
        availableTags.filter { candidate ->
            existingTags.none { it.equals(candidate, ignoreCase = true) } &&
                (q.isEmpty() || candidate.lowercase().contains(q))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = suggestions.isNotEmpty() && tagDraft.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp)
            ) {
                suggestions.take(8).forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onAddTag(suggestion) },
                        label = { Text("#$suggestion") },
                        modifier = Modifier.testTag("tag_suggestion_$suggestion")
                    )
                }
            }
        }

        OutlinedTextField(
            value = tagDraft,
            onValueChange = onTagDraftChange,
            singleLine = true,
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (tagDraft.isNotBlank()) onAddTag(tagDraft)
                }
            ),
            trailingIcon = {
                if (tagDraft.isNotBlank()) {
                    IconButton(onClick = { onAddTag(tagDraft) }) {
                        Icon(Icons.Filled.Add, contentDescription = addTagDesc)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag("tag_draft_input")
        )
    }
}

@Composable
private fun ReadTimerPill(
    elapsedMs: Long,
    targetDurationMs: Long?,
    isRunning: Boolean,
    enabled: Boolean,
    strings: AppStrings,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSelectStopwatch: () -> Unit,
    onSelectCountdownMinutes: (Int) -> Unit
) {
    var showTimerMenu by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesDraft by remember(targetDurationMs) {
        mutableStateOf(
            if (targetDurationMs != null && targetDurationMs > 0L) {
                (targetDurationMs / 60_000L).coerceIn(1L, 180L).toString()
            } else {
                "5"
            }
        )
    }

    val display = remember(elapsedMs, targetDurationMs) {
        ReadTimerFormatter.computeDisplay(elapsedMs = elapsedMs, targetDurationMs = targetDurationMs)
    }
    val isActiveOrConfigured = isRunning || elapsedMs > 0L || targetDurationMs != null

    val containerColor = when {
        display.isOvertime -> MaterialTheme.colorScheme.errorContainer
        display.isWarning -> MaterialTheme.colorScheme.tertiaryContainer
        isActiveOrConfigured -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = when {
        display.isOvertime -> MaterialTheme.colorScheme.onErrorContainer
        display.isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
        isActiveOrConfigured -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        display.isOvertime -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        display.isWarning -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
        isActiveOrConfigured -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            border = BorderStroke(width = 1.dp, color = borderColor)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isRunning -> Icons.Filled.Pause
                            elapsedMs > 0L || targetDurationMs != null -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.Timer
                        },
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = display.text,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("editor_timer_text")
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(borderColor.copy(alpha = 0.7f))
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(start = 3.dp, end = 5.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Full 48dp-tall split touch zones aligned over the slim visual pill
        Row(modifier = Modifier.matchParentSize()) {
            val toggleDesc = if (isRunning) strings.readTimerPauseDesc else strings.readTimerStartDesc
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .semantics { contentDescription = toggleDesc }
                    .clickable(enabled = enabled, onClick = onToggleTimer)
                    .testTag("editor_timer_toggle_button")
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .semantics { contentDescription = strings.readTimerOptionsDesc }
                    .clickable(enabled = enabled) { showTimerMenu = true }
                    .testTag("editor_timer_menu_button")
            )
        }

        DropdownMenu(
            expanded = showTimerMenu && enabled,
            onDismissRequest = { showTimerMenu = false }
        ) {
            val isStopwatchSelected = targetDurationMs == null
            DropdownMenuItem(
                text = {
                    Text(
                        text = strings.readTimerStopwatchMode,
                        fontWeight = if (isStopwatchSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isStopwatchSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (isStopwatchSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (isStopwatchSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    showTimerMenu = false
                    onSelectStopwatch()
                },
                modifier = Modifier.testTag("timer_mode_stopwatch")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = strings.readTimerCountdownHeader,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            listOf(2, 5, 10, 15, 30).forEach { minutes ->
                val isSelected = targetDurationMs == minutes * 60_000L
                DropdownMenuItem(
                    text = {
                        Text(
                            text = strings.readTimerMinutesFormat.replace("{min}", minutes.toString()),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        showTimerMenu = false
                        onSelectCountdownMinutes(minutes)
                    },
                    modifier = Modifier.testTag("timer_preset_${minutes}m")
                )
            }

            DropdownMenuItem(
                text = { Text(strings.readTimerCustomDurationAction) },
                onClick = {
                    showTimerMenu = false
                    showCustomDialog = true
                },
                modifier = Modifier.testTag("timer_preset_custom")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text(strings.readTimerResetAction) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                enabled = elapsedMs > 0L || isRunning,
                onClick = {
                    showTimerMenu = false
                    onResetTimer()
                },
                modifier = Modifier.testTag("timer_reset_item")
            )
        }
    }

    if (showCustomDialog) {
        val parsedMinutes = customMinutesDraft.trim().toIntOrNull()
        val isValid = parsedMinutes != null && parsedMinutes in 1..180
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(strings.readTimerCustomDialogTitle) },
            text = {
                OutlinedTextField(
                    value = customMinutesDraft,
                    onValueChange = { input ->
                        customMinutesDraft = input.filter { it.isDigit() }.take(3)
                    },
                    label = { Text(strings.readTimerMinutesLabel) },
                    singleLine = true,
                    isError = customMinutesDraft.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValid && parsedMinutes != null) {
                                showCustomDialog = false
                                onSelectCountdownMinutes(parsedMinutes)
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timer_custom_minutes_input")
                )
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(strings.cancelAction)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isValid,
                    onClick = {
                        if (parsedMinutes != null) {
                            showCustomDialog = false
                            onSelectCountdownMinutes(parsedMinutes)
                        }
                    },
                    modifier = Modifier.testTag("timer_custom_confirm_button")
                ) {
                    Text(strings.confirmAction)
                }
            }
        )
    }
}

@Composable
private fun CompactEditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    detectedRefs: List<DetectedBibleRef>,
    primaryDetectedRef: DetectedBibleRef?,
    language: AppLanguage,
    refDisplayStyle: BibleRefDisplayStyle,
    strings: AppStrings,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onConvertDetectedRef: (DetectedBibleRef) -> Unit,
    onConvertAllDetectedRefs: () -> Unit,
    onAddBibleRef: () -> Unit,
    onAddTagClick: () -> Unit
) {
    val orderedRefs = remember(detectedRefs, primaryDetectedRef) {
        if (primaryDetectedRef == null) {
            detectedRefs
        } else {
            listOf(primaryDetectedRef) + detectedRefs.filterNot { it == primaryDetectedRef }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.ime)
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
        ) {
            AnimatedVisibility(
                visible = orderedRefs.isNotEmpty(),
                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("detected_bible_refs_banner")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.convertRefBannerPrefix,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        orderedRefs.take(6).forEachIndexed { idx, detected ->
                            val targetBook = if (refDisplayStyle == BibleRefDisplayStyle.FULL) {
                                detected.book.localizedName(language)
                            } else {
                                detected.book.localizedAbbr(language)
                            }
                            val targetLabel = JwLinkBuilder.formatReferenceDisplay(
                                bookName = targetBook,
                                chapter = detected.chapter,
                                verseSpec = detected.verseSpec
                            )
                            val chipLabel = if (detected.matchedText.equals(targetLabel, ignoreCase = true)) {
                                targetLabel
                            } else {
                                "${detected.matchedText} → $targetLabel"
                            }
                            SuggestionChip(
                                onClick = { onConvertDetectedRef(detected) },
                                label = {
                                    Text(
                                        text = chipLabel,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                modifier = Modifier.testTag("convert_detected_ref_$idx")
                            )
                        }
                        if (orderedRefs.size > 1) {
                            TextButton(
                                onClick = onConvertAllDetectedRefs,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("convert_all_detected_refs_button")
                            ) {
                                Text(
                                    text = strings.convertAllRefsAction.replace("{count}", orderedRefs.size.toString()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("editor_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = strings.undoDesc
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("editor_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = strings.redoDesc
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onAddBibleRef,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("toolbar_add_ref_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.addBibleRef,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    TextButton(
                        onClick = onAddTagClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("toolbar_add_tag_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.addTag,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
