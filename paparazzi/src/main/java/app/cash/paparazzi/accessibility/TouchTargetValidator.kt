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

/**
 * Classifies whether an [AccessibilityElement] is interactive and, if so, whether its touch
 * target is smaller than the configured minimum size.
 *
 * The minimum size is interpreted per Material and WCAG guidance (48x48dp by default): an element
 * is flagged when either rendered dimension is below the threshold.
 */
internal class TouchTargetValidator(
  private val minTouchTargetSizeDp: Float = RenderSettings.TOUCH_TARGET_SIZE_DP,
  private val density: Float
) {
  private val minTouchTargetSizePx = minTouchTargetSizeDp * density

  fun validate(element: AccessibilityElement, isInteractive: Boolean): AccessibilityElement {
    val tooSmall = isInteractive &&
      (
        element.displayBounds.width() < minTouchTargetSizePx ||
          element.displayBounds.height() < minTouchTargetSizePx
        )
    return element.copy(
      isInteractive = isInteractive,
      isTouchTargetTooSmall = tooSmall
    )
  }
}
