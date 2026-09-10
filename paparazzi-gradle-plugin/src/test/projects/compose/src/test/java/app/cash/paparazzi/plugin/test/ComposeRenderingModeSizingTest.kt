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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.junit.rules.TestRule
import java.awt.image.BufferedImage

private const val EXPECTED_CURRENT_V_SCROLL_WIDTH = 514
private const val EXPECTED_CURRENT_V_SCROLL_HEIGHT = 1000
private const val CONTENT_HEIGHT_DP = 700
private const val SHORT_CONTENT_TEST = "shortComposeContentFillsViewport"
private val SHORT_CONTENT_DEVICE =
  DeviceConfig.NEXUS_5.copy(
    screenWidth = 500,
    screenHeight = 1000,
    softButtons = false
  )

class ComposeRenderingModeSizingTest {
  private val snapshotHandler = DimensionAssertingSnapshotHandler()
  private lateinit var paparazzi: Paparazzi

  @get:Rule
  val paparazziRule = TestRule { base, description ->
    val isShortContentTest = description.methodName == SHORT_CONTENT_TEST
    paparazzi =
      Paparazzi(
        deviceConfig = if (isShortContentTest) SHORT_CONTENT_DEVICE else DeviceConfig.NEXUS_5,
        renderingMode = RenderingMode.V_SCROLL,
        snapshotHandler = snapshotHandler,
        useDeviceResolution = isShortContentTest
      )
    paparazzi.apply(base, description)
  }

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

  @Test
  fun shortComposeContentFillsViewport() {
    val snapshotName = "short_compose_v_scroll"
    snapshotHandler.expect(
      name = snapshotName,
      width = 500,
      height = 1000,
      verifySnapshot = false
    )

    val composeView =
      ComposeView(paparazzi.context).apply {
        setContent {
          Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
              Text("Error", color = Color.White)
            }
            Box(modifier = Modifier.fillMaxSize()) {
              Column(
                modifier =
                Modifier
                  .heightIn(max = 200.dp)
                  .verticalScroll(rememberScrollState())
              ) {
                Text("Something went wrong", color = Color.White)
              }
            }
          }
        }
      }

    paparazzi.snapshot(composeView, name = snapshotName)

    val image = snapshotHandler.image()
    assertEquals("image width", 500, image.width)
    assertEquals("image height", 1000, image.height)
    assertEquals("Compose root height", image.height, composeView.height)
    assertEquals("AndroidComposeView height", image.height, composeView.getChildAt(0).height)
    assertEquals("bottom-center pixel", 0xff000000.toInt(), image.getRGB(image.width / 2, image.height - 1))
  }

  private class DimensionAssertingSnapshotHandler : SnapshotHandler {
    private val expectedDimensions = mutableMapOf<String, Pair<Int, Int>>()
    private val unverifiedSnapshots = mutableSetOf<String>()
    private lateinit var capturedImage: BufferedImage
    private val snapshotHandler = if (System.getProperty("paparazzi.test.verify")?.toBoolean() == true) {
      SnapshotVerifier(maxPercentDifference = detectMaxPercentDifferenceDefault())
    } else {
      HtmlReportWriter(maxPercentDifference = detectMaxPercentDifferenceDefault())
    }

    fun expect(name: String, width: Int, height: Int, verifySnapshot: Boolean = true) {
      expectedDimensions[name] = width to height
      if (!verifySnapshot) unverifiedSnapshots += name
    }

    fun image(): BufferedImage = capturedImage

    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
      val name = requireNotNull(snapshot.name)
      val expected = requireNotNull(expectedDimensions[name]) {
        "No expected dimensions registered for snapshot '$name'"
      }
      val frameHandler =
        if (name in unverifiedSnapshots) {
          null
        } else {
          snapshotHandler.newFrameHandler(snapshot, frameCount, fps)
        }

      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          frameHandler?.handle(image)
          capturedImage = image
          assertEquals("Snapshot width for '$name'", expected.first, image.width)
          assertEquals("Snapshot height for '$name'", expected.second, image.height)
        }

        override fun close() {
          frameHandler?.close()
        }
      }
    }

    override fun close() {
      snapshotHandler.close()
    }
  }
}
