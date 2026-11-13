package app.cash.paparazzi.internal.differs

import app.cash.paparazzi.Differ
import app.cash.paparazzi.Differ.DiffResult
import app.cash.paparazzi.internal.ImageUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.Color
import java.awt.image.BufferedImage

@RunWith(Parameterized::class)
class PerceptualDiffersTest(private val differ: Differ) {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun `localized perceptible changes are different`() {
    val expected = image(Color.WHITE)
    val actual = image(Color.WHITE).apply { setRGB(32, 32, Color.BLACK.rgb) }

    val result = differ.compare(expected, actual)

    assertThat(result).isInstanceOf(DiffResult.Different::class.java)
    assertThat((result as DiffResult.Different).percentDifference).isGreaterThan(0f)
    assertThat(result.percentDifference).isLessThan(5f)
  }

  @Test
  fun `perceptible changes among similar pixels are different`() {
    val expected = image(Color.WHITE)
    val actual = image(Color(254, 254, 254)).apply { setRGB(32, 32, Color.BLACK.rgb) }

    assertThat(differ.compare(expected, actual)).isInstanceOf(DiffResult.Different::class.java)
  }

  @Test
  fun `verification respects configured tolerance for localized changes`() {
    val expected = image(Color.WHITE)
    val actual = image(Color.WHITE).apply { setRGB(32, 32, Color.BLACK.rgb) }
    val failureDir = temporaryFolder.newFolder()

    assertThrows(AssertionError::class.java) {
      ImageUtils.assertImageSimilar("snapshot.png", expected, actual, 0.0, failureDir, differ)
    }
    ImageUtils.assertImageSimilar("snapshot.png", expected, actual, 5.0, failureDir, differ)
  }

  private fun image(color: Color): BufferedImage =
    BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB).apply {
      for (y in 0 until height) {
        for (x in 0 until width) {
          setRGB(x, y, color.rgb)
        }
      }
    }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun differs(): List<Array<Differ>> = listOf(arrayOf(DeltaE2000), arrayOf(Flip), arrayOf(Mssim))
  }
}
