package app.cash.paparazzi.helper

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.internal.ImageUtils
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal class TestSnapshotVerifier(val failureDirProvider: () -> File) : SnapshotHandler {
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
          maxPercentDifferent = 0.1,
          failureDir = failureDirProvider()
        )
      }

      override fun close() = Unit
    }
  }

  override fun close() = Unit
}
