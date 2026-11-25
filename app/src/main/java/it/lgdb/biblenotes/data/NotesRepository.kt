package it.lgdb.biblenotes.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import it.lgdb.biblenotes.domain.BackupPayload
import it.lgdb.biblenotes.domain.BackupSerializer
import it.lgdb.biblenotes.domain.DraftEpochCoordinator
import it.lgdb.biblenotes.domain.NoteSorter
import it.lgdb.biblenotes.model.AppLanguage
import it.lgdb.biblenotes.model.DraftRecord
import it.lgdb.biblenotes.model.ImportMode
import it.lgdb.biblenotes.model.ImportSummary
import it.lgdb.biblenotes.model.NoteRecord
import it.lgdb.biblenotes.model.NoteSortOption
import it.lgdb.biblenotes.model.RichNoteContent
import it.lgdb.biblenotes.model.ThemeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

sealed interface PinToggleResult {
    data class Toggled(val pinned: Boolean) : PinToggleResult
    data object MaxLimitReached : PinToggleResult
    data object NotFound : PinToggleResult
}

class NotesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dbHelper = NotesDatabaseHelper(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val safetyBackupFile = File(appContext.filesDir, SAFETY_BACKUP_FILENAME)
    private val draftCoordinator = DraftEpochCoordinator()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun currentDraftEpoch(noteKey: String): Long = draftCoordinator.currentEpoch(noteKey)

    fun invalidateDraftEpoch(noteKey: String): Long = draftCoordinator.invalidateEpoch(noteKey)

    suspend fun getAllNotes(sortOption: NoteSortOption = getSortOption()): List<NoteRecord> =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            purgeExpiredTrashInternal(db, System.currentTimeMillis())

            val list = queryNotesInternal(
                db = db,
                selection = "${NotesDatabaseHelper.COL_DELETED_AT} IS NULL",
                orderBy = "${NotesDatabaseHelper.COL_UPDATED_AT} DESC"
            )

            val sortedResult = NoteSorter.sortForHome(list, sortOption)
            if (sortedResult.unpinnedExcessIds.isNotEmpty()) {
                db.beginTransaction()
                try {
                    sortedResult.unpinnedExcessIds.forEach { excessId ->
                        val cv = ContentValues().apply { put(NotesDatabaseHelper.COL_PINNED, 0) }
                        db.update(
                            NotesDatabaseHelper.TABLE_NOTES,
                            cv,
                            "${NotesDatabaseHelper.COL_ID} = ?",
                            arrayOf(excessId)
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            sortedResult.sorted
        }

    suspend fun getTrashedNotes(): List<NoteRecord> = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        purgeExpiredTrashInternal(db, System.currentTimeMillis())
        queryNotesInternal(
            db = db,
            selection = "${NotesDatabaseHelper.COL_DELETED_AT} IS NOT NULL",
            orderBy = "${NotesDatabaseHelper.COL_DELETED_AT} DESC"
        )
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
            cursorToNoteRecord(cursor)
        }
    }

    suspend fun saveNote(note: NoteRecord) = withContext(Dispatchers.IO) {
        require(!note.isCorrupted) { "Corrupted note cannot be overwritten" }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            saveNoteInternal(db, note)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun saveNoteAndDeleteDraft(note: NoteRecord, draftKey: String) {
        require(!note.isCorrupted) { "Corrupted note cannot be overwritten" }
        draftCoordinator.invalidateAndExecuteExclusive(draftKey) {
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    saveNoteInternal(db, note)
                    db.delete(
                        NotesDatabaseHelper.TABLE_DRAFTS,
                        "${NotesDatabaseHelper.COL_DRAFT_KEY} = ?",
                        arrayOf(draftKey)
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    private fun saveNoteInternal(db: SQLiteDatabase, note: NoteRecord) {
        // Verify we are not overwriting a corrupted stored row
        db.query(
            NotesDatabaseHelper.TABLE_NOTES,
            arrayOf(NotesDatabaseHelper.COL_RICH_CONTENT_JSON),
            "${NotesDatabaseHelper.COL_ID} = ?",
            arrayOf(note.id),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val existingJson = cursor.getString(0)
                val decoded = runCatching { json.decodeFromString<RichNoteContent>(existingJson) }
                if (decoded.isFailure) {
                    error("Corrupted stored note cannot be overwritten")
                }
            }
        }

        val knownLabels = readLabelsTableInternal(db)
        val canonicalTags = canonicalizeTags(note.tags, knownLabels)

        val values = noteContentValues(note.copy(tags = canonicalTags))
        db.insertWithOnConflict(
            NotesDatabaseHelper.TABLE_NOTES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        canonicalTags.forEach { tag ->
            insertLabelIfAbsentCaseInsensitive(db, tag)
        }
    }

    suspend fun moveNoteToTrash(id: String) {
        draftCoordinator.invalidateAndExecuteExclusive(id) {
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    val values = ContentValues().apply {
                        put(NotesDatabaseHelper.COL_DELETED_AT, System.currentTimeMillis())
                        put(NotesDatabaseHelper.COL_PINNED, 0)
                    }
                    db.update(
                        NotesDatabaseHelper.TABLE_NOTES,
                        values,
                        "${NotesDatabaseHelper.COL_ID} = ?",
                        arrayOf(id)
                    )
                    db.delete(
                        NotesDatabaseHelper.TABLE_DRAFTS,
                        "${NotesDatabaseHelper.COL_DRAFT_KEY} = ?",
                        arrayOf(id)
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    suspend fun restoreNoteFromTrash(id: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            putNull(NotesDatabaseHelper.COL_DELETED_AT)
        }
        db.update(
            NotesDatabaseHelper.TABLE_NOTES,
            values,
            "${NotesDatabaseHelper.COL_ID} = ?",
            arrayOf(id)
        )
    }

    suspend fun permanentlyDeleteNote(id: String) {
        draftCoordinator.invalidateAndExecuteExclusive(id) {
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    db.delete(
                        NotesDatabaseHelper.TABLE_NOTES,
                        "${NotesDatabaseHelper.COL_ID} = ?",
                        arrayOf(id)
                    )
                    db.delete(
                        NotesDatabaseHelper.TABLE_DRAFTS,
                        "${NotesDatabaseHelper.COL_DRAFT_KEY} = ?",
                        arrayOf(id)
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            NotesDatabaseHelper.TABLE_NOTES,
            "${NotesDatabaseHelper.COL_DELETED_AT} IS NOT NULL",
            null
        )
    }

    suspend fun getDraft(noteKey: String): DraftRecord? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.query(
            NotesDatabaseHelper.TABLE_DRAFTS,
            null,
            "${NotesDatabaseHelper.COL_DRAFT_KEY} = ?",
            arrayOf(noteKey),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val title = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TITLE))
            val richJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_RICH_CONTENT_JSON))
            val tagsJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TAGS_JSON))
            val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_UPDATED_AT))
            val richContent = runCatching {
                json.decodeFromString<RichNoteContent>(richJson)
            }.getOrNull() ?: return@withContext null
            val tags = runCatching {
                json.decodeFromString<List<String>>(tagsJson)
            }.getOrElse { emptyList() }
            DraftRecord(
                noteKey = noteKey,
                title = title,
                content = richContent,
                tags = tags,
                updatedAt = updatedAt
            )
        }
    }

    suspend fun saveDraft(draft: DraftRecord, expectedEpoch: Long? = null): Boolean {
        val targetEpoch = expectedEpoch ?: currentDraftEpoch(draft.noteKey)
        return draftCoordinator.writeDraftIfEpochCurrent(draft.noteKey, targetEpoch) {
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put(NotesDatabaseHelper.COL_DRAFT_KEY, draft.noteKey)
                    put(NotesDatabaseHelper.COL_TITLE, draft.title)
                    put(NotesDatabaseHelper.COL_RICH_CONTENT_JSON, json.encodeToString(RichNoteContent.serializer(), draft.content))
                    put(NotesDatabaseHelper.COL_TAGS_JSON, json.encodeToString(draft.tags))
                    put(NotesDatabaseHelper.COL_UPDATED_AT, draft.updatedAt)
                }
                db.insertWithOnConflict(
                    NotesDatabaseHelper.TABLE_DRAFTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    suspend fun deleteDraft(noteKey: String) {
        draftCoordinator.invalidateAndExecuteExclusive(noteKey) {
            withContext(Dispatchers.IO) {
                val db = dbHelper.writableDatabase
                db.delete(
                    NotesDatabaseHelper.TABLE_DRAFTS,
                    "${NotesDatabaseHelper.COL_DRAFT_KEY} = ?",
                    arrayOf(noteKey)
                )
            }
        }
    }

    suspend fun importBackup(payload: BackupPayload, mode: ImportMode): ImportSummary =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val activeExisting = queryNotesInternal(
                db = db,
                selection = "${NotesDatabaseHelper.COL_DELETED_AT} IS NULL",
                orderBy = "${NotesDatabaseHelper.COL_UPDATED_AT} DESC"
            )
            val allExisting = queryNotesInternal(db = db, selection = null, orderBy = null)
            val existingLabels = readLabelsTableInternal(db)

            var safetyBackupCreated = false
            if (mode == ImportMode.REPLACE_ALL && (activeExisting.isNotEmpty() || existingLabels.isNotEmpty())) {
                val backupJson = BackupSerializer.encode(activeExisting, existingLabels)
                safetyBackupFile.writeText(backupJson, Charsets.UTF_8)
                safetyBackupCreated = true
            }

            var invalidCount = 0
            var ignoredCount = 0
            var importedCount = 0

            val validCandidates = mutableListOf<NoteRecord>()
            for (rawNote in payload.notes) {
                val isEmptyNote = rawNote.title.isBlank() &&
                    rawNote.content.text.isBlank() &&
                    rawNote.content.bibleRefs.isEmpty() &&
                    rawNote.tags.isEmpty()
                if (isEmptyNote) {
                    invalidCount++
                } else {
                    validCandidates.add(rawNote.copy(deletedAt = null))
                }
            }

            db.beginTransaction()
            try {
                if (mode == ImportMode.REPLACE_ALL) {
                    db.delete(NotesDatabaseHelper.TABLE_NOTES, null, null)
                    db.delete(NotesDatabaseHelper.TABLE_LABELS, null, null)
                    db.delete(NotesDatabaseHelper.TABLE_DRAFTS, null, null)

                    val seenById = linkedMapOf<String, NoteRecord>()
                    val canonicalLabelMap = linkedMapOf<String, String>()
                    payload.allLabels.forEach { label ->
                        val trimmed = label.trim()
                        if (trimmed.isNotEmpty()) {
                            canonicalLabelMap.putIfAbsent(trimmed.lowercase(), trimmed)
                        }
                    }

                    val notesToInsert = mutableListOf<NoteRecord>()
                    for (candidate in validCandidates) {
                        val normalizedTags = candidate.tags.mapNotNull { t ->
                            val trimmed = t.trim()
                            if (trimmed.isEmpty()) null
                            else canonicalLabelMap.getOrPut(trimmed.lowercase()) { trimmed }
                        }.distinctBy { it.lowercase() }

                        val existingSameId = seenById[candidate.id]
                        if (existingSameId != null) {
                            if (areNotesEquivalent(existingSameId, candidate.copy(tags = normalizedTags))) {
                                ignoredCount++
                                continue
                            } else {
                                val reassigned = candidate.copy(
                                    id = "${candidate.id}-${UUID.randomUUID().toString().take(6)}",
                                    tags = normalizedTags
                                )
                                seenById[reassigned.id] = reassigned
                                notesToInsert.add(reassigned)
                                importedCount++
                            }
                        } else {
                            val clean = candidate.copy(tags = normalizedTags)
                            seenById[clean.id] = clean
                            notesToInsert.add(clean)
                            importedCount++
                        }
                    }

                    val normalized = NoteSorter.sortForHome(notesToInsert, getSortOption()).sorted
                    normalized.forEach { note ->
                        db.insertWithOnConflict(
                            NotesDatabaseHelper.TABLE_NOTES,
                            null,
                            noteContentValues(note),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    canonicalLabelMap.values.forEach { label ->
                        insertLabelExact(db, label)
                    }
                    db.setTransactionSuccessful()
                    ImportSummary(
                        mode = mode,
                        importedCount = importedCount,
                        ignoredCount = ignoredCount,
                        invalidCount = invalidCount,
                        labelsCount = canonicalLabelMap.size,
                        safetyBackupCreated = safetyBackupCreated
                    )
                } else {
                    // MERGE mode
                    val existingById = allExisting.associateBy { it.id }.toMutableMap()
                    val canonicalLabelMap = linkedMapOf<String, String>()
                    (existingLabels + activeExisting.flatMap { it.tags } + payload.allLabels).forEach { label ->
                        val trimmed = label.trim()
                        if (trimmed.isNotEmpty()) {
                            canonicalLabelMap.putIfAbsent(trimmed.lowercase(), trimmed)
                        }
                    }

                    val newNotesToInsert = mutableListOf<NoteRecord>()
                    for (candidate in validCandidates) {
                        val normalizedTags = candidate.tags.mapNotNull { t ->
                            val trimmed = t.trim()
                            if (trimmed.isEmpty()) null
                            else canonicalLabelMap.getOrPut(trimmed.lowercase()) { trimmed }
                        }.distinctBy { it.lowercase() }
                        val normalizedCandidate = candidate.copy(tags = normalizedTags)

                        val existing = existingById[normalizedCandidate.id]
                        if (existing != null) {
                            if (existing.deletedAt == null && areNotesEquivalent(existing, normalizedCandidate)) {
                                ignoredCount++
                            } else {
                                val newId = "${normalizedCandidate.id}-imp-${UUID.randomUUID().toString().take(6)}"
                                val copied = normalizedCandidate.copy(id = newId)
                                existingById[newId] = copied
                                newNotesToInsert.add(copied)
                                importedCount++
                            }
                        } else {
                            existingById[normalizedCandidate.id] = normalizedCandidate
                            newNotesToInsert.add(normalizedCandidate)
                            importedCount++
                        }
                    }

                    // Enforce MAX_PINNED_NOTES across merged active notes
                    val combinedActive = activeExisting + newNotesToInsert
                    val sortedCombined = NoteSorter.sortForHome(combinedActive, getSortOption())
                    val excessSet = sortedCombined.unpinnedExcessIds.toSet()

                    newNotesToInsert.forEach { note ->
                        val finalNote = if (note.id in excessSet) note.copy(pinned = false) else note
                        db.insertWithOnConflict(
                            NotesDatabaseHelper.TABLE_NOTES,
                            null,
                            noteContentValues(finalNote),
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }
                    excessSet.forEach { excessId ->
                        if (activeExisting.any { it.id == excessId }) {
                            val cv = ContentValues().apply { put(NotesDatabaseHelper.COL_PINNED, 0) }
                            db.update(
                                NotesDatabaseHelper.TABLE_NOTES,
                                cv,
                                "${NotesDatabaseHelper.COL_ID} = ?",
                                arrayOf(excessId)
                            )
                        }
                    }

                    canonicalLabelMap.values.forEach { label ->
                        insertLabelIfAbsentCaseInsensitive(db, label)
                    }

                    db.setTransactionSuccessful()
                    ImportSummary(
                        mode = mode,
                        importedCount = importedCount,
                        ignoredCount = ignoredCount,
                        invalidCount = invalidCount,
                        labelsCount = canonicalLabelMap.size,
                        safetyBackupCreated = false
                    )
                }
            } finally {
                db.endTransaction()
            }
        }

    fun hasSafetyBackup(): Boolean = safetyBackupFile.exists() && safetyBackupFile.length() > 0L

    suspend fun readSafetyBackup(): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            check(hasSafetyBackup()) { "No safety backup available" }
            BackupSerializer.decode(safetyBackupFile.readText(Charsets.UTF_8)).getOrThrow()
        }
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
        val db = dbHelper.readableDatabase
        val canonicalMap = linkedMapOf<String, String>()
        readLabelsTableInternal(db).forEach { name ->
            canonicalMap.putIfAbsent(name.lowercase(), name)
        }
        queryNotesInternal(
            db = db,
            selection = "${NotesDatabaseHelper.COL_DELETED_AT} IS NULL",
            orderBy = null
        ).forEach { note ->
            note.tags.forEach { tag ->
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    canonicalMap.putIfAbsent(trimmed.lowercase(), trimmed)
                }
            }
        }
        canonicalMap.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    suspend fun createLabel(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = getAllTags()
        if (existing.any { it.equals(trimmed, ignoreCase = true) }) return@withContext false

        val db = dbHelper.writableDatabase
        insertLabelExact(db, trimmed)
        true
    }

    suspend fun renameLabel(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isEmpty() || trimmedNew == oldName) return@withContext false

        val existing = getAllTags()
        val collidesWithOther = existing.any {
            it.equals(trimmedNew, ignoreCase = true) && !it.equals(oldName, ignoreCase = true)
        }
        if (collidesWithOther) return@withContext false

        val db = dbHelper.writableDatabase
        val allNotes = queryNotesInternal(db = db, selection = null, orderBy = null)
        db.beginTransaction()
        try {
            val storedLabels = readLabelsTableInternal(db)
            storedLabels.filter { it.equals(oldName, ignoreCase = true) }.forEach { match ->
                db.delete(
                    NotesDatabaseHelper.TABLE_LABELS,
                    "${NotesDatabaseHelper.COL_LABEL_NAME} = ?",
                    arrayOf(match)
                )
            }
            insertLabelExact(db, trimmedNew)

            allNotes.forEach { note ->
                if (note.tags.any { it.equals(oldName, ignoreCase = true) }) {
                    val updatedTags = note.tags
                        .map { if (it.equals(oldName, ignoreCase = true)) trimmedNew else it }
                        .distinctBy { it.lowercase() }
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
        val db = dbHelper.writableDatabase
        val allNotes = queryNotesInternal(db = db, selection = null, orderBy = null)
        db.beginTransaction()
        try {
            val storedLabels = readLabelsTableInternal(db)
            storedLabels.filter { it.equals(name, ignoreCase = true) }.forEach { match ->
                db.delete(
                    NotesDatabaseHelper.TABLE_LABELS,
                    "${NotesDatabaseHelper.COL_LABEL_NAME} = ?",
                    arrayOf(match)
                )
            }

            allNotes.forEach { note ->
                if (note.tags.any { it.equals(name, ignoreCase = true) }) {
                    val updatedTags = note.tags.filterNot { it.equals(name, ignoreCase = true) }
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

    fun getSortOption(): NoteSortOption {
        val raw = prefs.getString(KEY_SORT_OPTION, NoteSortOption.UPDATED_DESC.name)
            ?: NoteSortOption.UPDATED_DESC.name
        return runCatching { NoteSortOption.valueOf(raw) }.getOrDefault(NoteSortOption.UPDATED_DESC)
    }

    fun setSortOption(option: NoteSortOption) {
        prefs.edit().putString(KEY_SORT_OPTION, option.name).apply()
    }

    private fun purgeExpiredTrashInternal(db: SQLiteDatabase, now: Long) {
        val cutoff = now - TRASH_RETENTION_MS
        db.delete(
            NotesDatabaseHelper.TABLE_NOTES,
            "${NotesDatabaseHelper.COL_DELETED_AT} IS NOT NULL AND ${NotesDatabaseHelper.COL_DELETED_AT} < ?",
            arrayOf(cutoff.toString())
        )
    }

    private fun queryNotesInternal(
        db: SQLiteDatabase,
        selection: String?,
        orderBy: String?
    ): List<NoteRecord> {
        val list = mutableListOf<NoteRecord>()
        db.query(
            NotesDatabaseHelper.TABLE_NOTES,
            null,
            selection,
            null,
            null,
            null,
            orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToNoteRecord(cursor))
            }
        }
        return list
    }

    private fun cursorToNoteRecord(cursor: Cursor): NoteRecord {
        val richJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_RICH_CONTENT_JSON))
        val tagsJson = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TAGS_JSON))
        val richResult = runCatching { json.decodeFromString<RichNoteContent>(richJson) }
        val isCorrupted = richResult.isFailure
        val richContent = richResult.getOrElse { RichNoteContent() }
        val tags = runCatching {
            json.decodeFromString<List<String>>(tagsJson)
        }.getOrElse { emptyList() }
        val deletedAtIdx = cursor.getColumnIndex(NotesDatabaseHelper.COL_DELETED_AT)
        val deletedAt = if (deletedAtIdx >= 0 && !cursor.isNull(deletedAtIdx)) {
            cursor.getLong(deletedAtIdx)
        } else {
            null
        }

        return NoteRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_TITLE)),
            content = richContent,
            tags = tags,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_UPDATED_AT)),
            pinned = cursor.getInt(cursor.getColumnIndexOrThrow(NotesDatabaseHelper.COL_PINNED)) == 1,
            deletedAt = deletedAt,
            isCorrupted = isCorrupted,
            rawCorruptedJson = if (isCorrupted) richJson else null
        )
    }

    private fun noteContentValues(note: NoteRecord): ContentValues = ContentValues().apply {
        put(NotesDatabaseHelper.COL_ID, note.id)
        put(NotesDatabaseHelper.COL_TITLE, note.title)
        put(NotesDatabaseHelper.COL_PLAIN_TEXT, note.content.text)
        put(
            NotesDatabaseHelper.COL_RICH_CONTENT_JSON,
            json.encodeToString(RichNoteContent.serializer(), note.content)
        )
        put(NotesDatabaseHelper.COL_TAGS_JSON, json.encodeToString(note.tags))
        put(NotesDatabaseHelper.COL_CREATED_AT, note.createdAt)
        put(NotesDatabaseHelper.COL_UPDATED_AT, note.updatedAt)
        put(NotesDatabaseHelper.COL_PINNED, if (note.pinned) 1 else 0)
        if (note.deletedAt != null) {
            put(NotesDatabaseHelper.COL_DELETED_AT, note.deletedAt)
        } else {
            putNull(NotesDatabaseHelper.COL_DELETED_AT)
        }
    }

    private fun readLabelsTableInternal(db: SQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
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
                if (name.isNotEmpty()) result.add(name)
            }
        }
        return result
    }

    private fun canonicalizeTags(tags: List<String>, knownLabels: List<String>): List<String> {
        val map = linkedMapOf<String, String>()
        knownLabels.forEach { label ->
            val trimmed = label.trim()
            if (trimmed.isNotEmpty()) map.putIfAbsent(trimmed.lowercase(), trimmed)
        }
        return tags.mapNotNull { tag ->
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) null
            else map.getOrPut(trimmed.lowercase()) { trimmed }
        }.distinctBy { it.lowercase() }
    }

    private fun insertLabelIfAbsentCaseInsensitive(db: SQLiteDatabase, label: String) {
        val existing = readLabelsTableInternal(db)
        if (existing.none { it.equals(label, ignoreCase = true) }) {
            insertLabelExact(db, label)
        }
    }

    private fun insertLabelExact(db: SQLiteDatabase, label: String) {
        val labelValues = ContentValues().apply {
            put(NotesDatabaseHelper.COL_LABEL_NAME, label)
        }
        db.insertWithOnConflict(
            NotesDatabaseHelper.TABLE_LABELS,
            null,
            labelValues,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    private fun areNotesEquivalent(a: NoteRecord, b: NoteRecord): Boolean {
        return a.title.trim() == b.title.trim() &&
            a.content == b.content &&
            a.pinned == b.pinned &&
            a.tags.map { it.lowercase() } == b.tags.map { it.lowercase() }
    }

    companion object {
        const val NEW_NOTE_DRAFT_KEY = "__new_note_draft__"
        const val TRASH_RETENTION_DAYS = 30L
        const val TRASH_RETENTION_MS = TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L

        private const val SAFETY_BACKUP_FILENAME = "pre_import_safety_backup.json"
        private const val PREFS_NAME = "bible_notes_prefs"
        private const val KEY_OPEN_JW_LIBRARY = "open_with_jw_library"
        private const val KEY_THEME = "theme_option"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SORT_OPTION = "sort_option"
    }
}
