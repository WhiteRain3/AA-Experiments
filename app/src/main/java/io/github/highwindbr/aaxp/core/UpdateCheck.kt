package io.github.highwindbr.aaxp.core

import org.json.JSONObject

/** A published build of this fork, as described by GitHub's "latest release" answer. */
data class AvailableUpdate(val versionName: String, val build: Int, val apkUrl: String, val sizeBytes: Long, val notes: String)

/**
 * Update checks against this fork's own GitHub releases. Nothing about the phone or the user is
 * sent: the check is a plain download of a public page, and the APK is only ever taken from
 * this repository's release downloads.
 */
object UpdateCheck {
    const val LATEST_URL = "https://api.github.com/repos/WhiteRain3/AA-Experiments/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/WhiteRain3/AA-Experiments/releases/download/"
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private val BUILD = Regex("-wr(\\d{1,6})$")

    /** The fork build number in a version name such as "1.0.0-wr17"; null for any other name. */
    fun buildNumber(versionName: String?): Int? =
        versionName?.let { BUILD.find(it.trim()) }?.groupValues?.get(1)?.toIntOrNull()

    /** Reads GitHub's answer; null when it holds no usable APK from this repository. */
    fun parse(json: String): AvailableUpdate? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val name = root.getString("tag_name").removePrefix("v")
        val build = buildNumber(name) ?: return null
        val assets = root.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            val size = asset.optLong("size")
            if (asset.optString("name").endsWith(".apk") && url.startsWith(DOWNLOAD_PREFIX) && size in 1..MAX_APK_BYTES) {
                return AvailableUpdate(name, build, url, size, root.optString("body").take(2000))
            }
        }
        null
    }.getOrNull()

    const val LATEST_PAGE = "https://github.com/WhiteRain3/AA-Experiments/releases/latest"
    private const val TAG_PREFIX = "https://github.com/WhiteRain3/AA-Experiments/releases/tag/"
    private val TAG = Regex("[0-9A-Za-z.]+-wr\\d{1,6}")

    /**
     * The latest build from where GitHub's "latest release" page redirects to. This needs no
     * API call, so it is not subject to the API's hourly limit, which is shared by everyone
     * behind the same mobile network address. The size is unknown (0) until the download starts.
     */
    fun fromLatestRedirect(location: String?): AvailableUpdate? {
        val tag = location?.takeIf { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX) ?: return null
        if (!TAG.matches(tag)) return null
        val build = buildNumber(tag) ?: return null
        return AvailableUpdate(tag, build, "$DOWNLOAD_PREFIX$tag/AA-Experiments-$tag.apk", 0L, "")
    }

    fun isNewer(update: AvailableUpdate, installedVersionName: String?): Boolean {
        val installed = buildNumber(installedVersionName) ?: return true
        return update.build > installed
    }
}
