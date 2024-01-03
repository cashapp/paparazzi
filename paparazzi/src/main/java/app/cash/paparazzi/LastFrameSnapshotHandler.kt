package app.cash.paparazzi

import app.cash.paparazzi.SnapshotHandler.FrameHandler
import java.awt.image.BufferedImage

class LastFrameSnapshotHandler(
  private val maxPercentDifference: Double = 0.1,
) : SnapshotHandler {
  override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): FrameHandler {
    val handler = if (isVerifying) SnapshotVerifier(maxPercentDifference) else HtmlReportWriter()
    val frameHandler = handler.newFrameHandler(snapshot, 1, -1)
    return object : FrameHandler {
      private lateinit var lastFrame: BufferedImage
      override fun handle(image: BufferedImage) {
        lastFrame = image
      }

      override fun close() {
        frameHandler.handle(lastFrame)
        frameHandler.close()
      }
    }
  }

  override fun close() = Unit

  companion object {
    private val isVerifying: Boolean =
      System.getProperty("paparazzi.test.verify")?.toBoolean() == true
  }
}

