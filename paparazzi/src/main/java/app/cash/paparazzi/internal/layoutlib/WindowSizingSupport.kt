package app.cash.paparazzi.internal.layoutlib

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewRootImpl
import android.view.ViewRootImpl_Accessor
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.view.WindowRelayoutResult
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.SessionParams.RenderingMode.SizeAction

/**
 * The out-of-line half of the canvas-sizing patch. Called from bytecode that
 * [LayoutlibPatch] inlines into layoutlib; see [RenderSizingAdvice].
 */
internal object WindowSizingSupport {
  /**
   * Finishes `RenderSessionImpl.measureLayout` for the shrinking rendering modes, which size the
   * canvas from `mContentRoot.getChildAt(0)` alone and so measure 0x0 whenever the content lives in
   * a window of its own - a dialog, a popup, a bottom sheet.
   *
   * A sub-window's natural size cannot be read without traversing it: measuring its `DecorView`
   * directly answers 0 under `UNSPECIFIED`/`AT_MOST` and the full display under `EXACTLY`, because
   * the spec a window's content is measured against is derived by `ViewRootImpl` from the window's
   * layout params, and for Compose content the composition itself only runs inside a traversal.
   * So each sub-window is traversed here, and `renderAndBuildResult`'s own per-window traversal -
   * which runs a few instructions later, against the canvas this returns - is what positions it.
   *
   * Returns the canvas size packed as `width << 32 | height`.
   */
  @JvmStatic
  fun sizeCanvasForWindows(
    params: SessionParams,
    baseWindow: ViewGroup?,
    measuredWidth: Int,
    measuredHeight: Int
  ): Long {
    var width = measuredWidth
    var height = measuredHeight

    val horizontal = params.renderingMode.horizAction
    val vertical = params.renderingMode.vertAction
    if (horizontal == SizeAction.SHRINK || vertical == SizeAction.SHRINK) {
      for (window in sessionWindows(baseWindow)) {
        val viewRoot = window.parent as? ViewRootImpl ?: continue
        ViewRootImpl_Accessor.performTraversals(viewRoot)
        if (horizontal == SizeAction.SHRINK) width = maxOf(width, window.measuredWidth)
        if (vertical == SizeAction.SHRINK) height = maxOf(height, window.measuredHeight)
      }
    }

    // A shrunk canvas can legitimately reach zero - content that renders nothing, or a session
    // whose only content is in a window this pass could not measure - and `renderAndBuildResult`
    // hands the result straight to `new BufferedImage`, which rejects a zero dimension with
    // `IllegalArgumentException: Width (0) and height (0) cannot be <= 0`. Keep one pixel so the
    // caller gets an empty snapshot rather than a crash.
    width = maxOf(width, 1)
    height = maxOf(height, 1)

    RenderSizingState.canvasWidth = width
    RenderSizingState.canvasHeight = height
    return (width.toLong() shl 32) or (height.toLong() and 0xffffffffL)
  }

  /**
   * Positions a window inside the canvas rather than inside the display.
   *
   * `BridgeWindowSession.relayout` runs `Gravity.apply` against
   * `RenderAction.getCurrentContext().getMetrics()`, so a window is placed as if the surface were
   * the whole device. `LayoutlibRenderer.draw` then composites each sub-window at
   * `mWinFrame.left/top`, so once `measureLayout` has shrunk the canvas the window lands outside
   * it - a centered 880x652 dialog is translated to (100, 844) on an 880x652 surface.
   */
  @JvmStatic
  fun positionWindowInCanvas(
    attrs: WindowManager.LayoutParams?,
    requestedWidth: Int,
    requestedHeight: Int,
    result: WindowRelayoutResult?
  ) {
    val width = RenderSizingState.canvasWidth
    val height = RenderSizingState.canvasHeight
    if (result == null || width <= 0 || height <= 0) return

    var x = 0
    var y = 0
    if (attrs != null) {
      val gravity = if (attrs.gravity == 0) Gravity.START or Gravity.TOP else attrs.gravity
      val out = Rect()
      Gravity.apply(
        gravity,
        requestedWidth,
        requestedHeight,
        Rect(0, 0, width, height),
        attrs.x,
        attrs.y,
        out
      )
      x = out.left
      y = out.top
    }
    result.frames.frame.set(x, y, x + requestedWidth, y + requestedHeight)
    result.frames.displayFrame.set(0, 0, width, height)
  }

  private fun sessionWindows(baseWindow: ViewGroup?): List<View> =
    WindowManagerGlobal.getInstance().windowViews
      .filter { it !== baseWindow && it.visibility == View.VISIBLE }
}
