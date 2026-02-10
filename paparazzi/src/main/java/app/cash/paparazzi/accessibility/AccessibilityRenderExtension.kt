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

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManagerImpl
import android.widget.FrameLayout
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
  private val accessibilityElementCollector: AccessibilityElementCollector
  private val onHierarchyStringGenerated: (String) -> Unit
  private var collectedElements = emptySet<AccessibilityElement>()

  public constructor() : this(AccessibilityElementCollector(), {})

  internal constructor(
    accessibilityElementCollector: AccessibilityElementCollector,
    onHierarchyStringGenerated: (String) -> Unit
  ) {
    this.accessibilityElementCollector = accessibilityElementCollector
    this.onHierarchyStringGenerated = onHierarchyStringGenerated
  }

  override fun renderView(contentView: View): View {
    collectedElements = emptySet()

    // WindowManager needed to access accessibility elements for views that draw to other windows.
    val windowManager = contentView.context.getSystemService(WindowManager::class.java)

    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

      addView(contentView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(overlayDetailsView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDrawable = AccessibilityOverlayDrawable()
      viewTreeObserver.addOnGlobalLayoutListener {
        val rootView = contentView.findRootView()
        rootView.foreground = overlayDrawable

        // The root of the view hierarchy is rendered at full width.
        // We need to restrict it when taking accessibility snapshots.
        val windowManagerRootView = (windowManager as WindowManagerImpl).currentRootView
        if (windowManagerRootView != null) {
          windowManagerRootView.layoutParams =
            FrameLayout.LayoutParams(contentView.measuredWidth, MATCH_PARENT, Gravity.START)
        }

        OneShotPreDrawListener.add(this@apply) {
          val elements = accessibilityElementCollector.collect(
            rootView = this@apply,
            windowManagerRootView = windowManagerRootView
          )
          collectedElements = elements
          overlayDrawable.updateElements(elements)
          overlayDetailsView.updateElements(elements)
        }
      }
    }
  }

  override fun onSnapshotRunCompleted() {
    val hierarchyString = accessibilityElementCollector.toHierarchyString(collectedElements)
    onHierarchyStringGenerated(hierarchyString)
    collectedElements = emptySet()
  }
}

private fun View.findRootView(): View {
  var parent = parent
  while (parent != null) {
    if (parent is ComposeViewAdapter) {
      return parent
    }
    parent = parent.parent
  }
  throw IllegalArgumentException("View hierarchy does not contain a ComposeViewAdapter")
}
