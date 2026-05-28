package app.cash.paparazzi.internal

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.WindowManagerGlobal
import android.widget.FrameLayout
import app.cash.paparazzi.findPopupRootView

/**
 * Ported from: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/ComposeViewAdapter.kt?q=ComposeViewAdapter
 *
 * A wrapper layout for compose-based layouts which allows [android.view.WindowManagerImpl] to find
 * a composable root
 */
internal class ComposeViewAdapter(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs) {
  init {
    /**
     * Needed as [android.view.WindowManagerImpl] uses the view root background color to set the WindowManagerImpl view's background color.
     * If we set this as transparent, the WindowManagerImpl view will be transparent as well and correctly renders the window above content.
     */
    setBackgroundColor(Color.TRANSPARENT)
  }

  internal val requiresPreRenderMeasure: Boolean
    get() = layoutParams?.let { it.width == WRAP_CONTENT || it.height == WRAP_CONTENT } == true

  internal fun sizeZeroContentFromPopupRoot(contentView: View) {
    if (contentView.measuredWidth != 0 && contentView.measuredHeight != 0) {
      return
    }

    val overlayWindow = WindowManagerGlobal.getInstance()
      .findPopupRootView(this)
      ?.takeIf { it.width > 0 && it.height > 0 }
      ?: return

    contentView.layoutParams = LayoutParams(
      overlayWindow.width,
      overlayWindow.height
    )

    val overlayLayoutParams = overlayWindow.layoutParams
    if (overlayLayoutParams is WindowManager.LayoutParams) {
      overlayLayoutParams.gravity = Gravity.START or Gravity.TOP
      overlayLayoutParams.x = 0
      overlayLayoutParams.y = 0
      WindowManagerGlobal.getInstance().updateViewLayout(overlayWindow, overlayLayoutParams)
    }
  }
}
