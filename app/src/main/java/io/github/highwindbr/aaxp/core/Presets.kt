package io.github.highwindbr.aaxp.core

import org.json.JSONObject

/**
 * A named set of normal overrides. Values are stored in the same "TYPE:value" form as
 * [DynamicOverrideCodec.serialize], keyed by experiment name, so a preset stays readable and
 * is validated against the installed build again every time it is applied.
 */
data class Preset(val name: String, val overrides: Map<String, String>, val savedOnVersion: String = "")

/** What applying a preset would do to the current normal overrides. */
data class PresetDiff(
    val added: Map<String, String>,
    val changed: Map<String, Pair<String, String>>,
    val removed: Map<String, String>
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
    val size: Int get() = added.size + changed.size + removed.size
}

object Presets {
    const val MAX_NAME_LENGTH = 40
    const val MAX_PRESETS = 50

    /** Trims and bounds a preset name; null when nothing usable is left. */
    fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }

    /** Applying replaces the current set: keys missing from the preset go back to default. */
    fun diff(current: Map<String, String>, target: Map<String, String>): PresetDiff = PresetDiff(
        added = target.filterKeys { it !in current }.toSortedMap(),
        changed = target.filter { (key, value) -> current[key]?.let { it != value } == true }
            .mapValues { (key, value) -> current.getValue(key) to value }.toSortedMap(),
        removed = current.filterKeys { it !in target }.toSortedMap()
    )

    /** "TYPE:value" shown as just the value. */
    fun displayValue(serialized: String): String = serialized.substringAfter(':', serialized)

    fun encode(presets: Collection<Preset>): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("presets", JSONObject().apply {
            presets.forEach { preset ->
                put(preset.name, JSONObject().apply {
                    put("savedOnVersion", preset.savedOnVersion)
                    put("overrides", JSONObject().apply { preset.overrides.toSortedMap().forEach { (k, v) -> put(k, v) } })
                })
            }
        })
    }.toString(1)

    /** Parses presets, skipping entries that are not well formed. Throws on an unusable document. */
    fun decode(text: String): List<Preset> {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion") == 1) { "Unsupported presets file" }
        val presets = root.getJSONObject("presets")
        return buildList {
            presets.keys().forEach { rawName ->
                val name = cleanName(rawName) ?: return@forEach
                val entry = presets.optJSONObject(rawName) ?: return@forEach
                val values = entry.optJSONObject("overrides") ?: return@forEach
                val overrides = buildMap {
                    values.keys().forEach { key ->
                        val value = values.optString(key)
                        // Only values the codec itself can read are kept.
                        if (key.isNotBlank() && DynamicOverrideCodec.deserialize(value) != null) put(key, value)
                    }
                }
                add(Preset(name, overrides, entry.optString("savedOnVersion")))
            }
        }.take(MAX_PRESETS)
    }
}
