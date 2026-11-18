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

    /**
     * Lower quantisation numbers (QP) mean a sharper picture and more data. Android Auto's own
     * values are 15 and 30; these are lower. The best values for a given adapter are not known.
     */
    val SHARP_PICTURE = FlagSwitch("sharp_picture", "Sharp picture", linkedMapOf(
        "VideoEncoderParams__max_qp_wireless" to "22",
        "VideoEncoderParams__min_qp_wireless" to "10"))

    val ALL: List<FlagSwitch> = listOf(PARKED_APPS, SHARP_PICTURE)

    /** True when every flag of [switch] currently has the switch's value. [current] maps flag key to a serialized override. */
    fun isOn(switch: FlagSwitch, current: Map<String, String>): Boolean =
        switch.flags.all { (key, value) -> DynamicOverrideCodec.deserialize(current[key])?.encodedValue == value }

    /**
     * What to remember before turning [switch] on: each flag's current serialized override, or
     * null for none. An earlier memory ([existing]) wins, so turning the switch on twice never
     * records its own values as "before".
     */
    fun beforeValues(switch: FlagSwitch, current: Map<String, String>, existing: Map<String, String?>?): Map<String, String?> =
        switch.flags.keys.associateWith { key ->
            // An earlier memory only still applies while the flag sits at the switch's own value; a hand edit since then wins.
            val atSwitchValue = DynamicOverrideCodec.deserialize(current[key])?.encodedValue == switch.flags[key]
            if (existing != null && key in existing && atSwitchValue) existing[key] else current[key]
        }

    /**
     * What turning [switch] off changes: only flags still at the switch's value go back to their
     * earlier value (null removes the override); a flag the owner has changed since is left alone.
     * Null when there is no record of earlier values, which means the values were set by hand and
     * must not be removed.
     */
    fun turnOffPlan(switch: FlagSwitch, current: Map<String, String>, before: Map<String, String?>?): Map<String, String?>? {
        if (before == null) return null
        return switch.flags.filter { (key, value) -> DynamicOverrideCodec.deserialize(current[key])?.encodedValue == value }
            .keys.associateWith { valueToRestore(before, it) }
    }

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
