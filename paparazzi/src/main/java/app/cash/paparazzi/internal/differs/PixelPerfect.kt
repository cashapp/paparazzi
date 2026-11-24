package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.abs
import kotlin.math.max

internal object PixelPerfect : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    val expectedWidth = expected.width
    val expectedHeight = expected.height

    val actualWidth = actual.width
    val actualHeight = actual.height

    val maxWidth = max(expectedWidth, actualWidth)
    val maxHeight = max(expectedHeight, actualHeight)

    val deltaImage = BufferedImage(expectedWidth + maxWidth + actualWidth, maxHeight, TYPE_INT_ARGB)
    val g = deltaImage.graphics

    // Compute delta map
    var delta: Long = 0
    var differentPixels: Long = 0
    for (y in 0 until maxHeight) {
      for (x in 0 until maxWidth) {
        val expectedRgb = if (x >= expectedWidth || y >= expectedHeight) {
          0x00808080
        } else {
          expected.getRGB(x, y)
        }

        val actualRgb = if (x >= actualWidth || y >= actualHeight) {
          0x00808080
        } else {
          actual.getRGB(x, y)
        }

        if (expectedRgb == actualRgb) {
          deltaImage.setRGB(expectedWidth + x, y, 0x00808080)
          continue
        }

        // If the pixels have no opacity, don't delta colors at all
        if (expectedRgb and -0x1000000 == 0 && actualRgb and -0x1000000 == 0) {
          deltaImage.setRGB(expectedWidth + x, y, 0x00808080)
          continue
        }

        differentPixels++

        val deltaR = (actualRgb and 0xFF0000).ushr(16) - (expectedRgb and 0xFF0000).ushr(16)
        val newR = 128 + deltaR and 0xFF
        val deltaG = (actualRgb and 0x00FF00).ushr(8) - (expectedRgb and 0x00FF00).ushr(8)
        val newG = 128 + deltaG and 0xFF
        val deltaB = (actualRgb and 0x0000FF) - (expectedRgb and 0x0000FF)
        val newB = 128 + deltaB and 0xFF

        val avgAlpha =
          ((expectedRgb and -0x1000000).ushr(24) + (actualRgb and -0x1000000).ushr(24)) / 2 shl 24

        val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB
        deltaImage.setRGB(expectedWidth + x, y, newRGB)

        delta += abs(deltaR).toLong()
        delta += abs(deltaG).toLong()
        delta += abs(deltaB).toLong()
      }
    }

    // Expected on the left
    // Actual on the right
    g.drawImage(expected, 0, 0, null)
    g.drawImage(actual, expectedWidth + maxWidth, 0, null)

    g.dispose()

    // 3 different colors, 256 color levels
    val total = actualHeight.toLong() * actualWidth.toLong() * 3L * 256L
    var percentDifference = (delta * 100 / total.toDouble()).toFloat()

    // If the delta diff is all black pixels, the computed difference is 0, but there are still
    // different pixels. We can fallback to the amount of different pixels to less precise difference to ensure difference is reported.
    if (differentPixels > 0 && percentDifference == 0f) {
      percentDifference = differentPixels * 100 / (actualWidth * actualHeight.toDouble()).toFloat()
    }

    return if (percentDifference == 0f) {
      DiffResult.Identical(delta = deltaImage)
    } else {
      DiffResult.Different(
        delta = deltaImage,
        percentDifference = percentDifference,
        numDifferentPixels = differentPixels
      )
    }
  }
}
