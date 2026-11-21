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
      displayBounds = Rect(-5, -7, 10, 10),
      mainAccessibilityText = "First"
    )
    val second = AccessibilityElement(
      id = "second",
      displayBounds = Rect(0, 10, 10, 20),
      mainAccessibilityText = "Second"
    )
    val orderedElements = collector.withTraversalNeighbors(linkedSetOf(first, second))

    val hierarchy = serializer.toHierarchyString(
      AccessibilityFrame(orderedElements.toList(), width = 100, height = 200)
    )

    assertThat(hierarchy).isEqualTo(
      """
      {
        "renderWidth": 100,
        "renderHeight": 200,
        "imageWidth": 100,
        "imageHeight": 200,
        "elements": [
          {
            "id": "first",
            "beforeElementId": null,
            "afterElementId": "second",
            "bounds": {
              "left": -5,
              "top": -7,
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
      }
      """.trimIndent()
    )
  }

  @Test
  fun `empty hierarchy retains canvas and image dimensions`() {
    val hierarchy = serializer.toHierarchyString(
      AccessibilityFrame(emptyList(), width = 1001, height = 2003, imageWidth = 500, imageHeight = 1000)
    )

    assertThat(hierarchy).isEqualTo(
      """
      {
        "renderWidth": 1001,
        "renderHeight": 2003,
        "imageWidth": 500,
        "imageHeight": 1000,
        "elements": []
      }
      """.trimIndent()
    )
  }
}
