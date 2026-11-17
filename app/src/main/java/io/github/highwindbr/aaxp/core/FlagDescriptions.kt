package io.github.highwindbr.aaxp.core

import android.content.Context
import org.json.JSONObject

/** Plain-language note about one experiment: what it changes, how risky it is, what to try. */
data class FlagDescription(
    val title: String,
    val description: String,
    val risk: String,
    val category: String,
    val suggest: String?,
    val verified: String?
) {
    val riskLabel: String get() = when (risk) {
        "safe" -> "Safe"
        "may_break" -> "May break"
        "safety" -> "Driving safety"
        else -> "Experimental"
    }
}

/**
 * Bundled descriptions keyed by experiment name. They are editorial notes, not resolver input:
 * they never affect which experiments are editable or what value is applied. An experiment
 * without an entry simply has no description.
 */
object FlagDescriptions {
    @Volatile private var cached: Map<String, FlagDescription>? = null

    fun parse(json: String): Map<String, FlagDescription> {
        val flags = JSONObject(json).getJSONArray("flags")
        val result = LinkedHashMap<String, FlagDescription>(flags.length())
        for (i in 0 until flags.length()) {
            val item = flags.getJSONObject(i)
            val name = item.optString("name")
            val description = item.optString("description")
            if (name.isEmpty() || description.isEmpty()) continue
            result[name] = FlagDescription(
                title = item.optString("title", name),
                description = description,
                risk = item.optString("risk", "experimental"),
                category = item.optString("category", "other"),
                suggest = item.optString("suggest").takeIf { it.isNotEmpty() },
                verified = item.optString("verified").takeIf { it.isNotEmpty() }
            )
        }
        return result
    }

    fun all(context: Context): Map<String, FlagDescription> = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.applicationContext.assets.open("flag_descriptions.json")
                .bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }.getOrDefault(emptyMap()).also { cached = it }
    }

    fun find(context: Context, key: String): FlagDescription? = all(context)[key]

    /** Names of described experiments whose title or description contains [query]. */
    fun matching(context: Context, query: String): Set<String> =
        if (query.isBlank()) emptySet()
        else all(context).filterValues {
            it.title.contains(query, true) || it.description.contains(query, true)
        }.keys
}
