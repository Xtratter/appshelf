package io.github.xtratter.appshelf

import android.graphics.Bitmap

/**
 * Плавное размытие картинки на процессоре: три прохода «скользящего среднего» по строкам и столбцам —
 * почти как гауссово, без зерна и ряби, которые даёт выборка отдельных точек в шейдере.
 */
object Blur {
    fun apply(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Одно скользящее среднее шириной 2r+1 (края — повтор крайнего пикселя). */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val start = if (horizontal) line * w else line
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = src[start + i.coerceIn(0, len - 1) * step]
                a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            for (i in 0 until len) {
                dst[start + i * step] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[start + (i - r).coerceIn(0, len - 1) * step]
                val inn = src[start + (i + r + 1).coerceIn(0, len - 1) * step]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
