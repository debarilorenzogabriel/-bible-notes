package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.BibleRefSpan
import it.lgdb.biblenotes.model.RichNoteContent
import java.text.Normalizer
import kotlin.math.abs

data class DetectedBibleRef(
    val start: Int,
    val end: Int,
    val book: BibleBook,
    val chapter: Int,
    val verseSpec: String?,
    val matchedText: String
)

object BibleReferenceDetector {

    private data class BookAlias(
        val normalizedKey: String,
        val book: BibleBook,
        val isFullName: Boolean,
        val requiresCapitalizedStopword: Boolean
    )

    // Common Italian/English 2-letter prepositions/articles/verbs that match official abbreviations
    private val STOPWORD_COLLISIONS = setOf(
        "le", "la", "da", "at", "am", "is", "ho", "so", "no", "ha", "in", "re"
    )

    private val EXTRA_ALIASES_BY_BOOK_NUMBER: Map<Int, List<Pair<String, Boolean>>> = mapOf(
        1 to listOf("gen" to false, "gn" to false),
        2 to listOf("esodo" to true, "es" to false),
        3 to listOf("levitico" to true, "lev" to false),
        4 to listOf("num" to false),
        5 to listOf("deut" to false, "dt" to false),
        6 to listOf("giosue" to true, "gs" to false),
        9 to listOf("1 sam" to false, "1sam" to false, "1 sa" to false, "1samuele" to true, "1samuel" to true),
        10 to listOf("2 sam" to false, "2sam" to false, "2 sa" to false, "2samuele" to true, "2samuel" to true),
        11 to listOf("1 re" to true, "1re" to false, "1 kings" to true, "1kings" to false),
        12 to listOf("2 re" to true, "2re" to false, "2 kings" to true, "2kings" to false),
        13 to listOf("1 cron" to false, "1cron" to false, "1 cr" to false, "1cronache" to true, "1chronicles" to true),
        14 to listOf("2 cron" to false, "2cron" to false, "2 cr" to false, "2cronache" to true, "2chronicles" to true),
        18 to listOf("giob" to false),
        19 to listOf("salmo" to true, "sal" to false, "psalm" to true, "psa" to false),
        20 to listOf("prov" to false, "prv" to false),
        21 to listOf("eccl" to false, "qoelet" to true),
        22 to listOf("cantico" to true, "cant" to false, "song" to true),
        23 to listOf("is" to false),
        24 to listOf("ger" to false, "jer" to false),
        26 to listOf("ezec" to false, "ezek" to false),
        27 to listOf("dan" to false),
        32 to listOf("giona" to true),
        33 to listOf("mich" to false),
        40 to listOf("mat" to false, "matt" to false),
        41 to listOf("mc" to false, "mar" to false),
        42 to listOf("lc" to false, "luc" to false, "lk" to false),
        43 to listOf("giov" to false, "jn" to false),
        44 to listOf("acts" to true),
        45 to listOf("rom" to false),
        46 to listOf("1 cor" to false, "1cor" to false, "1 co" to false, "1corinti" to true, "1corinthians" to true),
        47 to listOf("2 cor" to false, "2cor" to false, "2 co" to false, "2corinti" to true, "2corinthians" to true),
        49 to listOf("efes" to false),
        50 to listOf("fil" to false, "filip" to false, "phil" to false),
        51 to listOf("coloss" to false),
        52 to listOf("1 tess" to false, "1tess" to false, "1 ts" to false, "1th" to false, "1tessalonicesi" to true),
        53 to listOf("2 tess" to false, "2tess" to false, "2 ts" to false, "2th" to false, "2tessalonicesi" to true),
        54 to listOf("1 tim" to false, "1tim" to false, "1 tm" to false, "1timoteo" to true, "1timothy" to true),
        55 to listOf("2 tim" to false, "2tim" to false, "2 tm" to false, "2timoteo" to true, "2timothy" to true),
        58 to listOf("ebr" to false),
        59 to listOf("giac" to false, "jas" to false),
        60 to listOf("1 pietro" to true, "1pietro" to true, "1 pt" to false, "1pt" to false, "1 peter" to true, "1pe" to false),
        61 to listOf("2 pietro" to true, "2pietro" to true, "2 pt" to false, "2pt" to false, "2 peter" to true, "2pe" to false),
        62 to listOf("1 giovanni" to true, "1giovanni" to true, "1 giov" to false, "1 gv" to false, "1gv" to false, "1 john" to true, "1jn" to false),
        63 to listOf("2 giovanni" to true, "2giovanni" to true, "2 giov" to false, "2 gv" to false, "2gv" to false, "2 john" to true, "2jn" to false),
        64 to listOf("3 giovanni" to true, "3giovanni" to true, "3 giov" to false, "3 gv" to false, "3gv" to false, "3 john" to true, "3jn" to false),
        66 to listOf("apocalisse" to true, "apoc" to false, "ap" to false, "riv" to false, "rev" to false)
    )

