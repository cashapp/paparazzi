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
import java.awt.Color

internal object RenderSettings {
  const val DEFAULT_RENDER_ALPHA = 40
  val DEFAULT_RENDER_COLORS = listOf(
    Color.RED,
    Color.GREEN,
    Color.BLUE,
    Color.YELLOW,
    Color.ORANGE,
    Color.MAGENTA,
    Color.CYAN,
    Color.PINK
  )
  val DEFAULT_TEXT_COLOR: Color = Color.BLACK
  val DEFAULT_DESCRIPTION_COLOR: Color = Color.WHITE
  val DEFAULT_TEXT_SIZE: Float = 30f
  val DEFAULT_RECT_SIZE: Int = 50

  private val colorMap = mutableMapOf<View, Color>()
  private var colorIndex = -1

  fun getColor(view: View): Color {
    return colorMap.getOrElse(view) {
      nextColor().withAlpha(DEFAULT_RENDER_ALPHA).apply {
        colorMap[view] = this
      }
    }
  }

  private fun nextColor(): Color {
    if (colorIndex + 1 > DEFAULT_RENDER_COLORS.size - 1) {
      colorIndex = 0
    } else {
      colorIndex++
    }
    return DEFAULT_RENDER_COLORS[colorIndex]
  }

  private fun Color.withAlpha(alpha: Int): Color {
    return Color(red, green, blue, alpha)
  }
}
