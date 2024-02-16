package app.cash.paparazzi.snapshot

import java.awt.image.BufferedImage

public data class Snapshot(
  val spec: FrameSpec,
  val image: BufferedImage
)
