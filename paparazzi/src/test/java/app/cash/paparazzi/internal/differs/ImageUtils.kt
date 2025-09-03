package app.cash.paparazzi.internal.differs

import java.awt.image.BufferedImage

fun createImage(
  width: Int,
  height: Int,
  rgb: Long = 0xFFFFFFFFF,
  onApplyPixel: BufferedImage.(x: Int, y: Int) -> Long = { _, _ -> rgb }
): BufferedImage =
  BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
    for (x in 0 until width) {
      for (y in 0 until height) {
        setRGB(x, y, onApplyPixel(x, y).toInt())
      }
    }
  }
