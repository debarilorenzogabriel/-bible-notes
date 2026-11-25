package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.HighlightColor
import it.lgdb.biblenotes.model.HighlightSpan
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID

@Serializable
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val notes: List<NoteRecord> = emptyList(),
    val allLabels: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

object BackupSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val fullRefRegex = Regex("^(.+?)\\s+(\\d+)(?::([^\\s]+))?$")
    private val jwBibleParamRegex = Regex("bible=(\\d{8})(?:-(\\d{8}))?")
    private val rgbColorRegex = Regex("background-color\\s*:\\s*rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", RegexOption.IGNORE_CASE)
    private val hexColorRegex = Regex("background-color\\s*:\\s*#([0-9a-fA-F]{6})", RegexOption.IGNORE_CASE)
    private val hrefAttrRegex = Regex("href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private val styleAttrRegex = Regex("style\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private val numericEntityRegex = Regex("&#(\\d+);")
    private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")

    fun encode(
        notes: List<NoteRecord>,
        labels: List<String>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val activeNotes = notes.filter { it.deletedAt == null && !it.isCorrupted }
        val normalizedLabels = (labels + activeNotes.flatMap { it.tags })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val payload = BackupPayload(
            version = BackupPayload.CURRENT_VERSION,
            exportedAt = exportedAt,
            notes = activeNotes,
            allLabels = normalizedLabels
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    fun decode(rawJson: String): Result<BackupPayload> = runCatching {
        val trimmed = rawJson.removePrefix("\uFEFF").trim()
        require(trimmed.isNotEmpty()) { "Empty backup file" }

        val root = json.parseToJsonElement(trimmed).jsonObject
        val notesElement = root["notes"] ?: error("Missing notes array")
        require(notesElement is JsonArray) { "notes must be an array" }

        val exportedAt = root["exportedAt"].asLongOrNull() ?: System.currentTimeMillis()
        val version = root["version"].asIntOrNull() ?: 1

        val rawLabels = (root["allLabels"] as? JsonArray)
            ?.mapNotNull { it.asStringOrNull()?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val parsedNotes = notesElement.mapIndexed { index, element ->
            parseNoteElement(element, index, exportedAt)
        }

        val combinedLabels = (rawLabels + parsedNotes.flatMap { it.tags })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        BackupPayload(
            version = version,
            exportedAt = exportedAt,
            notes = parsedNotes,
            allLabels = combinedLabels
        )
    }

    private fun parseNoteElement(
        element: JsonElement,
        index: Int,
        fallbackTimestamp: Long
    ): NoteRecord {
        val obj = element.jsonObject

        // Native v2 format has a "content" object
        if (obj["content"] is JsonObject) {
            val decoded = json.decodeFromJsonElement(NoteRecord.serializer(), obj)
            return decoded.copy(
                id = decoded.id.ifBlank { UUID.randomUUID().toString() },
                tags = decoded.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            )
        }

        // Legacy WebView / Capacitor v1 format: bodyHtml / bodyText / bibleRef (object, string, or null)
        val id = obj["id"].asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: "legacy-$fallbackTimestamp-$index-${UUID.randomUUID()}"
        val title = obj["title"].asStringOrNull().orEmpty()
        val bodyHtml = obj["bodyHtml"].asStringOrNull().orEmpty()
        val bodyText = obj["bodyText"].asStringOrNull()

        val legacyTags = (obj["tags"] as? JsonArray)
            ?.mapNotNull { it.asStringOrNull()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinctBy { it.lowercase() }
            .orEmpty()

        val createdAt = obj["createdAt"].asLongOrNull() ?: fallbackTimestamp
        val updatedAt = obj["updatedAt"].asLongOrNull() ?: createdAt
        val pinned = obj["pinned"].asBooleanOrNull() ?: false

        var content = if (bodyHtml.isNotBlank()) {
            parseLegacyBodyHtml(bodyHtml)
        } else {
            RichNoteContent(text = bodyText.orEmpty().trim())
        }

        if (content.text.isBlank() && !bodyText.isNullOrBlank()) {
            content = content.copy(text = bodyText.trim())
        }

        // Fallback if legacy "bibleRef" was stored as a plain string and no inline refs were parsed from HTML
        val legacyRefString = (obj["bibleRef"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (legacyRefString != null && content.bibleRefs.isEmpty()) {
            val updatedText = if (content.text.contains(legacyRefString, ignoreCase = true)) {
                content.text
            } else if (content.text.isEmpty()) {
                legacyRefString
            } else {
                "$legacyRefString\n${content.text}"
            }
            val parsedFallbackRef = parseLegacyBibleRef(legacyRefString, updatedText)
            content = content.copy(
                text = updatedText,
                bibleRefs = parsedFallbackRef?.let { listOf(it) }.orEmpty()
            )
        }

        return NoteRecord(
            id = id,
            title = title,
            content = content,
            tags = legacyTags,
            createdAt = createdAt,
            updatedAt = updatedAt,
            pinned = pinned
        )
    }

    internal fun parseLegacyBodyHtml(html: String): RichNoteContent {
        val sb = StringBuilder()
        val highlights = mutableListOf<HighlightSpan>()
        val bibleRefs = mutableListOf<BibleRefSpan>()
        val spanStack = ArrayDeque<Pair<HighlightColor?, Int>>()

        var inAnchor = false
        var anchorHref = ""
        val anchorRawText = StringBuilder()

        var pos = 0
        val len = html.length

        while (pos < len) {
            val lt = html.indexOf('<', pos)
            if (lt < 0) {
                val chunk = decodeHtmlEntities(html.substring(pos))
                if (inAnchor) anchorRawText.append(chunk) else sb.append(chunk)
                break
            }

            if (lt > pos) {
                val chunk = decodeHtmlEntities(html.substring(pos, lt))
                if (inAnchor) anchorRawText.append(chunk) else sb.append(chunk)
            }

            val gt = html.indexOf('>', lt + 1)
            if (gt < 0) {
                val chunk = decodeHtmlEntities(html.substring(lt))
                if (inAnchor) anchorRawText.append(chunk) else sb.append(chunk)
                break
            }

            val rawTag = html.substring(lt + 1, gt).trim()
            pos = gt + 1

            if (rawTag.isEmpty() || rawTag.startsWith("!")) continue

            val isClosing = rawTag.startsWith("/")
            val tagBody = if (isClosing) rawTag.substring(1).trim() else rawTag
            val tagName = tagBody.substringBefore(' ').substringBefore('/').lowercase()

            when (tagName) {
                "br" -> {
                    if (inAnchor) {
                        anchorRawText.append(' ')
                    } else {
                        sb.append('\n')
                    }
                }

                "div", "p", "li", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!inAnchor && sb.isNotEmpty() && !sb.endsWith('\n')) {
                        sb.append('\n')
                    }
                }

                "span" -> {
                    if (!isClosing) {
                        val style = extractAttribute(tagBody, styleAttrRegex)
                        val color = style?.let(::parseHighlightColor)
                        spanStack.addLast(color to sb.length)
                    } else if (spanStack.isNotEmpty()) {
                        val (color, startIdx) = spanStack.removeLast()
                        if (color != null) {
                            var endIdx = sb.length
                            while (endIdx > startIdx && sb[endIdx - 1] == '\n') {
                                endIdx--
                            }
                            if (endIdx > startIdx && sb.substring(startIdx, endIdx).isNotBlank()) {
                                highlights.add(
                                    HighlightSpan(
                                        start = startIdx,
                                        end = endIdx,
                                        color = color
                                    )
                                )
                            }
                        }
                    }
                }

                "a" -> {
                    if (!isClosing) {
                        inAnchor = true
                        anchorHref = decodeHtmlEntities(extractAttribute(tagBody, hrefAttrRegex).orEmpty())
                        anchorRawText.clear()
                    } else if (inAnchor) {
                        inAnchor = false
                        val linkText = anchorRawText.toString()
                        val resolved = resolveAnchorBibleRef(
                            href = anchorHref,
                            rawLinkText = linkText,
                            insertOffset = sb.length
                        )
                        if (resolved != null) {
                            val (span, hadExplicitChapterInLink) = resolved
                            val startOffset = sb.length
                            val display = span.displayText
                            sb.append(display)
                            val endOffset = sb.length
                            bibleRefs.add(span.copy(start = startOffset, end = endOffset))

                            // Avoid duplicating trailing " 11:6" when legacy link only wrapped book abbr (e.g. "<a>Eccl</a> 11:6")
                            if (!hadExplicitChapterInLink) {
                                val expectedSuffix = buildString {
                                    append(' ')
                                    append(span.chapter)
                                    if (!span.verseSpec.isNullOrBlank()) {
                                        append(':')
                                        append(span.verseSpec)
                                    }
                                }
                                if (html.startsWith(expectedSuffix, startIndex = pos)) {
                                    pos += expectedSuffix.length
                                }
                            }
                        } else {
                            sb.append(linkText)
                        }
                        anchorHref = ""
                        anchorRawText.clear()
                    }
                }
            }
        }

        // Flush any unclosed anchor safely
        if (inAnchor && anchorRawText.isNotEmpty()) {
            sb.append(anchorRawText)
        }

        // Trim trailing newlines produced by closing block tags
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') {
            sb.deleteCharAt(sb.length - 1)
        }
        val finalText = sb.toString()
        val maxLen = finalText.length

        val clampedHighlights = highlights
            .mapNotNull { span ->
                val s = span.start.coerceIn(0, maxLen)
                val e = span.end.coerceIn(0, maxLen)
                if (s < e) span.copy(start = s, end = e) else null
            }
            .sortedWith(compareBy<HighlightSpan> { it.start }.thenBy { it.end })

        val clampedRefs = bibleRefs
            .filter { it.start in 0 until it.end && it.end <= maxLen }
            .sortedBy { it.start }

        return RichNoteContent(
            text = finalText,
            highlights = clampedHighlights,
            bibleRefs = clampedRefs
        )
    }

    private fun resolveAnchorBibleRef(
        href: String,
        rawLinkText: String,
        insertOffset: Int
    ): Pair<BibleRefSpan, Boolean>? {
        val hrefParsed = parseJwBibleParam(href)
        val normalizedText = rawLinkText.replace(Regex("\\s+"), " ").trim()

        val textMatch = fullRefRegex.find(normalizedText)
        if (textMatch != null) {
            val bookStr = textMatch.groupValues[1].trim()
            val chapter = textMatch.groupValues[2].toIntOrNull()
            val verseSpec = textMatch.groupValues[3].trim().takeIf { it.isNotEmpty() }
            val catalogBook = BibleCatalog.findByName(bookStr) ?: hrefParsed?.first?.let { BibleCatalog.findByNumber(it) }
            if (catalogBook != null && chapter != null && chapter > 0) {
                val bookName = if (BibleCatalog.findByName(bookStr) != null) bookStr else catalogBook.nameIt
                val span = BibleRefSpan(
                    start = insertOffset,
                    end = insertOffset,
                    bookNumber = catalogBook.number,
                    bookName = bookName,
                    chapter = chapter,
                    verseSpec = verseSpec
                )
                return span to true
            }
        }

        if (hrefParsed != null) {
            val (bookNum, chapter, verseSpec) = hrefParsed
            val catalogBook = BibleCatalog.findByNumber(bookNum)
            if (catalogBook != null && chapter > 0) {
                val span = BibleRefSpan(
                    start = insertOffset,
                    end = insertOffset,
                    bookNumber = catalogBook.number,
                    bookName = catalogBook.nameIt,
                    chapter = chapter,
                    verseSpec = verseSpec
                )
                return span to false
            }
        }

        return null
    }

    private fun parseJwBibleParam(href: String): Triple<Int, Int, String?>? {
        val match = jwBibleParamRegex.find(href) ?: return null
        val first = match.groupValues[1]
        if (first.length != 8) return null

        val bookNum = first.substring(0, 2).toIntOrNull() ?: return null
        val chapter = first.substring(2, 5).toIntOrNull() ?: return null
        val verse1 = first.substring(5, 8).toIntOrNull() ?: 0

        val second = match.groupValues.getOrNull(2).orEmpty()
        val verseSpec = if (second.length == 8 && second.substring(0, 5) == first.substring(0, 5)) {
            val verse2 = second.substring(5, 8).toIntOrNull()
            if (verse2 != null && verse1 > 0 && verse2 >= verse1) {
                if (verse2 == verse1) verse1.toString() else "$verse1-$verse2"
            } else if (verse1 > 0) {
                verse1.toString()
            } else {
                null
            }
        } else {
            if (verse1 > 0) verse1.toString() else null
        }

        return Triple(bookNum, chapter, verseSpec)
    }

    private fun parseHighlightColor(style: String): HighlightColor? {
        val rgbMatch = rgbColorRegex.find(style)
        if (rgbMatch != null) {
            val r = rgbMatch.groupValues[1].toIntOrNull()
            val g = rgbMatch.groupValues[2].toIntOrNull()
            val b = rgbMatch.groupValues[3].toIntOrNull()
            if (r != null && g != null && b != null) {
                return when {
                    r == 255 && g == 245 && b == 157 -> HighlightColor.YELLOW
                    r == 255 && g == 204 && b == 128 -> HighlightColor.ORANGE
                    r == 179 && g == 157 && b == 219 -> HighlightColor.PURPLE
                    r == 165 && g == 214 && b == 167 -> HighlightColor.GREEN
                    r >= 230 && g >= 225 && b < 190 -> HighlightColor.YELLOW
                    r >= 230 && g in 160..224 && b < 170 -> HighlightColor.ORANGE
                    r in 140..210 && g < 180 && b >= 190 -> HighlightColor.PURPLE
                    g >= 180 && r < 200 -> HighlightColor.GREEN
                    else -> null
                }
            }
        }

        val hexMatch = hexColorRegex.find(style)
        if (hexMatch != null) {
            val hex = "#${hexMatch.groupValues[1].lowercase()}"
            return HighlightColor.entries.firstOrNull { it.cssHex == hex }
        }

        return null
    }

    private fun extractAttribute(tagBody: String, regex: Regex): String? {
        val match = regex.find(tagBody) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        var result = input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")

        if (result.contains("&#")) {
            result = numericEntityRegex.replace(result) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            result = hexEntityRegex.replace(result) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
        }
        return result
    }

    private fun parseLegacyBibleRef(refStr: String, fullText: String): BibleRefSpan? {
        val match = fullRefRegex.find(refStr.trim()) ?: return null
        val bookName = match.groupValues[1].trim()
        val chapter = match.groupValues[2].toIntOrNull() ?: return null
        val verse = match.groupValues[3].trim().takeIf { it.isNotEmpty() }
        val book = BibleCatalog.findByName(bookName) ?: return null
        val startIdx = fullText.indexOf(refStr, ignoreCase = true)
        if (startIdx < 0) return null
        return BibleRefSpan(
            start = startIdx,
            end = startIdx + refStr.length,
            bookNumber = book.number,
            bookName = bookName,
            chapter = chapter,
            verseSpec = verse
        )
    }

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.asLongOrNull(): Long? =
        (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.asIntOrNull(): Int? =
        (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.asBooleanOrNull(): Boolean? =
        (this as? JsonPrimitive)?.booleanOrNull
}
