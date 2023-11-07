package app.cash.paparazzi.snapshotter

import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.Flow

data class Clip(
  val spec: ClipSpec,
  val images: Flow<BufferedImage>
)
