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

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.RenderExtension
import com.android.internal.view.OneShotPreDrawListener

class AccessibilityRenderExtension : RenderExtension {
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
        addView(
          contentView,
          FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        )
      }

      val contentLayoutParams = contentView.layoutParams ?: generateLayoutParams(null)
      addView(
        overlay,
        LinearLayout.LayoutParams(
          contentLayoutParams.width,
          contentLayoutParams.height,
          1f
        )
      )

      val accessibilityOverlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(
        accessibilityOverlayDetailsView,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          1f
        )
      )

      doOnPreDraw {
        val accessibilityState = contentView.accessibilityState()
        accessibilityOverlayDetailsView.addElements(accessibilityState.elements)
        overlay.addElements(
          accessibilityState.elements.map {
            AccessibilityOverlayView.AccessibilityElement(it.color, it.displayBounds)
          }
        )
        true
      }
    }
  }
}

/**
 * Taken from AndroidX.core.ktx
 */
private inline fun View.doOnPreDraw(
  crossinline action: (view: View) -> Unit
): OneShotPreDrawListener = OneShotPreDrawListener.add(this) { action(this) }
