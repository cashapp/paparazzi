package app.cash.paparazzi

import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.Flow

data class Clip(
  val spec: ClipSpec,
  val images: Flow<BufferedImage>
)
