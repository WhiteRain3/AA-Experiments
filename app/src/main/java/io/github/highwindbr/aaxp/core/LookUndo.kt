package io.github.highwindbr.aaxp.core

import org.json.JSONObject

/**
 * The state kept so the last look change can be undone: the colours, extra colours, glyph icons,
 * status bar changes and the wallpaper's settings. The wallpaper image itself is kept in a file
 * of its own and identified by [wallpaperStamp]; it is only copied when it has changed.
 */
class LookSnapshot(
    val label: String,
    val themeColors: String?,
    val glyphColors: String?,
    val statusBar: String?,
    val resourceColors: String?,
    val wallpaperSettings: JSONObject?,
    val wallpaperStamp: String?,
    /** Whether a car wallpaper was in use; null in copies made before this was recorded. */
    val wallpaperOn: Boolean? = null
)

object LookUndo {
    fun encode(s: LookSnapshot): String = JSONObject().apply {
        put("label", s.label)
        s.themeColors?.let { put("themeColors", it) }
        s.glyphColors?.let { put("glyphColors", it) }
        s.statusBar?.let { put("statusBar", it) }
        s.resourceColors?.let { put("resourceColors", it) }
        s.wallpaperOn?.let { put("wallpaperOn", it) }
        if (s.wallpaperStamp != null && s.wallpaperSettings != null) {
            put("wallpaperSettings", s.wallpaperSettings)
            put("wallpaperStamp", s.wallpaperStamp)
        }
    }.toString()

    /** Null when the text is not a snapshot. */
    fun decode(text: String): LookSnapshot? = runCatching {
        val j = JSONObject(text)
        fun str(key: String) = j.optString(key).ifEmpty { null }
        val stamp = str("wallpaperStamp")
        LookSnapshot(j.optString("label", "a change"), str("themeColors"), str("glyphColors"), str("statusBar"),
            str("resourceColors"), if (stamp != null) j.optJSONObject("wallpaperSettings") else null, stamp,
            if (j.has("wallpaperOn")) j.optBoolean("wallpaperOn") else null)
    }.getOrNull()

    enum class WallpaperAction { REMOVE, RESTORE, LEAVE }

    /**
     * What undoing should do with the car wallpaper. It is removed only when the copy positively
     * records that there was none; with no usable kept image it is left exactly as it is.
     */
    fun wallpaperAction(kept: LookSnapshot, imageKept: Boolean): WallpaperAction = when {
        kept.wallpaperOn == false -> WallpaperAction.REMOVE
        imageKept -> WallpaperAction.RESTORE
        else -> WallpaperAction.LEAVE
    }

    fun stamp(length: Long, lastModified: Long) = "$length-$lastModified"

    class WallpaperPlan(val copyImage: Boolean, val snapshotStamp: String?)

    /**
     * Decides what to keep of the wallpaper image. [appliedStamp] identifies the image the car is
     * using (null if not known: the current file is then assumed to be it); [undoImageStamp]
     * identifies the copy already kept. A newly picked image that has not been applied yet is
     * never mistaken for the one in use.
     */
    fun wallpaperPlan(enabled: Boolean, sourceStamp: String?, appliedStamp: String?, undoImageStamp: String?): WallpaperPlan {
        if (!enabled || sourceStamp == null) return WallpaperPlan(false, null)
        val applied = appliedStamp ?: sourceStamp
        return when {
            sourceStamp == applied -> WallpaperPlan(undoImageStamp != applied, applied)
            undoImageStamp == applied -> WallpaperPlan(false, applied)
            else -> WallpaperPlan(false, null)
        }
    }

    /** Plain-English names of the parts that differ between what is set now and the snapshot. */
    fun differences(now: LookSnapshot, kept: LookSnapshot): List<String> = buildList {
        if (now.themeColors != kept.themeColors) add("colours")
        if (now.resourceColors != kept.resourceColors) add("extra colours")
        if (now.glyphColors != kept.glyphColors) add("single-colour icons")
        if (now.statusBar != kept.statusBar) add("status bar")
        if (now.wallpaperStamp != kept.wallpaperStamp || now.wallpaperSettings?.toString() != kept.wallpaperSettings?.toString())
            add("wallpaper")
    }

    fun restoredMessage(parts: List<String>, label: String) =
        if (parts.isEmpty()) "Nothing to undo: the look is already as it was before $label."
        else "Undone: ${parts.joinToString(", ")} put back to how they were before $label."
}
