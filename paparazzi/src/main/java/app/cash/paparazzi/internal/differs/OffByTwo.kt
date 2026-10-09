package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import kotlin.math.abs
import kotlin.math.max

internal object OffByTwo : Differ {
  /** Checks the passing case without allocating or painting a diagnostic image. */
  fun isSimilar(expected: BufferedImage, actual: BufferedImage): Boolean {
    if (expected.width != actual.width || expected.height != actual.height) return false
    // Subclasses may override getRGB; let the full differ honor their per-pixel behavior.
    if (expected.javaClass != BufferedImage::class.java || actual.javaClass != BufferedImage::class.java) return false

    val width = expected.width
    val expectedRow = IntArray(width)
    val actualRow = IntArray(width)
    for (y in 0 until expected.height) {
      readArgbRow(expected, y, expectedRow)
      readArgbRow(actual, y, actualRow)
      if (expectedRow.contentEquals(actualRow)) continue
      for (x in 0 until width) {
        val expectedRgb = expectedRow[x]
        val actualRgb = actualRow[x]
        if (expectedRgb == actualRgb) continue
        if (expectedRgb ushr 24 == 0 && actualRgb ushr 24 == 0) continue
        if (abs((expectedRgb ushr 24) - (actualRgb ushr 24)) > 2 ||
          abs((expectedRgb shr 16 and 0xFF) - (actualRgb shr 16 and 0xFF)) > 2 ||
          abs((expectedRgb shr 8 and 0xFF) - (actualRgb shr 8 and 0xFF)) > 2 ||
          abs((expectedRgb and 0xFF) - (actualRgb and 0xFF)) > 2
        ) {
          return false
        }
      }
    }
    return true
  }

  internal fun readArgbRow(image: BufferedImage, y: Int, row: IntArray) {
    when (image.type) {
      TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE -> {
        image.raster.getDataElements(0, y, image.width, 1, row)
        // coerceData changes the alpha state without updating the image type.
        if (!image.isAlphaPremultiplied) return
        val components = unpremultipliedComponents
        for (x in row.indices) {
          val pixel = row[x]
          val alpha = pixel ushr 24
          if (alpha == 255) continue
          val offset = alpha shl 8
          row[x] = (alpha shl 24) or
            (components[offset or (pixel shr 16 and 0xFF)] shl 16) or
            (components[offset or (pixel shr 8 and 0xFF)] shl 8) or
            components[offset or (pixel and 0xFF)]
        }
      }
      else -> image.getRGB(0, y, image.width, 1, row, 0, image.width)
    }
  }

  // Use the JDK's conversion, including its rounding, once per possible alpha/component pair.
  // Layoutlib supplies premultiplied images; getRGB otherwise repeats this conversion per pixel.
  private val unpremultipliedComponents: IntArray by lazy {
    val model = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE).colorModel
    IntArray(256 * 256) { index ->
      model.getBlue((index shr 8 shl 24) or (index and 0xFF))
    }
  }

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

        val deltaA = (actualRgb and -0x1000000).ushr(24) - (expectedRgb and -0x1000000).ushr(24)
        val deltaR = (actualRgb and 0xFF0000).ushr(16) - (expectedRgb and 0xFF0000).ushr(16)
        val deltaG = (actualRgb and 0x00FF00).ushr(8) - (expectedRgb and 0x00FF00).ushr(8)
        val deltaB = (actualRgb and 0x0000FF) - (expectedRgb and 0x0000FF)

        val newR = 128 + deltaR and 0xFF
        val newG = 128 + deltaG and 0xFF
        val newB = 128 + deltaB and 0xFF
        val avgAlpha =
          ((expectedRgb and -0x1000000).ushr(24) + (actualRgb and -0x1000000).ushr(24)) / 2 shl 24
        val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB

        if (abs(deltaR) <= 2 && abs(deltaG) <= 2 && abs(deltaB) <= 2 && abs(deltaA) <= 2) {
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
    var percentDifference = (delta * 100 / total.toDouble()).toFloat()

    // If the delta diff is all black pixels, the computed difference is 0, but there are still
    // different pixels. We can fallback to the amount of different pixels to less precise difference to ensure difference is reported.
    if (differentPixels > 0 && percentDifference == 0f) {
      percentDifference = differentPixels * 100 / (actualWidth * actualHeight.toDouble()).toFloat()
    }

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
