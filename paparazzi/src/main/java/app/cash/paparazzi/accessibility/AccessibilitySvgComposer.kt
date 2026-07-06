/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.accessibility

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Composes a self-contained SVG with the snapshot screenshot embedded as a
 * base64 PNG, vector overlay rectangles drawn at each [AccessibilityElement]'s
 * bounds, and a legend listing each element's content description.
 *
 * Self-contained means the SVG can be loaded via `<img src="...svg">` in a
 * browser (which sandboxes SVG and disallows external resource fetches) — the
 * screenshot is inlined as a `data:` URI rather than referenced by path.
 *
 * The legend is emitted as an HTML `<foreignObject>` so the browser handles
 * word-wrap, badge alignment, and row stacking natively with real Roboto
 * metrics — no JVM-side font measurement.
 */
internal object AccessibilitySvgComposer {

  // Mirrors AccessibilityOverlayDetailsView at PIXEL density × thumbnail scale.
  private const val LEGEND_MARGIN = 11        // legacy: margin = 8dp
  private const val LEGEND_INNER_MARGIN = 5   // legacy: innerMargin = 4dp
  private const val LEGEND_SWATCH_SIZE = 22   // legacy: rectSize = 16dp
  private const val LEGEND_CORNER_RADIUS = 5  // legacy: rectSize / 4
  private const val LEGEND_FONT_SIZE = 13.5f  // ~10dp × density × thumbnail-scale
  private const val LEGEND_LINE_HEIGHT = 16   // font-size × ~1.2 leading
  private const val OVERLAY_STROKE_WIDTH = 2
  private const val OVERLAY_STROKE_ALPHA = 80

  fun compose(
    screenshot: BufferedImage,
    elements: List<AccessibilityElement>,
    nativeWidth: Int,
    nativeHeight: Int
  ): String {
    val screenshotWidth = screenshot.width
    val screenshotHeight = screenshot.height
    // displayBounds are in pre-scale (nativeWidth × nativeHeight) coordinates;
    // scale them onto the (possibly thumbnailed) screenshot bitmap.
    val scaleX = screenshotWidth.toDouble() / nativeWidth
    val scaleY = screenshotHeight.toDouble() / nativeHeight
    // Native a11y layout: screenshot on the left at its bitmap size, legend
    // column to the right at the same width. Total SVG width = 2 × screenshot
    // width. Total height matches the screenshot (legacy: panel height is
    // fixed = screen height; rows overflowing get clipped).
    val totalWidth = screenshotWidth * 2
    val totalHeight = screenshotHeight
    val pngBase64 = encodeAsBase64Png(screenshot)

    return buildString {
      append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
      append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
      append("viewBox=\"0 0 $totalWidth $totalHeight\" ")
      append("width=\"$totalWidth\" height=\"$totalHeight\">")

      appendScreenshot(screenshotWidth, screenshotHeight, pngBase64)
      elements.forEach { appendOverlayRect(it, scaleX, scaleY) }
      appendLegend(elements, screenshotWidth, screenshotHeight)

      append("</svg>")
    }
  }

  private fun StringBuilder.appendScreenshot(
    width: Int,
    height: Int,
    pngBase64: String
  ) {
    append("<image x=\"0\" y=\"0\" width=\"$width\" height=\"$height\" ")
    append("href=\"data:image/png;base64,$pngBase64\"/>")
  }

  private fun StringBuilder.appendOverlayRect(
    element: AccessibilityElement,
    scaleX: Double,
    scaleY: Double
  ) {
    val rect = element.displayBounds
    val fill = rgba(element.color, element.color.alpha)
    val stroke = rgba(element.color, OVERLAY_STROKE_ALPHA)
    val x = rect.left * scaleX
    val y = rect.top * scaleY
    val width = rect.width() * scaleX
    val height = rect.height() * scaleY
    append("<rect x=\"$x\" y=\"$y\" ")
    append("width=\"$width\" height=\"$height\" ")
    append("fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"$OVERLAY_STROKE_WIDTH\"/>")
  }

  private fun StringBuilder.appendLegend(
    elements: List<AccessibilityElement>,
    legendColumnX: Int,
    legendHeight: Int
  ) {
    if (elements.isEmpty()) return
    append("<foreignObject x=\"$legendColumnX\" y=\"0\" ")
    append("width=\"$legendColumnX\" height=\"$legendHeight\">")
    append("<div xmlns=\"http://www.w3.org/1999/xhtml\" ")
    // Legacy textLayoutWidth = panel_width - textX; only left margin, no right.
    append("style=\"padding:${LEGEND_INNER_MARGIN}px 0 ${LEGEND_INNER_MARGIN}px ${LEGEND_MARGIN}px;")
    append("font-family:Roboto,sans-serif;font-size:${LEGEND_FONT_SIZE}px;")
    append("line-height:${LEGEND_LINE_HEIGHT}px;color:#000;\">")
    // Legacy row advance = max(rectSize + margin, textHeight). CSS expresses
    // this as min-height on the row: single-line rows take min-height (giving
    // the inter-row spacing), multi-line rows grow with their content.
    val rowMinHeight = LEGEND_SWATCH_SIZE + LEGEND_MARGIN
    elements.forEach { element ->
      val fill = rgba(element.color, element.color.alpha)
      val stroke = rgba(element.color, OVERLAY_STROKE_ALPHA)
      append("<div style=\"display:flex;align-items:flex-start;")
      append("min-height:${rowMinHeight}px;\">")
      append("<div style=\"flex:0 0 auto;width:${LEGEND_SWATCH_SIZE}px;height:${LEGEND_SWATCH_SIZE}px;")
      append("border-radius:${LEGEND_CORNER_RADIUS}px;background:$fill;")
      append("border:1px solid $stroke;margin-right:${LEGEND_INNER_MARGIN}px;\"></div>")
      append("<span style=\"flex:1;word-wrap:break-word;\">")
      append(escapeXml(element.contentDescription.ifBlank { element.id }))
      append("</span>")
      append("</div>")
    }
    append("</div>")
    append("</foreignObject>")
  }

  private fun encodeAsBase64Png(image: BufferedImage): String {
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", out)
    return Base64.getEncoder().encodeToString(out.toByteArray())
  }

  private fun rgba(color: Color, alpha: Int): String {
    val a = (alpha.coerceIn(0, 255)) / 255.0
    val aRounded = String.format("%.3f", a)
    return "rgba(${color.red},${color.green},${color.blue},$aRounded)"
  }

  private fun escapeXml(text: String): String = buildString(text.length) {
    text.forEach { c ->
      when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&apos;")
        else -> append(c)
      }
    }
  }
}
