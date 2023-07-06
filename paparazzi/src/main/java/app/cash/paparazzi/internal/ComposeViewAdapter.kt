package app.cash.paparazzi.internal

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Ported from: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-tooling/src/androidMain/kotlin/androidx/compose/ui/tooling/ComposeViewAdapter.kt?q=ComposeViewAdapter
 *
 * A wrapper layout for compose-based layouts which allows [android.view.WindowManagerImpl] to find
 * a composable root
 */
class ComposeViewAdapter(
  context: Context,
  attrs: AttributeSet
) : FrameLayout(context, attrs)
