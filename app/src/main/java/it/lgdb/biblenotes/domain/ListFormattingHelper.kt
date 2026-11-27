package it.lgdb.biblenotes.domain

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import it.lgdb.biblenotes.model.BibleRefSpan

data class RecognizedListLine(
    val lineStart: Int,
    val lineEnd: Int,
    val markerStart: Int,
    val markerEnd: Int,
    val isNumbered: Boolean
)

object ListFormattingHelper {

    private val bulletLineRegex = Regex("^([ \\t]*)([•\\-*]) (.*)$")
    private val numberedLineRegex = Regex("^([ \\t]*)(\\d+)([.)]) (.*)$")

    fun findRecognizedListLines(text: String): List<RecognizedListLine> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<RecognizedListLine>()
        var lineStart = 0
        val len = text.length

        while (lineStart <= len) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl >= 0) nl else len
            val line = text.substring(lineStart, lineEnd)

            val bulletMatch = bulletLineRegex.matchEntire(line)
            if (bulletMatch != null) {
                val indentLen = bulletMatch.groupValues[1].length
                val markerStart = lineStart + indentLen
                val markerEnd = markerStart + 1
                lines.add(
                    RecognizedListLine(
                        lineStart = lineStart,
                        lineEnd = lineEnd,
                        markerStart = markerStart,
                        markerEnd = markerEnd,
                        isNumbered = false
                    )
                )
            } else {
                val numMatch = numberedLineRegex.matchEntire(line)
                if (numMatch != null) {
                    val indentLen = numMatch.groupValues[1].length
                    val numLen = numMatch.groupValues[2].length
                    val sepLen = numMatch.groupValues[3].length
                    val markerStart = lineStart + indentLen
                    val markerEnd = markerStart + numLen + sepLen
                    lines.add(
                        RecognizedListLine(
                            lineStart = lineStart,
                            lineEnd = lineEnd,
                            markerStart = markerStart,
                            markerEnd = markerEnd,
                            isNumbered = true
                        )
                    )
                }
            }

            if (nl < 0) break
            lineStart = nl + 1
        }
        return lines
    }

    /**
     * Intercepts typing "- " / "* " at line start (converting to "• ") and pressing Enter on
     * bullet / numbered list lines (continuing with the next bullet/number or exiting when empty).
     * Returns null if no list transformation applies.
     */
    fun processTextEdit(
        oldText: String,
        oldSelection: TextRange,
        newValue: TextFieldValue,
        bibleRefs: List<BibleRefSpan>
    ): TextFieldValue? {
        if (!oldSelection.collapsed || !newValue.selection.collapsed) return null
        val newText = newValue.text
        val newCursor = newValue.selection.start
        if (newText.length != oldText.length + 1) return null
        if (newCursor <= 0 || newCursor > newText.length) return null

        val insertedIndex = newCursor - 1
        val insertedChar = newText[insertedIndex]

        // Verify this was a clean single-character insertion at insertedIndex
        if (oldText.substring(0, insertedIndex) != newText.substring(0, insertedIndex)) return null
        if (oldText.substring(insertedIndex) != newText.substring(newCursor)) return null
        if (bibleRefs.any { insertedIndex in it.start..it.end }) return null

        // 1. Typing space after "-" or "*" at line start -> "• "
        if (insertedChar == ' ') {
            val lineStart = if (insertedIndex == 0) {
                0
            } else {
                oldText.lastIndexOf('\n', insertedIndex - 1).let { if (it < 0) 0 else it + 1 }
            }
            val prefixUpToSpace = oldText.substring(lineStart, insertedIndex)
            val shortcutMatch = Regex("^([ \\t]*)([-*])$").matchEntire(prefixUpToSpace)
            if (shortcutMatch != null) {
                val markerIndex = lineStart + shortcutMatch.groupValues[1].length
                val updatedText = buildString {
                    append(newText.substring(0, markerIndex))
                    append('•')
                    append(newText.substring(markerIndex + 1))
                }
                return newValue.copy(
                    text = updatedText,
                    selection = TextRange(newCursor)
                )
            }
            return null
        }

        // 2. Pressing Enter on a bullet or numbered list line
        if (insertedChar == '\n') {
            val lineStart = if (insertedIndex == 0) {
                0
            } else {
                oldText.lastIndexOf('\n', insertedIndex - 1).let { if (it < 0) 0 else it + 1 }
            }
            val lineEndInOld = oldText.indexOf('\n', insertedIndex).let { if (it < 0) oldText.length else it }
            val lineBeforeCursor = oldText.substring(lineStart, insertedIndex)
            val lineAfterCursor = oldText.substring(insertedIndex, lineEndInOld)

            // Match bullet prefix with space (or empty bullet with optional trailing text)
            val bulletMatch = Regex("^([ \\t]*)([•\\-*]) (.*)$").matchEntire(lineBeforeCursor)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1]
                val marker = bulletMatch.groupValues[2]
                val contentBeforeCursor = bulletMatch.groupValues[3]
                val nextMarker = "$indent$marker "
                return if (contentBeforeCursor.isBlank() && lineAfterCursor.isBlank()) {
                    // Empty bullet item -> remove prefix and exit list cleanly
                    val updatedText = oldText.substring(0, lineStart) + oldText.substring(lineEndInOld)
                    TextFieldValue(
                        text = updatedText,
                        selection = TextRange(lineStart)
                    )
                } else {
                    val updatedText = newText.substring(0, newCursor) + nextMarker + newText.substring(newCursor)
                    TextFieldValue(
                        text = updatedText,
                        selection = TextRange(newCursor + nextMarker.length)
                    )
                }
            }

            // Match numbered prefix: "<indent><num><. or )> <content>"
            val numMatch = Regex("^([ \\t]*)(\\d+)([.)]) (.*)$").matchEntire(lineBeforeCursor)
            if (numMatch != null) {
                val indent = numMatch.groupValues[1]
                val num = numMatch.groupValues[2].toIntOrNull() ?: return null
                val sep = numMatch.groupValues[3]
                val contentBeforeCursor = numMatch.groupValues[4]
                return if (contentBeforeCursor.isBlank() && lineAfterCursor.isBlank()) {
                    // Empty numbered item -> remove prefix and exit list cleanly
                    val updatedText = oldText.substring(0, lineStart) + oldText.substring(lineEndInOld)
                    TextFieldValue(
                        text = updatedText,
                        selection = TextRange(lineStart)
                    )
                } else {
                    val nextMarker = "$indent${num + 1}$sep "
                    val updatedText = newText.substring(0, newCursor) + nextMarker + newText.substring(newCursor)
                    TextFieldValue(
                        text = updatedText,
                        selection = TextRange(newCursor + nextMarker.length)
                    )
                }
            }
        }

        return null
    }
}
