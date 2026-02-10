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
  fun `toHierarchyString serializes all elements in traversal order`() {
    val first = AccessibilityElement(
      id = "first",
      displayBounds = Rect(0, 0, 10, 10),
      mainAccessibilityText = "First"
    )
    val second = AccessibilityElement(
      id = "second",
      displayBounds = Rect(0, 10, 10, 20),
      mainAccessibilityText = "Second"
    )
    val orderedElements = collector.withTraversalNeighbors(linkedSetOf(first, second))

    val hierarchy = collector.toHierarchyString(orderedElements)

    assertThat(hierarchy).isEqualTo(
      """
      [
        {
          "id": "first",
          "beforeElementId": null,
          "afterElementId": "second",
          "bounds": {
            "left": 0,
            "top": 0,
            "right": 10,
            "bottom": 10
          },
          "legendText": "First"
        },
        {
          "id": "second",
          "beforeElementId": "first",
          "afterElementId": null,
          "bounds": {
            "left": 0,
            "top": 10,
            "right": 10,
            "bottom": 20
          },
          "legendText": "Second"
        }
      ]
      """.trimIndent()
    )
  }

  @Test
  fun `toHierarchyString returns empty JSON array for empty hierarchy`() {
    val hierarchy = collector.toHierarchyString(emptyList())

    assertThat(hierarchy).isEqualTo("[]")
  }
}
