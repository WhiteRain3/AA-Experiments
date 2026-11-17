package io.github.highwindbr.aaxp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperGeometryTest {
    private val whole = WallpaperCoverage.WHOLE_SCREEN

    @Test fun leftRailCanvasIsSymmetricAroundMainArea() {
        // 800x480 with a 72 px rail on the left: main area 728x480 centred at x=436.
        val canvas = WallpaperGeometry.canvas(CarScreen(800, 480, RailSide.LEFT, 72), whole)
        assertEquals(WallpaperCanvas(872, 480, 0, 0, 800, 480), canvas)
    }

    @Test fun rightRailShiftsTheFrameInsideTheCanvas() {
        val canvas = WallpaperGeometry.canvas(CarScreen(800, 480, RailSide.RIGHT, 72), whole)
        assertEquals(WallpaperCanvas(872, 480, 72, 0, 800, 480), canvas)
    }

    @Test fun bottomRailPadsAboveTheScreen() {
        // Main area 800x400 centred at y=200. To stay symmetric around that centre and still reach
        // the bottom edge (280 below it), the canvas is 560 tall and starts 80 px above the screen.
        val canvas = WallpaperGeometry.canvas(CarScreen(800, 480, RailSide.BOTTOM, 80), whole)
        assertEquals(WallpaperCanvas(800, 560, 0, 80, 800, 480), canvas)
    }

    @Test fun noRailAndMainAreaOnly() {
        assertEquals(WallpaperCanvas(800, 480, 0, 0, 800, 480),
            WallpaperGeometry.canvas(CarScreen(800, 480, RailSide.NONE, 72), whole))
        assertEquals(WallpaperCanvas(728, 480, 0, 0, 728, 480),
            WallpaperGeometry.canvas(CarScreen(800, 480, RailSide.LEFT, 72), WallpaperCoverage.MAIN_AREA))
    }

    @Test fun canvasFillsTheMainAreaAtOneToOne() {
        // The canvas must be exactly main-area sized on one axis, or Android Auto would rescale it.
        for (screen in listOf(CarScreen(800, 480, RailSide.LEFT, 72), CarScreen(1280, 720, RailSide.RIGHT, 80),
            CarScreen(800, 480, RailSide.BOTTOM, 80), CarScreen(1920, 720, RailSide.NONE, 0))) {
            val canvas = WallpaperGeometry.canvas(screen, whole)
            assertEquals(1f, WallpaperGeometry.coverScale(canvas.width, canvas.height, screen.mainWidth, screen.mainHeight), 0.0001f)
        }
    }

    @Test fun separatorSitsOnTheMainAreaEdgeNextToTheRail() {
        val left = CarScreen(800, 480, RailSide.LEFT, 72)
        assertEquals(FrameRect(72, 0, 76, 480), WallpaperGeometry.separator(left, whole, 4))
        assertEquals(FrameRect(0, 0, 4, 480), WallpaperGeometry.separator(left, WallpaperCoverage.MAIN_AREA, 4))
        assertEquals(FrameRect(724, 0, 728, 480),
            WallpaperGeometry.separator(CarScreen(800, 480, RailSide.RIGHT, 72), whole, 4))
        assertEquals(FrameRect(0, 396, 800, 400),
            WallpaperGeometry.separator(CarScreen(800, 480, RailSide.BOTTOM, 80), whole, 4))
        assertEquals(null, WallpaperGeometry.separator(left, whole, 0))
        assertEquals(null, WallpaperGeometry.separator(CarScreen(800, 480, RailSide.NONE, 72), whole, 4))
    }

    @Test fun coloursParseWithOrWithoutAlpha() {
        assertEquals(0xFFFFFFFF.toInt(), WallpaperGeometry.parseColor("#FFFFFF"))
        assertEquals(0xFF112233.toInt(), WallpaperGeometry.parseColor("112233"))
        assertEquals(0x80000000.toInt(), WallpaperGeometry.parseColor("#80000000"))
        assertEquals(null, WallpaperGeometry.parseColor("#12345"))
        assertEquals(null, WallpaperGeometry.parseColor("white"))
    }

    @Test fun scalesAndRotation() {
        assertEquals(0.5f, WallpaperGeometry.coverScale(1600, 1600, 800, 480), 0.0001f)
        assertEquals(0.3f, WallpaperGeometry.fitScale(1600, 1600, 800, 480), 0.0001f)
        assertEquals(480 to 800, WallpaperGeometry.rotatedSize(800, 480, 1))
        assertEquals(800 to 480, WallpaperGeometry.rotatedSize(800, 480, 2))
        assertEquals(2048f / 872f, WallpaperGeometry.outputScale(WallpaperCanvas(872, 480, 0, 0, 800, 480), 2048), 0.0001f)
    }

    @Test fun railRectIsTheStripBesideTheMainAreaInCanvasPixels() {
        val screen = CarScreen(1200, 723, RailSide.LEFT, 107)
        val rect = WallpaperGeometry.railRect(screen, WallpaperCoverage.WHOLE_SCREEN)!!
        val spec = WallpaperGeometry.canvas(screen, WallpaperCoverage.WHOLE_SCREEN)
        assertEquals(spec.frameLeft, rect.left)
        assertEquals(spec.frameLeft + 107, rect.right)
        assertEquals(723, rect.bottom - rect.top)
        assertEquals(null, WallpaperGeometry.railRect(screen, WallpaperCoverage.MAIN_AREA))
        assertEquals(null, WallpaperGeometry.railRect(CarScreen(1200, 723, RailSide.NONE, 0), WallpaperCoverage.WHOLE_SCREEN))
        val right = WallpaperGeometry.railRect(CarScreen(1200, 723, RailSide.RIGHT, 107), WallpaperCoverage.WHOLE_SCREEN)!!
        assertEquals(107, right.right - right.left)
    }
}
