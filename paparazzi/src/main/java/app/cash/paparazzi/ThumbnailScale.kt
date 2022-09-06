package app.cash.paparazzi

sealed interface ThumbnailScale {

  /**
   * Thumbnails should not be scaled
   */
  object NoScale : ThumbnailScale

  /**
   * Scale thumbnail to have max side less or equal to [size]
   */
  data class ScaleMaxSideTo(
    val size: Int
  ) : ThumbnailScale
}
