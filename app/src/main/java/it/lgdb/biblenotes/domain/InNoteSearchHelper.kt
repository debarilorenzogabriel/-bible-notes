package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefDisplayStyle
import it.lgdb.biblenotes.model.RichNoteContent

data class InNoteSearchMatch(
    val start: Int,
    val end: Int,
    val isBibleRef: Boolean,
    val bibleRefIndex: Int? = null
)

object InNoteSearchHelper {

    fun findMatches(
        content: RichNoteContent,
        query: String,
        language: AppLanguage = AppLanguage.IT,
        refDisplayStyle: BibleRefDisplayStyle = BibleRefDisplayStyle.ABBREVIATED
    ): List<InNoteSearchMatch> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val text = content.text
        val textLength = text.length
        val indexedRefs = content.bibleRefs
            .mapIndexed { index, ref -> index to ref }
            .filter { (_, ref) -> ref.start in 0 until ref.end && ref.end <= textLength }
            .sortedBy { (_, ref) -> ref.start }

        val matches = mutableListOf<InNoteSearchMatch>()
        var cursor = 0

        fun scanPlainSegment(segStart: Int, segEnd: Int) {
            if (segStart >= segEnd) return
            val segment = text.substring(segStart, segEnd)
            var searchFrom = 0
            while (searchFrom <= segment.length - cleanQuery.length) {
                val found = segment.indexOf(cleanQuery, startIndex = searchFrom, ignoreCase = true)
                if (found < 0) break
                matches.add(
                    InNoteSearchMatch(
                        start = segStart + found,
                        end = segStart + found + cleanQuery.length,
                        isBibleRef = false
                    )
                )
                searchFrom = found + cleanQuery.length.coerceAtLeast(1)
            }
        }

        indexedRefs.forEach { (originalIndex, ref) ->
            if (ref.start >= cursor) {
                scanPlainSegment(cursor, ref.start)
                val primaryLabel = JwLinkBuilder.formatReference(ref, language, refDisplayStyle)
                val abbrLabel = JwLinkBuilder.formatAbbreviatedReference(ref, language)
                val fullLabel = JwLinkBuilder.formatFullReference(ref, language)
                val refMatched = primaryLabel.contains(cleanQuery, ignoreCase = true) ||
                    abbrLabel.contains(cleanQuery, ignoreCase = true) ||
                    fullLabel.contains(cleanQuery, ignoreCase = true) ||
                    ref.displayText.contains(cleanQuery, ignoreCase = true)
                if (refMatched) {
                    matches.add(
                        InNoteSearchMatch(
                            start = ref.start,
                            end = ref.end,
                            isBibleRef = true,
                            bibleRefIndex = originalIndex
                        )
                    )
                }
                cursor = ref.end
            }
        }

        scanPlainSegment(cursor, textLength)
        return matches
    }
}
