package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoteSorterTest {

    private fun sampleNote(
        id: String,
        title: String = "",
        text: String = "",
        tags: List<String> = emptyList(),
        refs: List<BibleRefSpan> = emptyList(),
        createdAt: Long = 100L,
        pinned: Boolean = false
    ) = NoteRecord(
        id = id,
        title = title,
        content = RichNoteContent(text = text, bibleRefs = refs),
        tags = tags,
        createdAt = createdAt,
        updatedAt = createdAt,
        pinned = pinned
    )

    @Test
    fun `sortForHome keeps at most 4 pinned notes first and orders by createdAt desc`() {
        val notes = listOf(
            sampleNote("p1", createdAt = 10, pinned = true),
            sampleNote("u1", createdAt = 50, pinned = false),
            sampleNote("p2", createdAt = 20, pinned = true),
            sampleNote("p3", createdAt = 30, pinned = true),
            sampleNote("p4", createdAt = 40, pinned = true),
            sampleNote("p5", createdAt = 60, pinned = true),
            sampleNote("u2", createdAt = 5, pinned = false)
        )

        val result = NoteSorter.sortForHome(notes)
        assertEquals(listOf("p5"), result.unpinnedExcessIds)
        assertEquals(4, result.sorted.count { it.pinned })
        assertEquals(listOf("p4", "p3", "p2", "p1", "p5", "u1", "u2"), result.sorted.map { it.id })
        assertFalse(result.sorted.first { it.id == "p5" }.pinned)
    }

    @Test
    fun `filterNotes searches across title, body, tags, and multiple bible references`() {
        val notes = listOf(
            sampleNote(
                id = "1",
                title = "Studio domenicale",
                text = "Meditazione sulla speranza",
                tags = listOf("incoraggiamento")
            ),
            sampleNote(
                id = "2",
                title = "Appunti",
                text = "Confronto tra Salmi 23:1 e Giovanni 17:3",
                refs = listOf(
                    BibleRefSpan(13, 23, 19, "Salmi", 23, "1"),
                    BibleRefSpan(26, 39, 43, "Giovanni", 17, "3")
                ),
                tags = listOf("preghiera")
            )
        )

        assertEquals(listOf("1"), NoteSorter.filterNotes(notes, "domenicale").map { it.id })
        assertEquals(listOf("2"), NoteSorter.filterNotes(notes, "preghiera").map { it.id })
        assertEquals(listOf("2"), NoteSorter.filterNotes(notes, "Giovanni 17:3").map { it.id })
        assertEquals(listOf("1", "2"), NoteSorter.filterNotes(notes, " ").map { it.id })
    }
}
