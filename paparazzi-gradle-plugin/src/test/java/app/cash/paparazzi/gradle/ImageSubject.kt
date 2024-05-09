package app.cash.paparazzi.gradle

import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert
import org.junit.Assert.fail
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
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
  fun isSimilarTo(expected: File?, name: String = expected?.path?.replace(File.pathSeparatorChar, '_') ?: "image"): ImageAssert {
    assertThat(actual).exists()
    assertThat(expected).exists()

    return ImageAssert(ImageIO.read(actual), ImageIO.read(expected), name)
  }

  class ImageAssert(
    private val img1: BufferedImage,
    private val img2: BufferedImage,
    private val name: String
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
      // val percentDiff = getDifferencePercent(img1, img2)
      val (deltaImage, percentDiff) = compareImages(img1, img2)

      if (percentDiff > threshold) {
        val output = File(failureDir, "delta-$name.png")
        output.mkdirs()

        if (output.exists()) {
          val deleted = output.delete()
          Assert.assertTrue(deleted)
        }
        ImageIO.write(deltaImage, "PNG", output)

        fail("Expected % diff less than $threshold, but was: $percentDiff")
      }
    }

    @Throws(IOException::class)
    private fun compareImages(
      goldenImage: BufferedImage,
      image: BufferedImage,
      withText: Boolean = true
    ): Pair<BufferedImage, Float> {
      var goldenImage = goldenImage
      if (goldenImage.type != BufferedImage.TYPE_INT_ARGB) {
        val temp = BufferedImage(
          goldenImage.width,
          goldenImage.height,
          BufferedImage.TYPE_INT_ARGB
        )
        temp.graphics.drawImage(goldenImage, 0, 0, null)
        goldenImage = temp
      }
      Assert.assertEquals(BufferedImage.TYPE_INT_ARGB.toLong(), goldenImage.type.toLong())

      val goldenImageWidth = goldenImage.width
      val goldenImageHeight = goldenImage.height

      val imageWidth = image.width
      val imageHeight = image.height

      val deltaWidth = Math.max(goldenImageWidth, imageWidth)
      val deltaHeight = Math.max(goldenImageHeight, imageHeight)

      // Blur the images to account for the scenarios where there are pixel
      // differences
      // in where a sharp edge occurs
      // goldenImage = blur(goldenImage, 6);
      // image = blur(image, 6);
      val width = goldenImageWidth + deltaWidth + imageWidth
      val deltaImage = BufferedImage(width, deltaHeight, BufferedImage.TYPE_INT_ARGB)
      val g = deltaImage.graphics

      // Compute delta map
      var delta: Long = 0
      for (y in 0 until deltaHeight) {
        for (x in 0 until deltaWidth) {
          val goldenRgb = if (x >= goldenImageWidth || y >= goldenImageHeight) {
            0x00808080
          } else {
            goldenImage.getRGB(x, y)
          }

          val rgb = if (x >= imageWidth || y >= imageHeight) {
            0x00808080
          } else {
            image.getRGB(x, y)
          }

          if (goldenRgb == rgb) {
            deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
            continue
          }

          // If the pixels have no opacity, don't delta colors at all
          if (goldenRgb and -0x1000000 == 0 && rgb and -0x1000000 == 0) {
            deltaImage.setRGB(goldenImageWidth + x, y, 0x00808080)
            continue
          }

          val deltaR = (rgb and 0xFF0000).ushr(16) - (goldenRgb and 0xFF0000).ushr(16)
          val newR = 128 + deltaR and 0xFF
          val deltaG = (rgb and 0x00FF00).ushr(8) - (goldenRgb and 0x00FF00).ushr(8)
          val newG = 128 + deltaG and 0xFF
          val deltaB = (rgb and 0x0000FF) - (goldenRgb and 0x0000FF)
          val newB = 128 + deltaB and 0xFF

          val avgAlpha =
            ((goldenRgb and -0x1000000).ushr(24) + (rgb and -0x1000000).ushr(24)) / 2 shl 24

          val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB
          deltaImage.setRGB(goldenImageWidth + x, y, newRGB)

          delta += Math.abs(deltaR)
            .toLong()
          delta += Math.abs(deltaG)
            .toLong()
          delta += Math.abs(deltaB)
            .toLong()
        }
      }

      // Expected on the left
      // Golden on the right
      g.drawImage(goldenImage, 0, 0, null)
      g.drawImage(image, goldenImageWidth + deltaWidth, 0, null)

      // Labels
      if (withText && deltaWidth > 80) {
        g.color = Color.RED
        g.drawString("Expected", 10, 20)
        g.drawString("Actual", goldenImageWidth + deltaWidth + 10, 20)
      }

      g.dispose()

      // 3 different colors, 256 color levels
      val total = imageHeight.toLong() * imageWidth.toLong() * 3L * 256L
      val percentDifference = (delta * 100 / total.toDouble()).toFloat()

      return deltaImage to percentDifference
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
      val g1 = (rgb1 shr 8) and 0xff
      val b1 = rgb1 and 0xff
      val r2 = (rgb2 shr 16) and 0xff
      val g2 = (rgb2 shr 8) and 0xff
      val b2 = rgb2 and 0xff
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

    private val failureDir: File
      get() {
        val buildDirString = System.getProperty("paparazzi.build.dir")
        val failureDir = File(buildDirString, "paparazzi/failures")
        failureDir.mkdirs()
        return failureDir
      }

    fun assertThat(actual: File?): ImageSubject {
      return assertAbout(IMAGE_SUBJECT_FACTORY).that(actual)
    }
  }
}
