package io.github.highwindbr.aaxp.core

import org.json.JSONObject

/**
 * A named group of flag overrides that one switch turns on together. Turning it on sets every flag
 * in [flags] to its value; turning it off puts back what each flag was before (a flag that had no
 * override goes back to Android Auto's default). The values are written through the normal
 * flag-override path, so they show up on the Flags page like any other override.
 */
data class FlagSwitch(val id: String, val title: String, val flags: Map<String, String>)

object FlagSwitches {
    val PARKED_APPS = FlagSwitch("parked_apps", "Parked apps", linkedMapOf(
        "CradleFeature__all_app_launcher_enabled" to "true",
        "CradleFeature__allow_video_apps" to "true"))

    val ALL: List<FlagSwitch> = listOf(PARKED_APPS)

    /** True when every flag of [switch] currently has the switch's value. [current] maps flag key to a serialized override. */
    fun isOn(switch: FlagSwitch, current: Map<String, String>): Boolean =
        switch.flags.all { (key, value) -> DynamicOverrideCodec.deserialize(current[key])?.encodedValue == value }

    /**
     * What to remember before turning [switch] on: each flag's current serialized override, or
     * null for none. An earlier memory ([existing]) wins, so turning the switch on twice never
     * records its own values as "before".
     */
    fun beforeValues(switch: FlagSwitch, current: Map<String, String>, existing: Map<String, String?>?): Map<String, String?> =
        switch.flags.keys.associateWith { key -> if (existing != null && key in existing) existing[key] else current[key] }

    /** The plain value to set for [key] when turning off, or null to remove the override. */
    fun valueToRestore(before: Map<String, String?>?, key: String): String? =
        DynamicOverrideCodec.deserialize(before?.get(key))?.encodedValue

    fun encodeBefore(before: Map<String, String?>): String = JSONObject().apply {
        before.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }.toString()

    fun decodeBefore(text: String?): Map<String, String?>? = runCatching {
        val json = JSONObject(text ?: return null)
        json.keys().asSequence().associateWith { key -> if (json.isNull(key)) null else json.getString(key) }
    }.getOrNull()
}
