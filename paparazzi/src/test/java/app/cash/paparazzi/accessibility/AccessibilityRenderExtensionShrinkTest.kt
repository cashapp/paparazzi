package app.cash.paparazzi.accessibility

import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.internal.ImageUtils
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class AccessibilityRenderExtensionShrinkTest {
  private val snapshotHandler = TestSnapshotVerifier()

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    snapshotHandler = snapshotHandler,
    renderExtensions = setOf(AccessibilityRenderExtension()),
    renderingMode = SessionParams.RenderingMode.SHRINK
  )

  @Test
  fun `verify baseline`() {
    val view = TextView(paparazzi.context).apply {
      id = 1
      text = "Text View Sample"
      setPadding(50, 50, 50, 50)
    }
    paparazzi.snapshot(view, name = "accessibility-shrink")
  }

  private class TestSnapshotVerifier : SnapshotHandler {
    override fun newFrameHandler(
      snapshot: Snapshot,
      frameCount: Int,
      fps: Int
    ): SnapshotHandler.FrameHandler {
      return object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          val expected = File("src/test/resources/${snapshot.name}.png")
          ImageUtils.assertImageSimilar(
            relativePath = expected.path,
            image = image,
            goldenImage = ImageIO.read(expected),
            maxPercentDifferent = 0.1
          )
        }

        override fun close() = Unit
      }
    }

    override fun close() = Unit
  }
}
