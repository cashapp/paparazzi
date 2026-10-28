package app.cash.paparazzi.accessibility

import android.graphics.Rect
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class AccessibilityFailureImageTest {
  @get:Rule
  val paparazzi = Paparazzi(
    snapshotHandler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) = Unit
          override fun close() = Unit
        }

      override fun close() = Unit
    }
  )

  @Test
  fun `highlights scale to snapshot coordinates and legend uses existing colors`() {
    val image = solidImage(Color.WHITE)
    val element = AccessibilityElement(
      id = "first",
      displayBounds = Rect(40, 80, 120, 160),
      mainAccessibilityText = "First accessible element"
    )
    val frame = AccessibilityFrame(listOf(element), width = 400, height = 600)

    val diagnostic = AccessibilityFailureImage.render(paparazzi.context, image, frame)

    assertThat(diagnostic.width).isAtLeast(image.width * 2)
    assertThat(diagnostic.height).isEqualTo(image.height)
    // Independent x/y ratios: bounds become (20, 20)-(60, 40) in the snapshot.
    assertThat(diagnostic.getRGB(30, 30)).isNotEqualTo(Color.WHITE.rgb)
    assertThat(diagnostic.getRGB(10, 10)).isEqualTo(Color.WHITE.rgb)
    assertThat(diagnostic.getRGB(50, 60)).isEqualTo(Color.WHITE.rgb)
    assertThat(image.getRGB(30, 30)).isEqualTo(Color.WHITE.rgb)
    val legendPixels = diagnostic.getRGB(image.width, 0, image.width, image.height, null, 0, image.width)
    assertThat(legendPixels.any { it == Color.BLACK.rgb }).isTrue()
    assertThat(legendPixels.any { it != Color.WHITE.rgb && it != Color.BLACK.rgb }).isTrue()
  }

  @Test
  fun `empty hierarchy keeps main snapshot and includes empty legend`() {
    val image = solidImage(Color.BLUE)
    val diagnostic = AccessibilityFailureImage.render(
      paparazzi.context,
      image,
      AccessibilityFrame(emptyList(), width = 400, height = 600)
    )

    assertThat(diagnostic.getRGB(30, 30)).isEqualTo(Color.BLUE.rgb)
    assertThat(diagnostic.getRGB(image.width + 30, 30)).isEqualTo(Color.WHITE.rgb)
  }

  @Test
  fun `translucent highlights retain their color and alpha`() {
    val image = solidImage(Color(0, 0, 0, 0))
    val element = AccessibilityElement(
      id = "alpha",
      displayBounds = Rect(40, 80, 120, 160),
      mainAccessibilityText = "Translucent highlight"
    )

    val diagnostic = AccessibilityFailureImage.render(
      paparazzi.context,
      image,
      AccessibilityFrame(listOf(element), width = 400, height = 600)
    )

    assertThat(diagnostic.getRGB(30, 30)).isEqualTo(element.color.rgb)
    assertThat(diagnostic.getRGB(10, 10)).isEqualTo(0)
    assertThat(image.getRGB(30, 30)).isEqualTo(0)
  }

  @Test
  fun `24dp snapshot leaves room for legend text and all rows`() {
    val side = (24 * paparazzi.context.resources.displayMetrics.density).toInt()
    val image = solidImage(Color.WHITE, side, side)
    val elements = (1..3).map {
      AccessibilityElement(
        id = "icon-$it",
        displayBounds = Rect(0, 0, side, side),
        mainAccessibilityText = "Icon $it"
      )
    }

    val diagnostic = AccessibilityFailureImage.render(
      paparazzi.context,
      image,
      AccessibilityFrame(elements, width = side, height = side)
    )

    assertThat(diagnostic.width).isGreaterThan(image.width * 2)
    assertThat(diagnostic.height).isGreaterThan(image.height)
    val legendWidth = diagnostic.width - image.width
    val legendPixels = diagnostic.getRGB(image.width, 0, legendWidth, diagnostic.height, null, 0, legendWidth)
    assertThat(legendPixels.any { it == Color.BLACK.rgb }).isTrue()
    assertThat(legendPixels.any { it != Color.WHITE.rgb && it != Color.BLACK.rgb }).isTrue()
    // The last row must remain visible below the small snapshot's bottom edge.
    val bottomThird = legendPixels.takeLast(legendPixels.size / 3)
    assertThat(bottomThird.any { it == Color.BLACK.rgb }).isTrue()
    assertThat(image.getRGB(0, 0)).isEqualTo(Color.WHITE.rgb)
  }

  private fun solidImage(color: Color, width: Int = 200, height: Int = 150) =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
      val graphics = createGraphics()
      try {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
      } finally {
        graphics.dispose()
      }
    }
}
