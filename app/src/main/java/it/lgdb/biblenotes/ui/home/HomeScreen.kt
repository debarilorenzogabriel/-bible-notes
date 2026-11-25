package it.lgdb.biblenotes.ui.home

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.domain.SpanAdjuster
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
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val showScrollTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 150
        }
    }

    var pendingDeleteNote by remember { mutableStateOf<NoteRecord?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

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
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        NotePostItCard(
                            note = note,
                            strings = strings,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onNoteClick(note.id) },
                            onCopy = {
                                val textToCopy = buildString {
                                    if (note.title.isNotBlank()) {
                                        append(note.title)
                                        append("\n\n")
                                    }
                                    append(note.rawCorruptedJson ?: note.content.text)
                                }
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                onNoteCopied()
                            },
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
    strings: AppStrings,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extraColors = LocalBibleNotesExtraColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val dateFormatter = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    var menuExpanded by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(14.dp)
    val primaryLabel = remember(note.tags) {
        when {
            note.tags.isEmpty() -> ""
            note.tags.size == 1 -> note.tags.first()
            else -> "${note.tags.first()} +${note.tags.size - 1}"
        }
    }
    val previewAnnotated = remember(note.content, textColor) {
        SpanAdjuster.buildAnnotatedString(
            content = note.content,
            linkColor = textColor.copy(alpha = 0.92f),
            refBackgroundColor = Color.Transparent
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
            .defaultMinSize(minHeight = 148.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (note.pinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = strings.contextPin,
                tint = extraColors.pinColor.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .graphicsLayer { rotationZ = 32f }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 124.dp),
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
                        modifier = Modifier.padding(end = if (note.pinned) 20.dp else 0.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (note.isCorrupted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(
                            top = 2.dp,
                            end = if (note.title.isBlank() && note.pinned) 20.dp else 0.dp
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
                        maxLines = if (note.title.isNotBlank()) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (note.title.isBlank() && note.pinned) {
                            Modifier.padding(end = 20.dp)
                        } else {
                            Modifier
                        }
                    )
                }

                if (!note.isCorrupted && note.content.bibleRefs.isNotEmpty()) {
                    val firstRef = note.content.bibleRefs.first()
                    val extraRefsCount = note.content.bibleRefs.size - 1
                    val scriptureLabel = if (extraRefsCount > 0) {
                        "${firstRef.displayText} +$extraRefsCount"
                    } else {
                        firstRef.displayText
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
                onClick = {
                    menuExpanded = false
                    onCopy()
                }
            )
            if (!note.isCorrupted) {
                DropdownMenuItem(
                    text = { Text(if (note.pinned) strings.contextUnpin else strings.contextPin) },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(strings.contextEdit) },
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
                onClick = {
                    menuExpanded = false
                    onDeleteRequest()
                }
            )
        }
    }
}
