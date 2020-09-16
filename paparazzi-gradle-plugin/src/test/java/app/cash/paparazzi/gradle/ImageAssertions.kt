package app.cash.paparazzi.gradle

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.fail
import java.awt.image.BufferedImage
import java.io.File
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
      for (y in 0 until height) {
        for (x in 0 until width) {
          diff += pixelDiff(img1.getRGB(x, y), img2.getRGB(x, y))
        }
      }
      val maxDiff = 3L * 255 * width * height
      return 100.0 * diff / maxDiff
    }

    private fun pixelDiff(rgb1: Int, rgb2: Int): Int {
      val r1 = (rgb1 shr 16) and 0xff
      val g1 = (rgb1 shr 8)  and 0xff
      val b1 =  rgb1         and 0xff
      val r2 = (rgb2 shr 16) and 0xff
      val g2 = (rgb2 shr 8)  and 0xff
      val b2 =  rgb2         and 0xff
      return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
    }

    companion object {
      const val DEFAULT_PERCENT_DIFFERENCE_THRESHOLD = 0.01
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