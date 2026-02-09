package app.cash.paparazzi.accessibility

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.RenderExtension
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class AccessibilityElementCollectorSnapshotTest {
  private var collectedElements: List<AccessibilityElement> = emptyList()
  private val collectingRenderExtension = CollectingAccessibilityRenderExtension { elements ->
    collectedElements = elements
  }

  @get:Rule
  val paparazzi = Paparazzi(
    renderExtensions = setOf(collectingRenderExtension)
  )

  @Test
  fun `collect orders view traversal elements during snapshot processing`() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL

      val thirdTextView = TextView(context).apply {
        id = View.generateViewId()
        text = "Third"
      }

      val firstTextView = TextView(context).apply {
        id = View.generateViewId()
        text = "First"
        accessibilityTraversalBefore = thirdTextView.id
      }

      val secondTextView = TextView(context).apply {
        id = View.generateViewId()
        text = "Second"
        accessibilityTraversalBefore = thirdTextView.id
      }

      addView(thirdTextView)
      addView(firstTextView)
      addView(secondTextView)
    }

    paparazzi.snapshot(view)

    val orderedElements = collectedElements
      .filter { it.mainAccessibilityText in setOf("First", "Second", "Third") }

    assertThat(orderedElements).hasSize(3)
    assertThat(orderedElements.map { it.mainAccessibilityText })
      .containsExactly("First", "Second", "Third")
      .inOrder()

    val firstElement = orderedElements[0]
    val secondElement = orderedElements[1]
    val thirdElement = orderedElements[2]

    assertThat(firstElement.beforeElementId).isNull()
    assertThat(firstElement.afterElementId).isEqualTo(secondElement.id)
    assertThat(secondElement.beforeElementId).isEqualTo(firstElement.id)
    assertThat(secondElement.afterElementId).isEqualTo(thirdElement.id)
    assertThat(thirdElement.beforeElementId).isEqualTo(secondElement.id)
    assertThat(thirdElement.afterElementId).isNull()
  }
}

private class CollectingAccessibilityRenderExtension(
  private val onElementsCollected: (List<AccessibilityElement>) -> Unit
) : RenderExtension {
  private val collector = AccessibilityElementCollector()

  override fun renderView(contentView: View): View =
    FrameLayout(contentView.context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      addView(contentView)

      viewTreeObserver.addOnPreDrawListener(object : OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          viewTreeObserver.removeOnPreDrawListener(this)
          onElementsCollected(
            collector.collect(
              rootView = this@apply,
              windowManagerRootView = null
            ).toList()
          )
          return true
        }
      })
    }
}
