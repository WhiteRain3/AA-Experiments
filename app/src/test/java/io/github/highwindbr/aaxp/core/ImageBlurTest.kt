package io.github.highwindbr.aaxp.core

import org.junit.Assert.*
import org.junit.Test

class ImageBlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun lum(p: Int) = p and 0xFF

    @Test fun radiusZeroReturnsInput() {
        val input = IntArray(12) { gray(it * 20) }
        assertArrayEquals(input, ImageBlur.blur(input, 4, 3, 0f))
    }

    @Test fun uniformImageStaysUniform() {
        val input = IntArray(100) { 0xFF336699.toInt() }
        assertTrue(ImageBlur.blur(input, 10, 10, 3f).all { it == 0xFF336699.toInt() })
    }

    @Test fun brightPixelSpreadsSymmetricallyAndKeepsBrightness() {
        val size = 41
        val input = IntArray(size * size) { gray(0) }
        input[20 * size + 20] = gray(255)
        val out = ImageBlur.blur(input, size, size, 2f)
        val total = out.sumOf { lum(it) }
        assertTrue("total $total", total in 255 - 40..255 + 40)
        assertTrue(lum(out[20 * size + 20]) < 255)
        assertEquals(lum(out[20 * size + 18]), lum(out[20 * size + 22]))
        assertEquals(lum(out[18 * size + 20]), lum(out[22 * size + 20]))
        assertTrue(kotlin.math.abs(lum(out[20 * size + 19]) - lum(out[19 * size + 20])) <= 1)
    }

    @Test fun opaqueStaysOpaque() {
        val input = IntArray(64) { gray(it * 3) }
        assertTrue(ImageBlur.blur(input, 8, 8, 2f).all { (it ushr 24) == 0xFF })
    }

    @Test fun thinImagesDoNotCrash() {
        assertEquals(5, ImageBlur.blur(IntArray(5) { gray(it * 50) }, 1, 5, 3f).size)
        assertEquals(5, ImageBlur.blur(IntArray(5) { gray(it * 50) }, 5, 1, 3f).size)
        assertEquals(1, ImageBlur.blur(intArrayOf(gray(9)), 1, 1, 3f).size)
    }
}
