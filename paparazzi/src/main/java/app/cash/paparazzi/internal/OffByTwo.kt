package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.Differ.DiffResult
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.abs
import kotlin.math.max

internal object OffByTwo : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    check(expected.width == actual.width && expected.height == actual.height) { "Images are different sizes" }

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
    var similarPixels: Long = 0
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

        val deltaR = (actualRgb and 0xFF0000).ushr(16) - (expectedRgb and 0xFF0000).ushr(16)
        val deltaG = (actualRgb and 0x00FF00).ushr(8) - (expectedRgb and 0x00FF00).ushr(8)
        val deltaB = (actualRgb and 0x0000FF) - (expectedRgb and 0x0000FF)

        val newR = 128 + deltaR and 0xFF
        val newG = 128 + deltaG and 0xFF
        val newB = 128 + deltaB and 0xFF
        val avgAlpha =
          ((expectedRgb and -0x1000000).ushr(24) + (actualRgb and -0x1000000).ushr(24)) / 2 shl 24
        val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB

        if (abs(deltaR) <= 2 && abs(deltaG) <= 2 && abs(deltaB) <= 2) {
          similarPixels++
          deltaImage.setRGB(expectedWidth + x, y, 0xFF0000FF.toInt())
          continue
        }

        differentPixels++
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
    val percentDifference = (delta * 100 / total.toDouble()).toFloat()

    return if (differentPixels > 0) {
      DiffResult.Different(
        delta = deltaImage,
        percentDifference = percentDifference,
        numDifferentPixels = differentPixels
      )
    } else if (similarPixels > 0) {
      DiffResult.Similar(
        delta = deltaImage,
        numSimilarPixels = similarPixels
      )
    } else {
      DiffResult.Identical(delta = deltaImage)
    }
  }
}
