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

import java.awt.Color

data class RenderSettings(
  val renderAlpha: Int = DEFAULT_RENDER_ALPHA,
  val renderColors: List<Color> = Colors.DEFAULT_COLORS,
  val textColor: Color = Color.BLACK,
  val descriptionBackgroundColor: Color = Color.WHITE,
  val textSize: Float = 30f,
  val colorRectSize: Int = 50
) {

  fun validate() {
    if (renderAlpha < 0 || renderAlpha > 255) {
      throw IllegalArgumentException("renderAlpha should be between 0 and 255")
    }
  }

  companion object {
    const val DEFAULT_RENDER_ALPHA = 40
  }
}
