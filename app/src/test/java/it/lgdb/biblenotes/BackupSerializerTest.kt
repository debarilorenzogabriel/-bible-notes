package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.HighlightSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupSerializerTest {

    @Test
    fun `encode and decode preserves native v2 rich notes and labels`() {
        val originalNotes = listOf(
            NoteRecord(
                id = "n1",
                title = "Studio",
                content = RichNoteContent(
                    text = "Salmi 23:1 testo importante",
                    highlights = listOf(HighlightSpan(11, 27, HighlightColor.YELLOW)),
                    bibleRefs = listOf(BibleRefSpan(0, 10, 19, "Salmi", 23, "1"))
                ),
                tags = listOf("studio", "incoraggiamento"),
                createdAt = 1000L,
                updatedAt = 2000L,
                pinned = true
            )
        )
        val json = BackupSerializer.encode(
            notes = originalNotes,
            labels = listOf("studio", "predicazione"),
            exportedAt = 5000L
        )

        val decoded = BackupSerializer.decode(json).getOrThrow()
        assertEquals(1, decoded.notes.size)
        assertEquals(originalNotes.first(), decoded.notes.first())
        assertEquals(listOf("incoraggiamento", "predicazione", "studio"), decoded.allLabels)
    }

    @Test
    fun `decode supports legacy webview v1 backup with bibleRef object, bodyHtml links, and rgb highlights`() {
        val legacyJson = """
            {
              "version": 1,
              "exportedAt": 1795599043612,
              "notes": [
                {
                  "id": "1773479332922-6a78523076adc8",
                  "title": "Come possiamo allora ascoltare ciò che lo spirito dice?",
                  "bodyHtml": "<a href=\"jwlibrary:/finder?srcid=jwlshare&amp;wtlocale=I&amp;prefer=lang&amp;pub=nwtsty&amp;bible=66001010\" target=\"_blank\" rel=\"noopener noreferrer\">Rivelazione 1:10</a> Gesù dice.<div><span style=\"background-color: rgb(255, 204, 128);\">Chi ha orecchi ascolti.</span></div><div><a href=\"jwlibrary:/finder?srcid=jwlshare&amp;wtlocale=I&amp;prefer=lang&amp;pub=nwtsty&amp;bible=66002001-66002003\" target=\"_blank\">Rivelazione 2:1-3</a></div><div><a href=\"jwlibrary:/finder?srcid=jwlshare&amp;wtlocale=I&amp;prefer=lang&amp;pub=nwtsty&amp;bible=21011006\" target=\"_blank\">Eccl</a> 11:6</div>",
                  "bodyText": "Rivelazione 1:10 Gesù dice.\nChi ha orecchi ascolti.\nRivelazione 2:1-3\nEccl 11:6",
                  "tags": ["Assemblea 2026"],
                  "bibleRef": {
                    "book": "Rivelazione",
                    "chapter": 1,
                    "verse": null
                  },
                  "createdAt": 1773479332922,
                  "updatedAt": 1780324492648
                },
                {
                  "id": "1773482172004-6e34d77bc9343",
                  "title": "",
                  "bodyHtml": "<span style=\"background-color: rgb(165, 214, 167);\">Testo verde</span> e <span style=\"background-color: rgb(179, 157, 219);\">testo viola</span><div><a href=\"jwlibrary:/finder?bible=19084001\">Salmi 84</a></div><div><a href=\"jwlibrary:/finder?bible=23041020\">Isaia 41:10</a></div>",
                  "bodyText": "Testo verde e testo viola\nSalmi 84\nIsaia 41:10",
                  "tags": [],
                  "bibleRef": null,
                  "createdAt": 1773482172004,
                  "updatedAt": 1776188831564,
                  "pinned": false
                }
              ],
              "allLabels": []
            }
        """.trimIndent()

        val decoded = BackupSerializer.decode(legacyJson).getOrThrow()
        assertEquals(2, decoded.notes.size)
        assertEquals(listOf("Assemblea 2026"), decoded.allLabels)

        val first = decoded.notes[0]
        assertFalse(first.pinned)
        assertEquals(3, first.content.bibleRefs.size)
        assertEquals("Rivelazione 1:10", first.content.bibleRefs[0].displayText)
        assertEquals("Rivelazione 2:1-3", first.content.bibleRefs[1].displayText)
        assertEquals("Ecclesiaste 11:6", first.content.bibleRefs[2].displayText)
        assertEquals(1, first.content.highlights.size)
        assertEquals(HighlightColor.ORANGE, first.content.highlights[0].color)
        assertEquals(
            "Chi ha orecchi ascolti.",
            first.content.text.substring(first.content.highlights[0].start, first.content.highlights[0].end)
        )
        first.content.bibleRefs.forEach { ref ->
            assertEquals(ref.displayText, first.content.text.substring(ref.start, ref.end))
        }
        assertFalse(first.content.text.contains("Ecclesiaste 11:6 11:6"))

        val second = decoded.notes[1]
        assertEquals(2, second.content.highlights.size)
        assertEquals(HighlightColor.GREEN, second.content.highlights[0].color)
        assertEquals(HighlightColor.PURPLE, second.content.highlights[1].color)
        assertEquals(2, second.content.bibleRefs.size)
        assertEquals("Salmi 84", second.content.bibleRefs[0].displayText)
        assertEquals("Isaia 41:10", second.content.bibleRefs[1].displayText)
    }

    @Test
    fun `decode handles legacy string bibleRef fallback`() {
        val legacyJson = """
            {
              "version": 1,
              "exportedAt": 1700000000000,
              "notes": [
                {
                  "id": "legacy-1",
                  "title": "Nota vecchia",
                  "bodyHtml": "<p>Riflessione su <b>Giovanni 3:16</b></p>",
                  "bodyText": "Riflessione su Giovanni 3:16",
                  "tags": ["fede"],
                  "bibleRef": "Giovanni 3:16",
                  "createdAt": 1700000000000,
                  "updatedAt": 1700000001000,
                  "pinned": true
                }
              ],
              "allLabels": ["fede", "extra"]
            }
        """.trimIndent()

        val decoded = BackupSerializer.decode(legacyJson).getOrThrow()
        assertEquals(1, decoded.notes.size)
        val note = decoded.notes.first()
        assertEquals(43, note.content.bibleRefs.first().bookNumber)
        assertTrue(note.pinned)
        assertEquals(listOf("extra", "fede"), decoded.allLabels)
    }

    @Test
    fun `decode real root backup file if present`() {
        val candidates = listOf(
            File("../Bible Notes backup.json"),
            File("Bible Notes backup.json"),
            File("../public/assets/aistudio/Bible_Notes_backup_68829219.json")
        )
        val realFile = candidates.firstOrNull { it.exists() } ?: return
        val decoded = BackupSerializer.decode(realFile.readText()).getOrThrow()
        assertTrue(decoded.notes.isNotEmpty())
        decoded.notes.forEach { note ->
            note.content.bibleRefs.forEach { ref ->
                assertEquals(
                    "Span mismatch in note ${note.id}",
                    ref.displayText,
                    note.content.text.substring(ref.start, ref.end)
                )
            }
        }
    }

    @Test
    fun `decode rejects invalid json without notes array`() {
        val result = BackupSerializer.decode("""{"version":1}""")
        assertTrue(result.isFailure)
    }
}
