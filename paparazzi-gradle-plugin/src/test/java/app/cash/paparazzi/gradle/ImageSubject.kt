package app.cash.paparazzi.gradle

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.fail
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.annotation.CheckReturnValue
import javax.imageio.ImageIO
import kotlin.math.abs

internal class ImageSubject private constructor(
  metadata: FailureMetadata,
  private val actual: File?
) : Subject(metadata, actual) {
  fun exists() {
    assertThat(actual).isNotNull()
    require(actual is File) // smart cast

    assertWithMessage("File $actual does not exist").that(actual.exists())
      .isTrue()
  }

  @CheckReturnValue
  fun isSimilarTo(expected: File?): ImageAssert {
    assertThat(actual).exists()
    assertThat(expected).exists()

    return ImageAssert(ImageIO.read(actual), ImageIO.read(expected))
  }

  class ImageAssert(
    private val img1: BufferedImage,
    private val img2: BufferedImage
  ) {
    fun withDefaultThreshold() {
      withThreshold(DEFAULT_PERCENT_DIFFERENCE_THRESHOLD)
    }

    fun withThreshold(threshold: Double) {
      assertWithMessage("Threshold ($threshold) is less than 0.0")
        .that(threshold)
        .isAtLeast(0.0)
      assertWithMessage("Threshold ($threshold) is greater than 1.0")
        .that(threshold)
        .isAtMost(1.0)

      // Based on https://rosettacode.org/wiki/Percentage_difference_between_images#Kotlin
      val percentDiff = getDifferencePercent(img1, img2)
      if (percentDiff > threshold) {
        fail("Expected % diff less than $threshold, but was: $percentDiff")
      }
    }

    private fun getDifferencePercent(
      img1: BufferedImage,
      img2: BufferedImage
    ): Double {
      val width = img1.width
      val height = img1.height
      val width2 = img2.width
      val height2 = img2.height
      if (width != width2 || height != height2) {
        val f = "(%d,%d) vs. (%d,%d)".format(width, height, width2, height2)
        throw IllegalArgumentException("Images must have the same dimensions: $f")
      }
      var diff = 0L
      val highlights = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
      var numSimilarPixels = 0
      var numDifferentPixels = 0
      for (y in 0 until height) {
        for (x in 0 until width) {
          val aPixel = img1.getRGB(x, y)
          val bPixel = img2.getRGB(x, y)

          val deltaR = (aPixel and 0xFF0000).ushr(16) - (bPixel and 0xFF0000).ushr(16)
          val deltaG = (aPixel and 0x00FF00).ushr(8) - (bPixel and 0x00FF00).ushr(8)
          val deltaB = (aPixel and 0x0000FF) - (bPixel and 0x0000FF)
          if (deltaR != 0 || deltaG != 0 || deltaB != 0) {
            println("JROD: $x, $y = $deltaR, $deltaG, $deltaB")
          }

          // Compare full ARGB pixels
          if (aPixel == bPixel) {
            highlights.setRGB(x, y, -1)
          } else if (abs(deltaR) < 2 && abs(deltaG) < 2 && abs(deltaB) < 2) {
            numSimilarPixels++
            highlights.setRGB(x, y, -16776961)
          } else {
            numDifferentPixels++
            highlights.setRGB(x, y, -65536)
          }
          diff += pixelDiff(img1.getRGB(x, y), img2.getRGB(x, y))
        }
      }

      if (numDifferentPixels > 0) {
        println("JROD: different?, numDiff: $numDifferentPixels, numSimilar: $numSimilarPixels")
      } else if (numSimilarPixels > 0) {
        val highlightsFile = File("highlights-${UUID.randomUUID()}.png")
        println("JROD: similar? ${highlightsFile.absolutePath}, numDiff: $numDifferentPixels, numSimilar: $numSimilarPixels")
        if (highlightsFile.exists()) {
          highlightsFile.delete()
        }
        ImageIO.write(highlights, "PNG", highlightsFile)
      }

      val maxDiff = 3L * 255 * width * height
      return 100.0 * diff / maxDiff
    }

    private fun pixelDiff(rgb1: Int, rgb2: Int): Int {
      val r1 = (rgb1 shr 16) and 0xff
      val g1 = (rgb1 shr 8) and 0xff
      val b1 = rgb1 and 0xff
      val r2 = (rgb2 shr 16) and 0xff
      val g2 = (rgb2 shr 8) and 0xff
      val b2 = rgb2 and 0xff
      val dr = abs(r1 - r2)
      val dg = abs(g1 - g2)
      val db = abs(b1 - b2)
      return if (dr == 0 && dg == 0 && db == 0) {
        0
      } else if (dr < 2 && dg < 2 && db < 2) {
        0
      } else {
        dr + dg + db
      }
    }

    companion object {
      const val DEFAULT_PERCENT_DIFFERENCE_THRESHOLD = 0.00
    }
  }

  companion object {
    private val IMAGE_SUBJECT_FACTORY = Factory<ImageSubject, File> { metadata, actual ->
      ImageSubject(metadata, actual)
    }

    fun assertThat(actual: File?): ImageSubject {
      return assertAbout(IMAGE_SUBJECT_FACTORY).that(actual)
    }
  }
}
