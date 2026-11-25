package app.cash.paparazzi.internal

import app.cash.paparazzi.Differ
import app.cash.paparazzi.internal.differs.OffByTwo
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB

class ImageVerificationTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  @Test
  fun `identical pixels still invoke custom differs`() {
    val image = BufferedImage(3, 3, TYPE_INT_ARGB)
    var invoked = false
    val differ = object : Differ {
      override fun compare(expected: BufferedImage, actual: BufferedImage): Differ.DiffResult {
        invoked = true
        return Differ.DiffResult.Different(BufferedImage(9, 3, TYPE_INT_ARGB), 100f, 9)
      }
    }
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", image, image, 0.0, tempDir.root, differ)
    }
    assertThat(invoked).isTrue()
  }

  @Test
  fun `image subclasses retain their pixel access behavior`() {
    val expected = BufferedImage(3, 3, TYPE_INT_ARGB)
    val actual = object : BufferedImage(3, 3, TYPE_INT_ARGB) {
      override fun getRGB(x: Int, y: Int): Int = -1
    }
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", expected, actual, 0.0, tempDir.root, OffByTwo)
    }
  }

  @Test
  fun `coerced premultiplied pixels still fail verification`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB).apply {
      setRGB(0, 0, 0x80404040.toInt())
    }
    val actual = BufferedImage(1, 1, TYPE_INT_ARGB).apply {
      setRGB(0, 0, 0x80808080.toInt())
      coerceData(true)
    }
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", expected, actual, 0.0, tempDir.root, OffByTwo)
    }
  }

  @Test
  fun `coerced unpremultiplied pixels still fail verification`() {
    val expected = BufferedImage(1, 1, TYPE_INT_ARGB).apply {
      setRGB(0, 0, 0x80FFFFFF.toInt())
    }
    val actual = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE).apply {
      setRGB(0, 0, 0x80808080.toInt())
      coerceData(false)
    }
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", expected, actual, 0.0, tempDir.root, OffByTwo)
    }
  }

  @Test
  fun `identical pixels do not bypass negative thresholds`() {
    val image = BufferedImage(3, 3, TYPE_INT_ARGB)
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", image, image, -1.0, tempDir.root, OffByTwo)
    }
  }

  @Test
  fun `transparent size changes still fail verification`() {
    val expected = BufferedImage(3, 3, TYPE_INT_ARGB)
    val actual = BufferedImage(3, 5, TYPE_INT_ARGB)
    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("golden.png", expected, actual, 100.0, tempDir.root, OffByTwo)
    }
  }

  @Test
  fun `golden conversion preserves existing alpha rounding`() {
    val expected = BufferedImage(16, 16, BufferedImage.TYPE_4BYTE_ABGR)
    for (y in 0 until 16) {
      for (x in 0 until 16) expected.setRGB(x, y, ((y * 16 + x) shl 24) or 0x123456)
    }
    val actual = BufferedImage(16, 16, TYPE_INT_ARGB)
    val g = actual.createGraphics()
    g.drawImage(expected, 0, 0, null)
    g.dispose()
    ImageUtils.assertImageSimilar("golden.png", expected, actual, 0.0, tempDir.root, OffByTwo)
    assertThat(tempDir.root.listFiles()).isEmpty()
  }
}
