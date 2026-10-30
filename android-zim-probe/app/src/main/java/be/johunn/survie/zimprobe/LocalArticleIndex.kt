package be.johunn.survie.zimprobe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Offline full-text index for ZIM articles.
 *
 * The original ZIM remains untouched. Only extracted article text is stored
 * in the application's private database using SQLite FTS4.
 */
class LocalArticleIndex(
    context: Context,
    archiveKey: String
) : SQLiteOpenHelper(
    context,
    "zim_articles_${archiveKey.filter { it.isLetterOrDigit() }.take(48)}.db",
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_VERSION = 1
        private const val TABLE = "article_fts"
        private const val STATE_TABLE = "index_state"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE $TABLE USING fts4(title, path, body, tokenize=unicode61)"
        )
        db.execSQL(
            "CREATE TABLE $STATE_TABLE (id INTEGER PRIMARY KEY CHECK(id = 1), complete INTEGER NOT NULL, article_count INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO $STATE_TABLE (id, complete, article_count) VALUES (1, 0, 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        db.execSQL("DROP TABLE IF EXISTS $STATE_TABLE")
        onCreate(db)
    }

    @Synchronized
    fun isReady(): Boolean {
        readableDatabase.rawQuery(
            "SELECT complete FROM $STATE_TABLE WHERE id = 1",
            null
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) == 1
        }
    }

    @Synchronized
    fun beginRebuild() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, null, null)
            db.execSQL(
                "UPDATE $STATE_TABLE SET complete = 0, article_count = 0 WHERE id = 1"
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun addArticles(articles: List<ArticleDocument>) {
        if (articles.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (article in articles) {
                val values = ContentValues().apply {
                    put("title", article.title)
                    put("path", article.path)
                    put("body", article.body)
                }
                db.insertOrThrow(TABLE, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun finishRebuild(articleCount: Int) {
        writableDatabase.execSQL(
            "UPDATE $STATE_TABLE SET complete = 1, article_count = ? WHERE id = 1",
            arrayOf(articleCount)
        )
    }

    @Synchronized
    fun articleCount(): Int {
        readableDatabase.rawQuery(
            "SELECT article_count FROM $STATE_TABLE WHERE id = 1",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    @Synchronized
    fun search(rawQuery: String, limit: Int): List<ArticleSearchResult> {
        val tokens = Regex("[\\p{L}\\p{N}]+")
            .findAll(rawQuery)
            .map { it.value }
            .toList()
        if (tokens.isEmpty()) return emptyList()

        // Quote each token so punctuation cannot become FTS query syntax.
        val matchQuery = tokens.joinToString(" AND ") { "\"$it\"" }
        val results = mutableListOf<ArticleSearchResult>()
        readableDatabase.rawQuery(
            "SELECT title, path, snippet($TABLE, 2, '⟦', '⟧', '…', 18) " +
                "FROM $TABLE WHERE $TABLE MATCH ? LIMIT ?",
            arrayOf(matchQuery, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ArticleSearchResult(
                    title = cursor.getString(0).orEmpty(),
                    path = cursor.getString(1).orEmpty(),
                    snippet = cursor.getString(2).orEmpty()
                )
            }
        }
        return results
    }
}

data class ArticleDocument(
    val title: String,
    val path: String,
    val body: String
)

data class ArticleSearchResult(
    val title: String,
    val path: String,
    val snippet: String
)
