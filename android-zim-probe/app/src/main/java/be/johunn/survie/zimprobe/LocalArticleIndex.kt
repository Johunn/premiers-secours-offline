package be.johunn.survie.zimprobe

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.ln

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
        private const val DATABASE_VERSION = 3
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
        if (limit <= 0) return emptyList()
        val queryGroups = Regex("(?:[\\p{L}\\p{N}]+[-‐‑‒–—−])+[\\p{L}\\p{N}]+|[\\p{L}\\p{N}]+")
            .findAll(rawQuery)
            .map { group ->
                Regex("[\\p{L}\\p{N}]+")
                    .findAll(group.value)
                    .map { it.value }
                    .toList()
            }
            .toList()
        if (queryGroups.isEmpty()) return emptyList()

        // Keep hyphenated words as phrases (e.g. grand-place -> "grand place").
        // Separate query groups still use AND, while punctuation cannot become
        // FTS query syntax.
        val tokens = queryGroups.flatten()
        val matchQuery = queryGroups.joinToString(" AND ") { group ->
            "\"${group.joinToString(" ")}\""
        }
        val phraseQuery = "\"${tokens.joinToString(" ")}\""
        val documentCount = articleCount().coerceAtLeast(1)
        val db = readableDatabase
        val phraseMatches = db.rawQuery(
            "SELECT docid FROM $TABLE WHERE $TABLE MATCH ?",
            arrayOf(phraseQuery)
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
        val rankedResults = mutableListOf<RankedArticleSearchResult>()

        db.rawQuery(
            "SELECT docid, title, path, " +
                "snippet($TABLE, '⟦', '⟧', '…', 2, 18), " +
                "snippet($TABLE, '⟦', '⟧', '…', -1, 18), " +
                "matchinfo($TABLE, 'pcx') " +
                "FROM $TABLE WHERE $TABLE MATCH ?",
            arrayOf(matchQuery)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val bodySnippet = cursor.getString(3).orEmpty()
                val anyColumnSnippet = cursor.getString(4).orEmpty()
                val result = ArticleSearchResult(
                    title = cursor.getString(1).orEmpty(),
                    path = cursor.getString(2).orEmpty(),
                    snippet = bodySnippet.takeIf { '⟦' in it } ?: anyColumnSnippet
                )
                rankedResults += RankedArticleSearchResult(
                    result = result,
                    score = relevanceScore(
                        matchInfo = cursor.getBlob(5),
                        documentCount = documentCount,
                        phraseMatch = cursor.getLong(0) in phraseMatches
                    )
                )
            }
        }

        // FTS4 does not expose a built-in rank column, so sort the matching rows
        // by the per-row match statistics before applying the display limit.
        return rankedResults
            .sortedWith(
                compareByDescending<RankedArticleSearchResult> { it.score }
                    .thenBy { it.result.title.lowercase(Locale.ROOT) }
            )
            .take(limit)
            .map { it.result }
    }

    private fun relevanceScore(
        matchInfo: ByteArray,
        documentCount: Int,
        phraseMatch: Boolean
    ): Double {
        if (matchInfo.size < 8 || matchInfo.size % Int.SIZE_BYTES != 0) return 0.0

        val info = ByteBuffer.wrap(matchInfo).order(ByteOrder.nativeOrder())
        val phraseCount = info.int
        val columnCount = info.int
        if (phraseCount <= 0 || columnCount <= 0) return 0.0

        val requiredInts = phraseCount.toLong() * columnCount * 3L
        if (requiredInts > info.remaining() / Int.SIZE_BYTES) return 0.0

        val columnWeights = doubleArrayOf(2.0, 1.5, 1.0)
        val phraseHitsInTitle = LongArray(phraseCount)
        var idfTotal = 0.0
        var score = 0.0

        repeat(phraseCount) { phrase ->
            var phraseIdf = 0.0
            repeat(columnCount) { column ->
                val hitsInRow = info.int.toLong() and 0xffffffffL
                info.int // Total hits in this column across the index.
                val matchingDocuments = info.int.toLong() and 0xffffffffL
                if (hitsInRow > 0L) {
                    val idf = ln(
                        1.0 + (documentCount - matchingDocuments + 0.5) /
                            (matchingDocuments + 0.5)
                    )
                    val termFrequency = hitsInRow.toDouble()
                    val saturatedFrequency =
                        termFrequency * 2.2 / (termFrequency + 1.2)
                    val columnWeight = columnWeights.getOrElse(column) { 1.0 }
                    score += columnWeight * idf * saturatedFrequency
                    phraseIdf = maxOf(phraseIdf, idf)
                    if (column == 0) phraseHitsInTitle[phrase] = hitsInRow
                }
            }
            idfTotal += phraseIdf
        }

        // A partial title hit must not outweigh a complete phrase in an article.
        // Give a strong title boost only when every query term is in the title.
        if (phraseHitsInTitle.all { it > 0L }) score += idfTotal * 12.0
        else if (phraseHitsInTitle.any { it > 0L }) score += idfTotal * 0.5

        // Prefer results where the normalized words occur together, such as
        // “Grand-Place”, over pages that contain the words far apart.
        if (phraseMatch) score += idfTotal * 8.0
        return score
    }

    private data class RankedArticleSearchResult(
        val result: ArticleSearchResult,
        val score: Double
    )
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
