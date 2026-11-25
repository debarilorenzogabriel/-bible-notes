package it.lgdb.biblenotes.domain

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.HighlightSpan
import it.lgdb.biblenotes.model.RichNoteContent
import kotlin.math.max
import kotlin.math.min

object SpanAdjuster {

    const val BIBLE_REF_TAG = "BIBLE_REF"

    fun inlineRefId(index: Int): String = "bible_ref_$index"

    fun onTextChanged(current: RichNoteContent, newText: String): RichNoteContent {
        val oldText = current.text
        if (oldText == newText) return current

        var prefix = 0
        val minLen = min(oldText.length, newText.length)
        while (prefix < minLen && oldText[prefix] == newText[prefix]) {
            prefix++
        }

        var oldEnd = oldText.length
        var newEnd = newText.length
        while (oldEnd > prefix && newEnd > prefix && oldText[oldEnd - 1] == newText[newEnd - 1]) {
            oldEnd--
            newEnd--
        }

        val removedLen = oldEnd - prefix
        val insertedLen = newEnd - prefix
        val delta = insertedLen - removedLen

        val updatedHighlights = adjustHighlightsAfterEdit(
            spans = current.highlights,
            editStart = prefix,
            oldEditEnd = oldEnd,
            delta = delta,
            maxLength = newText.length
        )

        val updatedRefs = adjustBibleRefsAfterEdit(
            refs = current.bibleRefs,
            newText = newText,
            editStart = prefix,
            oldEditEnd = oldEnd,
            delta = delta
        )

        return RichNoteContent(
            text = newText,
            highlights = updatedHighlights,
            bibleRefs = updatedRefs
        )
    }

