package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.exp
import kotlin.math.pow

internal object Mssim : Differ {
  override fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult {
    require(expected.width == actual.width && expected.height == actual.height)

    val width = expected.width
    val height = expected.height
    val windowSize = 11
    val sigma = 1.5
    val gaussianKernel = createGaussianKernel(windowSize, sigma)

    val expectedLuma = grayscale(expected)
    val actualLuma = grayscale(actual)

    var sumSSIM = 0.0
    var numWindows = 0

    val deltaImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y in 0 until height - windowSize) {
      for (x in 0 until width - windowSize) {
        val window1 = extractWindow(expectedLuma, x, y, windowSize)
        val window2 = extractWindow(actualLuma, x, y, windowSize)

        val ssim = computeSSIM(window1, window2, gaussianKernel)
        sumSSIM += ssim
        numWindows++

        // Optional: visualize error as inverted SSIM
        val visualIntensity = ((1.0 - ssim).coerceIn(0.0, 1.0) * 255).toInt()
        val gray = Color(visualIntensity, visualIntensity, visualIntensity).rgb
        deltaImage.setRGB(x + windowSize / 2, y + windowSize / 2, gray)
      }
    }

    val mssim = sumSSIM / numWindows
    val percentDifference = ((1.0 - mssim) * 100).toFloat()

    return when {
      percentDifference == 0f -> DiffResult.Identical(deltaImage)

      percentDifference < 5f ->
        DiffResult.Similar(deltaImage, ((1.0 - percentDifference / 100f) * width * height).toLong())

      else -> DiffResult.Different(deltaImage, percentDifference, (percentDifference / 100f * width * height).toLong())
    }
  }

  private fun grayscale(image: BufferedImage): Array<DoubleArray> {
    val w = image.width
    val h = image.height
    return Array(h) { y ->
      DoubleArray(w) { x ->
        val c = Color(image.getRGB(x, y))
        0.299 * c.red + 0.587 * c.green + 0.114 * c.blue // Luma formula
      }
    }
  }

  private fun extractWindow(img: Array<DoubleArray>, x: Int, y: Int, size: Int): Array<DoubleArray> {
    return Array(size) { dy -> DoubleArray(size) { dx -> img[y + dy][x + dx] } }
  }

  private fun createGaussianKernel(size: Int, sigma: Double): Array<DoubleArray> {
    val center = size / 2
    val kernel = Array(size) { DoubleArray(size) }
    var sum = 0.0
    for (i in 0 until size) {
      for (j in 0 until size) {
        val x = i - center
        val y = j - center
        val value = exp(-(x * x + y * y) / (2 * sigma * sigma))
        kernel[i][j] = value
        sum += value
      }
    }
    // Normalize
    for (i in 0 until size) {
      for (j in 0 until size) {
        kernel[i][j] /= sum
      }
    }
    return kernel
  }

  private fun computeSSIM(img1: Array<DoubleArray>, img2: Array<DoubleArray>, kernel: Array<DoubleArray>): Double {
    val size = kernel.size
    var mu1 = 0.0
    var mu2 = 0.0
    for (i in 0 until size) {
      for (j in 0 until size) {
        mu1 += img1[i][j] * kernel[i][j]
        mu2 += img2[i][j] * kernel[i][j]
      }
    }

    var sigma1Sq = 0.0
    var sigma2Sq = 0.0
    var sigma12 = 0.0
    for (i in 0 until size) {
      for (j in 0 until size) {
        val i1 = img1[i][j]
        val i2 = img2[i][j]
        sigma1Sq += kernel[i][j] * (i1 - mu1).pow(2)
        sigma2Sq += kernel[i][j] * (i2 - mu2).pow(2)
        sigma12 += kernel[i][j] * (i1 - mu1) * (i2 - mu2)
      }
    }

    val c1 = (0.01 * 255).pow(2)
    val c2 = (0.03 * 255).pow(2)

    val numerator = (2 * mu1 * mu2 + c1) * (2 * sigma12 + c2)
    val denominator = (mu1.pow(2) + mu2.pow(2) + c1) * (sigma1Sq + sigma2Sq + c2)

    return numerator / denominator
  }
}
