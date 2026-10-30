package be.johunn.survie.zimprobe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Searchable copy of the curated guide bundled from the PWA source files. */
class GuideContentIndex(context: Context) {
    private val fiches: List<GuideFiche> = context.assets
        .open("guide/guide-fiches.json")
        .bufferedReader(Charsets.UTF_8)
        .use { reader -> parseFiches(JSONArray(reader.readText())) }

    fun search(rawQuery: String, limit: Int): List<GuideSearchResult> {
        if (limit <= 0) return emptyList()
        val tokens = normalize(rawQuery)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val normalizedPhrase = tokens.joinToString(" ")

        return fiches.mapNotNull { fiche ->
            val title = normalize(fiche.title)
            val summary = normalize(fiche.summary)
            val category = normalize(fiche.category)
            val intro = normalize(fiche.intro)
            val steps = normalize(fiche.steps.joinToString(" "))
            val avoid = normalize(fiche.avoid)
            val searchable = listOf(title, summary, category, intro, steps, avoid).joinToString(" ")
            if (tokens.any { token -> !searchable.contains(token) }) return@mapNotNull null

            var score = 0
            if (title == normalizedPhrase) score += 100
            else if (tokens.all { title.contains(it) }) score += 60
            else if (tokens.any { title.contains(it) }) score += 5
            if (summary.contains(normalizedPhrase)) score += 25
            if (tokens.all { summary.contains(it) }) score += 12
            if (tokens.all { category.contains(it) }) score += 10
            if (intro.contains(normalizedPhrase)) score += 8

            GuideSearchResult(
                fiche = fiche,
                snippet = fiche.summary.ifBlank { fiche.intro.take(220) },
                score = score
            )
        }
            .sortedWith(compareByDescending<GuideSearchResult> { it.score }
                .thenBy { it.fiche.title.lowercase(Locale.ROOT) })
            .take(limit)
    }

    fun getFiche(id: String): GuideFiche? = fiches.firstOrNull { it.id == id }

    private fun parseFiches(array: JSONArray): List<GuideFiche> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                GuideFiche(
                    id = item.optString("id"),
                    category = item.optString("category"),
                    title = item.optString("title"),
                    summary = item.optString("summary"),
                    intro = item.optString("intro"),
                    steps = item.stringList("steps"),
                    avoid = item.optString("avoid"),
                    sources = item.sourceList()
                )
            )
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val values = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                values.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.sourceList(): List<GuideSource> {
        val values = optJSONArray("sources") ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                val source = values.optJSONArray(index) ?: continue
                val title = source.optString(0)
                val url = source.optString(1)
                if (title.isNotBlank() && url.isNotBlank()) add(GuideSource(title, url))
            }
        }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

data class GuideFiche(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val intro: String,
    val steps: List<String>,
    val avoid: String,
    val sources: List<GuideSource>
)

data class GuideSource(val title: String, val url: String)

data class GuideSearchResult(
    val fiche: GuideFiche,
    val snippet: String,
    val score: Int
)
