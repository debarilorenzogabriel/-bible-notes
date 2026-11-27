package it.lgdb.biblenotes.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.domain.NoteExportFormatter
import it.lgdb.biblenotes.domain.NotePdfExporter
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.HomeLayoutMode
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteSortOption
import it.lgdb.biblenotes.ui.theme.LocalBibleNotesExtraColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    notes: List<NoteRecord>,
    searchQuery: String,
    selectedLabelFilter: String?,
    sortOption: NoteSortOption,
    trashedCount: Int,
    homeLayoutMode: HomeLayoutMode = HomeLayoutMode.GRID,
    bibleRefStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED,
    strings: AppStrings,
    onSearchQueryChange: (String) -> Unit,
    onClearLabelFilter: () -> Unit,
    onSelectSortOption: (NoteSortOption) -> Unit,
    onOpenTrash: () -> Unit,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onNoteCopied: () -> Unit,
    onNotifyMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val showScrollTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 150
        }
    }

    var pendingDeleteNote by remember { mutableStateOf<NoteRecord?>(null) }
    var pendingPdfExportNote by remember { mutableStateOf<NoteRecord?>(null) }
    var pendingPdfSaveNote by remember { mutableStateOf<NoteRecord?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val target = pendingPdfSaveNote
        pendingPdfSaveNote = null
        if (uri != null && target != null) {
            coroutineScope.launch {
                val success = NotePdfExporter.writePdfToUri(
                    context = context,
                    uri = uri,
                    note = target,
                    language = strings.language,
                    refDisplayStyle = bibleRefStyle,
                    strings = strings
                )
                onNotifyMessage(if (success) strings.pdfExportSuccess else strings.pdfExportError)
            }
        }
    }

    val shareNote: (NoteRecord) -> Unit = { target ->
        val shareText = NoteExportFormatter.formatForPlainShare(
            note = target,
            language = strings.language,
            refDisplayStyle = bibleRefStyle
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (target.title.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, target.title.trim())
            }
        }
        val chooser = Intent.createChooser(sendIntent, strings.shareChooserTitle)
        runCatching { context.startActivity(chooser) }
            .onFailure { onNotifyMessage(strings.noAppAvailableMessage) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header row: "Le mie note" + Trash button + "Nuova nota"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.homeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onOpenTrash,
                        modifier = Modifier.testTag("open_trash_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (trashedCount > 0) {
                                    Badge { Text(trashedCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = strings.trashTitle
                            )
                        }
                    }

                    Button(
                        onClick = onNewNoteClick,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("new_note_button")
                    ) {
                        Text(
                            text = strings.newNoteButton,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = { Text(strings.homeSearchPlaceholder) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Active label filter chip + sort selector
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(durationMillis = 200))
            ) {
                AnimatedVisibility(
                    visible = selectedLabelFilter != null,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(140)) + shrinkHorizontally(tween(180))
                ) {
                    val activeFilter = selectedLabelFilter ?: ""
                    InputChip(
                        selected = true,
                        onClick = onClearLabelFilter,
                        label = { Text("${strings.filterByLabelPrefix} #$activeFilter") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = strings.clearLabelFilterDesc,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("active_label_filter_chip")
                    )
                }

                Box {
                    val sortText = when (sortOption) {
                        NoteSortOption.UPDATED_DESC -> strings.sortUpdatedDesc
                        NoteSortOption.CREATED_DESC -> strings.sortCreatedDesc
                        NoteSortOption.TITLE_ASC -> strings.sortTitleAsc
                    }
                    AssistChip(
                        onClick = { showSortMenu = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("${strings.sortLabel}: $sortText") },
                        modifier = Modifier.testTag("home_sort_chip")
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortMenuOption(
                            label = strings.sortUpdatedDesc,
                            selected = sortOption == NoteSortOption.UPDATED_DESC,
                            onClick = {
                                showSortMenu = false
                                onSelectSortOption(NoteSortOption.UPDATED_DESC)
                            }
                        )
                        SortMenuOption(
                            label = strings.sortCreatedDesc,
                            selected = sortOption == NoteSortOption.CREATED_DESC,
                            onClick = {
                                showSortMenu = false
                                onSelectSortOption(NoteSortOption.CREATED_DESC)
                            }
                        )
                        SortMenuOption(
                            label = strings.sortTitleAsc,
                            selected = sortOption == NoteSortOption.TITLE_ASC,
                            onClick = {
                                showSortMenu = false
                                onSelectSortOption(NoteSortOption.TITLE_ASC)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(12.dp))

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = strings.emptyNotesMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val isListMode = homeLayoutMode == HomeLayoutMode.LIST
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (isListMode) {
                        GridCells.Fixed(1)
                    } else {
                        GridCells.Adaptive(minSize = 152.dp)
                    },
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isListMode) 12.dp else 14.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(if (isListMode) "home_notes_list" else "home_notes_grid")
                ) {
                    items(notes, key = { it.id }) { note ->
                        NotePostItCard(
                            note = note,
                            isListMode = isListMode,
                            bibleRefStyle = bibleRefStyle,
                            strings = strings,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onNoteClick(note.id) },
                            onCopy = {
                                val textToCopy = NoteExportFormatter.formatForPlainShare(
                                    note = note,
                                    language = strings.language,
                                    refDisplayStyle = bibleRefStyle
                                )
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                onNoteCopied()
                            },
                            onShare = { shareNote(note) },
                            onExportPdf = { pendingPdfExportNote = note },
                            onTogglePin = { onTogglePin(note.id) },
                            onDeleteRequest = { pendingDeleteNote = note },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 180),
                                fadeOutSpec = tween(durationMillis = 150),
                                placementSpec = tween(durationMillis = 220)
                            )
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = strings.scrollToTopDesc
                )
            }
        }
    }

    pendingDeleteNote?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null },
            title = { Text(strings.confirmDeleteTitle) },
            text = { Text(strings.confirmDeleteBody) },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNote = null }) {
                    Text(strings.cancelAction)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteNote = null
                        onDeleteNote(target.id)
                    }
                ) {
                    Text(strings.contextDelete, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    pendingPdfExportNote?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPdfExportNote = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(strings.exportPdfDialogTitle)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = strings.exportPdfDialogBody,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            pendingPdfExportNote = null
                            pendingPdfSaveNote = target
                            val suggestedName = NoteExportFormatter.buildPdfSuggestedFileName(
                                note = target,
                                language = strings.language
                            )
                            pdfSaveLauncher.launch(suggestedName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_pdf_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.exportPdfSaveAction)
                    }
                    OutlinedButton(
                        onClick = {
                            pendingPdfExportNote = null
                            val started = NotePdfExporter.printNote(
                                context = context,
                                note = target,
                                language = strings.language,
                                refDisplayStyle = bibleRefStyle,
                                strings = strings
                            )
                            if (!started) {
                                onNotifyMessage(strings.pdfExportError)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_pdf_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.exportPdfPrintAction)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingPdfExportNote = null }) {
                    Text(strings.cancelAction)
                }
            }
        )
    }
}

