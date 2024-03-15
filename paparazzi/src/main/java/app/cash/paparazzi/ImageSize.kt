package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils

public sealed class ImageSize {
  public data object FullBleed : ImageSize()
  public data class Limit(val height: Int = ImageUtils.THUMBNAIL_SIZE) : ImageSize()
}
