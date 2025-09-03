package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal object DeltaE2000 : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    require(expected.width == actual.width && expected.height == actual.height)

    val width = expected.width
    val height = expected.height
    var totalDifference = 0.0
    var numDifferent = 0L
    var numSimilar = 0L

    val deltaImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val rgb1 = Color(expected.getRGB(x, y))
        val rgb2 = Color(actual.getRGB(x, y))

        val lab1 = rgbToLab(rgb1)
        val lab2 = rgbToLab(rgb2)

        val deltaE = deltaE2000(lab1, lab2)

        val isDifferent = deltaE > 2.3 // JND (just noticeable difference) threshold

        if (isDifferent) {
          numDifferent++
        } else {
          numSimilar++
        }

        // Encode delta visually as grayscale based on magnitude
        val intensity = (min(1.0, deltaE / 10.0) * 255).toInt()
        val gray = Color(intensity, intensity, intensity).rgb
        deltaImage.setRGB(x, y, gray)

        totalDifference += deltaE
      }
    }

    val totalPixels = width * height
    val percentDifference = numDifferent.toFloat() / totalPixels * 100f

    return when {
      percentDifference == 0f -> DiffResult.Identical(deltaImage)
      percentDifference < 5f -> DiffResult.Similar(deltaImage, numSimilar)
      else -> DiffResult.Different(deltaImage, percentDifference, numDifferent)
    }
  }

  private fun rgbToLab(color: Color): DoubleArray {
    val r = pivotRgb(color.red / 255.0)
    val g = pivotRgb(color.green / 255.0)
    val b = pivotRgb(color.blue / 255.0)

    // Convert to XYZ (sRGB D65)
    val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
    val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000
    val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883

    // Convert to Lab
    val fx = pivotXyz(x)
    val fy = pivotXyz(y)
    val fz = pivotXyz(z)

    val l = 116.0 * fy - 16.0
    val a = 500.0 * (fx - fy)
    val bVal = 200.0 * (fy - fz)

    return doubleArrayOf(l, a, bVal)
  }

  private fun pivotRgb(c: Double): Double {
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
  }

  private fun pivotXyz(t: Double): Double {
    return if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)
  }

  private fun deltaE2000(lab1: DoubleArray, lab2: DoubleArray): Double {
    val (l1, a1, b1) = lab1
    val (l2, a2, b2) = lab2

    val avgLp = (l1 + l2) / 2.0
    val c1 = sqrt(a1 * a1 + b1 * b1)
    val c2 = sqrt(a2 * a2 + b2 * b2)
    val avgC = (c1 + c2) / 2.0

    val g = 0.5 * (1 - sqrt((avgC.pow(7)) / (avgC.pow(7) + 25.0.pow(7))))
    val a1p = a1 * (1 + g)
    val a2p = a2 * (1 + g)
    val c1p = sqrt(a1p * a1p + b1 * b1)
    val c2p = sqrt(a2p * a2p + b2 * b2)
    val avgCp = (c1p + c2p) / 2.0

    val h1p = atan2(b1, a1p).let { if (it >= 0) it else it + 2 * PI }
    val h2p = atan2(b2, a2p).let { if (it >= 0) it else it + 2 * PI }

    val deltahp = when {
      abs(h1p - h2p) <= PI -> h2p - h1p
      h2p <= h1p -> h2p - h1p + 2 * PI
      else -> h2p - h1p - 2 * PI
    }

    val deltaLp = l2 - l1
    val deltaCp = c2p - c1p
    val deltaHp = 2 * sqrt(c1p * c2p) * sin(deltahp / 2)

    val avgHp = when {
      abs(h1p - h2p) > PI -> (h1p + h2p + 2 * PI) / 2.0
      else -> (h1p + h2p) / 2.0
    }

    val t = 1 - 0.17 * cos(avgHp - PI / 6) +
      0.24 * cos(2 * avgHp) +
      0.32 * cos(3 * avgHp + PI / 30) -
      0.20 * cos(4 * avgHp - 21 * PI / 60)

    val deltaTheta = 30 * PI / 180 * exp(-((avgHp * 180 / PI - 275) / 25).pow(2))
    val rc = 2 * sqrt((avgCp.pow(7)) / (avgCp.pow(7) + 25.0.pow(7)))
    val sl = 1 + ((0.015 * ((avgLp - 50).pow(2))) / sqrt(20 + ((avgLp - 50).pow(2))))
    val sc = 1 + 0.045 * avgCp
    val sh = 1 + 0.015 * avgCp * t
    val rt = -sin(2 * deltaTheta) * rc

    return sqrt(
      (deltaLp / sl).pow(2) +
        (deltaCp / sc).pow(2) +
        (deltaHp / sh).pow(2) +
        rt * (deltaCp / sc) * (deltaHp / sh)
    )
  }
}
