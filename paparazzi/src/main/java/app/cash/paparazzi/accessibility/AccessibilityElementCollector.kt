/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi.accessibility

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.isVisible

/**
 * Collects accessibility metadata from a rendered Paparazzi view hierarchy.
 *
 * This collector traverses both classic Android views and Compose semantics nodes, applies
 * Paparazzi's accessibility ordering rules, and returns the resulting [AccessibilityElement] set
 * used by accessibility-specific tooling (for example, overlay rendering and future manifest
 * generation).
 */
internal class AccessibilityElementCollector {
  /**
   * Collects accessibility elements from the provided render roots.
   *
   * [windowManagerRootView] is optional and is used for UI that renders in separate windows
   * (dialogs, popups, etc.). [rootView] is always traversed.
   */
  fun collect(rootView: View, windowManagerRootView: View?): Set<AccessibilityElement> {
    val orderedElements = linkedSetOf<AccessibilityElement>().apply {
      windowManagerRootView?.processAccessibleChildren { add(it) }
      rootView.processAccessibleChildren { add(it) }
    }

    return withTraversalNeighbors(orderedElements)
  }

  internal fun withTraversalNeighbors(elements: Collection<AccessibilityElement>): Set<AccessibilityElement> {
    val orderedElements = elements.toList()

    return orderedElements
      .mapIndexed { index, element ->
        element.copy(
          beforeElementId = orderedElements.getOrNull(index - 1)?.id,
          afterElementId = orderedElements.getOrNull(index + 1)?.id
        )
      }
      .toCollection(linkedSetOf())
  }

  private fun View.processAccessibleChildren(processElement: (AccessibilityElement) -> Unit) {
    val bounds = Rect().also(::getBoundsOnScreen)

    if (isImportantForAccessibility && isVisible) {
      AccessibilityElement.fromView(this, bounds)?.let(processElement)
    }

    if (this is AbstractComposeView && isVisible) {
      // ComposeView creates a child view `AndroidComposeView` for view root for test.
      val viewRoot = getChildAt(0) as ViewRootForTest
      val unmergedNodes = viewRoot.semanticsOwner.getAllSemanticsNodes(false)

      // SemanticsNode.boundsInScreen isn't reported correctly for nodes so locationOnScreen used to correctly calculate displayBounds.
      val locationOnScreen = arrayOf(bounds.left, bounds.top).toIntArray()
      locationOnScreen[0] += paddingLeft
      locationOnScreen[1] += paddingTop
      val orderedSemanticsNodes = viewRoot.semanticsOwner.rootSemanticsNode.orderSemanticsNodeGroup()
      orderedSemanticsNodes.forEach {
        it.processAccessibleChildren(
          processElement = processElement,
          locationOnScreen = locationOnScreen,
          viewBounds = bounds,
          unmergedNodes = unmergedNodes
        )
      }
    }

    if (this is ViewGroup) {
      val orderedViews = orderViewGroup()
      orderedViews.forEach {
        it.processAccessibleChildren(processElement)
      }
    }
  }

  private fun SemanticsNode.orderSemanticsNodeGroup(): List<SemanticsNode> {
    val topLevelNodes = mutableListOf<SemanticsNodeTraversalEntry>()

    val currentNodeTraversalIndex = config.getOrNull(SemanticsProperties.TraversalIndex)
    if (currentNodeTraversalIndex != null) {
      topLevelNodes.add(SemanticsNodeTraversalEntry(currentNodeTraversalIndex, listOf(this)))
    } else {
      topLevelNodes.add(SemanticsNodeTraversalEntry(nodes = listOf(this)))
    }

    for (child in children) {
      val descendants = child.orderSemanticsNodeGroup()
      if (child.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true) {
        // Treat group as one item, recurse within it
        val childTraversalIndex = child.config.getOrNull(SemanticsProperties.TraversalIndex)
        topLevelNodes.add(
          if (childTraversalIndex != null) {
            SemanticsNodeTraversalEntry(childTraversalIndex, descendants)
          } else {
            SemanticsNodeTraversalEntry(nodes = descendants)
          }
        )
      } else {
        // Leaf or regular node
        for (node in descendants) {
          val nodeTraversalIndex = child.config.getOrNull(SemanticsProperties.TraversalIndex)
          topLevelNodes.add(
            if (nodeTraversalIndex != null) {
              SemanticsNodeTraversalEntry(nodeTraversalIndex, descendants)
            } else {
              SemanticsNodeTraversalEntry(nodes = listOf(node))
            }
          )
        }
      }
    }

    return topLevelNodes
      .sortedWith(
        compareBy(
          { it.traversalIndex },
          { it.orderIndex } // Order of discovery = fallback layout order
        )
      )
      .flatMap { it.nodes }
  }

