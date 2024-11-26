package app.cash.paparazzi.internal

import java.awt.image.BufferedImage

public interface Differ {
  public fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult

  public sealed interface DiffResult {
    public data class Identical(
      val delta: BufferedImage
    ) : DiffResult

    public data class Similar(
      val delta: BufferedImage,
      val numSimilarPixels: Long
    ) : DiffResult

    public data class Different(
      val delta: BufferedImage,
      val percentDifference: Float,
      val numDifferentPixels: Long
    ) : DiffResult
  }
}
