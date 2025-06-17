package app.cash.paparazzi.internal.differs

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Simplified FLIP differ
internal object FlipDiffer : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
    require(expected.width == actual.width && expected.height == actual.height) {
      "Image dimensions must match"
    }

    val width = expected.width
    val height = expected.height
    val deltaImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    var totalError = 0.0
    var maxError = 0.0
    var differentPixels = 0L
    var similarPixels = 0L

    for (y in 0 until height) {
      for (x in 0 until width) {
        val expectedColor = Color(expected.getRGB(x, y))
        val actualColor = Color(actual.getRGB(x, y))

        val flipError = computeFLIPError(expectedColor, actualColor)

        val grayLevel = (flipError * 255).coerceIn(0.0, 255.0).toInt()
        val errorColor = Color(grayLevel, grayLevel, grayLevel)
        deltaImage.setRGB(x, y, errorColor.rgb)

        totalError += flipError
        maxError = max(maxError, flipError)

        if (flipError > 0.01) { // perceptual threshold for noticing difference
          differentPixels++
        } else if (flipError > 0.001) {
          similarPixels++
        }
      }
    }

    val avgError = totalError / (width * height)

    return when {
      maxError <= 0.01 -> Differ.DiffResult.Identical(deltaImage)
      avgError <= 0.1 -> Differ.DiffResult.Similar(deltaImage, similarPixels)
      else -> Differ.DiffResult.Different(deltaImage, avgError.toFloat(), differentPixels)
    }
  }

  // Simplified FLIP computation — perceptual RGB delta with gamma correction
  private fun computeFLIPError(a: Color, b: Color): Double {
    val linearA = rgbToLinear(a)
    val linearB = rgbToLinear(b)

    // Euclidean distance in linear light
    val dr = linearA[0] - linearB[0]
    val dg = linearA[1] - linearB[1]
    val db = linearA[2] - linearB[2]

    // FLIP is much more sophisticated (cone responses, edge detection etc.)
    // Here we compute a simple perceptual color difference
    return sqrt(dr * dr + dg * dg + db * db)
  }

  private fun rgbToLinear(c: Color): DoubleArray {
    return doubleArrayOf(
      srgbToLinear(c.red / 255.0),
      srgbToLinear(c.green / 255.0),
      srgbToLinear(c.blue / 255.0)
    )
  }

  private fun srgbToLinear(c: Double): Double {
    return if (c <= 0.04045) {
      c / 12.92
    } else {
      ((c + 0.055) / 1.055).pow(2.4)
    }
  }
}
