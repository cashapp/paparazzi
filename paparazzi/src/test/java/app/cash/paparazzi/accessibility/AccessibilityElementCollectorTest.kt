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

  @Test
  fun `withTraversalNeighbors assigns unique ids to elements with duplicate labels`() {
    val first = AccessibilityElement(
      id = "button",
      displayBounds = Rect(0, 0, 10, 10),
      mainAccessibilityText = "OK"
    )
    val second = AccessibilityElement(
      id = "button",
      displayBounds = Rect(0, 10, 10, 20),
      mainAccessibilityText = "OK"
    )

    val withNeighbors = collector
      .withTraversalNeighbors(listOf(first, second))
      .toList()

    assertThat(withNeighbors.map { it.id }).containsExactly("button#1", "button#2").inOrder()
    assertThat(withNeighbors[0].afterElementId).isEqualTo("button#2")
    assertThat(withNeighbors[1].beforeElementId).isEqualTo("button#1")
  }
}
