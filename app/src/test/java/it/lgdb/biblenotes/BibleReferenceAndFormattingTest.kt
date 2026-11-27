package it.lgdb.biblenotes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.domain.BibleCatalog
import it.lgdb.biblenotes.domain.BibleReferenceDetector
import it.lgdb.biblenotes.domain.InNoteSearchHelper
import it.lgdb.biblenotes.domain.JwLinkBuilder
import it.lgdb.biblenotes.domain.ListFormattingHelper
import it.lgdb.biblenotes.domain.NoteExportFormatter
import it.lgdb.biblenotes.domain.ReadTimerFormatter
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.HighlightSpan
import it.lgdb.biblenotes.model.LinkSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteTextSize
import it.lgdb.biblenotes.model.RichNoteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleReferenceAndFormattingTest {

    @Test
    fun `detector finds abbreviated and full Bible references while ignoring stopword false positives and URLs`() {
        val content = RichNoteContent(
            text = "Leggi Mt 5:5 e Matteo 6:33, anche 1 Corinti 13:4-7 e Salmi 23. " +
                "Sono le 5:30 e lavoro da 5 anni. Link: https://jw.org/it/Mt/5:5"
        )
        val detected = BibleReferenceDetector.findAll(content)
        assertEquals(4, detected.size)
        assertEquals("Mt 5:5", detected[0].matchedText)
        assertEquals(40, detected[0].book.number)
        assertEquals(5, detected[0].chapter)
        assertEquals("5", detected[0].verseSpec)

        assertEquals("Matteo 6:33", detected[1].matchedText)
        assertEquals(40, detected[1].book.number)
        assertEquals(6, detected[1].chapter)
        assertEquals("33", detected[1].verseSpec)

        assertEquals("1 Corinti 13:4-7", detected[2].matchedText)
        assertEquals(46, detected[2].book.number)
        assertEquals(13, detected[2].chapter)
        assertEquals("4-7", detected[2].verseSpec)

        assertEquals("Salmi 23", detected[3].matchedText)
        assertEquals(19, detected[3].book.number)
        assertEquals(23, detected[3].chapter)
        assertNull(detected[3].verseSpec)
    }

    @Test
    fun `detector skips already converted BibleRefSpans and converts plain refs cleanly`() {
        val base = RichNoteContent(text = "Confronta Matteo 5:5 con Giovanni 3:16 oggi")
        val detected = BibleReferenceDetector.findAll(base)
        assertEquals(2, detected.size)

        val (convertedOne, cursorAfterOne) = SpanAdjuster.convertDetectedBibleRef(
            content = base,
            detected = detected.first(),
            language = AppLanguage.IT,
            currentCursor = base.text.length
        )
        assertTrue(convertedOne.text.startsWith("Confronta Mt 5:5 con "))
        assertEquals(1, convertedOne.bibleRefs.size)
        assertEquals("Mt 5:5", convertedOne.bibleRefs.first().displayText)
        assertEquals(convertedOne.text.length, cursorAfterOne)

        // Remaining unconverted ref is still detected cleanly while Mt 5:5 is skipped
        val remaining = BibleReferenceDetector.findAll(convertedOne)
        assertEquals(1, remaining.size)
        assertEquals("Giovanni 3:16", remaining.single().matchedText)

        val (convertedAll, _) = SpanAdjuster.convertAllDetectedBibleRefs(
            content = base,
            detectedRefs = detected,
            language = AppLanguage.IT,
            currentCursor = base.text.length
        )
        assertEquals("Confronta Mt 5:5 con Gv 3:16 oggi", convertedAll.text)
        assertEquals(2, convertedAll.bibleRefs.size)
        assertTrue(BibleReferenceDetector.findAll(convertedAll).isEmpty())
    }

    @Test
    fun `bold and italic toggle cleanly, survive edits, and render across inline and editable notes`() {
        val base = RichNoteContent(text = "Fede speranza amore")
        val bolded = SpanAdjuster.toggleBold(base, 0, 13)
        assertTrue(SpanAdjuster.isRangeCoveredBySpans(bolded.bolds, 0, 13))

        // Toggling italic inside overlapping range
        val boldAndItalic = SpanAdjuster.toggleItalic(bolded, 5, 19)
        assertTrue(SpanAdjuster.isRangeCoveredBySpans(boldAndItalic.italics, 5, 19))

        // Toggling bold off on subrange splits cleanly
        val splitBold = SpanAdjuster.toggleBold(boldAndItalic, 4, 6)
        assertEquals(2, splitBold.bolds.size)

        // Prepending text shifts bold and italic spans accurately
        val shifted = SpanAdjuster.onTextChanged(splitBold, "Nota: " + splitBold.text)
        assertEquals("Fede", shifted.text.substring(shifted.bolds[0].start, shifted.bolds[0].end))

        val inline = SpanAdjuster.buildInlineAnnotatedString(shifted)
        assertTrue(inline.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(inline.spanStyles.any { it.item.fontStyle == FontStyle.Italic })

        val editable = SpanAdjuster.buildEditableTransformedNote(shifted, refTextColor = Color.Black)
        assertTrue(editable.annotatedString.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(editable.annotatedString.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `list recognition converts dash shortcut to bullet and continues or exits bullet and numbered lists`() {
        // Typing "- " at line start becomes "• "
        val afterDashSpace = ListFormattingHelper.processTextEdit(
            oldText = "-",
            oldSelection = TextRange(1),
            newValue = TextFieldValue("- ", TextRange(2)),
            bibleRefs = emptyList()
        )
        assertNotNull(afterDashSpace)
        assertEquals("• ", afterDashSpace!!.text)
        assertEquals(TextRange(2), afterDashSpace.selection)

        // Pressing Enter on non-empty bullet line continues with "• "
        val afterBulletEnter = ListFormattingHelper.processTextEdit(
            oldText = "• Primo punto",
            oldSelection = TextRange(13),
            newValue = TextFieldValue("• Primo punto\n", TextRange(14)),
            bibleRefs = emptyList()
        )
        assertNotNull(afterBulletEnter)
        assertEquals("• Primo punto\n• ", afterBulletEnter!!.text)
        assertEquals(TextRange(16), afterBulletEnter.selection)

        // Pressing Enter on empty bullet line exits the list
        val afterEmptyBulletEnter = ListFormattingHelper.processTextEdit(
            oldText = "• Primo punto\n• ",
            oldSelection = TextRange(16),
            newValue = TextFieldValue("• Primo punto\n• \n", TextRange(17)),
            bibleRefs = emptyList()
        )
        assertNotNull(afterEmptyBulletEnter)
        assertEquals("• Primo punto\n", afterEmptyBulletEnter!!.text)
        assertEquals(TextRange(14), afterEmptyBulletEnter.selection)

        // Pressing Enter on numbered list increments counter ("1. " -> "2. ")
        val afterNumEnter = ListFormattingHelper.processTextEdit(
            oldText = "1. Versetto chiave",
            oldSelection = TextRange(18),
            newValue = TextFieldValue("1. Versetto chiave\n", TextRange(19)),
            bibleRefs = emptyList()
        )
        assertNotNull(afterNumEnter)
        assertEquals("1. Versetto chiave\n2. ", afterNumEnter!!.text)
        assertEquals(TextRange(22), afterNumEnter.selection)

        // Recognized list markers are highlighted in annotated output
        val listContent = RichNoteContent(text = "• Voce uno\n2. Voce due")
        val recognized = ListFormattingHelper.findRecognizedListLines(listContent.text)
        assertEquals(2, recognized.size)
        assertEquals(false, recognized[0].isNumbered)
        assertEquals(true, recognized[1].isNumbered)

        val inline = SpanAdjuster.buildInlineAnnotatedString(listContent)
        assertTrue(inline.spanStyles.any { it.start == 0 && it.end == 1 && it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(inline.spanStyles.any { it.start == 11 && it.end == 13 && it.item.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun `BibleCatalog findByName still matches legacy and abbreviated names`() {
        assertEquals(40, BibleCatalog.findByName("Matteo")?.number)
        assertEquals(40, BibleCatalog.findByName("Mt")?.number)
    }

    @Test
    fun `BibleRefDisplayStyle FULL renders full book names across preview, inline, editable, and tap offsets`() {
        val ref = BibleRefSpan(
            start = 6,
            end = 12,
            bookNumber = 40,
            bookName = "Mt",
            chapter = 5,
            verseSpec = "5"
        )
        val content = RichNoteContent(
            text = "Leggi Mt 5:5 oggi",
            bibleRefs = listOf(ref)
        )

        assertEquals("Mt 5:5", JwLinkBuilder.formatReference(ref, AppLanguage.IT, BibleRefDisplayStyle.ABBREVIATED))
        assertEquals("Matteo 5:5", JwLinkBuilder.formatReference(ref, AppLanguage.IT, BibleRefDisplayStyle.FULL))
        assertEquals("Matthew 5:5", JwLinkBuilder.formatReference(ref, AppLanguage.EN, BibleRefDisplayStyle.FULL))

        val previewFull = SpanAdjuster.buildAnnotatedString(
            content = content,
            linkColor = Color.Blue,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertEquals("Leggi Matteo 5:5 oggi", previewFull.text)

        val inlineFull = SpanAdjuster.buildInlineAnnotatedString(
            content = content,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertEquals("Leggi Matteo 5:5 oggi", inlineFull.text)

        val editableFull = SpanAdjuster.buildEditableTransformedNote(
            content = content,
            refTextColor = Color.Black,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertTrue(editableFull.annotatedString.text.contains("Matteo\u00A05:5"))

        // Tapping after "Leggi Matteo 5:5 " in inline FULL mode maps accurately to "oggi" (index 13 in original)
        val tappedOrig = SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(
            content = content,
            language = AppLanguage.IT,
            annotatedOffset = "Leggi Matteo 5:5 ".length,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertEquals(13, tappedOrig)
    }

    @Test
    fun `in-note search finds plain words inside highlights and Bible refs without mutating RichNoteContent`() {
        val ref = BibleRefSpan(
            start = 15,
            end = 21,
            bookNumber = 40,
            bookName = "Mt",
            chapter = 5,
            verseSpec = "5"
        )
        val content = RichNoteContent(
            text = "Fede e pace in Mt 5:5 con fede viva.",
            highlights = listOf(HighlightSpan(start = 0, end = 4, color = HighlightColor.YELLOW)),
            bibleRefs = listOf(ref)
        )

        val wordMatches = InNoteSearchHelper.findMatches(
            content = content,
            query = "fede",
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
        )
        assertEquals(2, wordMatches.size)
        assertEquals(0, wordMatches[0].start)
        assertEquals(4, wordMatches[0].end)
        assertEquals(false, wordMatches[0].isBibleRef)
        assertEquals(26, wordMatches[1].start)
        assertEquals(30, wordMatches[1].end)
        assertEquals(1, content.highlights.size)

        // Searching "Matteo" matches Mt 5:5 even when display style is ABBREVIATED
        val refMatches = InNoteSearchHelper.findMatches(
            content = content,
            query = "Matteo",
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
        )
        assertEquals(1, refMatches.size)
        assertTrue(refMatches[0].isBibleRef)
        assertEquals(0, refMatches[0].bibleRefIndex)

        // Mapping ranges for inline annotated string handles FULL display style offset shifts accurately
        val inlineRangePlainAfterRef = SpanAdjuster.originalRangeToInlineAnnotatedRange(
            content = content,
            origStart = 26,
            origEnd = 30,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        val inlineFull = SpanAdjuster.buildInlineAnnotatedString(
            content = content,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertEquals("fede", inlineFull.text.substring(inlineRangePlainAfterRef.first, inlineRangePlainAfterRef.last + 1))

        val inlineRangeRef = SpanAdjuster.originalRangeToInlineAnnotatedRange(
            content = content,
            origStart = ref.start,
            origEnd = ref.end,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertEquals("Matteo 5:5", inlineFull.text.substring(inlineRangeRef.first, inlineRangeRef.last + 1))
    }

    @Test
    fun `NoteTextSize EXTRA_LARGE scales editable chip style while preserving user highlights`() {
        val ref = BibleRefSpan(
            start = 6,
            end = 12,
            bookNumber = 40,
            bookName = "Mt",
            chapter = 5,
            verseSpec = "5"
        )
        val content = RichNoteContent(
            text = "Vedi Mt 5:5 sempre",
            highlights = listOf(HighlightSpan(start = 12, end = 18, color = HighlightColor.GREEN)),
            bibleRefs = listOf(ref)
        )
        val editable = SpanAdjuster.buildEditableTransformedNote(
            content = content,
            refTextColor = Color.Black,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.ABBREVIATED,
            textSize = NoteTextSize.EXTRA_LARGE
        )
        assertTrue(
            editable.annotatedString.spanStyles.any {
                it.item.fontSize == NoteTextSize.EXTRA_LARGE.chipFontSp.sp
            }
        )
        assertTrue(
            editable.annotatedString.spanStyles.any {
                it.item.background == Color(HighlightColor.GREEN.argb)
            }
        )
    }

    @Test
    fun `NoteExportFormatter formats clean share text with scripture styles, links, and PDF filenames`() {
        val text = "Leggi Mt 5:5 e visita sito ufficiale oppure https://jw.org\n• Primo punto"
        val refStart = text.indexOf("Mt 5:5")
        val labelLinkStart = text.indexOf("sito ufficiale")
        val rawUrlStart = text.indexOf("https://jw.org")

        val note = NoteRecord(
            id = "n1",
            title = "Discorso: Monte / Studio?",
            content = RichNoteContent(
                text = text,
                bibleRefs = listOf(
                    BibleRefSpan(
                        start = refStart,
                        end = refStart + "Mt 5:5".length,
                        bookNumber = 40,
                        bookName = "Mt",
                        chapter = 5,
                        verseSpec = "5"
                    )
                ),
                links = listOf(
                    LinkSpan(
                        start = labelLinkStart,
                        end = labelLinkStart + "sito ufficiale".length,
                        url = "https://www.jw.org/it/"
                    ),
                    LinkSpan(
                        start = rawUrlStart,
                        end = rawUrlStart + "https://jw.org".length,
                        url = "https://jw.org"
                    )
                )
            ),
            tags = listOf("Studio", "Adunanza"),
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            pinned = false,
            locked = true
        )

        val sharedFull = NoteExportFormatter.formatForPlainShare(
            note = note,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.FULL
        )
        assertTrue(sharedFull.startsWith("Discorso: Monte / Studio?\n\n"))
        assertTrue(sharedFull.contains("Leggi Matteo 5:5 e visita sito ufficiale (https://www.jw.org/it/)"))
        assertTrue(sharedFull.contains("oppure https://jw.org\n• Primo punto"))
        assertFalse(sharedFull.contains("https://jw.org (https://jw.org)"))
        assertTrue(sharedFull.endsWith("\n\n#Studio #Adunanza"))

        val sharedAbbr = NoteExportFormatter.formatForPlainShare(
            note = note,
            language = AppLanguage.IT,
            refDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
        )
        assertTrue(sharedAbbr.contains("Leggi Mt 5:5 e visita"))

        val fileName = NoteExportFormatter.buildPdfSuggestedFileName(note, AppLanguage.IT)
        assertEquals("Discorso_Monte_Studio.pdf", fileName)

        val untitled = note.copy(title = "   ")
        val fallbackNameIt = NoteExportFormatter.buildPdfSuggestedFileName(untitled, AppLanguage.IT)
        val fallbackNameEn = NoteExportFormatter.buildPdfSuggestedFileName(untitled, AppLanguage.EN)
        assertTrue(fallbackNameIt.startsWith("nota_") && fallbackNameIt.endsWith(".pdf"))
        assertTrue(fallbackNameEn.startsWith("note_") && fallbackNameEn.endsWith(".pdf"))
    }

    @Test
    fun `BackupSerializer preserves locked flag and defaults omitted locked to false`() {
        val lockedNote = NoteRecord(
            id = "locked-1",
            title = "Appunti protetti",
            content = RichNoteContent(text = "Testo bloccato"),
            tags = emptyList(),
            createdAt = 1000L,
            updatedAt = 2000L,
            pinned = false,
            locked = true
        )
        val json = BackupSerializer.encode(notes = listOf(lockedNote), labels = emptyList(), exportedAt = 3000L)
        val decoded = BackupSerializer.decode(json).getOrThrow()
        assertEquals(1, decoded.notes.size)
        assertTrue(decoded.notes.single().locked)

        val withoutLockedField = """
            {
              "version": 2,
              "exportedAt": 3000,
              "notes": [
                {
                  "id": "legacy-2",
                  "title": "Vecchia nota",
                  "content": { "text": "Ciao" },
                  "tags": [],
                  "createdAt": 1000,
                  "updatedAt": 2000,
                  "pinned": false
                }
              ],
              "allLabels": []
            }
        """.trimIndent()
        val decodedLegacy = BackupSerializer.decode(withoutLockedField).getOrThrow()
        assertEquals(1, decodedLegacy.notes.size)
        assertFalse(decodedLegacy.notes.single().locked)
    }

    @Test
    fun `ReadTimerFormatter handles stopwatch elapsed time and countdown warning or overtime states`() {
        val swZero = ReadTimerFormatter.computeDisplay(elapsedMs = 0L, targetDurationMs = null)
        assertEquals("00:00", swZero.text)
        assertFalse(swZero.isCountdown)
        assertFalse(swZero.isWarning)
        assertFalse(swZero.isOvertime)

        val swHour = ReadTimerFormatter.computeDisplay(elapsedMs = 3_725_000L, targetDurationMs = null)
        assertEquals("1:02:05", swHour.text)

        val fiveMin = 5 * 60_000L
        val cdStart = ReadTimerFormatter.computeDisplay(elapsedMs = 0L, targetDurationMs = fiveMin)
        assertEquals("05:00", cdStart.text)
        assertTrue(cdStart.isCountdown)
        assertFalse(cdStart.isWarning)
        assertFalse(cdStart.isOvertime)

        val cdAfterOneSec = ReadTimerFormatter.computeDisplay(elapsedMs = 1_000L, targetDurationMs = fiveMin)
        assertEquals("04:59", cdAfterOneSec.text)
        assertFalse(cdAfterOneSec.isWarning)

        val cdWarning = ReadTimerFormatter.computeDisplay(elapsedMs = 245_000L, targetDurationMs = fiveMin)
        assertEquals("00:55", cdWarning.text)
        assertTrue(cdWarning.isWarning)
        assertFalse(cdWarning.isOvertime)

        val cdAtZero = ReadTimerFormatter.computeDisplay(elapsedMs = fiveMin, targetDurationMs = fiveMin)
        assertEquals("00:00", cdAtZero.text)
        assertTrue(cdAtZero.isOvertime)

        val cdOvertime = ReadTimerFormatter.computeDisplay(elapsedMs = fiveMin + 12_500L, targetDurationMs = fiveMin)
        assertEquals("+00:12", cdOvertime.text)
        assertTrue(cdOvertime.isOvertime)
        assertFalse(cdOvertime.isWarning)
    }
}
