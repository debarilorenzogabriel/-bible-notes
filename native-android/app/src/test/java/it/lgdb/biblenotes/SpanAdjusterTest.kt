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
}
