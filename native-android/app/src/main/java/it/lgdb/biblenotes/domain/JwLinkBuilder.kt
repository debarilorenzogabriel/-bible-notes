package it.lgdb.biblenotes.domain

import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.BibleRefSpan
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object JwLinkBuilder {

    fun formatReferenceDisplay(
        bookName: String,
        chapter: Int,
        verseSpec: String?
    ): String {
        val cleanBook = bookName.trim()
        val cleanVerse = verseSpec?.trim().orEmpty()
        return if (cleanVerse.isNotEmpty()) {
            "$cleanBook $chapter:$cleanVerse"
        } else {
            "$cleanBook $chapter"
        }
    }

    fun buildBibleCode(
        bookNumber: Int,
        chapter: Int,
        verseSpec: String?
    ): String? {
        if (bookNumber !in 1..66 || chapter <= 0) return null
        val b = bookNumber.toString().padStart(2, '0')
        val c = chapter.toString().padStart(3, '0')

        val cleanVerse = verseSpec?.trim().orEmpty()
        if (cleanVerse.isEmpty()) {
            return "${b}${c}001"
        }

        val numbers = Regex("[0-9]+")
            .findAll(cleanVerse)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()

        if (numbers.isEmpty()) return null

        val firstVerse = numbers.min()
        val lastVerse = numbers.max()
        val vStart = firstVerse.toString().padStart(3, '0')
        val vEnd = lastVerse.toString().padStart(3, '0')

        return if (firstVerse == lastVerse) {
            "$b$c$vStart"
        } else {
            "$b$c$vStart-$b$c$vEnd"
        }
    }

    private fun wtLocale(language: AppLanguage): String =
        if (language == AppLanguage.EN) "E" else "I"

    fun buildJwLibraryUrl(
        ref: BibleRefSpan,
        language: AppLanguage = AppLanguage.IT
    ): String {
        val locale = wtLocale(language)
        val bibleCode = buildBibleCode(ref.bookNumber, ref.chapter, ref.verseSpec)
        return if (bibleCode != null) {
            "jwlibrary://finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&pub=nwtsty&bible=$bibleCode"
        } else {
            val encoded = URLEncoder.encode(ref.displayText, StandardCharsets.UTF_8.toString())
            "jwlibrary://finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&pub=nwtsty&q=$encoded"
        }
    }

    fun buildJwLibraryLegacyUrl(
        ref: BibleRefSpan,
        language: AppLanguage = AppLanguage.IT
    ): String {
        val locale = wtLocale(language)
        val bibleCode = buildBibleCode(ref.bookNumber, ref.chapter, ref.verseSpec)
        return if (bibleCode != null) {
            "jwlibrary:/finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&pub=nwtsty&bible=$bibleCode"
        } else {
            val encoded = URLEncoder.encode(ref.displayText, StandardCharsets.UTF_8.toString())
            "jwlibrary:/finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&pub=nwtsty&q=$encoded"
        }
    }

    fun buildJwOrgUrl(
        ref: BibleRefSpan,
        language: AppLanguage = AppLanguage.IT
    ): String {
        val locale = wtLocale(language)
        val bibleCode = buildBibleCode(ref.bookNumber, ref.chapter, ref.verseSpec)
        return if (bibleCode != null) {
            "https://www.jw.org/finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&pub=nwtsty&bible=$bibleCode"
        } else {
            val encoded = URLEncoder.encode(ref.displayText, StandardCharsets.UTF_8.toString())
            "https://www.jw.org/finder?srcid=jwlshare&wtlocale=$locale&prefer=lang&q=$encoded&pub=nwtsty"
        }
    }
}
