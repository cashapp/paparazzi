/*
 * Copyright (C) 2026 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.accessibility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.ceil
import kotlin.math.max

internal object AccessibilityFailureImage {
  fun render(context: Context, image: BufferedImage, frame: AccessibilityFrame): BufferedImage {
    // Keep the captured pixels, highlights, and legend at full render resolution so text stays readable.
    // Reuse the render extension's highlights and legend without relaying out the captured UI.
    // A cropped snapshot can be smaller than the legend's badge and margins. Give the diagnostic
    // legend enough room for text and every row, independently of the captured UI's dimensions.
    val legendWidth = max(frame.width, ceil(200 * context.resources.displayMetrics.density).toInt())
    val legend = AccessibilityOverlayDetailsView(context).apply { updateElements(frame.elements) }
    val legendHeight = max(frame.height, legend.contentHeightForWidth(legendWidth))
    legend.layout(0, 0, legendWidth, legendHeight)
    val bitmap = Bitmap.createBitmap(frame.width + legendWidth, legendHeight, Bitmap.Config.ARGB_8888)
    val overlayImage = try {
      val canvas = Canvas(bitmap)
      canvas.save()
      canvas.clipRect(0, 0, frame.width, frame.height)
      AccessibilityOverlayDrawable().apply { updateElements(frame.elements) }.draw(canvas)
      canvas.restore()

      canvas.translate(frame.width.toFloat(), 0f)
      legend.draw(canvas)

      BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB).apply {
        bitmap.getPixels((raster.dataBuffer as DataBufferInt).data, 0, width, 0, 0, width, height)
      }
    } finally {
      bitmap.recycle()
    }

    return BufferedImage(overlayImage.width, overlayImage.height, BufferedImage.TYPE_INT_ARGB).apply {
      val graphics = createGraphics()
      try {
        graphics.drawImage(image, 0, 0, null)
        graphics.drawImage(overlayImage, 0, 0, null)
      } finally {
        graphics.dispose()
      }
    }
  }
}
