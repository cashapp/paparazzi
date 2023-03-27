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

import AccessibilityRowView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.accessibility.RenderSettings.DEFAULT_DESCRIPTION_BACKGROUND_COLOR
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

      val accessibilityRowViews = mutableListOf<AccessibilityRowView>()
      val accessibilityLegend = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(DEFAULT_DESCRIPTION_BACKGROUND_COLOR.toColorInt())

        accessibilityState.elements.forEach { _ ->
          accessibilityRowViews += AccessibilityRowView(context).also { rowView ->
            addView(rowView)
          }
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
              color = it.color,
              bounds = it.displayBounds
            )
          }
        )

        require(accessibilityState.elements.size == accessibilityRowViews.size) {
          "Accessibility state has changed since accessibility state was captured in measureView() - " +
            "expected ${accessibilityState.elements.size} elements, but found ${accessibilityRowViews.size} elements."
        }
        accessibilityState.elements.forEachIndexed { index, element ->
          accessibilityRowViews[index].updateForElement(element)
        }

        true
      }
    }
  }
}
