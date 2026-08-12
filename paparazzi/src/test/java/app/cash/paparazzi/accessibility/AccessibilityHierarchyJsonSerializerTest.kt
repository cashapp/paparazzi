package app.cash.paparazzi.accessibility

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessibilityHierarchyJsonSerializerTest {
  private val collector = AccessibilityElementCollector()
  private val serializer = AccessibilityHierarchyJsonSerializer()

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

    val hierarchy = serializer.toHierarchyString(orderedElements)

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
    val hierarchy = serializer.toHierarchyString(emptyList())

    assertThat(hierarchy).isEqualTo("[]")
  }
}
