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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.JwLinkOpener
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.domain.appStrings
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.ui.components.BibleRefInlineText
import it.lgdb.biblenotes.ui.theme.LocalBibleNotesExtraColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    onCloseEditor: () -> Unit,
    sharedNoteId: String? = state.activeNoteId,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val strings = remember(state.language) { appStrings(state.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showBookWizard by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }

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
                        if (state.isReadOnlyMode) {
                            FilledTonalButton(
                                onClick = { viewModel.enterEditMode() },
                                enabled = !state.isSaving,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("enter_edit_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.editMode)
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
                    strings = strings,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onApplyHighlight = viewModel::applyHighlight,
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
                    enabled = !state.isReadOnlyMode && !state.isCorrupted && !state.isSaving,
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

            val refAtCursor = state.activeRefAtCursor
            if (!state.isReadOnlyMode && !state.isCorrupted && refAtCursor != null) {
                AssistChip(
                    onClick = { openReference(refAtCursor) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("${strings.openRefAction} ${refAtCursor.displayText}") }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (state.isReadOnlyMode && !state.isCorrupted) {
                Text(
                    text = strings.tapToOpenHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Main body area occupies all remaining vertical space
            RichNoteBodyArea(
                content = state.richContent,
                textFieldValue = state.textFieldValue,
                isReadOnly = state.isReadOnlyMode || state.isCorrupted,
                isEnabled = !state.isSaving,
                placeholder = strings.bodyPlaceholder,
                onValueChange = viewModel::onBodyValueChanged,
                onReferenceTap = openReference,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            AnimatedVisibility(
                visible = !state.isReadOnlyMode && !state.isCorrupted && showTagInput,
                enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    TagInputWithSuggestions(
                        tagDraft = tagDraft,
                        onTagDraftChange = { tagDraft = it },
                        existingTags = state.tags,
                        availableTags = state.availableTags,
                        placeholder = strings.addTagPlaceholder,
                        onAddTag = { chosen ->
                            viewModel.addTag(chosen)
                            tagDraft = ""
                            showTagInput = false
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(durationMillis = 200))
            ) {
                if (state.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .animateContentSize(animationSpec = tween(durationMillis = 200))
                    ) {
                        state.tags.forEach { tag ->
                            key(tag) {
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text("#$tag") },
                                    trailingIcon = if (!state.isReadOnlyMode && !state.isCorrupted) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.removeTag(tag) }
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun RichNoteBodyArea(
    content: RichNoteContent,
    textFieldValue: TextFieldValue,
    isReadOnly: Boolean,
    isEnabled: Boolean,
    placeholder: String,
    onValueChange: (TextFieldValue) -> Unit,
    onReferenceTap: (BibleRefSpan) -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalBibleNotesExtraColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val annotated = remember(content, extraColors.chipText, extraColors.chipBg) {
        SpanAdjuster.buildAnnotatedString(
            content = content,
            linkColor = extraColors.chipText,
            refBackgroundColor = extraColors.chipBg
        )
    }

    if (isReadOnly) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = modifier
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                BibleRefInlineText(
                    content = content,
                    placeholder = placeholder,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 28.sp
                    ),
                    onReferenceClick = onReferenceTap,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        val visualTransformation = remember(annotated) {
            VisualTransformation { _ ->
                TransformedText(annotated, OffsetMapping.Identity)
            }
        }

        Box(
            modifier = modifier.padding(vertical = 4.dp)
        ) {
            if (textFieldValue.text.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            BasicTextField(
                value = textFieldValue,
                onValueChange = onValueChange,
                enabled = isEnabled,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxSize()
            )
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
    onAddTag: (String) -> Unit
) {
    val suggestions = remember(tagDraft, existingTags, availableTags) {
        val q = tagDraft.trim().lowercase()
        availableTags.filter { candidate ->
            existingTags.none { it.equals(candidate, ignoreCase = true) } &&
                (q.isEmpty() || candidate.lowercase().startsWith(q))
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
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
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded = suggestions.isNotEmpty() && tagDraft.isNotEmpty(),
            onDismissRequest = {},
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            suggestions.take(6).forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = { onAddTag(suggestion) }
                )
            }
        }
    }
}

@Composable
private fun CompactEditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    strings: AppStrings,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onAddBibleRef: () -> Unit,
    onAddTagClick: () -> Unit
) {
    var showHighlightPalette by remember { mutableStateOf(false) }

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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            AnimatedVisibility(
                visible = showHighlightPalette,
                enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(130))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HighlightColor.entries.forEach { color ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onApplyHighlight(color)
                                        showHighlightPalette = false
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(color.argb))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onApplyHighlight(null)
                                showHighlightPalette = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatClear,
                                contentDescription = strings.clearHighlightDesc,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }

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
                    IconButton(
                        onClick = { showHighlightPalette = !showHighlightPalette },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (showHighlightPalette) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .testTag("toolbar_highlight_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BorderColor,
                            contentDescription = strings.highlightLabel,
                            tint = if (showHighlightPalette) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
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
