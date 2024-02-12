package app.cash.paparazzi

import java.awt.image.BufferedImage

public data class Snapshot(
  val spec: FrameSpec,
  val image: BufferedImage
)
