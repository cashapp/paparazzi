package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult.Different
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE
import kotlin.random.Random

class OffByTwoTest {
  @Test
  fun `compare identical images`() {
    val expected = createImage(width = 1, height = 1)
    val actual = createImage(width = 1, height = 1)
    val result = OffByTwo.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare similar images`() {
    val expected = createImage(width = 1, height = 1, rgb = 0xFFFFFFFE)
    val actual = createImage(width = 1, height = 1)
    val result = OffByTwo.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Similar::class.java)
  }

  @Test
  fun `compare similar images using black actual and alpha expected`() {
    val expected = createImage(width = 1, height = 1, rgb = 0x00000000)
    val actual = createImage(width = 1, height = 1, rgb = 0xFF000000)
    val result = OffByTwo.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Different::class.java)
  }

  @Test
  fun `compare different images`() {
    val expected = createImage(width = 1, height = 1, rgb = 0x00000000)
    val actual = createImage(width = 1, height = 1)
    val result = OffByTwo.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Different::class.java)
  }

  @Test
  fun `premultiplied conversion exactly matches Java for every alpha and component`() {
    val image = BufferedImage(256, 256, TYPE_INT_ARGB_PRE)
    for (shift in listOf(0, 8, 16)) {
      for (alpha in 0..255) {
        val pixels = IntArray(256) { (alpha shl 24) or (it shl shift) }
        image.raster.setDataElements(0, alpha, 256, 1, pixels)
        val row = IntArray(256)
        OffByTwo.readArgbRow(image, alpha, row)
        assertThat(row).isEqualTo(image.getRGB(0, alpha, 256, 1, null, 0, 256))
      }
    }
  }

  @Test
  fun `row reading respects image formats and subimage strides`() {
    val random = Random(1)
    for (type in 1..13) {
      val parent = BufferedImage(19, 23, type)
      for (y in 0 until parent.height) {
        for (x in 0 until parent.width) parent.setRGB(x, y, random.nextInt())
      }
      for (image in listOf(parent, parent.getSubimage(3, 4, 7, 11))) {
        for (y in 0 until image.height) {
          val row = IntArray(image.width)
          OffByTwo.readArgbRow(image, y, row)
          assertThat(row).isEqualTo(image.getRGB(0, y, image.width, 1, null, 0, image.width))
        }
      }
    }
  }

  @Test
  fun `row reading matches Java after alpha data is coerced`() {
    for (type in listOf(TYPE_INT_ARGB, TYPE_INT_ARGB_PRE)) {
      val image = BufferedImage(256, 256, type)
      for (alpha in 0..255) {
        for (component in 0..255) {
          image.setRGB(component, alpha, (alpha shl 24) or (component * 0x010101))
        }
      }
      image.coerceData(!image.isAlphaPremultiplied)
      assertThat(image.type).isEqualTo(type)
      for (y in 0 until image.height) {
        val row = IntArray(image.width)
        OffByTwo.readArgbRow(image, y, row)
        assertThat(row).isEqualTo(image.getRGB(0, y, image.width, 1, null, 0, image.width))
      }
    }
  }

  @Test
  fun `passing check agrees with full differ at tolerance and transparency boundaries`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB)
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB)
    for (alpha in listOf(0, 1, 2, 3, 127, 253, 254, 255)) {
      for (component in listOf(0, 1, 2, 127, 253, 254, 255)) {
        val pixel = (alpha shl 24) or (component * 0x010101)
        expected.setRGB(0, 0, pixel)
        for (shift in listOf(0, 8, 16, 24)) {
          for (offset in -3..3) {
            actual.setRGB(0, 0, pixel + (offset shl shift))
            assertThat(OffByTwo.isSimilar(expected, actual))
              .isEqualTo(OffByTwo.compare(expected, actual) !is Different)
          }
        }
      }
    }
    expected.setRGB(0, 0, 0x00FFFFFF)
    actual.setRGB(0, 0, 0x00000000)
    assertThat(OffByTwo.isSimilar(expected, actual)).isTrue()
  }

  @Test
  fun `passing check rejects mismatched dimensions`() {
    assertThat(OffByTwo.isSimilar(BufferedImage(2, 3, TYPE_INT_ARGB), BufferedImage(3, 2, TYPE_INT_ARGB)))
      .isFalse()
  }
}
