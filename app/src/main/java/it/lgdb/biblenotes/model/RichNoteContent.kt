package it.lgdb.biblenotes.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class HighlightColor(
    val argb: Long,
    val cssHex: String,
    val labelIt: String,
    val labelEn: String
) {
    YELLOW(0xFFFFF59DL, "#fff59d", "Giallo", "Yellow"),
    ORANGE(0xFFFFCC80L, "#ffcc80", "Arancione", "Orange"),
    PURPLE(0xFFB39DDBL, "#b39ddb", "Viola", "Purple"),
    GREEN(0xFFA5D6A7L, "#a5d6a7", "Verde", "Green")
}

@Serializable
data class HighlightSpan(
    val start: Int,
    val end: Int,
    val color: HighlightColor
)

@Serializable
data class BibleRefSpan(
    val start: Int,
    val end: Int,
    val bookNumber: Int,
    val bookName: String,
    val chapter: Int,
    val verseSpec: String? = null
) {
    val displayText: String
        get() = buildString {
            append(bookName)
            append(" ")
            append(chapter)
            if (!verseSpec.isNullOrBlank()) {
                append(":")
                append(verseSpec.trim())
            }
        }
}

@Serializable
data class RichNoteContent(
    val text: String = "",
    val highlights: List<HighlightSpan> = emptyList(),
    val bibleRefs: List<BibleRefSpan> = emptyList()
)

@Serializable
data class NoteRecord(
    val id: String,
    val title: String,
    val content: RichNoteContent,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean,
    val deletedAt: Long? = null,
    @Transient val isCorrupted: Boolean = false,
    @Transient val rawCorruptedJson: String? = null
)

data class DraftRecord(
    val noteKey: String,
    val title: String,
    val content: RichNoteContent,
    val tags: List<String>,
    val updatedAt: Long
)

enum class NoteSortOption {
    UPDATED_DESC,
    CREATED_DESC,
    TITLE_ASC
}

enum class ImportMode {
    MERGE,
    REPLACE_ALL
}

data class ImportSummary(
    val mode: ImportMode,
    val importedCount: Int,
    val ignoredCount: Int,
    val invalidCount: Int,
    val labelsCount: Int,
    val safetyBackupCreated: Boolean
)

enum class ThemeOption {
    LIGHT,
    DARK,
    SEPIA,
    BLUE,
    SYSTEM_DYNAMIC
}

enum class AppLanguage(val code: String) {
    IT("it"),
    EN("en")
}
