package app.cash.paparazzi

import android.graphics.Color.BLUE
import android.graphics.Color.RED
import android.graphics.Color.WHITE
import java.awt.image.BufferedImage
import kotlin.math.abs

internal interface ImageDiffer {
  fun compare(a: BufferedImage, b: BufferedImage): DiffResult

  sealed interface DiffResult {
    data object Identical : DiffResult

    data class Similar(
      val highlights: BufferedImage,
      val numSimilarPixels: Int,
      val numTotalPixels: Int
    ) : DiffResult

    data class Different(
      val width: Int,
      val height: Int,
      val highlights: BufferedImage,
      val numDifferentPixels: Int,
      val numTotalPixels: Int
    ) : DiffResult
  }

  class PixelPerfect : ImageDiffer {
    override fun compare(a: BufferedImage, b: BufferedImage): DiffResult {
      TODO("Not yet implemented")
    }
  }

  class SSIMMatcher : ImageDiffer {
    override fun compare(a: BufferedImage, b: BufferedImage): DiffResult {
      TODO("Not yet implemented")
    }
  }

  object OffByOne : ImageDiffer {
    override fun compare(a: BufferedImage, b: BufferedImage): DiffResult {
      check(a.width == b.width && a.height == b.height) { "Images are different sizes" }

      val width = a.width
      val height = b.height
      val highlights = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      var numSimilarPixels = 0
      var numDifferentPixels = 0

      for (y in 0 until height) {
        for (x in 0 until width) {
          val aPixel = a.getRGB(x, y)
          val bPixel = b.getRGB(x, y)

          val deltaR = (aPixel and 0xFF0000).ushr(16) - (bPixel and 0xFF0000).ushr(16)
          val deltaG = (aPixel and 0x00FF00).ushr(8) - (bPixel and 0x00FF00).ushr(8)
          val deltaB = (aPixel and 0x0000FF) - (bPixel and 0x0000FF)
          if (deltaR != 0 || deltaG != 0 || deltaB != 0) {
            println("JROD: $x, $y = $deltaR, $deltaG, $deltaB")
          }

          // Compare full ARGB pixels, but allow other channels to differ if alpha is 0
          if (aPixel == bPixel || aPixel ushr 24 == 0 && bPixel ushr 24 == 0) {
            highlights.setRGB(x, y, WHITE)
          } else if (abs(deltaR) <= 2 && abs(deltaG) <= 2 && abs(deltaB) <= 2) {
            numSimilarPixels++
            highlights.setRGB(x, y, BLUE)
          } else {
            numDifferentPixels++
            highlights.setRGB(x, y, RED)
          }
        }
      }

      return if (numDifferentPixels > 0) {
        DiffResult.Different(width, height, highlights, numDifferentPixels, width * height)
      } else if (numSimilarPixels > 0) {
        DiffResult.Similar(highlights, numSimilarPixels, width * height)
      } else {
        DiffResult.Identical
      }
    }
  }
}
