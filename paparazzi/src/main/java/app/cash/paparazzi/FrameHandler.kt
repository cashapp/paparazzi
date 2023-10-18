package app.cash.paparazzi

import java.awt.image.BufferedImage
import java.io.Closeable

public interface FrameHandler : Closeable {
  public fun handle(image: BufferedImage)
}
