package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.LinkSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteExportFormatter {

    fun formatForPlainShare(
        note: NoteRecord,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle
    ): String {
        val rawBody = note.rawCorruptedJson ?: formatBodyForPlainShare(
            content = note.content,
            language = language,
            refDisplayStyle = refDisplayStyle
        )
        val trimmedTitle = note.title.trim()
        val trimmedBody = rawBody.trimEnd()
        val tagsLine = note.tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "#$it" }

        return buildString {
            if (trimmedTitle.isNotEmpty()) {
                append(trimmedTitle)
            }
            if (trimmedBody.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(trimmedBody)
            }
            if (tagsLine.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(tagsLine)
            }
        }
    }

    fun formatBodyForPlainShare(
        content: RichNoteContent,
        language: AppLanguage,
        refDisplayStyle: BibleRefDisplayStyle
    ): String {
        val text = content.text
        val textLen = text.length
        if (textLen == 0) return ""

        val validRefs = content.bibleRefs
            .filter { it.start in 0 until it.end && it.end <= textLen }
            .sortedBy { it.start }

        val validLinks = content.links
            .filter { it.start in 0 until it.end && it.end <= textLen }
            .sortedBy { it.start }

        val out = StringBuilder()
        var cursor = 0

        fun appendPlainRange(segStart: Int, segEnd: Int) {
            if (segStart >= segEnd) return
            var pos = segStart
            for (link in validLinks) {
                if (link.end <= pos || link.start >= segEnd) continue
                val ls = maxOf(pos, link.start)
                val le = minOf(segEnd, link.end)
                if (ls > pos) {
                    out.append(text, pos, ls)
                }
                val linkLabel = text.substring(ls, le)
                out.append(linkLabel)
                val isEndOfSpan = le == link.end
                if (isEndOfSpan && shouldAppendUrl(linkLabel, link)) {
                    out.append(" (").append(link.url).append(')')
                }
                pos = le
            }
            if (pos < segEnd) {
                out.append(text, pos, segEnd)
            }
        }

        for (ref in validRefs) {
            if (ref.start < cursor) continue
            appendPlainRange(cursor, ref.start)
            out.append(JwLinkBuilder.formatReference(ref, language, refDisplayStyle))
            cursor = ref.end
        }
        appendPlainRange(cursor, textLen)
        return out.toString()
    }

    private fun shouldAppendUrl(label: String, link: LinkSpan): Boolean {
        val cleanLabel = label.trim().lowercase()
        val cleanUrl = link.url.trim().lowercase()
        if (cleanUrl.isEmpty()) return false
        if (cleanLabel.contains(cleanUrl)) return false
        val urlWithoutScheme = cleanUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("mailto:")
            .removePrefix("tel:")
        return !cleanLabel.contains(urlWithoutScheme)
    }

    fun buildPdfSuggestedFileName(
        note: NoteRecord,
        language: AppLanguage
    ): String {
        val dateSuffix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(note.updatedAt))
        val fallbackPrefix = if (language == AppLanguage.IT) "nota" else "note"
        val sanitizedTitle = note.title.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .take(48)
        val base = if (sanitizedTitle.isNotEmpty()) sanitizedTitle else "${fallbackPrefix}_$dateSuffix"
        return "$base.pdf"
    }
}
