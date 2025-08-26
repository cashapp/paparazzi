package app.cash.paparazzi.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB

class PixelPerfectTest {
  @Test
  fun `compare identical images`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB)
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB)
    val result = PixelPerfect.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare similar images`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB)
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB)
    expected.setRGB(0, 0, 0xFFFFFFFE.toInt())
    actual.setRGB(0, 0, 0xFFFFFFFF.toInt())
    val result = PixelPerfect.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Different::class.java)
  }

  @Test
  fun `compare similar images using black actual and alpha expected`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB)
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB)
    expected.setRGB(0, 0, 0x00000000.toInt())
    actual.setRGB(0, 0, 0xFF000000.toInt())
    val result = PixelPerfect.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare different images`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB)
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB)
    actual.setRGB(0, 0, 0xFFFFFFFF.toInt())
    val result = PixelPerfect.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Different::class.java)
  }
}
