package app.cash.paparazzi

import app.cash.paparazzi.internal.ImageUtils
import app.cash.paparazzi.snapshotter.FrameSpec
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.ScreenRound
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage

fun BufferedImage.formatImage(spec: FrameSpec) =
  this.frameImage(spec.renderingMode, spec.deviceConfig).scaleImage()

fun BufferedImage.frameImage(
  renderingMode: SessionParams.RenderingMode,
  deviceConfig: DeviceConfig
): BufferedImage {
  // On device sized screenshot, we should apply any device specific shapes.
  if (renderingMode == SessionParams.RenderingMode.NORMAL && deviceConfig.screenRound == ScreenRound.ROUND) {
    val newImage = BufferedImage(this.width, this.height, this.type)
    val g = newImage.createGraphics()
    g.clip = Ellipse2D.Float(0f, 0f, this.height.toFloat(), this.width.toFloat())
    g.drawImage(this, 0, 0, this.width, this.height, null)
    return newImage
  }

  return this
}

fun BufferedImage.scaleImage(): BufferedImage {
  val scale = ImageUtils.getThumbnailScale(this)
  // Only scale images down so we don't waste storage space enlarging smaller layouts.
  return if (scale < 1f) ImageUtils.scale(this, scale, scale) else this
}
