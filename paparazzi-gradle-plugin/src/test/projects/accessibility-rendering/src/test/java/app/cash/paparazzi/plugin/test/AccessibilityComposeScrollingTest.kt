package app.cash.paparazzi.plugin.test

import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import kotlinx.coroutines.runBlocking

class AccessibilityComposeScrollingTest {
  private val scrollState = ScrollState(0)
  private var expectedScrollPosition = 0
  private var frames = 0

  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL,
    renderExtensions = setOf(AccessibilityRenderExtension()),
    snapshotHandler = object : SnapshotHandler {
      override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler =
        object : SnapshotHandler.FrameHandler {
          override fun handle(image: BufferedImage) {
            assertEquals(
              "Capture must preserve the requested scroll position",
              expectedScrollPosition,
              scrollState.value
            )
            frames++
          }

          override fun close() = Unit
        }

      override fun close() = Unit
    }
  )

  @Test
  fun repeatedCapturesReachTheEndOfWrappingText() =
    runBlocking {
      val hostView = ComposeView(paparazzi.context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setContent {
          Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            repeat(30) { index ->
              Text(
                "Row $index: " +
                  "Text that wraps across multiple lines at the accessibility viewport width. ".repeat(4)
              )
            }
            Text("End of content")
          }
        }
      }

      paparazzi.snapshot(hostView)
      val maxScroll = scrollState.maxValue
      val viewport = scrollState.viewportSize
      assertTrue("The fixture must require multiple pages", maxScroll > viewport)

      for (position in listOf(viewport, maxScroll, 0, maxScroll)) {
        scrollState.scrollTo(position)
        expectedScrollPosition = position
        paparazzi.snapshot(hostView)
        assertEquals("Capture must preserve the scroll range", maxScroll, scrollState.maxValue)
        assertEquals("Capture must preserve the viewport", viewport, scrollState.viewportSize)
      }
      assertFalse("The last capture must reach the end", scrollState.canScrollForward)
      assertEquals(5, frames)
    }
}