    private val aliases: List<BookAlias> by lazy {
        val list = mutableListOf<BookAlias>()
        BibleCatalog.books.forEach { book ->
            val candidates = linkedMapOf<String, Boolean>()
            candidates[normalizeBookKey(book.nameIt)] = true
            candidates[normalizeBookKey(book.nameEn)] = true
            candidates.putIfAbsent(normalizeBookKey(book.abbrIt), false)
            candidates.putIfAbsent(normalizeBookKey(book.abbrEn), false)
            EXTRA_ALIASES_BY_BOOK_NUMBER[book.number]?.forEach { (alias, isFull) ->
                val norm = normalizeBookKey(alias)
                if (isFull) {
                    candidates[norm] = true
                } else {
                    candidates.putIfAbsent(norm, false)
                }
            }
            candidates.forEach { (key, isFull) ->
                if (key.isNotEmpty()) {
                    list.add(
                        BookAlias(
                            normalizedKey = key,
                            book = book,
                            isFullName = isFull,
                            requiresCapitalizedStopword = !isFull && key in STOPWORD_COLLISIONS
                        )
                    )
                }
            }
        }
        list.sortedWith(compareByDescending<BookAlias> { it.normalizedKey.length }.thenByDescending { it.isFullName })
    }

    private val urlRegex = Regex("https?://\\S+|mailto:\\S+|tel:\\S+", RegexOption.IGNORE_CASE)
    private val chapterVerseTailRegex = Regex("^\\s*\\.?\\s+(\\d{1,3})(?:\\s*:\\s*(\\d{1,3}(?:\\s*[-–,]\\s*\\d{1,3})*))?")

