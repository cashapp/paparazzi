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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.FrameLayout
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

internal class AccessibilityOverlayView(context: Context) : FrameLayout(context) {
  private val accessibilityElements = mutableListOf<AccessibilityElement>()
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val strokePaint = Paint().apply {
    strokeWidth = 2f
    style = Paint.Style.STROKE
  }

  init {
    // Required for onDraw to be called
    setWillNotDraw(false)
  }

  fun addElements(elements: Collection<AccessibilityElement>) {
    accessibilityElements.addAll(elements)
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    accessibilityElements.forEach {
      paint.color = it.color.toColorInt()
      canvas.drawRect(it.displayBounds, paint)

      strokePaint.color = it.color.toColorInt()
      strokePaint.alpha = RenderSettings.DEFAULT_RENDER_ALPHA * 2
      canvas.drawRect(it.displayBounds, strokePaint)
    }
  }
}
