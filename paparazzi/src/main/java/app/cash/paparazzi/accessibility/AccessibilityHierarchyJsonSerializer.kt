package app.cash.paparazzi.accessibility

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal class AccessibilityHierarchyJsonSerializer {
  fun toHierarchyString(elements: Collection<AccessibilityElement>): String =
    hierarchyJsonAdapter.toJson(elements.map { it.toHierarchyJsonElement() })

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
    private val hierarchyJsonAdapter: JsonAdapter<List<AccessibilityHierarchyJsonElement>> =
      Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<AccessibilityHierarchyJsonElement>>(
          Types.newParameterizedType(List::class.java, AccessibilityHierarchyJsonElement::class.java)
        )
        .serializeNulls()
        .indent("  ")

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
