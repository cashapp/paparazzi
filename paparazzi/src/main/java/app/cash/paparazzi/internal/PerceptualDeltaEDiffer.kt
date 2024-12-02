package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.Differ.DiffResult
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.lang.Math.toRadians
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// DeltaE calculation based on this
// http://zschuessler.github.io/DeltaE/learn/#toc-defining-delta-e
public class PerceptualDeltaEDiffer : Differ {

  // Utility function to convert sRGB to linear RGB
  private fun srgbToLinearRGB(value: Double): Double {
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
  }

  private val Int.red
    get() = this shr 16 and 0xFF
  private val Int.blue
    get() = this shr 0 and 0xFF
  private val Int.green
    get() = this shr 8 and 0xFF
  private val Int.alpha
    get() = this shr 16 and 0xFF

  // Convert sRGB to XYZ color space
  private fun srgbToXyz(sRGB: Int): DoubleArray {
    val r = srgbToLinearRGB(sRGB.red / 255.0)
    val g = srgbToLinearRGB(sRGB.blue / 255.0)
    val b = srgbToLinearRGB(sRGB.green / 255.0)

    val X = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
    val Y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val Z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b

    return doubleArrayOf(X, Y, Z)
  }

  // Convert XYZ to CIELAB color space
  private fun xyzToLab(xyz: DoubleArray): DoubleArray {
    val xn = 0.95047 // Reference white D65
    val yn = 1.00000
    val zn = 1.08883

    fun f(t: Double): Double {
      return if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)
    }

    val fX = f(xyz[0] / xn)
    val fY = f(xyz[1] / yn)
    val fZ = f(xyz[2] / zn)

    val l = (116 * fY) - 16
    val a = 500 * (fX - fY)
    val b = 200 * (fY - fZ)

    return doubleArrayOf(l, a, b)
  }

  // Calculate ΔE (Delta E 76) between two CIELAB colors
  // private fun deltaE76(lab1: DoubleArray, lab2: DoubleArray): Double {
  //   val dL = lab1[0] - lab2[0]
  //   val da = lab1[1] - lab2[1]
  //   val db = lab1[2] - lab2[2]
  //
  //   return sqrt(dL * dL + da * da + db * db)
  // }

  // Calculate ΔE (Delta E 94) between two CIELAB colors
  // private fun deltaE94(
  //   lab1: DoubleArray,
  //   lab2: DoubleArray,
  //   lightnessAmount: Int = 1,
  //   chromaAmount: Int = 1,
  //   hueAmount: Int = 1
  // ): Double {
  //   // Extract LAB values
  //   val (L1, a1, b1) = lab1
  //   val (L2, a2, b2) = lab2
  //
  //   // Step 1: Calculate differences
  //   val deltaL = L2 - L1
  //   val C1 = chroma(a1, b1)
  //   val C2 = chroma(a2, b2)
  //   val deltaC = C2 - C1
  //
  //   val deltaA = a2 - a1
  //   val deltaB = b2 - b1
  //   val deltaH = sqrt(deltaA * deltaA + deltaB * deltaB - deltaC * deltaC)
  //
  //   // Step 2: Scaling factors
  //   val SL = 1.0
  //   val SC = 1.0 + 0.045 * C1
  //   val SH = 1.0 + 0.015 * C1
  //
  //   // Step 3: Compute ΔE94
  //   val deltaE = sqrt(
  //     (deltaL / (lightnessAmount * SL)).pow(2) +
  //       (deltaC / (chromaAmount * SC)).pow(2) +
  //       (deltaH / (hueAmount * SH)).pow(2)
  //   )
  //
  //   return deltaE
  // }

  // Helper function to calculate chroma
  private fun chroma(a: Double, b: Double): Double = sqrt(a * a + b * b)

  // Convert hue angle to degrees
  private fun hueAngle(a: Double, b: Double): Double {
    if (a == 0.0 && b == 0.0) return 0.0
    val angle = atan2(b, a).let { Math.toDegrees(it) }
    return if (angle < 0) angle + 360 else angle
  }

  // Calculate ΔE (Delta E 2000) between two CIELAB colors
  private fun deltaE2000(lab1: DoubleArray, lab2: DoubleArray): Double {
    // Extract LAB values
    val (L1, a1, b1) = lab1
    val (L2, a2, b2) = lab2

    // Step 1: Calculate chroma and mean chroma
    val C1 = chroma(a1, b1)
    val C2 = chroma(a2, b2)
    val CBar = (C1 + C2) / 2.0

    // Step 2: Calculate hue angle and mean hue
    val h1 = hueAngle(a1, b1)
    val h2 = hueAngle(a2, b2)
    val hBar = if (abs(h1 - h2) > 180) (h1 + h2 + 360) / 2.0 else (h1 + h2) / 2.0

    // Step 3: Calculate ΔL, ΔC, and ΔH
    val deltaL = L2 - L1
    val deltaC = C2 - C1
    val deltaH = if (C1 * C2 == 0.0) 0.0 else {
      val deltaHPrime = sqrt((a2 - a1).pow(2) + (b2 - b1).pow(2) - deltaC.pow(2))
      2 * sqrt(C1 * C2) * sin(toRadians(deltaHPrime / 2.0))
    }

    // Step 4: Weighting functions
    val LBar = (L1 + L2) / 2.0
    val SL = 1 + (0.015 * (LBar - 50).pow(2)) / sqrt(20 + (LBar - 50).pow(2))
    val SC = 1 + 0.045 * CBar
    val T = 1 - 0.17 * cos(toRadians(hBar - 30)) +
      0.24 * cos(toRadians(2 * hBar)) +
      0.32 * cos(toRadians(3 * hBar + 6)) -
      0.20 * cos(toRadians(4 * hBar - 63))
    val SH = 1 + 0.015 * CBar * T

    // Step 5: Rotation term
    val deltaTheta = 30.0 * exp(-((hBar - 275) / 25).pow(2))
    val RC = 2 * sqrt(CBar.pow(7) / (CBar.pow(7) + 25.0.pow(7)))
    val RT = -sin(toRadians(2 * deltaTheta)) * RC

    // Step 6: ΔE2000 calculation
    val deltaE = sqrt(
      (deltaL / SL).pow(2) +
        (deltaC / SC).pow(2) +
        (deltaH / SH).pow(2) +
        RT * (deltaC / SC) * (deltaH / SH)
    )
    return deltaE
  }

  // Main function to calculate ΔE between two sRGB colors
  private fun calculateDeltaE(expectedColor: Int, actualColor: Int): Double {
    val xyz1 = srgbToXyz(expectedColor)
    val xyz2 = srgbToXyz(actualColor)

    val lab1 = xyzToLab(xyz1)
    val lab2 = xyzToLab(xyz2)

    val de00 = deltaE2000(lab1, lab2)

    return de00
  }

  override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
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

        val deltaE = calculateDeltaE(expectedRgb, actualRgb)
        if (expectedRgb == actualRgb || deltaE < 1.0) {
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
    val percentDifference = (delta * 100 / total.toDouble()).toFloat()

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
