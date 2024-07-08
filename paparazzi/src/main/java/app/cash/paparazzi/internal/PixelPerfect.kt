package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.Differ.DiffResult
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.abs
import kotlin.math.max

internal object PixelPerfect : Differ {
  override fun compare(a: BufferedImage, b: BufferedImage): DiffResult {
    val goldenImageWidth = a.width
    val goldenImageHeight = a.height

    val imageWidth = b.width
    val imageHeight = b.height

    val deltaWidth = max(goldenImageWidth, imageWidth)
    val deltaHeight = max(goldenImageHeight, imageHeight)

    val width = goldenImageWidth + deltaWidth + imageWidth
    val deltaImage = BufferedImage(width, deltaHeight, TYPE_INT_ARGB)
    val g = deltaImage.graphics

    // Compute delta map
    var delta: Long = 0
    var differentPixels: Long = 0
    for (y in 0 until deltaHeight) {
      for (x in 0 until deltaWidth) {
        val goldenRgb = if (x >= goldenImageWidth || y >= goldenImageHeight) {
          0x00808080
        } else {
          a.getRGB(x, y)
        }

        val rgb = if (x >= imageWidth || y >= imageHeight) {
          0x00808080
        } else {
          b.getRGB(x, y)
        }

        if (goldenRgb == rgb) {
          deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
          continue
        }

        // If the pixels have no opacity, don't delta colors at all
        if (goldenRgb and -0x1000000 == 0 && rgb and -0x1000000 == 0) {
          deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
          continue
        }

        differentPixels++

        val deltaR = (rgb and 0xFF0000).ushr(16) - (goldenRgb and 0xFF0000).ushr(16)
        val newR = 128 + deltaR and 0xFF
        val deltaG = (rgb and 0x00FF00).ushr(8) - (goldenRgb and 0x00FF00).ushr(8)
        val newG = 128 + deltaG and 0xFF
        val deltaB = (rgb and 0x0000FF) - (goldenRgb and 0x0000FF)
        val newB = 128 + deltaB and 0xFF

        val avgAlpha =
          ((goldenRgb and -0x1000000).ushr(24) + (rgb and -0x1000000).ushr(24)) / 2 shl 24

        val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB
        deltaImage.setRGB(goldenImageWidth + x, y, newRGB)

        delta += abs(deltaR).toLong()
        delta += abs(deltaG).toLong()
        delta += abs(deltaB).toLong()
      }
    }

    // Expected on the left
    // Actual on the right
    g.drawImage(a, 0, 0, null)
    g.drawImage(b, goldenImageWidth + deltaWidth, 0, null)

    g.dispose()

    // 3 different colors, 256 color levels
    val total = imageHeight.toLong() * imageWidth.toLong() * 3L * 256L
    val percentDifference = (delta * 100 / total.toDouble()).toFloat()

    return if (percentDifference == 0f) {
      DiffResult.Identical(delta = deltaImage)
    } else {
      DiffResult.Different(
        delta = deltaImage,
        percentDifference = percentDifference
      )
    }
  }
}
