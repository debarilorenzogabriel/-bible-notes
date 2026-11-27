package it.lgdb.biblenotes

import it.lgdb.biblenotes.domain.BibleCatalog
import it.lgdb.biblenotes.domain.SpanAdjuster
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.RichNoteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanAdjusterTest {

    @Test
    fun `applyHighlight and clearHighlight split and merge cleanly`() {
        val initial = RichNoteContent(text = "In principio Dio creò i cieli")
        val yellow = SpanAdjuster.applyHighlight(initial, 3, 12, HighlightColor.YELLOW)
        assertEquals(1, yellow.highlights.size)
        assertEquals(3, yellow.highlights[0].start)
        assertEquals(12, yellow.highlights[0].end)

        // Override middle portion with orange
        val mixed = SpanAdjuster.applyHighlight(yellow, 6, 9, HighlightColor.ORANGE)
        assertEquals(3, mixed.highlights.size)
        assertEquals(HighlightColor.YELLOW, mixed.highlights[0].color)
        assertEquals(3..6, mixed.highlights[0].start..mixed.highlights[0].end)
        assertEquals(HighlightColor.ORANGE, mixed.highlights[1].color)
        assertEquals(6..9, mixed.highlights[1].start..mixed.highlights[1].end)
        assertEquals(HighlightColor.YELLOW, mixed.highlights[2].color)
        assertEquals(9..12, mixed.highlights[2].start..mixed.highlights[2].end)

        // Clear all highlights
        val cleared = SpanAdjuster.applyHighlight(mixed, 0, initial.text.length, null)
        assertTrue(cleared.highlights.isEmpty())
    }

    @Test
    fun `insertBibleRef supports multiple clickable references and preserves highlight offsets`() {
        val genesis = BibleCatalog.findByNumber(1)!!
        val john = BibleCatalog.findByNumber(43)!!

        val base = SpanAdjuster.applyHighlight(
            RichNoteContent(text = "Studio iniziale: "),
            0,
            6,
            HighlightColor.GREEN
        )

        val (afterFirst, cursor1) = SpanAdjuster.insertBibleRef(
            content = base,
            selectionStart = base.text.length,
            selectionEnd = base.text.length,
            book = genesis,
            chapter = 1,
            verseSpec = "1",
            bookDisplayName = genesis.nameIt
        )

        val withSeparator = SpanAdjuster.onTextChanged(afterFirst, afterFirst.text + "e ")
        val (afterSecond, _) = SpanAdjuster.insertBibleRef(
            content = withSeparator,
            selectionStart = withSeparator.text.length,
            selectionEnd = withSeparator.text.length,
            book = john,
            chapter = 3,
            verseSpec = "16",
            bookDisplayName = john.nameIt
        )

        assertTrue(cursor1 > base.text.length)
        assertEquals(2, afterSecond.bibleRefs.size)
        assertEquals("Genesi 1:1", afterSecond.bibleRefs[0].displayText)
        assertEquals("Giovanni 3:16", afterSecond.bibleRefs[1].displayText)
        assertEquals(1, afterSecond.highlights.size)
        assertEquals(0, afterSecond.highlights[0].start)
        assertEquals(6, afterSecond.highlights[0].end)
    }

    @Test
    fun `editing text before reference shifts its offsets while editing inside invalidates it`() {
        val john = BibleCatalog.findByNumber(43)!!
        val (withRef, _) = SpanAdjuster.insertBibleRef(
            content = RichNoteContent(text = "Leggi "),
            selectionStart = 6,
            selectionEnd = 6,
            book = john,
            chapter = 17,
            verseSpec = "3",
            bookDisplayName = john.nameIt
        )

        // Prepend text before the reference
        val shifted = SpanAdjuster.onTextChanged(withRef, "Nota: " + withRef.text)
        assertEquals(1, shifted.bibleRefs.size)
        val ref = shifted.bibleRefs.first()
        assertEquals("Giovanni 17:3", shifted.text.substring(ref.start, ref.end))

        // Corrupt the reference text itself
        val broken = SpanAdjuster.onTextChanged(
            shifted,
            shifted.text.replace("Giovanni 17:3", "Giov 17:3")
        )
        assertTrue(broken.bibleRefs.isEmpty())
    }

    @Test
    fun `buildInlineAnnotatedString replaces reference span with inline content and preserves highlights`() {
        val john = BibleCatalog.findByNumber(43)!!
        val (withRef, _) = SpanAdjuster.insertBibleRef(
            content = RichNoteContent(text = "Nota su "),
            selectionStart = 8,
            selectionEnd = 8,
            book = john,
            chapter = 3,
            verseSpec = "16",
            bookDisplayName = john.nameIt
        )
        val withHighlight = SpanAdjuster.applyHighlight(withRef, 0, 4, HighlightColor.YELLOW)

        val inlineAnnotated = SpanAdjuster.buildInlineAnnotatedString(withHighlight)
        val annotations = inlineAnnotated.getStringAnnotations(0, inlineAnnotated.length)
        assertTrue(annotations.any { it.item == SpanAdjuster.inlineRefId(0) })
        assertTrue(inlineAnnotated.text.startsWith("Nota su "))
        assertEquals(1, inlineAnnotated.spanStyles.size)
        assertEquals(0, inlineAnnotated.spanStyles.first().start)
        assertEquals(4, inlineAnnotated.spanStyles.first().end)
    }

    @Test
    fun `buildEditableTransformedNote produces monotonic bounded offset mapping for multiple and adjacent refs`() {
        val genesis = BibleCatalog.findByNumber(1)!!
        val john = BibleCatalog.findByNumber(43)!!

        val (withFirst, cursor1) = SpanAdjuster.insertBibleRef(
            content = RichNoteContent(text = "Inizio "),
            selectionStart = 7,
            selectionEnd = 7,
            book = genesis,
            chapter = 1,
            verseSpec = "1",
            bookDisplayName = genesis.nameIt
        )
        // Remove trailing space between first and second ref to test adjacent refs (end == next.start)
        val trimmed = SpanAdjuster.onTextChanged(withFirst, withFirst.text.trimEnd())
        val (withSecond, _) = SpanAdjuster.insertBibleRef(
            content = trimmed,
            selectionStart = trimmed.text.length,
            selectionEnd = trimmed.text.length,
            book = john,
            chapter = 3,
            verseSpec = "16",
            bookDisplayName = john.nameIt
        )
        val highlighted = SpanAdjuster.applyHighlight(withSecond, 0, withSecond.text.length, HighlightColor.YELLOW)

        val transformed = SpanAdjuster.buildEditableTransformedNote(
            content = highlighted,
            refTextColor = androidx.compose.ui.graphics.Color.Black
        )

        assertTrue(cursor1 > 7)
        assertEquals(2, transformed.chipRanges.size)
        val origLen = highlighted.text.length
        val transLen = transformed.annotatedString.text.length

        // Original -> Transformed: strictly bounded and monotonic
        var prevT = -1
        for (o in 0..origLen) {
            val t = transformed.offsetMapping.originalToTransformed(o)
            assertTrue("originalToTransformed($o)=$t out of range 0..$transLen", t in 0..transLen)
            assertTrue("originalToTransformed not monotonic at $o: $prevT -> $t", t >= prevT)
            prevT = t
        }

        // Transformed -> Original: strictly bounded and monotonic
        var prevO = -1
        for (t in 0..transLen) {
            val o = transformed.offsetMapping.transformedToOriginal(t)
            assertTrue("transformedToOriginal($t)=$o out of range 0..$origLen", o in 0..origLen)
            assertTrue("transformedToOriginal not monotonic at $t: $prevO -> $o", o >= prevO)
            prevO = o
        }

        // Each chip contains non-breaking spaces for unbroken display
        transformed.chipRanges.forEach { range ->
            val chipSlice = transformed.annotatedString.text.substring(range.transformedStart, range.transformedEnd)
            assertTrue(chipSlice.startsWith("\u00A0"))
            assertTrue(chipSlice.endsWith("\u00A0"))
            assertTrue(!chipSlice.contains(' '))
        }
    }

    @Test
    fun `legacy full-name BibleRefSpans render abbreviated in edit chips and preview with valid offset mapping`() {
        val legacyContent = RichNoteContent(
            text = "Leggi Giovanni 15:20 e Matteo 5:11,12 oggi",
            highlights = listOf(
                it.lgdb.biblenotes.model.HighlightSpan(38, 42, it.lgdb.biblenotes.model.HighlightColor.GREEN)
            ),
            bibleRefs = listOf(
                it.lgdb.biblenotes.model.BibleRefSpan(6, 20, 43, "Giovanni", 15, "20"),
                it.lgdb.biblenotes.model.BibleRefSpan(23, 37, 40, "Matteo", 5, "11,12")
            )
        )

        val preview = SpanAdjuster.buildAnnotatedString(
            content = legacyContent,
            linkColor = androidx.compose.ui.graphics.Color.Blue
        )
        assertEquals("Leggi Gv 15:20 e Mt 5:11,12 oggi", preview.text)

        val transformed = SpanAdjuster.buildEditableTransformedNote(
            content = legacyContent,
            refTextColor = androidx.compose.ui.graphics.Color.Black
        )
        assertTrue(transformed.annotatedString.text.contains("Gv\u00A015:20"))
        assertTrue(transformed.annotatedString.text.contains("Mt\u00A05:11,12"))

        val origLen = legacyContent.text.length
        val transLen = transformed.annotatedString.text.length
        var prevT = -1
        for (o in 0..origLen) {
            val t = transformed.offsetMapping.originalToTransformed(o)
            assertTrue(t in 0..transLen)
            assertTrue(t >= prevT)
            prevT = t
        }
        var prevO = -1
        for (t in 0..transLen) {
            val o = transformed.offsetMapping.transformedToOriginal(t)
            assertTrue(o in 0..origLen)
            assertTrue(o >= prevO)
            prevO = o
        }

        // Verify all 66 books have unique Italian abbreviations matching official JW Library set
        assertEquals(66, BibleCatalog.books.size)
        assertEquals(66, BibleCatalog.books.map { it.abbrIt }.distinct().size)
        assertEquals("Gsè", BibleCatalog.findByNumber(6)?.abbrIt)
        assertEquals("Gle", BibleCatalog.findByNumber(29)?.abbrIt)
        assertEquals("Gv", BibleCatalog.findByNumber(43)?.abbrIt)
        assertEquals("1Ts", BibleCatalog.findByNumber(52)?.abbrIt)
        assertEquals("Ri", BibleCatalog.findByNumber(66)?.abbrIt)

        // Verse range hyphens are replaced with non-breaking hyphen so chips stay unbroken on one line
        val rangeContent = RichNoteContent(
            text = "Vedi Giovanni 5:3-6",
            bibleRefs = listOf(it.lgdb.biblenotes.model.BibleRefSpan(5, 19, 43, "Giovanni", 5, "3-6"))
        )
        val rangeTransformed = SpanAdjuster.buildEditableTransformedNote(
            content = rangeContent,
            refTextColor = androidx.compose.ui.graphics.Color.Black
        )
        val chipRange = rangeTransformed.chipRanges.single()
        assertEquals(
            "Gv\u00A05:3\u20116",
            rangeTransformed.annotatedString.text.substring(chipRange.textStart, chipRange.textEnd)
        )
        assertEquals(7, chipRange.textStart - chipRange.transformedStart)
        assertEquals(3, chipRange.transformedEnd - chipRange.textEnd)
    }

    @Test
    fun `normalizeLinkUrl and isValidLinkUrl accept web mailto tel and reject unsafe schemes`() {
        assertEquals("https://jw.org", SpanAdjuster.normalizeLinkUrl("jw.org"))
        assertEquals("https://example.com/path", SpanAdjuster.normalizeLinkUrl("  HTTPS://example.com/path "))
        assertTrue(SpanAdjuster.isValidLinkUrl("jw.org"))
        assertTrue(SpanAdjuster.isValidLinkUrl("https://www.jw.org/it"))
        assertTrue(SpanAdjuster.isValidLinkUrl("mailto:test@example.com"))
        assertTrue(SpanAdjuster.isValidLinkUrl("tel:+390123456789"))

        assertTrue(!SpanAdjuster.isValidLinkUrl(""))
        assertTrue(!SpanAdjuster.isValidLinkUrl("javascript:alert(1)"))
        assertTrue(!SpanAdjuster.isValidLinkUrl("file:///etc/passwd"))
        assertTrue(!SpanAdjuster.isValidLinkUrl("not a url"))
    }

    @Test
    fun `applyLink insertLink and onTextChanged keep link spans synchronized`() {
        val base = RichNoteContent(text = "Visita sito ufficiale oggi")
        val linked = SpanAdjuster.applyLink(base, 7, 21, "jw.org")
        assertEquals(1, linked.links.size)
        assertEquals("https://jw.org", linked.links.first().url)
        assertEquals("sito ufficiale", linked.text.substring(linked.links.first().start, linked.links.first().end))

        // Typing before the link shifts it forward without pulling the prefix into the link
        val shifted = SpanAdjuster.onTextChanged(linked, "Ciao! " + linked.text)
        assertEquals(13, shifted.links.first().start)
        assertEquals(27, shifted.links.first().end)
        assertEquals("sito ufficiale", shifted.text.substring(shifted.links.first().start, shifted.links.first().end))

        // Typing space right after the link leaves link end untouched
        val spaceAfter = SpanAdjuster.onTextChanged(
            shifted,
            shifted.text.substring(0, 27) + " adesso" + shifted.text.substring(27)
        )
        assertEquals(13, spaceAfter.links.first().start)
        assertEquals(27, spaceAfter.links.first().end)

        // Removing the link clears the span
        val removed = SpanAdjuster.applyLink(spaceAfter, 13, 27, null)
        assertTrue(removed.links.isEmpty())

        // Inserting a link at a collapsed cursor inserts text + LinkSpan
        val (inserted, cursor) = SpanAdjuster.insertLink(
            content = RichNoteContent(text = "Apri "),
            selectionStart = 5,
            selectionEnd = 5,
            displayText = "JW.ORG",
            url = "www.jw.org"
        )
        assertEquals("Apri JW.ORG", inserted.text)
        assertEquals(11, cursor)
        assertEquals(1, inserted.links.size)
        assertEquals(5, inserted.links.first().start)
        assertEquals(11, inserted.links.first().end)
        assertEquals("https://www.jw.org", inserted.links.first().url)

        val inlineAnnotated = SpanAdjuster.buildInlineAnnotatedString(
            content = inserted,
            onLinkClick = {}
        )
        assertTrue(inlineAnnotated.getLinkAnnotations(5, 11).isNotEmpty())

        val editable = SpanAdjuster.buildEditableTransformedNote(
            content = inserted,
            refTextColor = androidx.compose.ui.graphics.Color.Black,
            linkTextColor = androidx.compose.ui.graphics.Color.Blue
        )
        assertTrue(editable.annotatedString.spanStyles.any { it.start == 5 && it.end == 11 })
    }

    @Test
    fun `inlineAnnotatedOffsetToOriginalOffset maps read-mode tap positions accurately across Bible refs`() {
        val john = BibleCatalog.findByNumber(43)!!
        val (withRef, _) = SpanAdjuster.insertBibleRef(
            content = RichNoteContent(text = "Leggi "),
            selectionStart = 6,
            selectionEnd = 6,
            book = john,
            chapter = 3,
            verseSpec = "16",
            bookDisplayName = john.nameIt
        )
        val full = SpanAdjuster.onTextChanged(withRef, withRef.text + "e medita")
        // Original: "Leggi Giovanni 3:16 e medita" (ref 6..19)
        // Inline:   "Leggi Gv 3:16 e medita"       (inline "Gv 3:16" is 6..13)
        assertEquals(3, SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(full, it.lgdb.biblenotes.model.AppLanguage.IT, 3))
        assertEquals(6, SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(full, it.lgdb.biblenotes.model.AppLanguage.IT, 7))
        assertEquals(19, SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(full, it.lgdb.biblenotes.model.AppLanguage.IT, 12))
        assertEquals(
            full.text.length,
            SpanAdjuster.inlineAnnotatedOffsetToOriginalOffset(
                full,
                it.lgdb.biblenotes.model.AppLanguage.IT,
                "Leggi Gv 3:16 e medita".length
            )
        )
    }
}
