package it.lgdb.biblenotes.domain

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.HighlightSpan
import it.lgdb.biblenotes.model.LinkSpan
import it.lgdb.biblenotes.model.NoteTextSize
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.model.TextFormatSpan
import java.net.URI
import kotlin.math.max
import kotlin.math.min

data class TransformedRefRange(
    val ref: BibleRefSpan,
    val transformedStart: Int,
    val textStart: Int,
    val textEnd: Int,
    val transformedEnd: Int
)

data class EditableTransformedNote(
    val annotatedString: AnnotatedString,
    val offsetMapping: OffsetMapping,
    val chipRanges: List<TransformedRefRange>
)

object SpanAdjuster {

    const val BIBLE_REF_TAG = "BIBLE_REF"
    const val LINK_TAG = "URL_LINK"
    private const val EDITABLE_CHIP_PREFIX = "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0"
    private const val EDITABLE_CHIP_SUFFIX = "\u00A0\u00A0\u00A0"
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")

    fun inlineRefId(index: Int): String = "bible_ref_$index"

    fun normalizeLinkUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        return if (SCHEME_REGEX.matches(trimmed)) {
            val colonIdx = trimmed.indexOf(':')
            val scheme = trimmed.substring(0, colonIdx).lowercase()
            "$scheme${trimmed.substring(colonIdx)}"
        } else {
            if (trimmed.any { it.isWhitespace() }) trimmed else "https://$trimmed"
        }
    }

    fun isValidLinkUrl(rawUrl: String): Boolean {
        val normalized = normalizeLinkUrl(rawUrl)
        if (normalized.isBlank() || normalized.any { it.isWhitespace() }) return false
        return when {
            normalized.startsWith("mailto:") -> {
                val addr = normalized.removePrefix("mailto:")
                val atIdx = addr.indexOf('@')
                atIdx > 0 && addr.indexOf('.', atIdx) > atIdx + 1 && atIdx < addr.lastIndex
            }
            normalized.startsWith("tel:") -> {
                val digits = normalized.removePrefix("tel:")
                digits.count { it.isDigit() } >= 3 &&
                    digits.all { it.isDigit() || it in "+()-." }
            }
            normalized.startsWith("http://") || normalized.startsWith("https://") -> {
                val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
                val host = uri.host ?: return false
                host.isNotBlank() &&
                    !host.startsWith('.') &&
                    !host.endsWith('.') &&
                    (host.contains('.') || host.equals("localhost", ignoreCase = true))
            }
            else -> false
        }
    }

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

        val updatedLinks = adjustLinksAfterEdit(
            links = current.links,
            editStart = prefix,
            oldEditEnd = oldEnd,
            delta = delta,
            maxLength = newText.length
        )

        val updatedBolds = adjustFormatSpansAfterEdit(
            spans = current.bolds,
            editStart = prefix,
            oldEditEnd = oldEnd,
            delta = delta,
            maxLength = newText.length
        )

        val updatedItalics = adjustFormatSpansAfterEdit(
            spans = current.italics,
            editStart = prefix,
            oldEditEnd = oldEnd,
            delta = delta,
            maxLength = newText.length
        )

        return RichNoteContent(
            text = newText,
            highlights = updatedHighlights,
            bibleRefs = updatedRefs,
            links = updatedLinks,
            bolds = updatedBolds,
            italics = updatedItalics
        )
    }

    fun isRangeCoveredBySpans(
        spans: List<TextFormatSpan>,
        selectionStart: Int,
        selectionEnd: Int
    ): Boolean {
        val start = min(selectionStart, selectionEnd)
        val end = max(selectionStart, selectionEnd)
        if (start >= end || spans.isEmpty()) return false
        var coveredUpTo = start
        for (span in spans.sortedBy { it.start }) {
            if (span.end <= coveredUpTo) continue
            if (span.start > coveredUpTo) return false
            coveredUpTo = max(coveredUpTo, span.end)
            if (coveredUpTo >= end) return true
        }
        return coveredUpTo >= end
    }

    fun toggleBold(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int
    ): RichNoteContent {
        val updated = toggleFormatSpan(content.bolds, selectionStart, selectionEnd, content.text.length)
        return if (updated == content.bolds) content else content.copy(bolds = updated)
    }

    fun toggleItalic(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int
    ): RichNoteContent {
        val updated = toggleFormatSpan(content.italics, selectionStart, selectionEnd, content.text.length)
        return if (updated == content.italics) content else content.copy(italics = updated)
    }

    private fun toggleFormatSpan(
        spans: List<TextFormatSpan>,
        selectionStart: Int,
        selectionEnd: Int,
        maxLength: Int
    ): List<TextFormatSpan> {
        val start = min(selectionStart, selectionEnd).coerceIn(0, maxLength)
        val end = max(selectionStart, selectionEnd).coerceIn(0, maxLength)
        if (start >= end) return spans

        val shouldRemove = isRangeCoveredBySpans(spans, start, end)
        val result = mutableListOf<TextFormatSpan>()
        for (span in spans) {
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
        if (!shouldRemove) {
            result.add(TextFormatSpan(start = start, end = end))
        }
        return mergeAdjacentFormatSpans(result)
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

        val adjustedLinks = adjustLinksAfterEdit(
            links = content.links,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )

        val adjustedBolds = adjustFormatSpansAfterEdit(
            spans = content.bolds,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )

        val adjustedItalics = adjustFormatSpansAfterEdit(
            spans = content.italics,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
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
            bibleRefs = allRefs,
            links = adjustedLinks,
            bolds = adjustedBolds,
            italics = adjustedItalics
        ) to newCursor
    }

    fun convertDetectedBibleRef(
        content: RichNoteContent,
        detected: DetectedBibleRef,
        language: AppLanguage,
        currentCursor: Int? = null
    ): Pair<RichNoteContent, Int> {
        val textLength = content.text.length
        val start = detected.start.coerceIn(0, textLength)
        val end = detected.end.coerceIn(start, textLength)
        if (start >= end) {
            return content to (currentCursor ?: textLength).coerceIn(0, textLength)
        }

        val bookDisplayName = detected.book.localizedAbbr(language)
        val display = JwLinkBuilder.formatReferenceDisplay(bookDisplayName, detected.chapter, detected.verseSpec)
        val newText = buildString {
            append(content.text.substring(0, start))
            append(display)
            append(content.text.substring(end))
        }
        val delta = display.length - (end - start)

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
        val adjustedLinks = adjustLinksAfterEdit(
            links = content.links,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )
        val adjustedBolds = adjustFormatSpansAfterEdit(
            spans = content.bolds,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )
        val adjustedItalics = adjustFormatSpansAfterEdit(
            spans = content.italics,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )

        val newRef = BibleRefSpan(
            start = start,
            end = start + display.length,
            bookNumber = detected.book.number,
            bookName = bookDisplayName,
            chapter = detected.chapter,
            verseSpec = detected.verseSpec?.trim()?.takeIf { it.isNotEmpty() }
        )

        val newCursor = when {
            currentCursor == null -> start + display.length
            currentCursor <= start -> currentCursor
            currentCursor >= end -> (currentCursor + delta).coerceIn(0, newText.length)
            else -> start + display.length
        }

        return RichNoteContent(
            text = newText,
            highlights = adjustedHighlights,
            bibleRefs = (adjustedExistingRefs + newRef).sortedBy { it.start },
            links = adjustedLinks,
            bolds = adjustedBolds,
            italics = adjustedItalics
        ) to newCursor
    }

    fun convertAllDetectedBibleRefs(
        content: RichNoteContent,
        detectedRefs: List<DetectedBibleRef>,
        language: AppLanguage,
        currentCursor: Int
    ): Pair<RichNoteContent, Int> {
        var updatedContent = content
        var updatedCursor = currentCursor.coerceIn(0, content.text.length)
        detectedRefs
            .sortedByDescending { it.start }
            .forEach { ref ->
                val result = convertDetectedBibleRef(
                    content = updatedContent,
                    detected = ref,
                    language = language,
                    currentCursor = updatedCursor
                )
                updatedContent = result.first
                updatedCursor = result.second
            }
        return updatedContent to updatedCursor
    }

    fun applyLink(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int,
        url: String?
    ): RichNoteContent {
        val start = min(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        val end = max(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        if (start >= end) return content

        val normalizedUrl = url?.let(::normalizeLinkUrl)?.takeIf { isValidLinkUrl(it) }
        if (!url.isNullOrBlank() && normalizedUrl == null) return content

        val result = mutableListOf<LinkSpan>()
        for (span in content.links) {
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

        if (normalizedUrl != null) {
            result.add(LinkSpan(start = start, end = end, url = normalizedUrl))
        }

        return content.copy(links = mergeAdjacentLinks(result))
    }

    fun insertLink(
        content: RichNoteContent,
        selectionStart: Int,
        selectionEnd: Int,
        displayText: String,
        url: String
    ): Pair<RichNoteContent, Int> {
        val normalizedUrl = normalizeLinkUrl(url)
        if (!isValidLinkUrl(normalizedUrl)) return content to min(selectionStart, selectionEnd)

        val start = min(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        val end = max(selectionStart, selectionEnd).coerceIn(0, content.text.length)
        val insertion = displayText.ifEmpty { normalizedUrl }

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
        val adjustedRefs = adjustBibleRefsAfterEdit(
            refs = content.bibleRefs,
            newText = newText,
            editStart = start,
            oldEditEnd = end,
            delta = delta
        )
        val adjustedLinks = adjustLinksAfterEdit(
            links = content.links,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )
        val adjustedBolds = adjustFormatSpansAfterEdit(
            spans = content.bolds,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )
        val adjustedItalics = adjustFormatSpansAfterEdit(
            spans = content.italics,
            editStart = start,
            oldEditEnd = end,
            delta = delta,
            maxLength = newText.length
        )

        val updatedContent = applyLink(
            content = RichNoteContent(
                text = newText,
                highlights = adjustedHighlights,
                bibleRefs = adjustedRefs,
                links = adjustedLinks,
                bolds = adjustedBolds,
                italics = adjustedItalics
            ),
            selectionStart = start,
            selectionEnd = start + insertion.length,
            url = normalizedUrl
        )
        return updatedContent to (start + insertion.length)
    }

    fun findRefAtOffset(content: RichNoteContent, offset: Int): BibleRefSpan? =
        content.bibleRefs.firstOrNull { offset in it.start..it.end }

    fun findLinkAtOffset(content: RichNoteContent, offset: Int): LinkSpan? =
        content.links.firstOrNull { offset in it.start..it.end }

    fun findLinkInRange(content: RichNoteContent, selectionStart: Int, selectionEnd: Int): LinkSpan? {
        val s = min(selectionStart, selectionEnd)
        val e = max(selectionStart, selectionEnd)
        if (s == e) return findLinkAtOffset(content, s)
        return content.links.firstOrNull { it.start < e && it.end > s }
    }

    fun inlineAnnotatedOffsetToOriginalOffset(
        content: RichNoteContent,
        language: AppLanguage,
        annotatedOffset: Int,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
    ): Int {
        val textLength = content.text.length
        if (textLength == 0) return 0
        val validRefs = content.bibleRefs
            .filter { it.start in 0 until it.end && it.end <= textLength }
            .sortedBy { it.start }

        var origCursor = 0
        var annCursor = 0
        for (ref in validRefs) {
            if (ref.start < origCursor) continue
            val plainLen = ref.start - origCursor
            if (annotatedOffset <= annCursor + plainLen) {
                return (origCursor + (annotatedOffset - annCursor)).coerceIn(0, textLength)
            }
            origCursor = ref.end
            annCursor += plainLen
            val inlineLen = JwLinkBuilder.formatReference(ref, language, refDisplayStyle).length.coerceAtLeast(1)
            if (annotatedOffset < annCursor + inlineLen) {
                val rel = annotatedOffset - annCursor
                return if (rel <= inlineLen / 2) ref.start else ref.end
            }
            annCursor += inlineLen
        }
        return (origCursor + (annotatedOffset - annCursor)).coerceIn(0, textLength)
    }

    fun originalRangeToInlineAnnotatedRange(
        content: RichNoteContent,
        origStart: Int,
        origEnd: Int,
        language: AppLanguage = AppLanguage.IT,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
    ): IntRange {
        val textLength = content.text.length
        if (textLength == 0) return 0..0
        val validRefs = content.bibleRefs
            .filter { it.start in 0 until it.end && it.end <= textLength }
            .sortedBy { it.start }

        fun mapPoint(offset: Int, isEnd: Boolean): Int {
            val clamped = offset.coerceIn(0, textLength)
            var origCursor = 0
            var annCursor = 0
            for (ref in validRefs) {
                if (ref.start < origCursor) continue
                val plainLen = ref.start - origCursor
                if (clamped <= ref.start) {
                    return annCursor + (clamped - origCursor)
                }
                origCursor = ref.end
                annCursor += plainLen
                val inlineLen = JwLinkBuilder.formatReference(ref, language, refDisplayStyle).length.coerceAtLeast(1)
                if (clamped <= ref.end) {
                    return if (isEnd || clamped == ref.end) annCursor + inlineLen else annCursor
                }
                annCursor += inlineLen
            }
            return annCursor + (clamped - origCursor)
        }

        val s = mapPoint(min(origStart, origEnd), isEnd = false)
        val e = mapPoint(max(origStart, origEnd), isEnd = true)
        return s until max(s, e)
    }

    fun buildAnnotatedString(
        content: RichNoteContent,
        linkColor: Color,
        highlightTextColor: Color = Color(0xFF111111),
        refBackgroundColor: Color = linkColor.copy(alpha = 0.14f),
        language: AppLanguage = AppLanguage.IT,
        urlColor: Color = linkColor,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
    ): AnnotatedString {
        val text = content.text
        val textLength = text.length
        val indexedRefs = content.bibleRefs
            .mapIndexed { index, ref -> index to ref }
            .filter { (_, ref) -> ref.start in 0 until ref.end && ref.end <= textLength }
            .sortedBy { (_, ref) -> ref.start }

        val listLines = ListFormattingHelper.findRecognizedListLines(text)

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
                content.bolds.forEach { span ->
                    val bs = max(segStart, span.start.coerceIn(0, textLength))
                    val be = min(segEnd, span.end.coerceIn(0, textLength))
                    if (bs < be) {
                        addStyle(
                            style = SpanStyle(fontWeight = FontWeight.Bold),
                            start = outStart + (bs - segStart),
                            end = outStart + (be - segStart)
                        )
                    }
                }
                content.italics.forEach { span ->
                    val iss = max(segStart, span.start.coerceIn(0, textLength))
                    val ie = min(segEnd, span.end.coerceIn(0, textLength))
                    if (iss < ie) {
                        addStyle(
                            style = SpanStyle(fontStyle = FontStyle.Italic),
                            start = outStart + (iss - segStart),
                            end = outStart + (ie - segStart)
                        )
                    }
                }
                listLines.forEach { listLine ->
                    val ms = max(segStart, listLine.markerStart)
                    val me = min(segEnd, listLine.markerEnd)
                    if (ms < me) {
                        addStyle(
                            style = SpanStyle(
                                color = urlColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            start = outStart + (ms - segStart),
                            end = outStart + (me - segStart)
                        )
                    }
                }
                content.links.forEach { link ->
                    val ls = max(segStart, link.start.coerceIn(0, textLength))
                    val le = min(segEnd, link.end.coerceIn(0, textLength))
                    if (ls < le) {
                        addStyle(
                            style = SpanStyle(
                                color = urlColor,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = outStart + (ls - segStart),
                            end = outStart + (le - segStart)
                        )
                    }
                }
            }

            indexedRefs.forEach { (index, ref) ->
                if (ref.start >= cursor) {
                    appendPlainSegment(cursor, ref.start)
                    val refStart = length
                    append(JwLinkBuilder.formatReference(ref, language, refDisplayStyle))
                    val refEnd = length
                    addStyle(
                        style = SpanStyle(
                            color = linkColor,
                            background = refBackgroundColor,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = refStart,
                        end = refEnd
                    )
                    addStringAnnotation(
                        tag = BIBLE_REF_TAG,
                        annotation = index.toString(),
                        start = refStart,
                        end = refEnd
                    )
                    cursor = ref.end
                }
            }

            appendPlainSegment(cursor, textLength)
        }
    }

    fun buildInlineAnnotatedString(
        content: RichNoteContent,
        highlightTextColor: Color = Color(0xFF111111),
        language: AppLanguage = AppLanguage.IT,
        linkColor: Color = Color(0xFF1A73E8),
        onLinkClick: ((String) -> Unit)? = null,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
    ): AnnotatedString {
        val text = content.text
        val textLength = text.length
        val indexedRefs = content.bibleRefs
            .mapIndexed { index, ref -> index to ref }
            .filter { (_, ref) -> ref.start in 0 until ref.end && ref.end <= textLength }
            .sortedBy { (_, ref) -> ref.start }

        val listLines = ListFormattingHelper.findRecognizedListLines(text)

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
                content.bolds.forEach { span ->
                    val bs = max(segStart, span.start.coerceIn(0, textLength))
                    val be = min(segEnd, span.end.coerceIn(0, textLength))
                    if (bs < be) {
                        addStyle(
                            style = SpanStyle(fontWeight = FontWeight.Bold),
                            start = outStart + (bs - segStart),
                            end = outStart + (be - segStart)
                        )
                    }
                }
                content.italics.forEach { span ->
                    val iss = max(segStart, span.start.coerceIn(0, textLength))
                    val ie = min(segEnd, span.end.coerceIn(0, textLength))
                    if (iss < ie) {
                        addStyle(
                            style = SpanStyle(fontStyle = FontStyle.Italic),
                            start = outStart + (iss - segStart),
                            end = outStart + (ie - segStart)
                        )
                    }
                }
                listLines.forEach { listLine ->
                    val ms = max(segStart, listLine.markerStart)
                    val me = min(segEnd, listLine.markerEnd)
                    if (ms < me) {
                        addStyle(
                            style = SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            start = outStart + (ms - segStart),
                            end = outStart + (me - segStart)
                        )
                    }
                }
                content.links.forEach { link ->
                    val ls = max(segStart, link.start.coerceIn(0, textLength))
                    val le = min(segEnd, link.end.coerceIn(0, textLength))
                    if (ls < le) {
                        val spanStart = outStart + (ls - segStart)
                        val spanEnd = outStart + (le - segStart)
                        val linkStyle = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                        if (onLinkClick != null) {
                            addLink(
                                clickable = LinkAnnotation.Clickable(
                                    tag = LINK_TAG,
                                    styles = TextLinkStyles(style = linkStyle),
                                    linkInteractionListener = { onLinkClick(link.url) }
                                ),
                                start = spanStart,
                                end = spanEnd
                            )
                        } else {
                            addStyle(
                                style = linkStyle,
                                start = spanStart,
                                end = spanEnd
                            )
                        }
                    }
                }
            }

            indexedRefs.forEach { (index, ref) ->
                if (ref.start >= cursor) {
                    appendPlainSegment(cursor, ref.start)
                    appendInlineContent(
                        id = inlineRefId(index),
                        alternateText = JwLinkBuilder.formatReference(ref, language, refDisplayStyle)
                    )
                    cursor = ref.end
                }
            }

            appendPlainSegment(cursor, textLength)
        }
    }

    fun buildEditableTransformedNote(
        content: RichNoteContent,
        refTextColor: Color,
        language: AppLanguage = AppLanguage.IT,
        highlightTextColor: Color = Color(0xFF111111),
        linkTextColor: Color = refTextColor,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED,
        textSize: NoteTextSize = NoteTextSize.NORMAL
    ): EditableTransformedNote {
        val text = content.text
        val originalLength = text.length
        val chipScale = textSize.chipFontSp / 13.5f

        // Keep strictly non-overlapping, valid, sorted Bible reference spans
        val validRefs = mutableListOf<BibleRefSpan>()
        var lastEnd = 0
        for (ref in content.bibleRefs.sortedBy { it.start }) {
            if (ref.start >= lastEnd && ref.start < ref.end && ref.end <= originalLength) {
                validRefs.add(ref)
                lastEnd = ref.end
            }
        }

        val chipRanges = mutableListOf<TransformedRefRange>()
        val annotated = buildAnnotatedString {
            var cursor = 0
            validRefs.forEach { ref ->
                if (ref.start > cursor) {
                    append(text.substring(cursor, ref.start))
                }
                val chipStart = length
                // Multiple non-breaking spaces reserve deterministic width for the book icon + left padding
                // without relying on single-glyph letterSpacing (which splits asymmetrically at line-start).
                append(EDITABLE_CHIP_PREFIX)
                val refTextStart = length
                addStyle(
                    style = SpanStyle(
                        color = Color.Transparent,
                        background = Color.Transparent,
                        fontSize = (15f * chipScale).sp,
                        letterSpacing = (0.8f * chipScale).sp
                    ),
                    start = chipStart,
                    end = refTextStart
                )

                val formattedText = JwLinkBuilder.formatReference(ref, language, refDisplayStyle)
                append(
                    formattedText
                        .replace(' ', '\u00A0')
                        .replace('-', '\u2011')
                )
                val refTextEnd = length
                val chipTextStyle = SpanStyle(
                    color = refTextColor,
                    background = Color.Transparent,
                    fontSize = textSize.chipFontSp.sp,
                    fontWeight = FontWeight.Medium,
                    baselineShift = BaselineShift(0.015f)
                )
                addStyle(
                    style = chipTextStyle,
                    start = refTextStart,
                    end = refTextEnd
                )

                append(EDITABLE_CHIP_SUFFIX)
                val chipEnd = length
                addStyle(
                    style = SpanStyle(
                        color = Color.Transparent,
                        background = Color.Transparent,
                        fontSize = (14f * chipScale).sp,
                        letterSpacing = (0.4f * chipScale).sp
                    ),
                    start = refTextEnd,
                    end = chipEnd
                )

                chipRanges.add(
                    TransformedRefRange(
                        ref = ref,
                        transformedStart = chipStart,
                        textStart = refTextStart,
                        textEnd = refTextEnd,
                        transformedEnd = chipEnd
                    )
                )
                cursor = ref.end
            }
            if (cursor < originalLength) {
                append(text.substring(cursor, originalLength))
            }

            fun mapOriginalOffset(offset: Int): Int {
                val clamped = offset.coerceIn(0, originalLength)
                var origCursor = 0
                var transCursor = 0
                for (i in validRefs.indices) {
                    val ref = validRefs[i]
                    val range = chipRanges[i]
                    val dispLen = (range.textEnd - range.textStart).coerceAtLeast(1)
                    val oldLen = (ref.end - ref.start).coerceAtLeast(1)
                    if (clamped <= ref.start) {
                        return (transCursor + (clamped - origCursor)).coerceIn(0, range.transformedStart)
                    }
                    if (clamped < ref.end) {
                        val rel = clamped - ref.start
                        val mappedRel = ((rel.toLong() * dispLen) / oldLen).toInt().coerceIn(0, dispLen)
                        return (range.textStart + mappedRel).coerceIn(range.textStart, range.textEnd)
                    }
                    origCursor = ref.end
                    transCursor = range.transformedEnd
                }
                return (transCursor + (clamped - origCursor)).coerceAtLeast(0)
            }

            content.highlights.forEach { span ->
                val os = span.start.coerceIn(0, originalLength)
                val oe = span.end.coerceIn(0, originalLength)
                if (os < oe) {
                    val ts = mapOriginalOffset(os)
                    val te = mapOriginalOffset(oe)
                    if (ts < te) {
                        addStyle(
                            style = SpanStyle(
                                background = Color(span.color.argb),
                                color = highlightTextColor
                            ),
                            start = ts,
                            end = te
                        )
                    }
                }
            }

            content.bolds.forEach { span ->
                val os = span.start.coerceIn(0, originalLength)
                val oe = span.end.coerceIn(0, originalLength)
                if (os < oe) {
                    val ts = mapOriginalOffset(os)
                    val te = mapOriginalOffset(oe)
                    if (ts < te) {
                        addStyle(
                            style = SpanStyle(fontWeight = FontWeight.Bold),
                            start = ts,
                            end = te
                        )
                    }
                }
            }

            content.italics.forEach { span ->
                val os = span.start.coerceIn(0, originalLength)
                val oe = span.end.coerceIn(0, originalLength)
                if (os < oe) {
                    val ts = mapOriginalOffset(os)
                    val te = mapOriginalOffset(oe)
                    if (ts < te) {
                        addStyle(
                            style = SpanStyle(fontStyle = FontStyle.Italic),
                            start = ts,
                            end = te
                        )
                    }
                }
            }

            ListFormattingHelper.findRecognizedListLines(text).forEach { listLine ->
                val overlapsRef = validRefs.any { listLine.markerStart < it.end && listLine.markerEnd > it.start }
                if (!overlapsRef && listLine.markerStart < listLine.markerEnd) {
                    val ts = mapOriginalOffset(listLine.markerStart)
                    val te = mapOriginalOffset(listLine.markerEnd)
                    if (ts < te) {
                        addStyle(
                            style = SpanStyle(
                                color = linkTextColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            start = ts,
                            end = te
                        )
                    }
                }
            }

            content.links.forEach { link ->
                val os = link.start.coerceIn(0, originalLength)
                val oe = link.end.coerceIn(0, originalLength)
                if (os < oe) {
                    val ts = mapOriginalOffset(os)
                    val te = mapOriginalOffset(oe)
                    if (ts < te) {
                        addStyle(
                            style = SpanStyle(
                                color = linkTextColor,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = ts,
                            end = te
                        )
                    }
                }
            }

            // Re-apply chip styles on top so highlights cannot paint over the chip surface
            chipRanges.forEach { range ->
                addStyle(
                    style = SpanStyle(
                        color = Color.Transparent,
                        background = Color.Transparent,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp
                    ),
                    start = range.transformedStart,
                    end = range.textStart
                )
                addStyle(
                    style = SpanStyle(
                        color = refTextColor,
                        background = Color.Transparent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Normal,
                        baselineShift = BaselineShift(0.015f)
                    ),
                    start = range.textStart,
                    end = range.textEnd
                )
                addStyle(
                    style = SpanStyle(
                        color = Color.Transparent,
                        background = Color.Transparent,
                        fontSize = 14.sp,
                        letterSpacing = 0.4.sp
                    ),
                    start = range.textEnd,
                    end = range.transformedEnd
                )
            }
        }

        val transformedLength = annotated.length
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, originalLength)
                var origCursor = 0
                var transCursor = 0
                for (i in validRefs.indices) {
                    val ref = validRefs[i]
                    val range = chipRanges[i]
                    val dispLen = (range.textEnd - range.textStart).coerceAtLeast(1)
                    val oldLen = (ref.end - ref.start).coerceAtLeast(1)
                    if (clamped <= ref.start) {
                        return (transCursor + (clamped - origCursor))
                            .coerceIn(0, range.transformedStart)
                            .coerceIn(0, transformedLength)
                    }
                    if (clamped < ref.end) {
                        val rel = clamped - ref.start
                        val mappedRel = ((rel.toLong() * dispLen) / oldLen).toInt().coerceIn(0, dispLen)
                        return (range.textStart + mappedRel)
                            .coerceIn(range.textStart, range.textEnd)
                            .coerceIn(0, transformedLength)
                    }
                    origCursor = ref.end
                    transCursor = range.transformedEnd
                }
                return (transCursor + (clamped - origCursor)).coerceIn(0, transformedLength)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, transformedLength)
                var origCursor = 0
                var transCursor = 0
                for (i in validRefs.indices) {
                    val ref = validRefs[i]
                    val range = chipRanges[i]
                    val dispLen = (range.textEnd - range.textStart).coerceAtLeast(1)
                    val oldLen = (ref.end - ref.start).coerceAtLeast(1)

                    if (clamped <= range.transformedStart) {
                        return (origCursor + (clamped - transCursor))
                            .coerceIn(0, ref.start)
                            .coerceIn(0, originalLength)
                    }
                    if (clamped <= range.textStart) {
                        return ref.start.coerceIn(0, originalLength)
                    }
                    if (clamped < range.textEnd) {
                        val relT = clamped - range.textStart
                        val mappedO = ((relT.toLong() * oldLen) / dispLen).toInt().coerceIn(0, oldLen)
                        return (ref.start + mappedO)
                            .coerceIn(ref.start, ref.end)
                            .coerceIn(0, originalLength)
                    }
                    if (clamped <= range.transformedEnd) {
                        return ref.end.coerceIn(0, originalLength)
                    }
                    origCursor = ref.end
                    transCursor = range.transformedEnd
                }
                return (origCursor + (clamped - transCursor)).coerceIn(0, originalLength)
            }
        }

        return EditableTransformedNote(
            annotatedString = annotated,
            offsetMapping = offsetMapping,
            chipRanges = chipRanges
        )
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

    private fun adjustLinksAfterEdit(
        links: List<LinkSpan>,
        editStart: Int,
        oldEditEnd: Int,
        delta: Int,
        maxLength: Int
    ): List<LinkSpan> {
        val updated = links.mapNotNull { span ->
            val newStart: Int
            val newEnd: Int

            when {
                // Span completely before the edited range (typing right at span.end stays outside)
                span.end <= editStart -> {
                    newStart = span.start
                    newEnd = span.end
                }
                // Span completely after the edited range (insertion right at span.start shifts link forward)
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
        return mergeAdjacentLinks(updated)
    }

    private fun mergeAdjacentLinks(spans: List<LinkSpan>): List<LinkSpan> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedWith(compareBy<LinkSpan> { it.start }.thenBy { it.end })
        val merged = mutableListOf<LinkSpan>()
        for (span in sorted) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.url == span.url && span.start <= prev.end) {
                merged[merged.lastIndex] = prev.copy(end = max(prev.end, span.end))
            } else {
                merged.add(span)
            }
        }
        return merged
    }

    private fun adjustFormatSpansAfterEdit(
        spans: List<TextFormatSpan>,
        editStart: Int,
        oldEditEnd: Int,
        delta: Int,
        maxLength: Int
    ): List<TextFormatSpan> {
        val updated = spans.mapNotNull { span ->
            val newStart: Int
            val newEnd: Int

            when {
                span.end <= editStart -> {
                    newStart = span.start
                    newEnd = span.end
                }
                span.start >= oldEditEnd -> {
                    newStart = span.start + delta
                    newEnd = span.end + delta
                }
                span.start <= editStart && span.end >= oldEditEnd -> {
                    newStart = span.start
                    newEnd = span.end + delta
                }
                span.start < editStart && span.end <= oldEditEnd -> {
                    newStart = span.start
                    newEnd = editStart
                }
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
                TextFormatSpan(start = clampedStart, end = clampedEnd)
            } else {
                null
            }
        }
        return mergeAdjacentFormatSpans(updated)
    }

    private fun mergeAdjacentFormatSpans(spans: List<TextFormatSpan>): List<TextFormatSpan> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedWith(compareBy<TextFormatSpan> { it.start }.thenBy { it.end })
        val merged = mutableListOf<TextFormatSpan>()
        for (span in sorted) {
            val prev = merged.lastOrNull()
            if (prev != null && span.start <= prev.end) {
                merged[merged.lastIndex] = prev.copy(end = max(prev.end, span.end))
            } else {
                merged.add(span)
            }
        }
        return merged
    }
}
