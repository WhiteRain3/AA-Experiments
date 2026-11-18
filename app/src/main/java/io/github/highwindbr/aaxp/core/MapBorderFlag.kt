package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.discovery.DiscoveredType

/** The "navigation window border width" flag, set through the normal flag overrides. */
object MapBorderFlag {
    const val KEY = "Coolwalk__nav_app_border_width"
    const val MIN = 0
    /** A modest upper limit for the slider; the flag's real unit and range are not known. */
    const val MAX = 12

    enum class Status { UNKNOWN, MISSING, NOT_INTEGER, OK }

    /** [found] is whether the installed catalogue has the flag (null when that could not be checked). */
    fun status(found: Boolean?, type: DiscoveredType?): Status = when {
        found == null -> Status.UNKNOWN
        !found -> Status.MISSING
        type != DiscoveredType.LONG && type != DiscoveredType.INT -> Status.NOT_INTEGER
        else -> Status.OK
    }

    /** The width currently overridden, or null when Android Auto's own is in use. [serialized] maps flag key to a stored override. */
    fun current(serialized: Map<String, String>): Int? =
        DynamicOverrideCodec.deserialize(serialized[KEY])?.encodedValue?.toIntOrNull()

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)

    fun label(value: Int?): String = if (value == null) "Android Auto's own" else "$value"
}
