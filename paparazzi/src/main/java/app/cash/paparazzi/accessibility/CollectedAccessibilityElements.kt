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
 * Thread-local hand-off between [AccessibilityRenderExtension] and the
 * accessibility SVG attachment writer in native report mode.
 *
 * The extension stashes the collected element list during its pre-draw
 * listener; the FrameHandler decorator consumes it after layoutlib delivers
 * the rendered bitmap. Thread-local because Paparazzi renders on a single
 * dedicated thread, but parallel test forks each see their own value.
 */
internal object CollectedAccessibilityElements {
  private val current = ThreadLocal<Stashed?>()

  internal data class Stashed(
    val elements: List<AccessibilityElement>,
    /** Pre-scale render width. [AccessibilityElement.displayBounds] are in this coordinate space. */
    val nativeWidth: Int,
    /** Pre-scale render height. */
    val nativeHeight: Int
  )

  fun stash(elements: Collection<AccessibilityElement>, nativeWidth: Int, nativeHeight: Int) {
    current.set(Stashed(elements.toList(), nativeWidth, nativeHeight))
  }

  fun consume(): Stashed? {
    val value = current.get()
    current.remove()
    return value
  }
}
