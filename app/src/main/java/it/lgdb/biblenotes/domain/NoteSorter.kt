package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.NoteRecord

object NoteSorter {
    const val MAX_PINNED_NOTES = 4

    data class SortedNotesResult(
        val sorted: List<NoteRecord>,
        val unpinnedExcessIds: List<String>
    )

    fun sortForHome(notes: List<NoteRecord>): SortedNotesResult {
        val pinned = notes.filter { it.pinned }
        val keptPinned = pinned.take(MAX_PINNED_NOTES)
        val excessPinned = pinned.drop(MAX_PINNED_NOTES).map { it.copy(pinned = false) }
        val excessIds = excessPinned.map { it.id }

        val unpinned = notes.filter { !it.pinned } + excessPinned

        val sortedPinned = keptPinned.sortedByDescending { it.createdAt }
        val sortedUnpinned = unpinned.sortedByDescending { it.createdAt }

        return SortedNotesResult(
            sorted = sortedPinned + sortedUnpinned,
            unpinnedExcessIds = excessIds
        )
    }

    fun filterNotes(notes: List<NoteRecord>, query: String): List<NoteRecord> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return notes

        return notes.filter { note ->
            val titleMatch = note.title.lowercase().contains(q)
            val bodyMatch = note.content.text.lowercase().contains(q)
            val tagsMatch = note.tags.joinToString(" ").lowercase().contains(q)
            val refsMatch = note.content.bibleRefs.any { ref ->
                ref.displayText.lowercase().contains(q)
            }
            titleMatch || bodyMatch || tagsMatch || refsMatch
        }
    }
}
