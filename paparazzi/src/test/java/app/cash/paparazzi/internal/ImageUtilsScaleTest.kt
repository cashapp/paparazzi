/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.internal

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE
import java.awt.image.DataBufferInt
import kotlin.random.Random

/**
 * [ImageUtils.scale] computes thumbnails of plain `TYPE_INT_ARGB_PRE` images (what Layoutlib renders) with [BilinearScaler] and
 * everything else with Java2D. These tests feed the same pixels through both paths and require
 * identical output, so existing snapshots keep their exact pixels.
 */
class ImageUtilsScaleTest {
  @Test
  fun deviceSizedScreenshots() {
    // Portrait and landscape sizes whose thumbnails take the iterative (<= 0.5) path.
    val sizes = listOf(
      1080 to 2340, // PIXEL_5
      1080 to 2400, // PIXEL_6
      1440 to 3120, // PIXEL_6_PRO
      1344 to 2992, // PIXEL_8_PRO
      2560 to 1800, // PIXEL_C
      2560 to 1600, // NEXUS_10
      1600 to 2560,
      3000 to 3000,
      4000 to 9 // extreme aspect ratio
    )
    for ((width, height) in sizes) {
      assertMatchesJava2d(screenshotLike(width, height, opaque = true))
      assertMatchesJava2d(screenshotLike(width, height, opaque = false))
    }
  }

  @Test
  fun randomSizesAndPixels() {
    val random = Random(2026)
    repeat(300) {
      val width = 1 + random.nextInt(if (it < 150) 64 else 2600)
      val height = 1 + random.nextInt(if (it < 150) 64 else 2600)
      val image = BufferedImage(width, height, TYPE_INT_ARGB_PRE)
      val pixels = (image.raster.dataBuffer as DataBufferInt).data
      val alphaMode = it % 3
      for (i in pixels.indices) {
        val color = random.nextInt()
        pixels[i] = when (alphaMode) {
          0 -> color or (0xff shl 24)
          1 -> premultiplied(listOf(0, 0xff, random.nextInt(256)).random(random), color)
          // Not valid premultiplied data, but both paths must still agree.
          else -> color
        }
      }
      val scale = random.nextDouble(0.01, 0.5)
      assertMatchesJava2d(image, scale, scale)
    }
  }

  @Test
  fun thumbnailScales() {
    val random = Random(1000)
    for (maxDimension in listOf(2000, 2001, 2047, 2048, 2049, 2340, 3999, 4000, 4001)) {
      val image = BufferedImage(maxDimension, 1 + random.nextInt(maxDimension), TYPE_INT_ARGB_PRE)
      val pixels = (image.raster.dataBuffer as DataBufferInt).data
      for (i in pixels.indices) pixels[i] = random.nextInt()
      assertMatchesJava2d(image)
    }
  }

  @Test
  fun unsupportedImagesUseJava2d() {
    val image = screenshotLike(1080, 2340, opaque = false)
    assertThat(BilinearScaler.supports(image)).isTrue()
    assertThat(BilinearScaler.supports(withPaddedRaster(image))).isFalse()
    assertThat(BilinearScaler.supports(image.getSubimage(1, 1, 100, 100))).isFalse()
    assertThat(BilinearScaler.supports(BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB))).isFalse()
    assertThat(BilinearScaler.supports(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB))).isFalse()
    val coerced = BufferedImage(10, 10, TYPE_INT_ARGB_PRE).apply { coerceData(false) }
    assertThat(BilinearScaler.supports(coerced)).isFalse()
    val subclass = object : BufferedImage(10, 10, TYPE_INT_ARGB_PRE) {}
    assertThat(BilinearScaler.supports(subclass)).isFalse()
  }

  private fun assertMatchesJava2d(
    image: BufferedImage,
    xScale: Double = ImageUtils.getThumbnailScale(image),
    yScale: Double = xScale
  ) {
    assertThat(BilinearScaler.supports(image)).isTrue()
    val actual = ImageUtils.scale(image, xScale, yScale)
    // A padded raster is not supported by BilinearScaler, so this takes the Java2D path.
    val expected = ImageUtils.scale(withPaddedRaster(image), xScale, yScale)

    val description = "${image.width}x${image.height} scaled by $xScale x $yScale"
    assertWithMessage(description).that(actual.type).isEqualTo(expected.type)
    assertWithMessage(description).that(actual.width).isEqualTo(expected.width)
    assertWithMessage(description).that(actual.height).isEqualTo(expected.height)
    // Compare raw premultiplied samples, not getRGB(), which would hide differences.
    val actualPixels = actual.raster.getDataElements(0, 0, actual.width, actual.height, null) as IntArray
    val expectedPixels = expected.raster.getDataElements(0, 0, expected.width, expected.height, null) as IntArray
    val firstDifference = actualPixels.indices.firstOrNull { actualPixels[it] != expectedPixels[it] }
    assertWithMessage("$description: first differing pixel index").that(firstDifference).isNull()
  }

  private fun premultiplied(alpha: Int, color: Int): Int {
    fun channel(shift: Int) = ((color shr shift and 0xff) * alpha + 127) / 255
    return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
  }

  /** Same pixels and type, but stored in a wider raster, so [BilinearScaler.supports] is false. */
  private fun withPaddedRaster(image: BufferedImage): BufferedImage {
    val padded = BufferedImage(image.width + 1, image.height, image.type)
      .getSubimage(0, 0, image.width, image.height)
    padded.raster.setDataElements(0, 0, image.raster)
    assertThat(BilinearScaler.supports(padded)).isFalse()
    return padded
  }

  /** Text, edges, gradients and (optionally) translucency, similar to rendered UI. */
  private fun screenshotLike(width: Int, height: Int, opaque: Boolean): BufferedImage {
    val image = BufferedImage(width, height, TYPE_INT_ARGB_PRE)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    if (opaque) {
      g.color = Color.WHITE
      g.fillRect(0, 0, width, height)
    }
    val random = Random(width * 31 + height)
    repeat(60) {
      g.color =
        Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), if (opaque) 255 else random.nextInt(256))
      val x = random.nextInt(width)
      val y = random.nextInt(height)
      g.fillRoundRect(x, y, random.nextInt(width / 2 + 1), random.nextInt(height / 4 + 1), 24, 24)
      g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12 + random.nextInt(60))
      g.drawString("Paparazzi 0123456789", random.nextInt(width), random.nextInt(height))
    }
    g.dispose()
    return image
  }
}
