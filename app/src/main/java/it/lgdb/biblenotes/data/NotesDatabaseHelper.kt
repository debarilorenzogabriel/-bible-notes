package it.lgdb.biblenotes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NotesDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_PLAIN_TEXT TEXT NOT NULL,
                $COL_RICH_CONTENT_JSON TEXT NOT NULL,
                $COL_TAGS_JSON TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_PINNED INTEGER NOT NULL DEFAULT 0,
                $COL_DELETED_AT INTEGER DEFAULT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_LABELS (
                $COL_LABEL_NAME TEXT PRIMARY KEY
            )
            """.trimIndent()
        )
        createDraftsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NOTES ADD COLUMN $COL_DELETED_AT INTEGER DEFAULT NULL")
            createDraftsTable(db)
        }
    }

    private fun createDraftsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DRAFTS (
                $COL_DRAFT_KEY TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_RICH_CONTENT_JSON TEXT NOT NULL,
                $COL_TAGS_JSON TEXT NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    companion object {
        const val DATABASE_NAME = "biblenotes_native.db"
        const val DATABASE_VERSION = 2

        const val TABLE_NOTES = "notes"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_PLAIN_TEXT = "plain_text"
        const val COL_RICH_CONTENT_JSON = "rich_content_json"
        const val COL_TAGS_JSON = "tags_json"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_PINNED = "pinned"
        const val COL_DELETED_AT = "deleted_at"

        const val TABLE_LABELS = "labels"
        const val COL_LABEL_NAME = "name"

        const val TABLE_DRAFTS = "drafts"
        const val COL_DRAFT_KEY = "note_key"
    }
}
