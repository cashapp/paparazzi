/*
 * Copyright (C) 2019 Square, Inc.
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
package app.cash.paparazzi.extensions.accessibility

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.extensions.accessibility.Colors.getColor
import java.awt.image.BufferedImage

class AccessibilityRenderExtension(private val renderSettings: RenderSettings = RenderSettings()) :
    RenderExtension {

  init {
    renderSettings.validate()
  }

  private val accessibilityTree = LinkedHashSet<View>()

  override fun render(
    snapshot: Snapshot,
    view: View,
    image: BufferedImage,
  ): BufferedImage {
    val location = IntArray(2)
    accessibilityTree.clear()
    renderAccessibility(view, image, location)
    return renderViewInfo(image)
  }

  private fun renderViewInfo(image: BufferedImage): BufferedImage {
    if (accessibilityTree.isEmpty()) {
      return image
    }

    val canvasImage = BufferedImage(image.width * 2, image.height, image.type)

    val graphics = canvasImage.createGraphics()
    graphics.drawImage(image, 0, 0, null)
    graphics.color = renderSettings.descriptionBackgroundColor
    graphics.fillRect(image.width, 0, image.width, image.height)

    val colorRectSize = renderSettings.colorRectSize
    graphics.font = graphics.font.deriveFont(renderSettings.textSize)

    val drawStartX = image.width + image.margin()
    accessibilityTree.forEachIndexed { index, view ->
      val drawStartY = (index * colorRectSize * 1.5f).toInt() + image.margin()

      graphics.color = getColor(view, renderSettings.renderAlpha)
      graphics.fillRoundRect(drawStartX, drawStartY, colorRectSize, colorRectSize,
          colorRectSize / 4, colorRectSize / 4)
      graphics.color = renderSettings.textColor
      var accessibilityText = view.iterableTextForAccessibility.toString()

      when (view) {
        is Button -> accessibilityText += BUTTON_ACCESSIBILITY_TEXT
      }

      graphics.drawString(accessibilityText,
          drawStartX + colorRectSize + image.margin(),
          drawStartY + colorRectSize / 2 + graphics.fontMetrics.height / 2)
    }
    return canvasImage
  }

  private fun BufferedImage.margin(): Int {
    return height / 80
  }

  private fun renderAccessibility(
    view: View,
    rootImage: BufferedImage,
    location: IntArray
  ) {
    val graphics = rootImage.createGraphics()
    view.getLocationInWindow(location)

    if (view.isImportantForAccessibility && !view.iterableTextForAccessibility.isNullOrBlank()) {
      accessibilityTree.add(view)
      graphics.color = getColor(view = view, alpha = renderSettings.renderAlpha)
      graphics.fillRect(location[0], location[1], view.width, view.height)
    }

    if (view is ViewGroup) {
      (0 until view.childCount).forEach {
        renderAccessibility(view.getChildAt(it), rootImage, location)
      }
    }
  }

  companion object {
    private const val BUTTON_ACCESSIBILITY_TEXT = ", Button"
  }
}