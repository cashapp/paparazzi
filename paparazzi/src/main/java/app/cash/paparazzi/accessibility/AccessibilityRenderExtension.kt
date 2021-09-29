/*
 * Copyright (C) 2021 Square, Inc.
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

import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_DESCRIPTION_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_RECT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.getColor
import java.awt.image.BufferedImage

class AccessibilityRenderExtension : RenderExtension {
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
    graphics.color = DEFAULT_DESCRIPTION_COLOR
    graphics.fillRect(image.width, 0, image.width, image.height)

    val colorRectSize = DEFAULT_RECT_SIZE
    graphics.font = graphics.font.deriveFont(DEFAULT_TEXT_SIZE)

    val drawStartX = image.width + image.margin()
    accessibilityTree.forEachIndexed { index, view ->
      val drawStartY = (index * colorRectSize * 1.5f).toInt() + image.margin()

      graphics.color = getColor(view)
      graphics.fillRoundRect(drawStartX, drawStartY, colorRectSize, colorRectSize,
          colorRectSize / 4, colorRectSize / 4)
      graphics.color = DEFAULT_TEXT_COLOR
      val accessibilityText = view.iterableTextForAccessibility.toString()

      view.createAccessibilityNodeInfo()
      graphics.drawString(accessibilityText,
          drawStartX + colorRectSize + image.margin(),
          drawStartY + colorRectSize / 2 + graphics.fontMetrics.height / 2)
    }
    return canvasImage
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
      graphics.color = getColor(view)
      graphics.fillRect(location[0], location[1], view.width, view.height)
    }

    if (view is ViewGroup) {
      (0 until view.childCount).forEach {
        renderAccessibility(view.getChildAt(it), rootImage, location)
      }
    }
  }

  private fun BufferedImage.margin(): Int = height / 80
}