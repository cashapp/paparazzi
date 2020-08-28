/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import org.junit.Assert.assertEquals
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Utilities related to image processing.
 */
internal object ImageUtils {
    /**
     * Normally, this test will fail when there is a missing thumbnail. However, when
     * you create creating a new test, it's useful to be able to turn this off such that
     * you can generate all the missing thumbnails in one go, rather than having to run
     * the test repeatedly to get to each new render assertion generating its thumbnail.
     */
    private const val FAIL_ON_MISSING_THUMBNAIL = false

    private const val MAX_PERCENT_DIFFERENCE = 0.1

//  fun requireSimilar(
//          goldenImagePath: String,
//          image: BufferedImage,
//          diffImagePath: File
//  ) {
//    File(goldenImagePath).let {
//      if (it.isFile) {
//        it.inputStream().use {
//          assertImageSimilar(
//                  ImageIO.read(it),
//                  image,
//                  MAX_PERCENT_DIFFERENCE,
//                  diffImagePath
//          )
//        }
//      } else {
//        val message = "Unable to load golden image: $goldenImagePath."
//        if (FAIL_ON_MISSING_THUMBNAIL) {
//          fail(message)
//        } else {
//          ImageIO.write(image, "PNG", diffImagePath)
//          Assume.assumeTrue("${message}. Generated image was stored at ${diffImagePath.absolutePath}.", false)
//        }
//      }
//    }
//  }