  private fun ViewGroup.orderViewGroup(): List<View> {
    if (childCount == 0) return emptyList()

    // Build a map of view ID to view for quick lookups
    val viewsById = mutableMapOf<Int, View>()
    val childViews = (0 until childCount).map { getChildAt(it) }

    childViews.forEach { child ->
      if (child.id != View.NO_ID) {
        viewsById[child.id] = child
      }
    }

    // Build the dependency graph based on accessibilityTraversalBefore/After
    // TraversalConstraints: before = Views that should come before this view, after = Views that should come after this view
    data class TraversalConstraints(
      val before: MutableList<View> = mutableListOf(),
      val after: MutableList<View> = mutableListOf()
    )

    val constraints = mutableMapOf<View, TraversalConstraints>()
    childViews.forEach { child ->
      constraints[child] = TraversalConstraints()
    }

    // Process accessibilityTraversalBefore and accessibilityTraversalAfter
    // These APIs were added in API 22 (Lollipop MR1)
    childViews.forEach { child ->
      // accessibilityTraversalBefore: this view comes BEFORE the referenced view
      val traversalBeforeId = child.accessibilityTraversalBefore
      if (traversalBeforeId != View.NO_ID) {
        val beforeView = viewsById[traversalBeforeId]
        if (beforeView != null && beforeView.parent == this) {
          // child -> beforeView (child should come before beforeView)
          constraints[beforeView]?.before?.add(child)
          constraints[child]?.after?.add(beforeView)
        }
      }

      // accessibilityTraversalAfter: this view comes AFTER the referenced view
      val traversalAfterId = child.accessibilityTraversalAfter
      if (traversalAfterId != View.NO_ID) {
        val afterView = viewsById[traversalAfterId]
        if (afterView != null && afterView.parent == this) {
          // afterView -> child (child should come after afterView)
          constraints[child]?.before?.add(afterView)
          constraints[afterView]?.after?.add(child)
        }
      }
    }

    // Perform topological sort with fallback to layout order
    val result = mutableListOf<View>()
    val visited = mutableSetOf<View>()
    val visiting = mutableSetOf<View>()

    fun visit(view: View): Boolean {
      if (visited.contains(view)) return true
      if (visiting.contains(view)) {
        // Cycle detected, use layout order
        return false
      }

      visiting.add(view)

      val viewConstraints = constraints[view] ?: TraversalConstraints()
      // Visit all views that should come before this one
      for (beforeView in viewConstraints.before) {
        if (!visit(beforeView)) {
          // Cycle detected, abort topological sort
          visiting.remove(view)
          return false
        }
      }

      visiting.remove(view)
      visited.add(view)
      result.add(view)
      return true
    }

    // Try topological sort with layout order as fallback
    var hasCycle = false

    for (view in childViews) {
      if (!visited.contains(view)) {
        if (!visit(view)) {
          hasCycle = true
          break
        }
      }
    }

    // If we detected a cycle or constraints create conflicts, fall back to layout order
    return if (hasCycle || result.size != childViews.size) {
      childViews
    } else {
      result
    }
  }

  private fun SemanticsNode.processAccessibleChildren(
    processElement: (AccessibilityElement) -> Unit,
    locationOnScreen: IntArray,
    viewBounds: Rect,
    unmergedNodes: List<SemanticsNode>?
  ) {
    // SemanticsNode.boundsInScreen isn't reported correctly for nodes so boundsInRoot + locationOnScreen used to correctly calculate displayBounds.
    val displayBounds = with(boundsInRoot) {
      Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()).run {
        offset(locationOnScreen[0], locationOnScreen[1])
        Rect(left, top, right.coerceIn(0, viewBounds.right), bottom.coerceIn(0, viewBounds.bottom))
      }
    }

    AccessibilityElement.fromSemanticsNode(
      node = this,
      displayBounds = displayBounds,
      unmergedNodes = unmergedNodes
    )?.let(processElement)
  }

  private companion object {
    data class SemanticsNodeTraversalEntry(
      val traversalIndex: Float = 0f,
      val nodes: List<SemanticsNode>, // May be 1 node or a whole traversal group
      val orderIndex: Int = nextOrderIndex()
    )

    private var orderIndexCounter = 0
    fun nextOrderIndex(): Int {
      return orderIndexCounter++
    }
  }
}