    fun findAll(content: RichNoteContent): List<DetectedBibleRef> {
        val text = content.text
        if (text.isBlank()) return emptyList()

        val blockedRanges = buildList {
            content.bibleRefs.forEach { add(it.start until it.end) }
            content.links.forEach { add(it.start until it.end) }
            urlRegex.findAll(text).forEach { add(it.range) }
        }

        val results = mutableListOf<DetectedBibleRef>()
        var i = 0
        val len = text.length

        while (i < len) {
            if (!isTokenStart(text, i)) {
                i++
                continue
            }

            if (blockedRanges.any { i in it }) {
                i++
                continue
            }

            var matched: DetectedBibleRef? = null
            for (alias in aliases) {
                val matchEndOfBook = matchAliasAt(text, i, alias.normalizedKey) ?: continue
                if (alias.requiresCapitalizedStopword && !text[i].isUpperCase()) {
                    continue
                }

                // Require a non-letter boundary at the end of the book name/abbr (period or whitespace)
                if (matchEndOfBook < len && text[matchEndOfBook].isLetterOrDigit()) {
                    continue
                }

                val tailSlice = text.substring(matchEndOfBook)
                val tailMatch = chapterVerseTailRegex.find(tailSlice) ?: continue

                val chapterGroup = tailMatch.groups[1] ?: continue
                val chapter = chapterGroup.value.toIntOrNull() ?: continue
                if (chapter !in 1..alias.book.chapters) continue

                val verseGroup = tailMatch.groups[2]
                val rawVerse = verseGroup?.value
                if (rawVerse == null && !alias.isFullName) {
                    // Abbreviations require explicit ":verse" to avoid colliding with prose ("da 5", "is 2", etc.)
                    continue
                }

                val totalEnd = matchEndOfBook + tailMatch.range.last + 1
                // Ensure trailing boundary is not immediately followed by letters (e.g. "Gen 1:4am" or "Gen 1a") or ":"
                if (totalEnd < len && (text[totalEnd].isLetterOrDigit() || text[totalEnd] == ':')) {
                    continue
                }

                // If chapter-only (no verse), avoid trailing time/decimal-like patterns (e.g. "Salmi 23.5")
                if (rawVerse == null && totalEnd < len && text[totalEnd] == '.' &&
                    totalEnd + 1 < len && text[totalEnd + 1].isDigit()
                ) {
                    continue
                }

                if (blockedRanges.any { range -> i <= range.last && totalEnd - 1 >= range.first }) {
                    continue
                }

                val normalizedVerse = rawVerse
                    ?.replace('–', '-')
                    ?.replace(Regex("\\s*([,-])\\s*"), "$1")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                matched = DetectedBibleRef(
                    start = i,
                    end = totalEnd,
                    book = alias.book,
                    chapter = chapter,
                    verseSpec = normalizedVerse,
                    matchedText = text.substring(i, totalEnd)
                )
                break
            }

            if (matched != null) {
                results.add(matched)
                i = matched.end
            } else {
                i++
            }
        }

        return results
    }

    fun findNearestToOffset(refs: List<DetectedBibleRef>, offset: Int): DetectedBibleRef? {
        if (refs.isEmpty()) return null
        return refs.minByOrNull { ref ->
            when {
                offset in ref.start..ref.end -> 0
                offset < ref.start -> ref.start - offset
                else -> offset - ref.end
            }
        }
    }

    fun findOverlappingRange(
        refs: List<DetectedBibleRef>,
        selectionStart: Int,
        selectionEnd: Int
    ): DetectedBibleRef? {
        val s = minOf(selectionStart, selectionEnd)
        val e = maxOf(selectionStart, selectionEnd)
        if (s >= e) return null
        return refs.firstOrNull { it.start < e && it.end > s }
    }

    private fun isTokenStart(text: String, index: Int): Boolean {
        val ch = text[index]
        if (!ch.isLetterOrDigit()) return false
        if (index == 0) return true
        val prev = text[index - 1]
        return !prev.isLetterOrDigit() && prev != ':' && prev != '/'
    }

    private fun matchAliasAt(text: String, startIndex: Int, normalizedAlias: String): Int? {
        var ti = startIndex
        var ai = 0
        val textLen = text.length
        val aliasLen = normalizedAlias.length

        while (ai < aliasLen) {
            if (ti >= textLen) return null
            val ac = normalizedAlias[ai]
            if (ac == ' ') {
                if (!text[ti].isWhitespace() || text[ti] == '\n') return null
                while (ti < textLen && text[ti].isWhitespace() && text[ti] != '\n') {
                    ti++
                }
                ai++
            } else {
                val folded = foldChar(text[ti])
                if (folded != ac) return null
                ti++
                ai++
            }
        }
        return ti
    }

    private fun normalizeBookKey(input: String): String {
        val stripped = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
        return stripped
    }

    private fun foldChar(ch: Char): Char {
        val lower = ch.lowercaseChar()
        return when (lower) {
            'à', 'á', 'â', 'ä' -> 'a'
            'è', 'é', 'ê', 'ë' -> 'e'
            'ì', 'í', 'î', 'ï' -> 'i'
            'ò', 'ó', 'ô', 'ö' -> 'o'
            'ù', 'ú', 'û', 'ü' -> 'u'
            else -> lower
        }
    }

    // Silence unused-import warning if BibleRefSpan isn't needed directly
    private fun unused(ref: BibleRefSpan): Int = abs(ref.start)
}
