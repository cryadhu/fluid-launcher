package com.example.launcher.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

/** Work is done once per viewport/theme change on Dispatchers.Default, never per card/frame. */
internal fun renderFrost(scene: GlassScene): ImageBitmap {
    val width = 320
    val scale = width / scene.size.width.coerceAtLeast(1f)
    val height = (scene.size.height * scale).roundToInt().coerceIn(1, 1600)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
        scale(scale, pivot = Offset.Zero) { wallpaper(scene) }
    }
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) { blurPass(pixels, width, height, 11) }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

/** Separable box blur with clamped edges; three passes approximate a Gaussian frost. */
internal fun blurPass(pixels: IntArray, width: Int, height: Int, radius: Int) {
    require(width > 0 && height > 0 && pixels.size == width * height && radius >= 0)
    if (radius == 0) return
    val temporary = IntArray(pixels.size)
    val window = radius * 2 + 1
    fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        fun index(line: Int, at: Int) = if (horizontal) line * width + at.coerceIn(0, length-1) else at.coerceIn(0, length-1) * width + line
        for (line in 0 until lines) {
            var red=0; var green=0; var blue=0
            fun add(at: Int, sign: Int) { val c=input[index(line,at)]; red+=((c ushr 16) and 255)*sign; green+=((c ushr 8) and 255)*sign; blue+=(c and 255)*sign }
            for (at in -radius..radius) add(at,1)
            for (at in 0 until length) {
                output[index(line,at)] = (255 shl 24) or ((red/window) shl 16) or ((green/window) shl 8) or (blue/window)
                add(at-radius,-1); add(at+radius+1,1)
            }
        }
    }
    pass(pixels,temporary,true)
    pass(temporary,pixels,false)
}
