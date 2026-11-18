package io.github.highwindbr.aaxp.core

import io.github.highwindbr.aaxp.runtime.GlyphIcons
import io.github.highwindbr.aaxp.runtime.StatusBarTweaks
import io.github.highwindbr.aaxp.runtime.ThemeColors
import org.json.JSONObject

/**
 * Everything that makes up a look, in one file: the colours in use (roles and the extra
 * resource colours), glyph icon colours, status bar changes, saved themes, flag presets and, optionally, the car wallpaper with its editor
 * settings. Used both as a style pack to share and as a backup. A part that is null is absent.
 */
class LookBundle(
    val themeColors: String? = null,
    val glyphColors: String? = null,
    val statusBar: String? = null,
    val themesJson: String? = null,
    val presetsJson: String? = null,
    val wallpaperSettings: JSONObject? = null,
    val wallpaperImage: ByteArray? = null,
    val resourceColors: String? = null,
    val launcherConfig: String? = null,
    val railLook: String? = null,
    val glyphSize: String? = null
)

object LookBundles {
    const val MAX_IMAGE_BYTES = 12 * 1024 * 1024

    fun encode(bundle: LookBundle): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("kind", "aa-experiments-look")
        bundle.themeColors?.let { put("themeColors", it) }
        bundle.glyphColors?.let { put("glyphColors", it) }
        bundle.statusBar?.let { put("statusBar", it) }
        bundle.resourceColors?.let { put("resourceColors", it) }
        bundle.launcherConfig?.let { put("launcherConfig", it) }
        bundle.railLook?.let { put("railLook", it) }
        bundle.glyphSize?.let { put("glyphSize", it) }
        bundle.themesJson?.let { put("themes", JSONObject(it)) }
        bundle.presetsJson?.let { put("presets", JSONObject(it)) }
        bundle.wallpaperImage?.let { image ->
            put("wallpaperImage", java.util.Base64.getEncoder().encodeToString(image))
            bundle.wallpaperSettings?.let { put("wallpaperSettings", it) }
        }
    }.toString()

    /** Parses and checks a look file. Throws with a readable reason when any part is unusable. */
    fun decode(text: String): LookBundle {
        val root = JSONObject(text)
        require(root.optString("kind") == "aa-experiments-look" && root.optInt("schemaVersion") == 1) { "Not a look file" }
        val colors = root.optString("themeColors").ifEmpty { null }
        require(colors == null || ThemeColors.parse(colors) != null) { "The colours in this file are not readable" }
        val glyph = root.optString("glyphColors").ifEmpty { null }
        require(glyph == null || GlyphIcons.parseColors(glyph) != null) { "The glyph colours in this file are not readable" }
        val statusBar = root.optString("statusBar").ifEmpty { null }
        require(statusBar == null || StatusBarTweaks.parse(statusBar) != null) { "The status bar settings in this file are not readable" }
        val resourceColors = root.optString("resourceColors").ifEmpty { null }
        require(resourceColors == null || io.github.highwindbr.aaxp.runtime.ResourceColors.parse(resourceColors) != null) {
            "The extra colours in this file are not readable"
        }
        val launcher = root.optString("launcherConfig").ifEmpty { null }
        require(launcher == null || io.github.highwindbr.aaxp.runtime.LauncherTweaks.parse(launcher) != null) {
            "The launcher settings in this file are not readable"
        }
        val railLook = root.optString("railLook").ifEmpty { null }
        require(railLook == null || io.github.highwindbr.aaxp.runtime.CarWallpaper.encodeRailLook(
            io.github.highwindbr.aaxp.runtime.CarWallpaper.parseRailLook(railLook)) != null) { "The rail strip settings in this file are not readable" }
        val glyphSize = root.optString("glyphSize").ifEmpty { null }
        require(glyphSize == null || glyphSize.trim().toIntOrNull()
            ?.let { it in io.github.highwindbr.aaxp.runtime.GlyphIcons.MIN_SIZE..io.github.highwindbr.aaxp.runtime.GlyphIcons.MAX_SIZE } == true) {
            "The icon size in this file is not readable"
        }
        val themes = root.optJSONObject("themes")?.toString()?.also { Themes.decode(it) }
        val presets = root.optJSONObject("presets")?.toString()?.also { Presets.decode(it) }
        val image = root.optString("wallpaperImage").ifEmpty { null }?.let {
            require(it.length <= MAX_IMAGE_BYTES / 3 * 4 + 4) { "The wallpaper in this file is too large" }
            java.util.Base64.getDecoder().decode(it)
        }
        return LookBundle(colors, glyph, statusBar, themes, presets,
            if (image != null) root.optJSONObject("wallpaperSettings") else null, image, resourceColors, launcher, railLook, glyphSize)
    }
}
