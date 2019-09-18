package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.internal.ImageUtils.MAX_PERCENT_DIFFERENCE
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class SnapshotVerifier(
  private val rootDirectory: File = File("screenshots")
) : SnapshotHandler() {
  private val imagesDirectory: File = File(rootDirectory, "images")
  private val videosDirectory: File = File(rootDirectory, "videos")

  init {
    imagesDirectory.mkdirs()
    videosDirectory.mkdirs()
  }

  override fun newFrameHandler(
    snapshot: Snapshot,
    frameCount: Int,
    fps: Int
  ): FrameHandler {
    return object : FrameHandler {
      override fun handle(image: BufferedImage) {
        val expected = File(rootDirectory, "images/$testName.png")
        if (!expected.exists()) {
          throw AssertionError("File $expected does not exist")
        }

        val goldenImage = ImageIO.read(expected)
        ImageUtils.assertImageSimilar(
            relativePath = expected.path,
            image = image,
            goldenImage = goldenImage,
            maxPercentDifferent = MAX_PERCENT_DIFFERENCE
        )
      }

      override fun close() = Unit
    }
  }

  override fun close() = Unit
}