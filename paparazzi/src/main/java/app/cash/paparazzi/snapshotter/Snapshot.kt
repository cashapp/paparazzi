package app.cash.paparazzi.snapshotter

import java.awt.image.BufferedImage

data class Snapshot(
  val spec: FrameSpec,
  val image: BufferedImage
)
