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
                $COL_PINNED INTEGER NOT NULL DEFAULT 0
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations will preserve existing local data
    }

    companion object {
        const val DATABASE_NAME = "biblenotes_native.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NOTES = "notes"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_PLAIN_TEXT = "plain_text"
        const val COL_RICH_CONTENT_JSON = "rich_content_json"
        const val COL_TAGS_JSON = "tags_json"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_PINNED = "pinned"

        const val TABLE_LABELS = "labels"
        const val COL_LABEL_NAME = "name"
    }
}
