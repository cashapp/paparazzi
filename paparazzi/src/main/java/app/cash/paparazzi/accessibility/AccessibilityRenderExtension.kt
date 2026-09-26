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
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL
import android.view.WindowManagerGlobal
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import app.cash.paparazzi.RenderExtension
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

  /**
   * The overlay for the elements of a sub-window - a dialog, popup or sheet - and the window it is
   * painted into. Both persist across renders: the window has to be added before the first one, and
   * the drawable is attached to it. Lazy because its [android.graphics.Paint]s need layoutlib's
   * native library, which is only loaded once a test runs.
   */
  private val windowOverlayDrawable by lazy { AccessibilityOverlayDrawable() }
  private var windowOverlayWindow: FrameLayout? = null

  override fun renderView(contentView: View): View {
    // Only a sub-window's own elements need a window of their own. layoutlib composites windows in
    // the order `getWindowViews()` returns them - window type ascending - so a window typed above
    // every sub-window is drawn above every sub-window, which is where those highlights belong. The
    // base window's elements need no window at all; see `foreground` below.
    val windowOverlayRoot = installOverlayWindow(contentView.context)

    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

      // The base window's own foreground is drawn after its content and before layoutlib
      // composites any sub-window, which is exactly where a base element's highlight belongs.
      val baseOverlayDrawable = AccessibilityOverlayDrawable()
      foreground = baseOverlayDrawable

      addView(contentView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(overlayDetailsView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      var requestedSettledLayoutPass = false
      viewTreeObserver.addOnGlobalLayoutListener {
        // The root of the view hierarchy is rendered at full width.
        // We need to restrict it when taking accessibility snapshots.
        val views = contentView.context.getWindowViews()
        val baseRoot = contentView.rootView
        val windowManagerRootView = views.lastOrNull {
          it !== baseRoot && it !== windowOverlayRoot && it.isVisible
        } as ViewGroup?

        if (windowManagerRootView != null) {
          val wmLp = windowManagerRootView.layoutParams as WindowManager.LayoutParams
          wmLp.width = contentView.measuredWidth
          wmLp.gravity = Gravity.START or (wmLp.gravity and Gravity.VERTICAL_GRAVITY_MASK)
          val windowManager = contentView.context.getSystemService(WindowManager::class.java)
          windowManager.updateViewLayout(windowManagerRootView, wmLp)
        }

        if (!requestedSettledLayoutPass) {
          requestedSettledLayoutPass = true
          // Since 36ccd15b44 a dialog or popup is a real window with its own ViewRootImpl, laid
          // out after the base window and only on its own traversal, so its frame is still
          // unresolved while this first pass collects bounds. Ask for one more layout pass; the
          // collection it dispatches sees the settled geometry.
          requestLayout()
        }

        OneShotPreDrawListener.add(this@apply) {
          val windowElements = windowManagerRootView?.let {
            accessibilityElementCollector.collect(rootView = it, windowManagerRootView = null)
          } ?: emptySet()
          val baseElements = accessibilityElementCollector.collect(
            rootView = this@apply,
            windowManagerRootView = null
          )
          windowOverlayDrawable.updateElements(windowElements)
          baseOverlayDrawable.updateElements(baseElements)
          // The legend lists every element, in the order the two-root collection produced them.
          overlayDetailsView.updateElements(windowElements + baseElements)
        }
      }
    }
  }

  /**
   * Returns an overlay window, adding it if the current render session does not have it yet.
   *
   * It has to be added before the first render: the renderer captures the window list up front, so
   * a window added during a layout pass would be missing from the frame that pass belongs to.
   */
  private fun installOverlayWindow(context: Context): FrameLayout {
    windowOverlayWindow?.let { if (it.isAttachedToWindow) return it }

    val window = FrameLayout(context).apply {
      // A foreground both opts a ViewGroup back into drawing itself and is painted after its
      // children, which is all the overlay needs; the window has no children of its own.
      foreground = windowOverlayDrawable
    }
    val layoutParams = WindowManager.LayoutParams(
      MATCH_PARENT,
      MATCH_PARENT,
      TYPE_APPLICATION_ABOVE_SUB_PANEL,
      FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      // Full-canvas and at the origin, so the overlay shares the coordinate space the element
      // bounds are collected in and nothing is clipped away.
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
    }
    context.getSystemService(WindowManager::class.java).addView(window, layoutParams)
    windowOverlayWindow = window
    return window
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