@Composable
private fun SortMenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (selected) {
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
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun NotePostItCard(
    note: NoteRecord,
    isListMode: Boolean,
    bibleRefStyle: BibleRefDisplayStyle,
    strings: AppStrings,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportPdf: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalBibleNotesExtraColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val dateFormatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    var menuExpanded by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(14.dp)
    val primaryLabel = remember(note.tags, isListMode) {
        when {
            note.tags.isEmpty() -> ""
            note.tags.size == 1 -> note.tags.first()
            isListMode && note.tags.size == 2 -> note.tags.joinToString(" • ")
            isListMode -> "${note.tags.take(2).joinToString(" • ")} +${note.tags.size - 2}"
            else -> "${note.tags.first()} +${note.tags.size - 1}"
        }
    }
    val urlColor = MaterialTheme.colorScheme.primary
    val previewAnnotated = remember(note.content, textColor, urlColor, strings.language, bibleRefStyle) {
        SpanAdjuster.buildAnnotatedString(
            content = note.content,
            linkColor = textColor.copy(alpha = 0.92f),
            refBackgroundColor = Color.Transparent,
            language = strings.language,
            urlColor = urlColor,
            refDisplayStyle = bibleRefStyle
        )
    }

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note_card_${note.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(sharedModifier)
            .clip(cardShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        extraColors.noteCardGradientTop,
                        extraColors.noteCardGradientBottom
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = cardShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .defaultMinSize(minHeight = if (isListMode) 96.dp else 148.dp)
            .padding(
                horizontal = if (isListMode) 16.dp else 14.dp,
                vertical = if (isListMode) 14.dp else 12.dp
            )
    ) {
        val topEndIconPadding = when {
            note.pinned && note.locked -> 38.dp
            note.pinned || note.locked -> 20.dp
            else -> 0.dp
        }

        if (note.pinned || note.locked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                if (note.locked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = strings.lockedBadgeLabel,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (note.pinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = strings.contextPin,
                        tint = extraColors.pinColor.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = 32f }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (isListMode) 68.dp else 124.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = topEndIconPadding)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (note.isCorrupted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(
                            top = 2.dp,
                            end = if (note.title.isBlank()) topEndIconPadding else 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = strings.corruptedNoteBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (note.content.text.isNotBlank()) {
                    Text(
                        text = previewAnnotated,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = textColor.copy(alpha = 0.86f),
                        maxLines = if (isListMode) {
                            if (note.title.isNotBlank()) 6 else 7
                        } else {
                            if (note.title.isNotBlank()) 3 else 4
                        },
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (note.title.isBlank() && topEndIconPadding > 0.dp) {
                            Modifier.padding(end = topEndIconPadding)
                        } else {
                            Modifier
                        }
                    )
                }

                if (!note.isCorrupted && note.content.bibleRefs.isNotEmpty()) {
                    val firstRef = note.content.bibleRefs.first()
                    val firstShortLabel = JwLinkBuilder.formatReference(
                        ref = firstRef,
                        language = strings.language,
                        style = bibleRefStyle
                    )
                    val extraRefsCount = note.content.bibleRefs.size - 1
                    val scriptureLabel = if (extraRefsCount > 0) {
                        "$firstShortLabel +$extraRefsCount"
                    } else {
                        firstShortLabel
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = scriptureLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (primaryLabel.isNotBlank()) {
                        Text(
                            text = primaryLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Text(
                        text = dateFormatter.format(Date(note.updatedAt)).lowercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(strings.contextCopy) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onCopy()
                }
            )
            if (!note.isCorrupted) {
                DropdownMenuItem(
                    text = { Text(strings.contextShare) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                    modifier = Modifier.testTag("note_context_share")
                )
                DropdownMenuItem(
                    text = { Text(strings.contextExportPdf) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onExportPdf()
                    },
                    modifier = Modifier.testTag("note_context_export_pdf")
                )
                DropdownMenuItem(
                    text = { Text(if (note.pinned) strings.contextUnpin else strings.contextPin) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(strings.contextEdit) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = strings.contextDelete,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDeleteRequest()
                }
            )
        }
    }
}