    fun areImagesSimilar(
            goldenImage: BufferedImage,
            image: BufferedImage,
            maxPercentDifferent: Double,
            outputDiffImagePath: File
    ): Boolean {
        assertEquals("Golden-image and generated image widths are not the same!", goldenImage.width, image.width)
        assertEquals("Golden-image and generated image heights are not the same!", goldenImage.height, image.height)

        // Blur the images to account for the scenarios where there are pixel
        // differences
        // in where a sharp edge occurs
        // goldenImage = blur(goldenImage, 6);
        // image = blur(image, 6);

        val deltaImage = BufferedImage(goldenImage.width, goldenImage.height, TYPE_INT_ARGB)

        // Compute delta map
        var delta: Long = 0
        for (y in 0 until goldenImage.height) {
            for (x in 0 until goldenImage.width) {
                val goldenRgb = Color(goldenImage.getRGB(x, y))
                val generatedRgb = Color(image.getRGB(x, y))
                if (goldenRgb.rgb == generatedRgb.rgb) {
                    deltaImage.setRGB(x, y, 0x00808080)
                } else if (goldenRgb.alpha == 0 && generatedRgb.alpha == 0) {
                    // If the pixels have no opacity, don't delta colors at all
                    deltaImage.setRGB(x, y, 0x00808080)
                } else {
                    deltaImage.setRGB(x, y, Color(
                            mean(goldenRgb.red, generatedRgb.red),
                            mean(goldenRgb.green, generatedRgb.green),
                            mean(goldenRgb.blue, generatedRgb.blue),
                            mean(goldenRgb.alpha, generatedRgb.alpha)).rgb)

                    delta += abs(goldenRgb.red - generatedRgb.red).toLong()
                    delta += abs(goldenRgb.green - generatedRgb.green).toLong()
                    delta += abs(goldenRgb.blue - generatedRgb.blue).toLong()
                    delta += abs(goldenRgb.alpha - generatedRgb.alpha).toLong()
                }
            }
        }
        ImageIO.write(deltaImage, "PNG", outputDiffImagePath)

        // 3 different colors + alpha, 256 color levels
        val total = goldenImage.height.toLong() * goldenImage.width.toLong() * 4L * 256L
        val percentDifference = (delta * 100 / total.toDouble()).toFloat()


        return percentDifference < maxPercentDifferent
    }

//  /**
//   * Resize the given image
//   *
//   * @param source the image to be scaled
//   * @param xScale x scale
//   * @param yScale y scale
//   * @return the scaled image
//   */
//  fun scale(
//    source: BufferedImage,
//    xScale: Double,
//    yScale: Double
//  ): BufferedImage {
//    var source = source
//
//    var sourceWidth = source.width
//    var sourceHeight = source.height
//    val destWidth = max(1, (xScale * sourceWidth).toInt())
//    val destHeight = max(1, (yScale * sourceHeight).toInt())
//    var imageType = source.type
//    if (imageType == BufferedImage.TYPE_CUSTOM) {
//      imageType = BufferedImage.TYPE_INT_ARGB
//    }
//    if (xScale > 0.5 && yScale > 0.5) {
//      val scaled = BufferedImage(destWidth, destHeight, imageType)
//      val g2 = scaled.createGraphics()
//      g2.composite = AlphaComposite.Src
//      g2.color = Color(0, true)
//      g2.fillRect(0, 0, destWidth, destHeight)
//      if (xScale == 1.0 && yScale == 1.0) {
//        g2.drawImage(source, 0, 0, null)
//      } else {
//        setRenderingHints(g2)
//        g2.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight, null)
//      }
//      g2.dispose()
//      return scaled
//    } else {
//      // When creating a thumbnail, using the above code doesn't work very well;
//      // you get some visible artifacts, especially for text. Instead use the
//      // technique of repeatedly scaling the image into half; this will cause
//      // proper averaging of neighboring pixels, and will typically (for the kinds
//      // of screen sizes used by this utility method in the layout editor) take
//      // about 3-4 iterations to get the result since we are logarithmically reducing
//      // the size. Besides, each successive pass in operating on much fewer pixels
//      // (a reduction of 4 in each pass).
//      //
//      // However, we may not be resizing to a size that can be reached exactly by
//      // successively diving in half. Therefore, once we're within a factor of 2 of
//      // the final size, we can do a resize to the exact target size.
//      // However, we can get even better results if we perform this final resize
//      // up front. Let's say we're going from width 1000 to a destination width of 85.
//      // The first approach would cause a resize from 1000 to 500 to 250 to 125, and
//      // then a resize from 125 to 85. That last resize can distort/blur a lot.
//      // Instead, we can start with the destination width, 85, and double it
//      // successfully until we're close to the initial size: 85, then 170,
//      // then 340, and finally 680. (The next one, 1360, is larger than 1000).
//      // So, now we *start* the thumbnail operation by resizing from width 1000 to
//      // width 680, which will preserve a lot of visual details such as text.
//      // Then we can successively resize the image in half, 680 to 340 to 170 to 85.
//      // We end up with the expected final size, but we've been doing an exact
//      // divide-in-half resizing operation at the end so there is less distortion.
//
//      var iterations = 0 // Number of halving operations to perform after the initial resize
//      var nearestWidth = destWidth // Width closest to source width that = 2^x, x is integer
//      var nearestHeight = destHeight
//      while (nearestWidth < sourceWidth / 2) {
//        nearestWidth *= 2
//        nearestHeight *= 2
//        iterations++
//      }
//
//      var scaled = BufferedImage(nearestWidth, nearestHeight, imageType)
//
//      var g2 = scaled.createGraphics()
//      setRenderingHints(g2)
//      g2.drawImage(source, 0, 0, nearestWidth, nearestHeight, 0, 0, sourceWidth, sourceHeight, null)
//      g2.dispose()
//
//      sourceWidth = nearestWidth
//      sourceHeight = nearestHeight
//      source = scaled
//
//      for (iteration in iterations - 1 downTo 0) {
//        val halfWidth = sourceWidth / 2
//        val halfHeight = sourceHeight / 2
//        scaled = BufferedImage(halfWidth, halfHeight, imageType)
//        g2 = scaled.createGraphics()
//        setRenderingHints(g2)
//        g2.drawImage(source, 0, 0, halfWidth, halfHeight, 0, 0, sourceWidth, sourceHeight, null)
//        g2.dispose()
//
//        sourceWidth = halfWidth
//        sourceHeight = halfHeight
//        source = scaled
//        iterations--
//      }
//      return scaled
//    }
//  }

//  private fun setRenderingHints(g2: Graphics2D) {
//    g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR)
//    g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY)
//    g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
//  }
}

private fun mean(a: Int, b: Int) = (a + b) / 2