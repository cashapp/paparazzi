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
import android.widget.AdapterView
import android.widget.Button
import android.widget.Checkable
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.ScrollView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.isVisible
import app.cash.paparazzi.accessibility.AccessibilityElement.Companion.findAllUnmergedNodes

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
    val actualRootView = (rootView as? ViewRootForTest)?.let { (it as? View)?.parent as? View } ?: rootView
    val orderedElements = linkedSetOf<AccessibilityElement>().apply {
      windowManagerRootView?.processAccessibleChildren { add(it) }
      actualRootView.processAccessibleChildren { add(it) }
    }

    return withTraversalNeighbors(orderedElements)
  }

  internal fun withTraversalNeighbors(elements: Collection<AccessibilityElement>): Set<AccessibilityElement> {
    val duplicateIds = elements
      .groupingBy { it.id }
      .eachCount()
      .filterValues { it > 1 }
      .keys
    val usedIds = elements.mapTo(mutableSetOf()) { it.id }
    val nextSuffixById = mutableMapOf<String, Int>()
    val orderedElements = elements.map { element ->
      if (element.id !in duplicateIds) {
        element
      } else {
        var suffix = nextSuffixById.getOrDefault(element.id, 1)
        var uniqueId: String
        do {
          uniqueId = "${element.id}#${suffix++}"
        } while (!usedIds.add(uniqueId))
        nextSuffixById[element.id] = suffix
        element.copy(id = uniqueId)
      }
    }

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
    if (importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return

    val bounds = Rect().also(::getBoundsOnScreen)

    val accessibilityElement = if (isImportantForAccessibility && isVisible) {
      AccessibilityElement.fromView(this, bounds)
    } else {
      null
    }

    if (isMissingAccessibilityDescription()) {
      processElement(
        AccessibilityElement(
          id = "${this::class.simpleName}($MISSING_DESCRIPTION_LABEL)",
          displayBounds = bounds,
          mainAccessibilityText = MISSING_DESCRIPTION_LABEL,
          isMissingDescription = true
        )
      )
    } else if (accessibilityElement != null) {
      processElement(accessibilityElement)
    }

    val composeViewRoot = (this as? AbstractComposeView)?.getChildAt(0) as? ViewRootForTest
      ?: this as? ViewRootForTest
    // Another window can trigger collection before this Compose root is attached. Its merged
    // semantics root is not ready yet; leave it for a subsequent collection after attachment.
    if (composeViewRoot != null && isVisible && composeViewRoot.view.isAttachedToWindow) {
      // ComposeView creates a child view `AndroidComposeView` for view root for test.
      val viewRoot = composeViewRoot
      val unmergedNodes = viewRoot.semanticsOwner.getAllSemanticsNodes(false)

      // SemanticsNode.boundsInRoot is relative to the AndroidComposeView, not to the
      // AbstractComposeView hosting it, so the origin has to come from that same view. Taking it
      // from the host drops the offset between the two, which is zero only when they coincide.
      val locationOnScreen = IntArray(2).also {
        (composeViewRoot as? View ?: composeViewRoot.view).getLocationOnScreen(it)
      }
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

  internal fun SemanticsNode.orderSemanticsNodeGroup(): List<SemanticsNode> {
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
        val childTraversalIndex = child.config.getOrNull(SemanticsProperties.TraversalIndex)
        if (childTraversalIndex != null) {
          topLevelNodes.add(SemanticsNodeTraversalEntry(childTraversalIndex, descendants))
        } else {
          for (node in descendants) {
            topLevelNodes.add(SemanticsNodeTraversalEntry(nodes = listOf(node)))
          }
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
    if (isHiddenFromAccessibility()) return

    // SemanticsNode.boundsInScreen isn't reported correctly for nodes so boundsInRoot + locationOnScreen used to correctly calculate displayBounds.
    val displayBounds = with(boundsInRoot) {
      Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()).run {
        offset(locationOnScreen[0], locationOnScreen[1])
        Rect(left, top, right.coerceIn(0, viewBounds.right), bottom.coerceIn(0, viewBounds.bottom))
      }
    }

    val announcedNodes = if (config.isMergingSemanticsOfDescendants) {
      unmergedNodes?.firstOrNull { it.id == id }?.findAllUnmergedNodes()
    } else {
      listOf(this)
    }

    val element = AccessibilityElement.fromSemanticsNode(
      node = this,
      displayBounds = displayBounds,
      unmergedNodes = unmergedNodes
    )

    // An interactive node needs a label of its own: a role, state or click action alone (e.g. "Button") isn't one.
    val hasLabel = announcedNodes.orEmpty().any { it.hasLabel() }
    val coveredByParent = isCoveredByParentLabel()
    val hasArea = boundsInRoot.width > 0f && boundsInRoot.height > 0f

    if (isInteractive() && !hasLabel && !coveredByParent && hasArea) {
      // Keep whatever else is announced (role, state, actions) so the legend still says what the element is.
      val description = listOfNotNull(MISSING_DESCRIPTION_LABEL, element?.legendText)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifEmpty { MISSING_DESCRIPTION_LABEL }
      processElement(
        (element ?: AccessibilityElement(id = description, displayBounds = displayBounds)).copy(
          id = description,
          displayBounds = displayBounds,
          mainAccessibilityText = description,
          unmergedElements = emptyList(),
          isMissingDescription = true
        )
      )
    } else if (element != null) {
      processElement(element)
    }
  }

  private fun View.hasAccessibleDescription(): Boolean {
    val mainText = iterableTextForAccessibility?.toString() ?: contentDescription?.toString()
    return !mainText.isNullOrBlank()
  }

  private fun View.isMissingAccessibilityDescription(): Boolean {
    if (!isVisible) return false
    if (importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO ||
      importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    ) {
      return false
    }

    if (this is AbstractComposeView || this is ViewRootForTest || this.javaClass.simpleName == "AndroidComposeView") {
      return false
    }

    val isInteractive = isClickable || isLongClickable || this is Checkable || this is Button ||
      (isFocusable && this !is AdapterView<*> && this !is ScrollView && this !is HorizontalScrollView)

    if (this is ViewGroup && childCount > 0 && !isInteractive) {
      return false
    }

    if (hasAccessibleDescription()) return false

    if (this is ImageView) {
      return isInteractive || importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    return isInteractive
  }

  private fun SemanticsNode.isHiddenFromAccessibility(): Boolean {
    val hiddenFromAccessibility =
      config.getOrNull(SemanticsProperties.InvisibleToUser) != null ||
        config.getOrNull(SemanticsProperties.HideFromAccessibility) != null
    val hasZeroAlphaModifier = layoutInfo.getModifierInfo().any {
      it.modifier == Modifier.alpha(0f)
    }
    return hiddenFromAccessibility || hasZeroAlphaModifier
  }

  private fun SemanticsNode.isInteractive(): Boolean {
    val role = config.getOrNull(SemanticsProperties.Role)?.toString()
    if (role in INTERACTIVE_ROLES) return true
    if (config.getOrNull(SemanticsProperties.ToggleableState) != null) return true
    if (config.getOrNull(SemanticsProperties.Selected) != null) return true
    if (config.getOrNull(SemanticsActions.SetProgress) != null) return true
    // Covers Modifier.clickable / combinedClickable without any other semantics. A long click alone
    // (e.g. a tooltip anchor) isn't treated as interactive, since the anchored content carries the label.
    if (config.getOrNull(SemanticsActions.OnClick) != null) return true
    return false
  }

  /** Whether this node names itself, through a content description, text or editable text. */
  private fun SemanticsNode.hasLabel(): Boolean {
    if (isHiddenFromAccessibility()) return false
    return config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() } ||
      config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() } ||
      !config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrBlank()
  }

  /**
   * Whether an ancestor's label is announced for this node, covering its lack of an individual label.
   *
   * Only suppresses warnings when the parent label is actually announced for this node (e.g. when
   * an ancestor merges descendants and has a label, or when a parent text node covers the child),
   * rather than suppressing any child whenever parent?.hasLabel() is true.
   */
  private fun SemanticsNode.isCoveredByParentLabel(): Boolean {
    var current = parent
    while (current != null) {
      if (current.config.isMergingSemanticsOfDescendants) {
        return current.findAllUnmergedNodes().any { it.hasLabel() }
      }
      if (current == parent &&
        current.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() }
      ) {
        return true
      }
      current = current.parent
    }
    return false
  }

  private companion object {
    private val INTERACTIVE_ROLES = setOf("Button", "Checkbox", "Switch", "RadioButton", "Tab")

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
