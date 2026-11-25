package it.lgdb.biblenotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.lgdb.biblenotes.domain.BibleBook
import it.lgdb.biblenotes.domain.BibleCatalog
import it.lgdb.biblenotes.domain.EditorStrings
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.model.AppLanguage

private enum class WizardStep {
    BOOK,
    CHAPTER,
    VERSE
}

@Composable
fun BibleReferenceWizardDialog(
    language: AppLanguage,
    strings: EditorStrings,
    onDismiss: () -> Unit,
    onConfirm: (book: BibleBook, chapter: Int, verseSpec: String?) -> Unit
) {
    var step by remember { mutableStateOf(WizardStep.BOOK) }
    var selectedBook by remember { mutableStateOf<BibleBook?>(null) }
    var selectedChapter by remember { mutableIntStateOf(1) }
    var verseInput by remember { mutableStateOf("") }

    when (step) {
        WizardStep.BOOK -> {
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.selectBookTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismiss) {
                                Text(strings.closeAction)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 62.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.hebrewScriptures.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(BibleCatalog.hebrewBooks, key = { it.number }) { book ->
                                BookTile(
                                    label = book.localizedAbbr(language),
                                    onClick = {
                                        selectedBook = book
                                        step = WizardStep.CHAPTER
                                    }
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = strings.greekScriptures.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(BibleCatalog.greekBooks, key = { it.number }) { book ->
                                BookTile(
                                    label = book.localizedAbbr(language),
                                    onClick = {
                                        selectedBook = book
                                        step = WizardStep.CHAPTER
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        WizardStep.CHAPTER -> {
            val book = selectedBook ?: return
            Dialog(onDismissRequest = onDismiss) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.selectChapterTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = book.localizedName(language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { step = WizardStep.BOOK }) {
                                Text(strings.backAction)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val chapters = remember(book) { (1..book.chapters).toList() }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 52.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(chapters, key = { it }) { chapter ->
                                BookTile(
                                    label = chapter.toString(),
                                    onClick = {
                                        selectedChapter = chapter
                                        verseInput = ""
                                        step = WizardStep.VERSE
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        WizardStep.VERSE -> {
            val book = selectedBook ?: return
            val preview = JwLinkBuilder.formatReferenceDisplay(
                bookName = book.localizedName(language),
                chapter = selectedChapter,
                verseSpec = verseInput.takeIf { it.isNotBlank() }
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(strings.enterVerseTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = verseInput,
                            onValueChange = { verseInput = it },
                            singleLine = true,
                            placeholder = { Text(strings.versePlaceholder) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { step = WizardStep.CHAPTER }) {
                        Text(strings.backAction)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onConfirm(
                                book,
                                selectedChapter,
                                verseInput.trim().takeIf { it.isNotEmpty() }
                            )
                        }
                    ) {
                        Text(strings.insertRefAction)
                    }
                }
            )
        }
    }
}

@Composable
private fun BookTile(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp)
        )
    }
}
