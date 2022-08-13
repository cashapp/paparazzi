package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils

sealed class ImageSize {
  object FullBleed : ImageSize()
  data class Limit(val height: Int = ImageUtils.THUMBNAIL_SIZE) : ImageSize()
}
