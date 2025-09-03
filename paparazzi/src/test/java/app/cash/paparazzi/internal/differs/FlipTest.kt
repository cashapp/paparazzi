package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage

class FlipTest {
  @Test
  fun `compare identical images`() {
    val expected = createImage(width = 1, height = 1)
    val actual = createImage(width = 1, height = 1)
    val result = Flip.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare similar images`() {
    val expected = createImage(width = 1, height = 1, rgb = 0xFFFFFFFE)
    val actual = createImage(width = 1, height = 1)
    val result = Flip.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare similar images using black actual and alpha expected`() {
    val expected = createImage(width = 1, height = 1, rgb = 0x00000000)
    val actual = createImage(width = 1, height = 1, rgb = 0xFF000000)
    val result = Flip.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Identical::class.java)
  }

  @Test
  fun `compare different images`() {
    val expected = createImage(width = 1, height = 1, rgb = 0x00000000)
    val actual = createImage(width = 1, height = 1)
    val result = Flip.compare(expected, actual)
    assertThat(result).isInstanceOf(Differ.DiffResult.Different::class.java)
  }
}
