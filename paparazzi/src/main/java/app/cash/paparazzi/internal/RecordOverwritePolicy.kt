package app.cash.paparazzi.internal

import app.cash.paparazzi.internal.apng.ApngReader
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import javax.imageio.ImageIO

internal object RecordOverwritePolicy {

  internal fun shouldOverwriteGoldenFile(
    goldenFile: File,
    snapshotFile: File,
    fps: Int,
    maxPercentDifference: Double?,
    fileSystem: FileSystem = FileSystem.SYSTEM
  ): Boolean {
    return if (maxPercentDifference != null && goldenFile.exists()) {
      if (fps == -1) {
        shouldOverwriteGoldenImageFile(goldenFile, snapshotFile, maxPercentDifference)
      } else {
        shouldOverwriteGoldenVideoFile(goldenFile, snapshotFile, maxPercentDifference, fileSystem)
      }
    } else {
      true
    }
  }

  private fun shouldOverwriteGoldenImageFile(
    goldenFile: File,
    snapshotFile: File,
    maxPercentDifference: Double
  ): Boolean {
    val goldenImage = ImageIO.read(goldenFile)
    val snapshotImage = ImageIO.read(snapshotFile)
    val (_, percentDifference) = ImageUtils.compareImages(
      goldenImage = goldenImage,
      image = snapshotImage
    )
    return percentDifference > maxPercentDifference
  }

  private fun shouldOverwriteGoldenVideoFile(
    goldenFile: File,
    snapshotFile: File,
    maxPercentDifference: Double,
    fileSystem: FileSystem
  ): Boolean {
    ApngReader(fileSystem.openReadOnly(goldenFile.path.toPath())).use { goldenApngReader ->
      ApngReader(fileSystem.openReadOnly(snapshotFile.path.toPath())).use { snapshotApngReader ->
        return anyFrameDifferenceExceedsThreshold(
          goldenApngReader = goldenApngReader,
          snapshotApngReader = snapshotApngReader,
          maxPercentDifference = maxPercentDifference
        )
      }
    }
  }

  private fun anyFrameDifferenceExceedsThreshold(
    goldenApngReader: ApngReader,
    snapshotApngReader: ApngReader,
    maxPercentDifference: Double
  ): Boolean {
    if (goldenApngReader.frameCount != snapshotApngReader.frameCount) {
      return true
    }
    while (!goldenApngReader.isFinished() || !snapshotApngReader.isFinished()) {
      val goldenFrame = goldenApngReader.readNextFrame()
      val snapshotFrame = snapshotApngReader.readNextFrame()
      if (goldenFrame == null || snapshotFrame == null) {
        return true
      }
      val (_, percentDifference) = ImageUtils.compareImages(
        goldenImage = goldenFrame,
        image = snapshotFrame
      )
      if (percentDifference > maxPercentDifference) {
        return true
      }
    }
    return false
  }
}
