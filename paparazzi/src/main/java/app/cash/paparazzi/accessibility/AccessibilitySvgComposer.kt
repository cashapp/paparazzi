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
import kotlin.math.max

/**
 * Composes a self-contained SVG with the snapshot screenshot embedded as a
 * base64 PNG, vector overlay rectangles drawn at each [AccessibilityElement]'s
 * bounds, and a legend listing each element's content description.
 *
 * Self-contained means the SVG can be loaded via `<img src="...svg">` in a
 * browser (which sandboxes SVG and disallows external resource fetches) — the
 * screenshot is inlined as a `data:` URI rather than referenced by path.
 *
 * Output is a String containing the SVG document (with XML declaration). The
 * caller writes this to disk; the JUnit Platform engine wrapper emits it as a
 * file attachment via `fileEntryPublished` with media type `image/svg+xml`.
 */
internal object AccessibilitySvgComposer {

  private const val LEGEND_ROW_HEIGHT = 18
  private const val LEGEND_PADDING = 8
  private const val LEGEND_SWATCH_SIZE = 14
  private const val LEGEND_TEXT_OFFSET_X = 20
  private const val LEGEND_FONT_SIZE = 12
  private const val OVERLAY_STROKE_WIDTH = 2
  private const val OVERLAY_STROKE_ALPHA = 80

  fun compose(
    screenshot: BufferedImage,
    elements: List<AccessibilityElement>
  ): String {
    val screenshotWidth = screenshot.width
    val screenshotHeight = screenshot.height
    val legendHeight = max(0, elements.size) * LEGEND_ROW_HEIGHT + LEGEND_PADDING * 2
    val totalHeight = screenshotHeight + legendHeight
    val pngBase64 = encodeAsBase64Png(screenshot)

    return buildString {
      append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
      append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
      append("viewBox=\"0 0 $screenshotWidth $totalHeight\" ")
      append("width=\"$screenshotWidth\" height=\"$totalHeight\">")

      appendScreenshot(screenshotWidth, screenshotHeight, pngBase64)
      elements.forEach { appendOverlayRect(it) }
      appendLegend(elements, screenshotHeight)

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

  private fun StringBuilder.appendOverlayRect(element: AccessibilityElement) {
    val rect = element.displayBounds
    val fill = rgba(element.color, element.color.alpha)
    val stroke = rgba(element.color, OVERLAY_STROKE_ALPHA)
    append("<rect x=\"${rect.left}\" y=\"${rect.top}\" ")
    append("width=\"${rect.width()}\" height=\"${rect.height()}\" ")
    append("fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"$OVERLAY_STROKE_WIDTH\"/>")
  }

  private fun StringBuilder.appendLegend(
    elements: List<AccessibilityElement>,
    screenshotHeight: Int
  ) {
    if (elements.isEmpty()) return
    append("<g transform=\"translate($LEGEND_PADDING ${screenshotHeight + LEGEND_PADDING})\">")
    elements.forEachIndexed { index, element ->
      val y = index * LEGEND_ROW_HEIGHT
      val fill = rgba(element.color, element.color.alpha)
      val stroke = rgba(element.color, OVERLAY_STROKE_ALPHA)
      append("<rect x=\"0\" y=\"$y\" ")
      append("width=\"$LEGEND_SWATCH_SIZE\" height=\"$LEGEND_SWATCH_SIZE\" ")
      append("fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"1\"/>")
      append("<text x=\"$LEGEND_TEXT_OFFSET_X\" y=\"${y + LEGEND_SWATCH_SIZE - 2}\" ")
      append("font-size=\"$LEGEND_FONT_SIZE\" font-family=\"sans-serif\">")
      append(escapeXml(element.contentDescription.ifBlank { element.id }))
      append("</text>")
    }
    append("</g>")
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
