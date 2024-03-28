package app.cash.paparazzi.internal.apng

import java.awt.Color
import java.awt.Point
import java.awt.image.BufferedImage

object TestPngUtils {

  val BACKGROUND_COLOR = Color.BLUE

  const val DEFAULT_SIZE = 100
  const val SQUARE_SIZE = 50

  internal fun createImage(imageSize: Int = DEFAULT_SIZE, squareOffset: Point) =
    BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB).apply {
      val g = graphics
      g.color = BACKGROUND_COLOR
      g.fillRect(0, 0, width, height)

      g.color = Color.GREEN
      g.fillRect(
        squareOffset.x, squareOffset.y,
        SQUARE_SIZE,
        SQUARE_SIZE
      )

      g.dispose()
    }
}
