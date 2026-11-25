package app.cash.paparazzi.accessibility

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal class AccessibilityHierarchyJsonSerializer {
  fun toHierarchyString(frame: AccessibilityFrame): String =
    hierarchyJsonAdapter.toJson(
      AccessibilityHierarchyJsonFrame(
        renderWidth = frame.width,
        renderHeight = frame.height,
        imageWidth = frame.imageWidth,
        imageHeight = frame.imageHeight,
        elements = frame.elements.map { it.toHierarchyJsonElement() }
      )
    )

  private fun AccessibilityElement.toHierarchyJsonElement(): AccessibilityHierarchyJsonElement {
    return AccessibilityHierarchyJsonElement(
      id = id,
      beforeElementId = beforeElementId,
      afterElementId = afterElementId,
      bounds = AccessibilityHierarchyBounds(
        left = displayBounds.left,
        top = displayBounds.top,
        right = displayBounds.right,
        bottom = displayBounds.bottom
      ),
      legendText = legendText
    )
  }

  private companion object {
    private val hierarchyJsonAdapter: JsonAdapter<AccessibilityHierarchyJsonFrame> =
      Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(AccessibilityHierarchyJsonFrame::class.java)
        .serializeNulls()
        .indent("  ")

    // Bounds use pixels relative to the top-left of the unscaled render canvas. Consumers map
    // them to the captured image using imageWidth / renderWidth and imageHeight / renderHeight.
    data class AccessibilityHierarchyJsonFrame(
      val renderWidth: Int,
      val renderHeight: Int,
      val imageWidth: Int,
      val imageHeight: Int,
      val elements: List<AccessibilityHierarchyJsonElement>
    )

    data class AccessibilityHierarchyJsonElement(
      val id: String,
      val beforeElementId: String?,
      val afterElementId: String?,
      val bounds: AccessibilityHierarchyBounds,
      val legendText: String
    )

    data class AccessibilityHierarchyBounds(
      val left: Int,
      val top: Int,
      val right: Int,
      val bottom: Int
    )
  }
}
