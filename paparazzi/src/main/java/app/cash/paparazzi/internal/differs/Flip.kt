package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal object Flip : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    require(expected.width == actual.width && expected.height == actual.height)

    val w = expected.width
    val h = expected.height
    val deltaImage = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)

    // Viewing parameters – adjust to your test conditions
    val pixelsPerDegree = 60.0
    val maxFrequency = 30.0

    var weightedSum = 0.0
    var weightTotal = 0.0
    var diffCount = 0L

    for (y in 0 until h) {
      for (x in 0 until w) {
        val c1 = Color(expected.getRGB(x, y))
        val c2 = Color(actual.getRGB(x, y))
        val lin1 = srgbToLinear(c1)
        val lin2 = srgbToLinear(c2)

        val fc1 = toFloatArray(lin1)
        val fc2 = toFloatArray(lin2)
        val ycxcz1 = toYCxCz(fc1)
        val ycxcz2 = toYCxCz(fc2)

        // Contrast sensitivity filter
        val csfAttn = contrastSensitivity(ycxcz1, pixelsPerDegree, maxFrequency)

        val diffVec = doubleArrayOf(
          ycxcz1[0] - ycxcz2[0],
          ycxcz1[1] - ycxcz2[1],
          ycxcz1[2] - ycxcz2[2]
        )
        val diffMag = sqrt(diffVec.mapIndexed { i, dv -> dv * dv * csfAttn[i] }.sum())
        weightedSum += diffMag
        weightTotal += 1.0

        if (diffMag > 0.02) diffCount++

        val gray = (min(1.0, diffMag) * 255).toInt()
        deltaImage.setRGB(x, y, Color(gray, gray, gray).rgb)
      }
    }

    val avgError = (weightedSum / weightTotal).toFloat()
    val percentDiff = diffCount.toFloat() / (w * h) * 100f

    return when {
      avgError < 0.005f -> DiffResult.Identical(deltaImage)
      avgError < 0.02f -> DiffResult.Similar(deltaImage, (w * h - diffCount))
      else -> DiffResult.Different(deltaImage, percentDiff, diffCount)
    }
  }

  private data class LinearRGB(val r: Double, val g: Double, val b: Double)
  private fun srgbToLinear(c: Color) =
    LinearRGB(
      gammaExpand(c.red / 255.0),
      gammaExpand(c.green / 255.0),
      gammaExpand(c.blue / 255.0)
    )
  private fun gammaExpand(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

  private fun toFloatArray(rgb: LinearRGB) = doubleArrayOf(rgb.r, rgb.g, rgb.b)

  private fun toYCxCz(rgb: DoubleArray): DoubleArray {
    val y = 0.2627 * rgb[0] + 0.6780 * rgb[1] + 0.0593 * rgb[2]
    val cx = rgb[0] - rgb[1]
    val cz = rgb[2] - rgb[1]
    return doubleArrayOf(y, cx, cz)
  }

  /**
   * Simplified contrast-sensitivity from FLIP's CSF – attenuates mid/high-frequency noise more
   * per-channel; multiplied element-wise into per-channel diffs.
   */
  private fun contrastSensitivity(ycxcz: DoubleArray, ppd: Double, freqLimit: Double): DoubleArray {
    // Example approximation: reduce sensitivity for chromatic channels and high frequency
    val lumaAtt = 1.0
    val chromAtt = 0.5
    val freqFactor = exp(-freqLimit / ppd)
    return doubleArrayOf(lumaAtt * freqFactor, chromAtt * freqFactor, chromAtt * freqFactor)
  }
}
