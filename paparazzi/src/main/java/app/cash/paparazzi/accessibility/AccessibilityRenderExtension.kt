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

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.widget.LinearLayout
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.internal.ComposeViewAdapter
import com.android.internal.view.OneShotPreDrawListener

/**
 * A [RenderExtension] that overlays accessibility property information on top of the rendered view.
 *
 * See [Paparazzi's accessibility documentation](https://cashapp.github.io/paparazzi/accessibility/) for usage
 * information and interpretation tips.
 */
public class AccessibilityRenderExtension : RenderExtension {
  private val accessibilityElementCollector = AccessibilityElementCollector()

  override fun renderView(contentView: View): View {
    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

      addView(contentView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(overlayDetailsView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDrawables = mutableMapOf<Int, AccessibilityOverlayDrawable>()
      viewTreeObserver.addOnGlobalLayoutListener {
        val accessibleWindowRoots = WindowManagerGlobal.getInstance().windowViews.reversed().associate {
          val id = it.hashCode()
          val coreView = it.findComposeViewAdapterChild()
          overlayDrawables.getOrPut(id) {
            AccessibilityOverlayDrawable()
          }.apply {
            coreView.foreground = this
          }
          id to coreView
        }

        OneShotPreDrawListener.add(this@apply) {
          val totalElements = mutableSetOf<AccessibilityElement>()
          accessibleWindowRoots.forEach { (id, view) ->
            val elements = accessibilityElementCollector.collect(
              rootView = view
            )

            overlayDrawables[id]?.updateElements(elements)
            totalElements += elements
          }

          overlayDetailsView.updateElements(totalElements)
        }
      }
    }
  }
}

private fun View.findComposeViewAdapterChild(): View {
  if (this is ComposeViewAdapter) return this

  if (this is ViewGroup) {
    (0 until childCount).forEach { index ->
      val result = getChildAt(index).findComposeViewAdapterChild()
      if (result is ComposeViewAdapter) return result
    }
  }

  return this
}
