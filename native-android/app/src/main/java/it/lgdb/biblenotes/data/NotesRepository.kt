package it.lgdb.biblenotes.data

import android.content.ContentValues
import android.content.Context
import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface PinToggleResult {
    data class Toggled(val pinned: Boolean) : PinToggleResult
    data object MaxLimitReached : PinToggleResult
    data object NotFound : PinToggleResult
}

class NotesRepository(context: Context) {

    private val dbHelper = NotesDatabaseHelper(context)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getAllNotes(): List<NoteRecord> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<NoteRecord>()
        db.query(
            NotesDatabaseHelper.TABLE_NOTES,
            null,
            null,
            null,
            null,
            null,
            "${NotesDatabaseHelper.COL_CREATED_AT} DESC"
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TITLE)
            val richIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_RICH_CONTENT_JSON)
            val tagsIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TAGS_JSON)
            val createdIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_CREATED_AT)
            val updatedIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_UPDATED_AT)
            val pinnedIdx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_PINNED)

            while (cursor.moveToNext()) {
                val richJson = cursor.getString(richIdx)
                val tagsJson = cursor.getString(tagsIdx)
                val richContent = runCatching {
                    json.decodeFromString<RichNoteContent>(richJson)
                }.getOrElse { RichNoteContent() }
                val tags = runCatching {
                    json.decodeFromString<List<String>>(tagsJson)
                }.getOrElse { emptyList() }

                list.add(
                    NoteRecord(
                        id = cursor.getString(idIdx),
                        title = cursor.getString(titleIdx),
                        content = richContent,
                        tags = tags,
                        createdAt = cursor.getLong(createdIdx),
                        updatedAt = cursor.getLong(updatedIdx),
                        pinned = cursor.getInt(pinnedIdx) == 1
                    )
                )
            }
        }

        val sortedResult = NoteSorter.sortForHome(list)
        if (sortedResult.unpinnedExcessIds.isNotEmpty()) {
            val writable = dbHelper.writableDatabase
            writable.beginTransaction()
            try {
                sortedResult.unpinnedExcessIds.forEach { excessId ->
                    val cv = ContentValues().apply { put(NotesDatabaseHelper.COL_PINNED, 0) }
                    writable.update(
                        NotesDatabaseHelper.TABLE_NOTES,
                        cv,
                        "${NotesDatabaseHelper.COL_ID} = ?",
                        arrayOf(excessId)
                    )
                }
                writable.setTransactionSuccessful()
            } finally {
                writable.endTransaction()
            }
        }

        sortedResult.sorted
    }

    suspend fun getNoteById(id: String): NoteRecord? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.query(
            NotesDatabaseHelper.TABLE_NOTES,
            null,
            "${NotesDatabaseHelper.COL_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val richJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_RICH_CONTENT_JSON))
            val tagsJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TAGS_JSON))
            NoteRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TITLE)),
                content = runCatching { json.decodeFromString<RichNoteContent>(richJson) }.getOrElse { RichNoteContent() },
                tags = runCatching { json.decodeFromString<List<String>>(tagsJson) }.getOrElse { emptyList() },
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_CREATED_AT)),
                updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_UPDATED_AT)),
                pinned = cursor.getInt(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_PINNED)) == 1
            )
        }
    }

    suspend fun saveNote(note: NoteRecord) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(NotesDatabaseHelper.COL_ID, note.id)
                put(NotesDatabaseHelper.COL_TITLE, note.title)
                put(NotesDatabaseHelper.COL_PLAIN_TEXT, note.content.text)
                put(NotesDatabaseHelper.COL_RICH_CONTENT_JSON, json.encodeToString(RichNoteContent.serializer(), note.content))
                put(NotesDatabaseHelper.COL_TAGS_JSON, json.encodeToString(note.tags))
                put(NotesDatabaseHelper.COL_CREATED_AT, note.createdAt)
                put(NotesDatabaseHelper.COL_UPDATED_AT, note.updatedAt)
                put(NotesDatabaseHelper.COL_PINNED, if (note.pinned) 1 else 0)
            }
            db.insertWithOnConflict(
                NotesDatabaseHelper.TABLE_NOTES,
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )

            note.tags.forEach { tag ->
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    val labelValues = ContentValues().apply {
                        put(NotesDatabaseHelper.COL_LABEL_NAME, trimmed)
                    }
                    db.insertWithOnConflict(
                        NotesDatabaseHelper.TABLE_LABELS,
                        null,
                        labelValues,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            NotesDatabaseHelper.TABLE_NOTES,
            "${NotesDatabaseHelper.COL_ID} = ?",
            arrayOf(id)
        )
    }

    suspend fun togglePinNote(noteId: String): PinToggleResult = withContext(Dispatchers.IO) {
        val allNotes = getAllNotes()
        val target = allNotes.firstOrNull { it.id == noteId } ?: return@withContext PinToggleResult.NotFound
        if (target.pinned) {
            updatePinnedFlag(noteId, false)
            PinToggleResult.Toggled(false)
        } else {
            val pinnedCount = allNotes.count { it.pinned }
            if (pinnedCount >= NoteSorter.MAX_PINNED_NOTES) {
                PinToggleResult.MaxLimitReached
            } else {
                updatePinnedFlag(noteId, true)
                PinToggleResult.Toggled(true)
            }
        }
    }

    private fun updatePinnedFlag(noteId: String, pinned: Boolean) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(NotesDatabaseHelper.COL_PINNED, if (pinned) 1 else 0)
        }
        db.update(
            NotesDatabaseHelper.TABLE_NOTES,
            values,
            "${NotesDatabaseHelper.COL_ID} = ?",
            arrayOf(noteId)
        )
    }

    suspend fun getAllTags(): List<String> = withContext(Dispatchers.IO) {
        val set = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        val db = dbHelper.readableDatabase
        db.query(
            NotesDatabaseHelper.TABLE_LABELS,
            arrayOf(NotesDatabaseHelper.COL_LABEL_NAME),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            val idx = cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_LABEL_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(idx).trim()
                if (name.isNotEmpty()) set.add(name)
            }
        }
        getAllNotes().forEach { note ->
            note.tags.forEach { t ->
                val trimmed = t.trim()
                if (trimmed.isNotEmpty()) set.add(trimmed)
            }
        }
        set.toList()
    }

    suspend fun createLabel(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = getAllTags()
        if (existing.any { it.equals(trimmed, ignoreCase = false) }) return@withContext false

        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(NotesDatabaseHelper.COL_LABEL_NAME, trimmed)
        }
        db.insertWithOnConflict(
            NotesDatabaseHelper.TABLE_LABELS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
        )
        true
    }

    suspend fun renameLabel(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isEmpty() || trimmedNew == oldName) return@withContext false

        val notes = getAllNotes()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                NotesDatabaseHelper.TABLE_LABELS,
                "${NotesDatabaseHelper.COL_LABEL_NAME} = ?",
                arrayOf(oldName)
            )
            val cv = ContentValues().apply {
                put(NotesDatabaseHelper.COL_LABEL_NAME, trimmedNew)
            }
            db.insertWithOnConflict(
                NotesDatabaseHelper.TABLE_LABELS,
                null,
                cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
            )

            notes.forEach { note ->
                if (note.tags.contains(oldName)) {
                    val updatedTags = note.tags
                        .map { if (it == oldName) trimmedNew else it }
                        .distinct()
                    val noteCv = ContentValues().apply {
                        put(NotesDatabaseHelper.COL_TAGS_JSON, json.encodeToString(updatedTags))
                    }
                    db.update(
                        NotesDatabaseHelper.TABLE_NOTES,
                        noteCv,
                        "${NotesDatabaseHelper.COL_ID} = ?",
                        arrayOf(note.id)
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        true
    }

    suspend fun deleteLabel(name: String) = withContext(Dispatchers.IO) {
        val notes = getAllNotes()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                NotesDatabaseHelper.TABLE_LABELS,
                "${NotesDatabaseHelper.COL_LABEL_NAME} = ?",
                arrayOf(name)
            )

            notes.forEach { note ->
                if (note.tags.contains(name)) {
                    val updatedTags = note.tags.filterNot { it == name }
                    val noteCv = ContentValues().apply {
                        put(NotesDatabaseHelper.COL_TAGS_JSON, json.encodeToString(updatedTags))
                    }
                    db.update(
                        NotesDatabaseHelper.TABLE_NOTES,
                        noteCv,
                        "${NotesDatabaseHelper.COL_ID} = ?",
                        arrayOf(note.id)
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getOpenWithJwLibrary(): Boolean =
        prefs.getBoolean(KEY_OPEN_JW_LIBRARY, false)

    fun setOpenWithJwLibrary(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_JW_LIBRARY, enabled).apply()
    }

    fun getTheme(): ThemeOption {
        val raw = prefs.getString(KEY_THEME, ThemeOption.LIGHT.name) ?: ThemeOption.LIGHT.name
        return runCatching { ThemeOption.valueOf(raw) }.getOrDefault(ThemeOption.LIGHT)
    }

    fun setTheme(theme: ThemeOption) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun getLanguage(): AppLanguage {
        val raw = prefs.getString(KEY_LANGUAGE, AppLanguage.IT.name) ?: AppLanguage.IT.name
        return runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.IT)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "bible_notes_prefs"
        private const val KEY_OPEN_JW_LIBRARY = "open_with_jw_library"
        private const val KEY_THEME = "theme_option"
        private const val KEY_LANGUAGE = "app_language"
    }
}
