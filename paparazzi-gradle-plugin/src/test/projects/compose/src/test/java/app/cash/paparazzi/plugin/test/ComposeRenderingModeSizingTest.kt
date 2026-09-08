package app.cash.paparazzi.plugin.test

import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.HtmlReportWriter
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.SnapshotVerifier
import app.cash.paparazzi.detectMaxPercentDifferenceDefault
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

private const val EXPECTED_CURRENT_V_SCROLL_WIDTH = 514
private const val EXPECTED_CURRENT_V_SCROLL_HEIGHT = 1000
private const val CONTENT_HEIGHT_DP = 700

class ComposeRenderingModeSizingTest {
  private val snapshotHandler = DimensionAssertingSnapshotHandler()

  @get:Rule
  val paparazzi =
    Paparazzi(
      deviceConfig = DeviceConfig.NEXUS_5,
      renderingMode = RenderingMode.V_SCROLL,
      snapshotHandler = snapshotHandler
    )

  @Test
  fun verticalScrollBoundedComposeRootKeepsCurrentSnapshotSize() {
    snapshotHandler.expect(
      name = "compose_v_scroll",
      width = EXPECTED_CURRENT_V_SCROLL_WIDTH,
      height = EXPECTED_CURRENT_V_SCROLL_HEIGHT
    )

    val composeView =
      ComposeView(paparazzi.context).apply {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        setContent {
          Column(
            modifier =
            Modifier
              .fillMaxWidth()
              .height(CONTENT_HEIGHT_DP.dp)
              .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier =
              Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Red)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("bottom edge")
            Box(
              modifier =
              Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Blue)
            )
          }
        }
      }

    paparazzi.snapshot(
      view = BoundedLayout(paparazzi.context).apply {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        addView(composeView)
      },
      name = "compose_v_scroll"
    )
  }

  private class DimensionAssertingSnapshotHandler : SnapshotHandler {
    private val expectedDimensions = mutableMapOf<String, Pair<Int, Int>>()
    private val snapshotHandler = if (System.getProperty("paparazzi.test.verify")?.toBoolean() == true) {
      SnapshotVerifier(maxPercentDifference = detectMaxPercentDifferenceDefault())
    } else {
      HtmlReportWriter(maxPercentDifference = detectMaxPercentDifferenceDefault())
    }

    fun expect(name: String, width: Int, height: Int) {
      expectedDimensions[name] = width to height
    }

    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
      val name = requireNotNull(snapshot.name)
      val expected = requireNotNull(expectedDimensions[name]) {
        "No expected dimensions registered for snapshot '$name'"
      }
      val frameHandler = snapshotHandler.newFrameHandler(snapshot, frameCount, fps)

      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          frameHandler.handle(image)
          assertEquals("Snapshot width for '$name'", expected.first, image.width)
          assertEquals("Snapshot height for '$name'", expected.second, image.height)
        }

        override fun close() {
          frameHandler.close()
        }
      }
    }

    override fun close() {
      snapshotHandler.close()
    }
  }
}
