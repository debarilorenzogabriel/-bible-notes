package it.lgdb.biblenotes.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.AppStrings
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.ui.components.BibleRefInlineText
import it.lgdb.biblenotes.ui.theme.LocalBibleNotesExtraColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    notes: List<NoteRecord>,
    searchQuery: String,
    strings: AppStrings,
    onSearchQueryChange: (String) -> Unit,
    onNewNoteClick: () -> Unit,
    onNoteClick: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onNoteCopied: () -> Unit,
    modifier: Modifier = Modifier
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header row: "Le mie note" + "Nuova nota"
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

                Button(
                    onClick = onNewNoteClick,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = strings.newNoteButton,
                        style = MaterialTheme.typography.labelLarge
                    )
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(notes, key = { _, item -> item.id }) { index, note ->
                        NotePostItCard(
                            note = note,
                            index = index,
                            strings = strings,
                            onClick = { onNoteClick(note.id) },
                            onCopy = {
                                val textToCopy = buildString {
                                    if (note.title.isNotBlank()) {
                                        append(note.title)
                                        append("\n\n")
                                    }
                                    append(note.content.text)
                                }
                                clipboardManager.setText(AnnotatedString(textToCopy))
                                onNoteCopied()
                            },
                            onTogglePin = { onTogglePin(note.id) },
                            onDeleteRequest = { pendingDeleteNote = note }
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
                    Text(strings.cancel)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotePostItCard(
    note: NoteRecord,
    index: Int,
    strings: AppStrings,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val extraColors = LocalBibleNotesExtraColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var menuExpanded by remember { mutableStateOf(false) }

    val tiltDegrees = if (index % 2 == 0) -1f else 1f
    val tagsLabel = remember(note.tags) {
        note.tags.joinToString(" ") { "#$it" }
    }

    Box(
        modifier = Modifier
            .graphicsLayer { rotationZ = tiltDegrees }
            .shadow(4.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        extraColors.noteCardGradientTop,
                        extraColors.noteCardGradientBottom
                    )
                )
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
            .defaultMinSize(minHeight = 136.dp)
            .padding(12.dp)
    ) {
        if (note.pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(extraColors.pinColor)
                    .border(2.dp, Color.Black.copy(alpha = 0.18f), CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (note.content.text.isNotBlank()) {
                    BibleRefInlineText(
                        content = note.content,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp,
                            lineHeight = 22.sp,
                            color = textColor
                        ),
                        chipFontSize = 11.5.sp,
                        chipHeightDp = 24.dp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = dateFormatter.format(Date(note.updatedAt)),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tagsLabel.isNotBlank()) {
                    Text(
                        text = tagsLabel,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            DropdownMenuItem(
                text = { Text(if (note.pinned) strings.contextUnpin else strings.contextPin) },
                onClick = {
                    menuExpanded = false
                    onTogglePin()
                }
            )
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
