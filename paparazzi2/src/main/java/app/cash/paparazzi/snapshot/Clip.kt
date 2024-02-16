package app.cash.paparazzi.snapshot

import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.Flow

public data class Clip(
  val spec: ClipSpec,
  val images: Flow<BufferedImage>
)
