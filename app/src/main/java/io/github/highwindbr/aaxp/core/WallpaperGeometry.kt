package io.github.highwindbr.aaxp.core

import kotlin.math.max
import kotlin.math.min

/** Where the app rail sits on the car screen. */
enum class RailSide { LEFT, RIGHT, BOTTOM, NONE }

/** What the wallpaper should cover. */
enum class WallpaperCoverage { WHOLE_SCREEN, MAIN_AREA }

/** A car screen in pixels, with the strip taken by the rail. */
data class CarScreen(val width: Int, val height: Int, val rail: RailSide, val railSize: Int) {
    private val strip = railSize.coerceIn(0, when (rail) {
        RailSide.LEFT, RailSide.RIGHT -> width - 1
        RailSide.BOTTOM -> height - 1
        RailSide.NONE -> 0
    }.coerceAtLeast(0))
    val mainLeft: Int get() = if (rail == RailSide.LEFT) strip else 0
    val mainTop: Int get() = 0
    val mainWidth: Int get() = if (rail == RailSide.LEFT || rail == RailSide.RIGHT) width - strip else width
    val mainHeight: Int get() = if (rail == RailSide.BOTTOM) height - strip else height
}

/**
 * The image to produce, and where the part being edited (the "frame") sits inside it.
 * Everything is in car-screen pixels; the stored image is this canvas scaled up uniformly.
 */
data class WallpaperCanvas(
    val width: Int, val height: Int,
    val frameLeft: Int, val frameTop: Int, val frameWidth: Int, val frameHeight: Int
)

/** A rectangle in frame pixels: left, top, right (exclusive), bottom (exclusive). */
data class FrameRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** How the source image is placed in the frame. Offsets are frame pixels from the centred position. */
data class WallpaperPlacement(
    val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f, val quarterTurns: Int = 0
)

/**
 * Geometry of the car wallpaper.
 *
 * Observed behaviour: Android Auto centres the wallpaper on the main area (the screen without the
 * rail) and scales it until it fills that area, letting any excess spill over the rail. To decide
 * what appears on the whole screen, the produced canvas is therefore made symmetric around the
 * main area's centre and exactly as large as the main area in the other direction, so Android
 * Auto shows it at 1:1 and the frame lands exactly on the screen.
 */
object WallpaperGeometry {
    fun canvas(screen: CarScreen, coverage: WallpaperCoverage): WallpaperCanvas {
        if (coverage == WallpaperCoverage.MAIN_AREA) {
            return WallpaperCanvas(screen.mainWidth, screen.mainHeight, 0, 0, screen.mainWidth, screen.mainHeight)
        }
        // Twice the larger distance from the main area's centre to a screen edge, per axis.
        val centreX2 = 2 * screen.mainLeft + screen.mainWidth
        val centreY2 = 2 * screen.mainTop + screen.mainHeight
        val width = max(centreX2, 2 * screen.width - centreX2)
        val height = max(centreY2, 2 * screen.height - centreY2)
        return WallpaperCanvas(width, height,
            frameLeft = (width - centreX2) / 2, frameTop = (height - centreY2) / 2,
            frameWidth = screen.width, frameHeight = screen.height)
    }

    /**
     * The separator line drawn where the main area meets the rail, in frame pixels, or null when
     * there is no rail or the line has no thickness. The line lies inside the main area, along
     * the edge that touches the rail, so it is visible whichever coverage is chosen.
     */
    fun separator(screen: CarScreen, coverage: WallpaperCoverage, thickness: Int): FrameRect? {
        if (thickness <= 0 || screen.rail == RailSide.NONE) return null
        val whole = coverage == WallpaperCoverage.WHOLE_SCREEN
        // Main area position inside the frame: the frame is the screen, or the main area itself.
        val left = if (whole) screen.mainLeft else 0
        val top = if (whole) screen.mainTop else 0
        val right = left + screen.mainWidth
        val bottom = top + screen.mainHeight
        return when (screen.rail) {
            RailSide.LEFT -> FrameRect(left, top, minOf(left + thickness, right), bottom)
            RailSide.RIGHT -> FrameRect(maxOf(right - thickness, left), top, right, bottom)
            RailSide.BOTTOM -> FrameRect(left, maxOf(bottom - thickness, top), right, bottom)
            RailSide.NONE -> null
        }
    }

    /** Parses "#RRGGBB" or "#AARRGGBB" (the # is optional) into an ARGB colour, or null. */
    fun parseColor(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        return (if (hex.length == 6) 0xFF000000L or value else value).toInt()
    }

    /** Scale at which an image just covers the frame (no empty edges). */
    fun coverScale(imageWidth: Int, imageHeight: Int, frameWidth: Int, frameHeight: Int): Float =
        max(frameWidth.toFloat() / imageWidth, frameHeight.toFloat() / imageHeight)

    /** Scale at which the whole image is visible inside the frame. */
    fun fitScale(imageWidth: Int, imageHeight: Int, frameWidth: Int, frameHeight: Int): Float =
        min(frameWidth.toFloat() / imageWidth, frameHeight.toFloat() / imageHeight)

    /** Image size as seen after [quarterTurns] rotations of 90 degrees. */
    fun rotatedSize(imageWidth: Int, imageHeight: Int, quarterTurns: Int): Pair<Int, Int> =
        if (quarterTurns % 2 == 0) imageWidth to imageHeight else imageHeight to imageWidth

    /** Uniform factor from car-screen pixels to stored pixels, keeping the longest edge within [maxEdge]. */
    fun outputScale(canvas: WallpaperCanvas, maxEdge: Int): Float =
        (maxEdge.toFloat() / max(canvas.width, canvas.height)).coerceIn(1f, 4f)
}
