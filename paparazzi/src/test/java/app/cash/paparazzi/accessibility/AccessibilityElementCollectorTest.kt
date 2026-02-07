package app.cash.paparazzi.accessibility

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityElementCollectorTest {
  private val collector = AccessibilityElementCollector()

  @Test
  fun `withTraversalNeighbors assigns before and after ids`() {
    val first = AccessibilityElement(
      id = "first",
      displayBounds = Rect(),
      mainAccessibilityText = "First"
    )
    val second = AccessibilityElement(
      id = "second",
      displayBounds = Rect(),
      mainAccessibilityText = "Second"
    )
    val third = AccessibilityElement(
      id = "third",
      displayBounds = Rect(),
      mainAccessibilityText = "Third"
    )

    val withNeighbors = collector
      .withTraversalNeighbors(linkedSetOf(first, second, third))
      .toList()

    assertThat(withNeighbors.map { it.id }).containsExactly("first", "second", "third").inOrder()
    assertThat(withNeighbors[0].beforeElementId).isNull()
    assertThat(withNeighbors[0].afterElementId).isEqualTo("second")
    assertThat(withNeighbors[1].beforeElementId).isEqualTo("first")
    assertThat(withNeighbors[1].afterElementId).isEqualTo("third")
    assertThat(withNeighbors[2].beforeElementId).isEqualTo("second")
    assertThat(withNeighbors[2].afterElementId).isNull()
  }
}
