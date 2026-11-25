package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteSortOption
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
        updatedAt: Long = createdAt,
        pinned: Boolean = false
    ) = NoteRecord(
        id = id,
        title = title,
        content = RichNoteContent(text = text, bibleRefs = refs),
        tags = tags,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = pinned
    )

    @Test
    fun `sortForHome keeps at most 4 pinned notes first and supports sort options`() {
        val notes = listOf(
            sampleNote("p1", title = "Zeta", createdAt = 10, updatedAt = 100, pinned = true),
            sampleNote("u1", title = "Alfa", createdAt = 50, updatedAt = 200, pinned = false),
            sampleNote("p2", title = "Beta", createdAt = 20, updatedAt = 90, pinned = true),
            sampleNote("p3", title = "Gamma", createdAt = 30, updatedAt = 80, pinned = true),
            sampleNote("p4", title = "Delta", createdAt = 40, updatedAt = 70, pinned = true),
            sampleNote("p5", title = "Epsilon", createdAt = 60, updatedAt = 300, pinned = true),
            sampleNote("u2", title = "Omega", createdAt = 5, updatedAt = 50, pinned = false)
        )

        val byUpdated = NoteSorter.sortForHome(notes, NoteSortOption.UPDATED_DESC)
        assertEquals(listOf("p4"), byUpdated.unpinnedExcessIds)
        assertEquals(4, byUpdated.sorted.count { it.pinned })
        assertEquals(listOf("p5", "p1", "p2", "p3", "u1", "p4", "u2"), byUpdated.sorted.map { it.id })
        assertFalse(byUpdated.sorted.first { it.id == "p4" }.pinned)

        val byCreated = NoteSorter.sortForHome(notes, NoteSortOption.CREATED_DESC)
        assertEquals(listOf("p1"), byCreated.unpinnedExcessIds)
        assertEquals(listOf("p5", "p4", "p3", "p2", "u1", "p1", "u2"), byCreated.sorted.map { it.id })

        val byTitle = NoteSorter.sortForHome(notes, NoteSortOption.TITLE_ASC)
        assertEquals(4, byTitle.sorted.count { it.pinned })
        // Pinned alphabetically: Beta (p2), Delta (p4), Epsilon (p5), Gamma (p3); excess: Zeta (p1)
        assertEquals(listOf("p2", "p4", "p5", "p3", "u1", "u2", "p1"), byTitle.sorted.map { it.id })
    }

    @Test
    fun `filterNotes separates labelFilter from search query`() {
        val notes = listOf(
            sampleNote(
                id = "1",
                title = "Studio domenicale",
                text = "Meditazione sulla fede",
                tags = listOf("Discorsi")
            ),
            sampleNote(
                id = "2",
                title = "Appunti personali",
                text = "Riflessione sulla fede e speranza",
                tags = listOf("Personale")
            ),
            sampleNote(
                id = "3",
                title = "Altro discorso",
                text = "Confronto tra Salmi 23:1 e Giovanni 17:3",
                refs = listOf(
                    BibleRefSpan(13, 23, 19, "Salmi", 23, "1"),
                    BibleRefSpan(26, 39, 43, "Giovanni", 17, "3")
                ),
                tags = listOf("discorsi")
            )
        )

        // Search "fede" inside label "Discorsi" (case-insensitive)
        assertEquals(
            listOf("1"),
            NoteSorter.filterNotes(notes, query = "fede", labelFilter = "Discorsi").map { it.id }
        )

        // Only label filter without text search
        assertEquals(
            listOf("1", "3"),
            NoteSorter.filterNotes(notes, query = "", labelFilter = "Discorsi").map { it.id }
        )

        // Text search without label filter matches across title, body, tags, refs
        assertEquals(
            listOf("3"),
            NoteSorter.filterNotes(notes, query = "Giovanni 17:3").map { it.id }
        )
        assertEquals(
            listOf("1", "2", "3"),
            NoteSorter.filterNotes(notes, query = " ").map { it.id }
        )
    }
}
