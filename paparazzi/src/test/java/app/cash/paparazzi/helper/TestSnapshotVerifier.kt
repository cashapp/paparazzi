package app.cash.paparazzi.helper

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.cash.paparazzi.internal.ImageUtils
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal class TestSnapshotVerifier(val tempDir: TemporaryFolder) : SnapshotHandler {
  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): SnapshotHandler.FrameHandler {
    return object : SnapshotHandler.FrameHandler {
      override fun handle(image: BufferedImage) {
        val expected = File("src/test/resources/${snapshot.name}.png")
        val failed = File("src/test/resources/${snapshot.name}/failed.png")
        ImageUtils.assertImageSimilar(
          relativePath = expected.path,
          image = image,
          goldenImage = ImageIO.read(expected),
          maxPercentDifferent = 0.1,
          relativePathFailure = failed.path,
          failureDir = tempDir.newFolder()
        )
      }

      override fun close() = Unit
    }
  }

  override fun close() = Unit
}
