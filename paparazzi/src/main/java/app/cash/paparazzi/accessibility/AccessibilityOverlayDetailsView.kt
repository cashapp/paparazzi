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
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.core.graphics.withTranslation
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt
import java.lang.Float.max

internal class AccessibilityOverlayDetailsView(context: Context) : FrameLayout(context) {
  private val accessibilityElements = mutableSetOf<AccessibilityElement>()
  private val paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
  }
  private val textPaint = Paint().apply {
    style = Paint.Style.FILL
    color = DEFAULT_TEXT_COLOR.toColorInt()
    textSize = context.dip(RenderSettings.DEFAULT_TEXT_SIZE)
  }
  private val margin = context.dip(8f)
  private val innerMargin = margin / 2f
  private val rectSize = context.dip(RenderSettings.DEFAULT_RECT_SIZE.toFloat())
  private val cornerRadius = rectSize / 4f

  init {
    // Required for onDraw to be called
    setWillNotDraw(false)

    setBackgroundColor(RenderSettings.DEFAULT_DESCRIPTION_BACKGROUND_COLOR.toColorInt())
  }

  fun updateElements(elements: Collection<AccessibilityElement>) {
    accessibilityElements.clear()
    accessibilityElements += elements
    invalidate()
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    var lastYCoord = innerMargin
    val textPaint = TextPaint(textPaint)

    accessibilityElements.forEach {
      paint.color = it.color.toColorInt()
      val badge = RectF(margin, lastYCoord, margin + rectSize, lastYCoord + rectSize)
      canvas.drawRoundRect(badge, cornerRadius, cornerRadius, paint)
      canvas.save()

      val text = it.contentDescription
      val textX = badge.right + innerMargin
      val textY = badge.top
      val textLayoutWidth = (width - textX).toInt()

      val textLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, textPaint, textLayoutWidth)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
      canvas.withTranslation(textX, textY) {
        textLayout.draw(this)
      }

      lastYCoord = max(badge.bottom + margin, textY + textLayout.height.toFloat())
    }
  }

  private fun Context.dip(value: Float): Float =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      value,
      resources.displayMetrics
    )
}
