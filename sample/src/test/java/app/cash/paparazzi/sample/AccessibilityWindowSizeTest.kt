package app.cash.paparazzi.sample

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import com.android.resources.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage

class AccessibilityWindowSizeTest {
  private var lastCapturedImage: BufferedImage? = null

  private val capturingHandler = object : SnapshotHandler {
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          lastCapturedImage = image
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    snapshotHandler = capturingHandler,
    renderExtensions = setOf(AccessibilityRenderExtension()),
    useDeviceResolution = true
  )

  @Test
  fun windowContainerSizeMatchesDeviceSize() {
    var windowSize: IntSize? = null
    var windowDpSize: DpSize? = null
    var composableSize: IntSize? = null
    var screenWidthDp = -1
    var screenHeightDp = -1
    var smallestScreenWidthDp = -1
    var orientation = -1

    paparazzi.snapshot {
      val windowInfo = LocalWindowInfo.current
      windowSize = windowInfo.containerSize
      windowDpSize = windowInfo.containerDpSize
      val config = LocalConfiguration.current
      screenWidthDp = config.screenWidthDp
      screenHeightDp = config.screenHeightDp
      smallestScreenWidthDp = config.smallestScreenWidthDp
      orientation = config.orientation
      BoxWithConstraints(Modifier.fillMaxSize()) {
        composableSize = IntSize(constraints.maxWidth, constraints.maxHeight)
      }
    }

    val expectedWidthDp = (DeviceConfig.PIXEL_5.screenWidth * 160) / DeviceConfig.PIXEL_5.density.dpiValue
    val expectedHeightDp = (DeviceConfig.PIXEL_5.screenHeight * 160) / DeviceConfig.PIXEL_5.density.dpiValue
    assertEquals(IntSize(1080, 2340), composableSize)
    assertEquals(expectedWidthDp, screenWidthDp)
    assertEquals(expectedHeightDp, screenHeightDp)
    assertEquals(minOf(expectedWidthDp, expectedHeightDp), smallestScreenWidthDp)
    assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation)
    assertEquals(DpSize(expectedWidthDp.dp, expectedHeightDp.dp), windowDpSize)
    // PIXEL_5 is 392x850 dp at 2.75x density -> 1078x2337 px (same as without AccessibilityRenderExtension)
    assertEquals(IntSize(1078, 2337), windowSize)
    // Snapshot canvas remains 2x wide to hold both the content and the accessibility legend.
    assertEquals(2160, lastCapturedImage!!.width)
    assertEquals(2340, lastCapturedImage!!.height)
  }

  @Test
  fun portraitDeviceWhoseDoubledWidthExceedsHeightKeepsPortraitOrientation() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)

    var windowSize: IntSize? = null
    var orientation = -1

    paparazzi.snapshot {
      windowSize = LocalWindowInfo.current.containerSize
      orientation = LocalConfiguration.current.orientation
    }

    // NEXUS_5: 1080x1920 at 480 dpi (3.0x) -> 360x640 dp -> 1080x1920 px, portrait
    assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation)
    assertEquals(IntSize(1080, 1920), windowSize)
    assertEquals(2160, lastCapturedImage!!.width)
    assertEquals(1920, lastCapturedImage!!.height)
  }

  @Test
  fun landscapeDeviceKeepsLandscapeDimensions() {
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(orientation = ScreenOrientation.LANDSCAPE)
    )

    var windowSize: IntSize? = null
    var orientation = -1

    paparazzi.snapshot {
      windowSize = LocalWindowInfo.current.containerSize
      orientation = LocalConfiguration.current.orientation
    }

    // NEXUS_5 landscape: 1920x1080 at 480 dpi (3.0x) -> 640x360 dp -> 1920x1080 px, landscape
    assertEquals(Configuration.ORIENTATION_LANDSCAPE, orientation)
    assertEquals(IntSize(1920, 1080), windowSize)
    assertEquals(3840, lastCapturedImage!!.width)
    assertEquals(1080, lastCapturedImage!!.height)
  }

  @Test
  fun themeOnlyUnsafeUpdateConfigPreservesRestoredDeviceSize() {
    paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5)
    paparazzi.unsafeUpdateConfig(theme = "android:Theme.Material.Light.NoActionBar")

    var windowSize: IntSize? = null
    var orientation = -1

    paparazzi.snapshot {
      windowSize = LocalWindowInfo.current.containerSize
      orientation = LocalConfiguration.current.orientation
    }

    assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation)
    assertEquals(IntSize(1080, 1920), windowSize)
  }
}
