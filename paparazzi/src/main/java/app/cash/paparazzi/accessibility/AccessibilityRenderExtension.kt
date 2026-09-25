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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.widget.LinearLayout
import androidx.core.view.isVisible
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.internal.ComposeViewAdapter
import com.android.internal.view.OneShotPreDrawListener
import com.android.layoutlib.bridge.android.BridgeContext

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

      val overlayDrawable = AccessibilityOverlayDrawable()
      viewTreeObserver.addOnGlobalLayoutListener {
        val rootView = contentView.findRootView()
        rootView.foreground = overlayDrawable

        // The root of the view hierarchy is rendered at full width.
        // We need to restrict it when taking accessibility snapshots.
        val views = contentView.context.getWindowViews()
        val baseRoot = contentView.rootView
        val windowManagerRootView = views.lastOrNull { it !== baseRoot && it.isVisible } as ViewGroup?

        if (windowManagerRootView != null) {
          val wmLp = windowManagerRootView.layoutParams as WindowManager.LayoutParams
          wmLp.width = contentView.measuredWidth
          wmLp.gravity = Gravity.START or (wmLp.gravity and Gravity.VERTICAL_GRAVITY_MASK)
          val windowManager = contentView.context.getSystemService(WindowManager::class.java)
          windowManager.updateViewLayout(windowManagerRootView, wmLp)
        }

        OneShotPreDrawListener.add(this@apply) {
          val elements = accessibilityElementCollector.collect(
            rootView = this@apply,
            windowManagerRootView = windowManagerRootView
          )
          overlayDrawable.updateElements(elements)
          overlayDetailsView.updateElements(elements)
        }
      }
    }
  }

  /**
   * The window root views belonging to this render session, sorted by window type, which is the
   * order layoutlib composites them in.
   */
  private fun Context.getWindowViews(): List<View> {
    val session = BridgeContext.getBaseContext(this)
    return WindowManagerGlobal.getInstance()
      .windowViews
      .filter { BridgeContext.getBaseContext(it.context) == session }
      .sortedBy { (it.layoutParams as? WindowManager.LayoutParams)?.type ?: Int.MIN_VALUE }
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
