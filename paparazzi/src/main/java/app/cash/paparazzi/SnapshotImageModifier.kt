package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils
import java.awt.image.BufferedImage

/**
 * Interface that allows access to the image from the bridge session for modification
 */
interface SnapshotImageModifier {

  /**
   * @param image The image from the bridge session to modify
   * @return modified image
   */
  fun modifyImage(image: BufferedImage): BufferedImage
}

/**
 * Uses thumbnail scale to modify the image from the bridge session
 */
internal object ThumbnailScaleImageModifier : SnapshotImageModifier {

  override fun modifyImage(image: BufferedImage): BufferedImage = scaleImage(image)

  private fun scaleImage(image: BufferedImage): BufferedImage {
    val scale = ImageUtils.getThumbnailScale(image)
    return ImageUtils.scale(image, scale, scale)
  }
}
