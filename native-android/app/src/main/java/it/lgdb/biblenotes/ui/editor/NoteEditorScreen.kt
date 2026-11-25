package it.lgdb.biblenotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    onCloseEditor: () -> Unit
) {
    val context = LocalContext.current
    val strings = remember(state.language) { appStrings(state.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showBookWizard by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveAndClose(silentIfEmpty = true) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.backAction,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                title = {
                    Text(
                        text = if (state.activeNoteId == null) strings.newNoteTitle else strings.editNoteTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleReadOnlyMode() }) {
                        Icon(
                            imageVector = if (state.isReadOnlyMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                            contentDescription = if (state.isReadOnlyMode) strings.editMode else strings.readMode
                        )
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomSection(
                state = state,
                strings = strings,
                onApplyHighlight = viewModel::applyHighlight,
                onAddBibleRef = { showBookWizard = true },
                onAddTagClick = { showTagInput = !showTagInput },
                onDeleteClick = { showDeleteConfirm = true },
                onCancelClick = { showDiscardConfirm = true },
                onSaveClick = { viewModel.saveAndClose(silentIfEmpty = false) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                enabled = !state.isReadOnlyMode,
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
                            if (state.title.isEmpty()) {
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

            Spacer(modifier = Modifier.height(10.dp))

            val refAtCursor = state.activeRefAtCursor
            if (!state.isReadOnlyMode && refAtCursor != null) {
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

            if (state.isReadOnlyMode) {
                Text(
                    text = strings.tapToOpenHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            RichNoteBodyArea(
                content = state.richContent,
                textFieldValue = state.textFieldValue,
                isReadOnly = state.isReadOnlyMode,
                placeholder = strings.bodyPlaceholder,
                onValueChange = viewModel::onBodyValueChanged,
                onReferenceTap = openReference
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.richContent.bibleRefs.isNotEmpty()) {
                Text(
                    text = strings.referencesInNoteLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.richContent.bibleRefs.forEach { ref ->
                        AssistChip(
                            onClick = { openReference(ref) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(ref.displayText) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (showTagInput) {
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.removeTag(tag) }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                    Text(strings.cancel)
                }
            },
            confirmButton = {
                TextButton(
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
                    Text(strings.cancel)
                }
            },
            confirmButton = {
                TextButton(
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
private fun RichNoteBodyArea(
    content: RichNoteContent,
    textFieldValue: TextFieldValue,
    isReadOnly: Boolean,
    placeholder: String,
    onValueChange: (TextFieldValue) -> Unit,
    onReferenceTap: (BibleRefSpan) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val annotated = remember(content, linkColor) {
        SpanAdjuster.buildAnnotatedString(
            content = content,
            linkColor = linkColor
        )
    }

    if (isReadOnly) {
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (annotated.text.isEmpty()) {
                    androidx.compose.ui.text.AnnotatedString(placeholder)
                } else {
                    annotated
                },
                style = TextStyle(
                    color = if (annotated.text.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        textColor
                    },
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .pointerInput(content) {
                        detectTapGestures { tapOffset ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            val hit = annotated
                                .getStringAnnotations(SpanAdjuster.BIBLE_REF_TAG, charOffset, charOffset)
                                .firstOrNull()
                            val index = hit?.item?.toIntOrNull()
                            if (index != null && index in content.bibleRefs.indices) {
                                onReferenceTap(content.bibleRefs[index])
                            }
                        }
                    }
            )
        }
    } else {
        val visualTransformation = remember(annotated) {
            VisualTransformation { _ ->
                TransformedText(annotated, OffsetMapping.Identity)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
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
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorBottomSection(
    state: NoteEditorUiState,
    strings: AppStrings,
    onApplyHighlight: (HighlightColor?) -> Unit,
    onAddBibleRef: () -> Unit,
    onAddTagClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.highlightLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HighlightColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(enabled = !state.isReadOnlyMode) {
                                onApplyHighlight(color)
                            }
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(enabled = !state.isReadOnlyMode) {
                            onApplyHighlight(null)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatClear,
                        contentDescription = strings.clearHighlightDesc,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onAddBibleRef,
                    enabled = !state.isReadOnlyMode
                ) {
                    Text(strings.addBibleRef)
                }
                OutlinedButton(
                    onClick = onAddTagClick,
                    enabled = !state.isReadOnlyMode
                ) {
                    Text(strings.addTag)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.activeNoteId != null) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.delete)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onCancelClick) {
                    Text(strings.cancel)
                }
                Button(onClick = onSaveClick) {
                    Text(strings.save)
                }
            }
        }
    }
}
