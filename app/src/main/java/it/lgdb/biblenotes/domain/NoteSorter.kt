package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteSortOption

object NoteSorter {
    const val MAX_PINNED_NOTES = 4

    data class SortedNotesResult(
        val sorted: List<NoteRecord>,
        val unpinnedExcessIds: List<String>
    )

    fun sortForHome(
        notes: List<NoteRecord>,
        sortOption: NoteSortOption = NoteSortOption.UPDATED_DESC
    ): SortedNotesResult {
        val comparator = comparatorFor(sortOption)
        val pinned = notes.filter { it.pinned }
            .sortedWith(comparator)
        val keptPinned = pinned.take(MAX_PINNED_NOTES)
        val excessPinned = pinned.drop(MAX_PINNED_NOTES).map { it.copy(pinned = false) }
        val excessIds = excessPinned.map { it.id }

        val unpinned = (notes.filter { !it.pinned } + excessPinned)
            .sortedWith(comparator)

        return SortedNotesResult(
            sorted = keptPinned + unpinned,
            unpinnedExcessIds = excessIds
        )
    }

    fun filterNotes(
        notes: List<NoteRecord>,
        query: String,
        labelFilter: String? = null
    ): List<NoteRecord> {
        val activeLabel = labelFilter?.trim()?.takeIf { it.isNotEmpty() }
        val q = query.trim().lowercase()

        return notes.filter { note ->
            val labelMatches = if (activeLabel == null) {
                true
            } else {
                note.tags.any { it.equals(activeLabel, ignoreCase = true) }
            }
            if (!labelMatches) return@filter false

            if (q.isEmpty()) return@filter true

            val titleMatch = note.title.lowercase().contains(q)
            val bodyMatch = note.content.text.lowercase().contains(q)
            val refsMatch = note.content.bibleRefs.any { ref ->
                ref.displayText.lowercase().contains(q) ||
                    JwLinkBuilder.formatAbbreviatedReference(ref, AppLanguage.IT).lowercase().contains(q) ||
                    JwLinkBuilder.formatAbbreviatedReference(ref, AppLanguage.EN).lowercase().contains(q) ||
                    JwLinkBuilder.formatFullReference(ref, AppLanguage.IT).lowercase().contains(q) ||
                    JwLinkBuilder.formatFullReference(ref, AppLanguage.EN).lowercase().contains(q)
            }
            val tagsMatch = if (activeLabel == null) {
                note.tags.any { it.lowercase().contains(q) }
            } else {
                false
            }
            titleMatch || bodyMatch || refsMatch || tagsMatch
        }
    }

    private fun comparatorFor(sortOption: NoteSortOption): Comparator<NoteRecord> = when (sortOption) {
        NoteSortOption.UPDATED_DESC -> compareByDescending<NoteRecord> { it.updatedAt }
            .thenByDescending { it.createdAt }
        NoteSortOption.CREATED_DESC -> compareByDescending<NoteRecord> { it.createdAt }
            .thenByDescending { it.updatedAt }
        NoteSortOption.TITLE_ASC -> compareBy<NoteRecord, String>(String.CASE_INSENSITIVE_ORDER) {
            it.title.trim().ifEmpty { it.content.text.lineSequence().firstOrNull().orEmpty().trim() }
        }.thenByDescending { it.updatedAt }
    }
}
