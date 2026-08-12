/*
 * Copyright (C) 2023 Square, Inc.
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

import android.graphics.Rect

internal data class AccessibilityElement(
  val id: String,
  val displayBounds: Rect,
  val contentDescription: String,
  /** Whether the element can be interacted with (clicked, toggled, edited, etc.). */
  val isInteractive: Boolean = false,
  /** Whether an interactive element's touch target is smaller than the configured minimum. */
  val isTouchTargetTooSmall: Boolean = false
) {
  val color = RenderSettings.getColor(id)
}
