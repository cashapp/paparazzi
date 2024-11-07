package app.cash.paparazzi.internal

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.FrameLayout

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
}