    fun applyHighlight(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int,
        color: HighlightColor?
    ): RichNoteContent {
        val start = min(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        val end = max(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        if (start >= end) return content

        val result = mutableListOf<HighlightSpan>()
        for (span in content.highlights) {
            if (span.end <= start || span.start >= end) {
                result.add(span)
            } else {
                if (span.start < start) {
                    result.add(span.copy(end = start))
                }
                if (span.end > end) {
                    result.add(span.copy(start = end))
                }
            }
        }

        if (color != null) {
            result.add(HighlightSpan(start = start, end = end, color = color))
        }

        return content.copy(highlights = mergeAdjacentHighlights(result))
    }

    fun insertBibleRef(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int,
        book: BibleBook,
        chapter: Int,
        verseSpec: String?,
        bookDisplayName: String
    ): Pair<RichNoteContent, Int> {
        val start = min(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        val end = max(selectionStart, selectionEnd).coerceIn(0, content.text.length)

        val display = JwLinkBuilder.formatReferenceDisplay(bookDisplayName, chapter, verseSpec)
        val insertion = "$display "
        val newText = buildString {
            append(content.text.substring(0, start))
            append(insertion)
            append(content.text.substring(end))
        }

        val delta = insertion.length - (end - start)

        val adjustedHighlights = adjustHighlightsAfterEdit(
            spans = content.highlights,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )

        val adjustedExistingRefs = adjustBibleRefsAfterEdit(
            refs = content.bibleRefs,
            newText = newText,
            editStart = start,
            oldEditEnd = end,
            delta = delta
        )

        val newRef = BibleRefSpan(
            start = start,
            end = start + display.length,
            bookNumber = book.number,
            bookName = bookDisplayName,
            chapter = chapter,
            verseSpec = verseSpec?.trim()?.takeIf { it.isNotEmpty() }
        )

        val allRefs = (adjustedExistingRefs + newRef).sortedBy { it.start }
        val newCursor = start + insertion.length

        return RichNoteContent(
            text = newText,
            highlights = adjustedHighlights,
            bibleRefs = allRefs
        ) to newCursor
    }

    fun findRefAtOffset(content: RichNoteContent, offset: Int): BibleRefSpan? =
        content.bibleRefs.firstOrNull { offset in it.start..it.end }

    fun buildAnnotatedString(
        content: RichNoteContent,
        linkColor: Color,
        highlightTextColor: Color = Color(0xFF111111),
        refBackgroundColor: Color = linkColor.copy(alpha = 0.14f)
    ): AnnotatedString {
        val textLength = content.text.length
        return buildAnnotatedString {
            append(content.text)

            content.highlights.forEach { span ->
                val s = span.start.coerceIn(0, textLength)
                val e = span.end.coerceIn(0, textLength)
                if (s < e) {
                    addStyle(
                        style = SpanStyle(
                            background = Color(span.color.argb),
                            color = highlightTextColor
                        ),
                        start = s,
                        end = e
                    )
                }
            }

            content.bibleRefs.forEachIndexed { index, ref ->
                val s = ref.start.coerceIn(0, textLength)
                val e = ref.end.coerceIn(0, textLength)
                if (s < e) {
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            background = refBackgroundColor,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = s,
                        end = e
                    )
                    addStringAnnotation(
                        tag = BIBLE_REF_TAG,
                        annotation = index.toString(),
                        start = s,
                        end = e
                    )
                }
            }
        }
    }

    fun buildInlineAnnotatedString(
        content: RichNoteContent,
        highlightTextColor: Color = Color(0xFF111111)
    ): AnnotatedString {
        val text = content.text
        val textLength = text.length
        val indexedRefs = content.bibleRefs
            .mapIndexed { index, ref -> index to ref }
            .filter { (_, ref) -> ref.start in 0 until ref.end && ref.end <= textLength }
            .sortedBy { (_, ref) -> ref.start }

        return buildAnnotatedString {
            var cursor = 0

            fun appendPlainSegment(segStart: Int, segEnd: Int) {
                if (segStart >= segEnd) return
                val outStart = length
                append(text.substring(segStart, segEnd))
                content.highlights.forEach { span ->
                    val hs = max(segStart, span.start.coerceIn(0, textLength))
                    val he = min(segEnd, span.end.coerceIn(0, textLength))
                    if (hs < he) {
                        addStyle(
                            style = SpanStyle(
                                background = Color(span.color.argb),
                                color = highlightTextColor
                            ),
                            start = outStart + (hs - segStart),
                            end = outStart + (he - segStart)
                        )
                    }
                }
            }

            indexedRefs.forEach { (index, ref) ->
                if (ref.start >= cursor) {
                    appendPlainSegment(cursor, ref.start)
                    appendInlineContent(id = inlineRefId(index), alternateText = ref.displayText)
                    cursor = ref.end
                }
            }

            appendPlainSegment(cursor, textLength)
        }
    }

    private fun adjustHighlightsAfterEdit(
        spans: List<HighlightSpan>,
        editStart: Int,
        oldEditEnd: Int,
        delta: Int,
        maxLength: Int
    ): List<HighlightSpan> {
        val updated = spans.mapNotNull { span ->
            val newStart: Int
            val newEnd: Int

            when {
                // Span completely before the edited range
                span.end <= editStart -> {
                    newStart = span.start
                    newEnd = span.end
                }
                // Span completely after the edited range
                span.start >= oldEditEnd -> {
                    newStart = span.start + delta
                    newEnd = span.end + delta
                }
                // Edit happened strictly inside the span
                span.start <= editStart && span.end >= oldEditEnd -> {
                    newStart = span.start
                    newEnd = span.end + delta
                }
                // Edit overlaps the end of the span
                span.start < editStart && span.end <= oldEditEnd -> {
                    newStart = span.start
                    newEnd = editStart
                }
                // Edit overlaps the start of the span
                span.start >= editStart && span.end > oldEditEnd -> {
                    newStart = editStart + max(0, oldEditEnd - editStart + delta)
                    newEnd = span.end + delta
                }
                else -> {
                    newStart = 0
                    newEnd = 0
                }
            }

            val clampedStart = newStart.coerceIn(0, maxLength)
            val clampedEnd = newEnd.coerceIn(0, maxLength)
            if (clampedStart < clampedEnd) {
                span.copy(start = clampedStart, end = clampedEnd)
            } else {
                null
            }
        }
        return mergeAdjacentHighlights(updated)
    }

    private fun adjustBibleRefsAfterEdit(
        refs: List<BibleRefSpan>,
        newText: String,
        editStart: Int,
        oldEditEnd: Int,
        delta: Int
    ): List<BibleRefSpan> {
        val maxLength = newText.length
        return refs.mapNotNull { ref ->
            val expectedText = ref.displayText
            val candidate: BibleRefSpan? = when {
                ref.end <= editStart -> ref
                ref.start >= oldEditEnd -> ref.copy(
                    start = ref.start + delta,
                    end = ref.end + delta
                )
                else -> {
                    // An edit touched or overlapped the reference span itself: drop if text no longer matches
                    val s = ref.start.coerceIn(0, maxLength)
                    val e = (ref.end + delta).coerceIn(0, maxLength)
                    if (s < e && newText.substring(s, e) == expectedText) {
                        ref.copy(start = s, end = e)
                    } else {
                        null
                    }
                }
            }

            candidate?.takeIf {
                it.start >= 0 &&
                    it.end <= maxLength &&
                    it.start < it.end &&
                    newText.substring(it.start, it.end) == expectedText
            }
        }
    }

    private fun mergeAdjacentHighlights(spans: List<HighlightSpan>): List<HighlightSpan> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedWith(compareBy<HighlightSpan> { it.start }.thenBy { it.end })
        val merged = mutableListOf<HighlightSpan>()
        for (span in sorted) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.color == span.color && span.start <= prev.end) {
                merged[merged.lastIndex] = prev.copy(end = max(prev.end, span.end))
            } else {
                merged.add(span)
            }
        }
        return merged
    }
}
