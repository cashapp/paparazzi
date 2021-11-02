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

import android.graphics.fonts.SystemFonts
import android.view.View
import android.view.ViewGroup
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_DESCRIPTION_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_RECT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.getColor
import java.awt.Font
import java.awt.image.BufferedImage

class AccessibilityRenderExtension : RenderExtension {
  private val accessibilityTree = LinkedHashSet<View>()

  // We have seen issues with the built in font rendering incorrectly on different OS (Linux, Mac OS, etc.).
  // We will use a roboto font provided by layoutLib to render accessibility metadata.
  private val robotoFont: Font by lazy {
    val inputStream = SystemFonts.getAvailableFonts().first { it.file.name.contains("Roboto-Light.ttf") }.file.inputStream()
    inputStream.use {
      Font.createFont(Font.TRUETYPE_FONT, it)
    }
  }

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
    graphics.font = robotoFont.deriveFont(DEFAULT_TEXT_SIZE)
    graphics.drawImage(image, 0, 0, null)
    graphics.color = DEFAULT_DESCRIPTION_COLOR
    graphics.fillRect(image.width, 0, image.width, image.height)

    val colorRectSize = DEFAULT_RECT_SIZE
    val drawStartX = image.width + image.margin()
    accessibilityTree.forEachIndexed { index, view ->
      val drawStartY = (index * colorRectSize * 1.5f).toInt() + image.margin()

      val viewColor = getColor(view)
      graphics.color = viewColor
      graphics.fillRoundRect(
        drawStartX, drawStartY, colorRectSize, colorRectSize,
        colorRectSize / 4, colorRectSize / 4
      )
      graphics.color = DEFAULT_TEXT_COLOR
      val accessibilityText = view.iterableTextForAccessibility.toString()

      val lineMetrics = graphics.fontMetrics.getLineMetrics(accessibilityText, graphics)
      graphics.drawString(
        accessibilityText,
        (drawStartX + colorRectSize + image.margin()).toFloat(),
        drawStartY + colorRectSize / 3 + lineMetrics.height / 2f
      )
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