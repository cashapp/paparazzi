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
import java.awt.Color

internal object Colors {

  val DEFAULT_COLORS = listOf(
      Color.RED,
      Color.GREEN,
      Color.BLUE,
      Color.YELLOW,
      Color.ORANGE,
      Color.MAGENTA,
      Color.CYAN,
      Color.PINK
  )

  private var colorIndex = -1
  private val colorMap = mutableMapOf<View, Color>()

  private fun nextColor(): Color {
    if (colorIndex + 1 > DEFAULT_COLORS.size - 1) {
      colorIndex = 0
    } else {
      colorIndex++
    }
    return DEFAULT_COLORS[colorIndex]
  }

  fun getColor(
    view: View,
    alpha: Int = 255,
  ): Color =
    colorMap.getOrElse(view) {
      val color = nextColor().withAlpha(alpha)
      colorMap[view] = color
      return@getOrElse color
    }

  private fun Color.withAlpha(alpha: Int): Color {
    return Color(red, blue, green, alpha)
  }
}
