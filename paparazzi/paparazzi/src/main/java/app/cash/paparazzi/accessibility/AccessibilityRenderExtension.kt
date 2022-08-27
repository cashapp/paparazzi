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

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_DESCRIPTION_BACKGROUND_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_RECT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_COLOR
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_TEXT_SIZE
import app.cash.paparazzi.accessibility.RenderSettings.getColor
import app.cash.paparazzi.accessibility.RenderSettings.toColorInt

class AccessibilityRenderExtension : RenderExtension {
  private lateinit var accessibilityState: AccessibilityState

  override val requiresMeasure: Boolean = true

  override fun measureView(contentView: View) {
    // Fetch accessibility state to render legend in renderView()
    accessibilityState = contentView.accessibilityState()
  }

  override fun renderView(
    contentView: View
  ): View {
    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )

      val overlay = AccessibilityOverlayView(context).apply {
        val contentLayoutParams = contentView.layoutParams ?: generateLayoutParams(null)
        this.addView(
          contentView,
          FrameLayout.LayoutParams(
            contentLayoutParams.width,
            contentLayoutParams.height
          )
        )
      }

      addView(
        overlay,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          1f
        )
      )

      val accessibilityLegend = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(DEFAULT_DESCRIPTION_BACKGROUND_COLOR.toColorInt())

        accessibilityState.elements.forEach {
          addView(buildAccessibilityRow(context, it))
        }
      }
      addView(
        accessibilityLegend,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          1f
        )
      )

      // Since Accessibility tests may use 2x rendering width, our accessibility state has incorrect view bounds.
      // If we process the state after layout and before rendering, we get the correct view bounds.
      contentView.viewTreeObserver.addOnPreDrawListener {
        val accessibilityState = contentView.accessibilityState()
        overlay.addElements(
          accessibilityState.elements.map {
            AccessibilityOverlayView.AccessibilityElement(
              color = getColor(it.id),
              bounds = it.displayBounds
            )
          }
        )
        true
      }
    }
  }

  private fun buildAccessibilityRow(
    context: Context,
    element: AccessibilityState.Element
  ): View {
    val color = getColor(element.id).toColorInt()
    val margin = context.dip(8)
    val innerMargin = context.dip(4)

    return LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      setPaddingRelative(margin, innerMargin, margin, innerMargin)

      addView(
        View(context).apply {
          layoutParams = ViewGroup.LayoutParams(
            context.dip(DEFAULT_RECT_SIZE),
            context.dip(
              DEFAULT_RECT_SIZE
            )
          )
          background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color, color)
          ).apply {
            cornerRadius = context.dip(DEFAULT_RECT_SIZE / 4f)
          }
          setPaddingRelative(innerMargin, innerMargin, innerMargin, innerMargin)
        }
      )
      addView(
        TextView(context).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
          )
          text = element.renderString()
          textSize = DEFAULT_TEXT_SIZE
          setTextColor(DEFAULT_TEXT_COLOR.toColorInt())
          setPaddingRelative(innerMargin, 0, innerMargin, 0)
        }
      )
    }
  }
}

private fun Context.dip(value: Float): Float =
  TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value,
    resources.displayMetrics
  )

private fun Context.dip(value: Int): Int = dip(value.toFloat()).toInt()
