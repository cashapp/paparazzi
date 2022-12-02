/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.paparazzi.accessibility

import android.graphics.Rect

/**
 * Shared representation of the accessibility elements in a Compose view.
 * A bridge between the ComposeA11yExtension and A11ySnapshotHandler.
 */
data class AccessibilityState(
  val width: Int,
  val height: Int,
  val elements: List<Element>
) {
  data class Element(
    val displayBounds: Rect,
    val touchBounds: Rect?,
    val text: List<String>?,
    val contentDescription: List<String>?,
    val stateDescription: String?,
    val onClickLabel: String?,
    val role: String?,
    val disabled: Boolean,
    val heading: Boolean,
    val customActions: List<CustomAction>?,
    val progress: Progress?
  ) {
    fun scaleBy(scale: Float): Element {
      return copy(
        displayBounds = displayBounds * scale,
        touchBounds = touchBounds?.times(scale)
      )
    }

    internal operator fun Rect.times(scale: Float): Rect {
      return Rect(
        (left * scale).toInt(),
        (top * scale).toInt(),
        (right * scale).toInt(),
        (bottom * scale).toInt()
      )
    }
  }

  data class CustomAction(val label: String)

  data class Progress(
    val current: Float,
    val range: ClosedRange<Float>,
    val steps: Int,
    val hasAction: Boolean
  ) {
    override fun toString(): String {
      return "$current [$range] ${if (hasAction) "Action" else ""}"
    }
  }
}
