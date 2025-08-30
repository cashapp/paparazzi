package app.cash.paparazzi.internal

import java.awt.image.BufferedImage

public sealed interface Differ {
  public fun compare(expected: BufferedImage, actual: BufferedImage): DiffResult

  public sealed interface DiffResult {
    public class Identical(
      public val delta: BufferedImage
    ) : DiffResult

    public class Similar(
      public val delta: BufferedImage,
      public val numSimilarPixels: Long
    ) : DiffResult

    public class Different(
      public val delta: BufferedImage,
      public val percentDifference: Float,
      public val numDifferentPixels: Long
    ) : DiffResult
  }
}
