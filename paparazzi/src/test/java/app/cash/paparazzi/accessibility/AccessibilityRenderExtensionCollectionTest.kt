package app.cash.paparazzi.accessibility

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AccessibilityRenderExtensionCollectionTest {
  @get:Rule
  val paparazzi = Paparazzi(renderExtensions = setOf(AccessibilityRenderExtension()))

  @Test
  fun `collects once per pre-draw pass however many layout passes ran`() {
    var nodeInfoInitializations = 0
    val counted = TextView(paparazzi.context).apply {
      text = "Counted"
      accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
          nodeInfoInitializations++
          super.onInitializeAccessibilityNodeInfo(host, info)
        }
      }
    }
    var layoutPasses = 0
    var preDrawPasses = 0
    val view = FrameLayout(paparazzi.context).apply {
      addView(counted)
      viewTreeObserver.addOnGlobalLayoutListener { layoutPasses++ }
      viewTreeObserver.addOnPreDrawListener {
        // A second layout pass used to register a second collecting pre-draw listener.
        if (++preDrawPasses == 1) requestLayout()
        true
      }
    }

    paparazzi.snapshot(view)
    val renderInitializations = nodeInfoInitializations
    AccessibilityElement.fromView(counted, Rect())
    val initializationsPerCollection = nodeInfoInitializations - renderInitializations

    assertThat(layoutPasses).isAtLeast(2)
    assertThat(renderInitializations).isEqualTo(initializationsPerCollection * preDrawPasses)
  }
}
