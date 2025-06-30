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
import android.os.ext.util.SdkLevel
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManagerImpl
import android.widget.Checkable
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.LiveRegionMode.Companion.Assertive
import androidx.compose.ui.semantics.LiveRegionMode.Companion.Polite
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.LinkAnnotation
import androidx.core.view.isVisible
import app.cash.paparazzi.RenderExtension
import app.cash.paparazzi.internal.ComposeViewAdapter
import com.android.internal.view.OneShotPreDrawListener

/**
 * A [RenderExtension] that overlays accessibility property information on top of the rendered view.
 *
 * See [Paparazzi's accessibility documentation](https://cashapp.github.io/paparazzi/accessibility/) for usage
 * information and interpretation tips.
 */
public class AccessibilityRenderExtension : RenderExtension {
  override fun renderView(contentView: View): View {
    // WindowManager needed to access accessibility elements for views that draw to other windows.
    val windowManager = contentView.context.getSystemService(WindowManager::class.java)

    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

      addView(contentView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(overlayDetailsView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      val overlayDrawable = AccessibilityOverlayDrawable()
      viewTreeObserver.addOnGlobalLayoutListener {
        val rootView = contentView.findRootView()
        rootView.foreground = overlayDrawable

        // The root of the view hierarchy is rendered at full width.
        // We need to restrict it when taking accessibility snapshots.
        val windowManagerRootView = (windowManager as WindowManagerImpl).currentRootView
        if (windowManagerRootView != null) {
          windowManagerRootView.layoutParams =
            FrameLayout.LayoutParams(contentView.measuredWidth, MATCH_PARENT, Gravity.START)
        }

        OneShotPreDrawListener.add(this@apply) {
          val elements = buildSet {
            windowManagerRootView?.processAccessibleChildren { add(it) }
            processAccessibleChildren { add(it) }
          }

          overlayDrawable.updateElements(elements)
          overlayDetailsView.updateElements(elements)
        }
      }
    }
  }

  private fun View.processAccessibleChildren(processElement: (AccessibilityElement) -> Unit) {
    val accessibilityText = this.accessibilityText()
    if (isImportantForAccessibility && !accessibilityText.isNullOrBlank() && isVisible) {
      val bounds = Rect().also(::getBoundsOnScreen)

      processElement(
        AccessibilityElement(
          id = "${this::class.simpleName}($accessibilityText)",
          displayBounds = bounds,
          contentDescription = accessibilityText
        )
      )
    }

    if (this is AbstractComposeView && isVisible) {
      // ComposeView creates a child view `AndroidComposeView` for view root for test.
      val viewRoot = getChildAt(0) as ViewRootForTest
      val unmergedNodes = viewRoot.semanticsOwner.getAllSemanticsNodes(false)

      val orderedSemanticsNodes = viewRoot.semanticsOwner.rootSemanticsNode.orderSemanticsNodeGroup()
      orderedSemanticsNodes.forEach {
        it.processAccessibleChildren(processElement, unmergedNodes)
      }
    }

    if (this is ViewGroup) {
      (0 until childCount).forEach {
        getChildAt(it).processAccessibleChildren(processElement)
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

  private fun SemanticsNode.processAccessibleChildren(
    processElement: (AccessibilityElement) -> Unit,
    unmergedNodes: List<SemanticsNode>?
  ) {
    val accessibilityText = if (config.isMergingSemanticsOfDescendants) {
      val unmergedNode = unmergedNodes?.filter { it.id == id }
      unmergedNode?.firstOrNull()?.let { node ->
        node.findAllUnmergedNodes()
          .mapNotNull { it.accessibilityText() }
          .joinToString(", ")
          .ifEmpty { null }
      }
    } else {
      accessibilityText()
    }

    if (accessibilityText != null) {
      val displayBounds = with(boundsInWindow) {
        Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
      }
      processElement(
        AccessibilityElement(
          // SemanticsNode.id is backed by AtomicInteger and is not guaranteed consistent across runs.
          id = accessibilityText,
          displayBounds = displayBounds,
          contentDescription = accessibilityText
        )
      )
    }
  }

  private fun SemanticsNode.findAllUnmergedNodes(): List<SemanticsNode> {
    // Semantics information is already set on parent semantic node where `clearAndSetSemantics` is called.
    // No need to iterate through children.
    if (config.isClearingSemantics) return listOf(this)

    return buildList {
      addAll(
        children
          .filter { !it.config.isMergingSemanticsOfDescendants }
          .flatMap { it.findAllUnmergedNodes() }
      )
      add(this@findAllUnmergedNodes)
    }
  }

  private fun SemanticsNode.accessibilityText(): String? {
    val invisibleToUser = config.getOrNull(SemanticsProperties.InvisibleToUser) != null
    val hasZeroAlphaModifier = layoutInfo.getModifierInfo().any {
      // We don't get direct access to an alpha field but we can inspect the modifiers and see if
      // a modifier of 0f was applied to the node.
      it.modifier == Modifier.alpha(0f)
    }
    if (invisibleToUser || hasZeroAlphaModifier) {
      return null
    }

    val stateDescription = config.getOrNull(SemanticsProperties.StateDescription)
    val selected = if (stateDescription != null) {
      // The selected state is only read by TalkBack if the state description is not set
      null
    } else {
      config.getOrNull(SemanticsProperties.Selected)
        ?.let { if (it) SELECTED_LABEL else UNSELECTED_LABEL }
    }
    val mainAccessibilityText =
      config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(", ")
        ?: config.getOrNull(SemanticsProperties.EditableText)?.text
    val role = config.getOrNull(SemanticsProperties.Role)?.toString()
    val editable = if (config.getOrNull(SemanticsProperties.IsEditable) == true) EDITABLE_LABEL else null
    val disabled =
      if (config.getOrNull(SemanticsProperties.Disabled) != null) DISABLED_LABEL else null
    val onClickLabel = if (disabled != null) {
      null
    } else {
      config.getOrNull(SemanticsActions.OnClick)?.label?.let { "$ON_CLICK_LABEL: $it" }
    }
    val heading = if (config.getOrNull(SemanticsProperties.Heading) != null) HEADING_LABEL else null
    val toggleableState = config.getOrNull(SemanticsProperties.ToggleableState)?.let {
      buildString {
        append("$TOGGLEABLE_LABEL: ")
        append(
          when (it) {
            ToggleableState.On -> CHECKED_LABEL
            ToggleableState.Off -> UNCHECKED_LABEL
            ToggleableState.Indeterminate -> INDETERMINATE_LABEL
          }
        )
      }
    }
    val errorLabel = config.getOrNull(SemanticsProperties.Error)
    val progressBarRangeInfo = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
    val progressBarRangeInfoLabel = when (progressBarRangeInfo) {
      ProgressBarRangeInfo.Indeterminate -> "$PROGRESS_LABEL: $INDETERMINATE_LABEL"
      else -> {
        progressBarRangeInfo?.let {
          val progressPercent = (it.current / it.range.endInclusive * 100).toInt()
          "$PROGRESS_LABEL: $progressPercent%"
        }
      }
    }
    val setProgress = config.getOrNull(SemanticsActions.SetProgress)?.let {
      if (it.label != null) {
        "$SET_PROGRESS_LABEL: ${it.label}"
      } else {
        ADJUSTABLE_LABEL
      }
    }

    val liveRegionMode = when (config.getOrNull(SemanticsProperties.LiveRegion)) {
      Assertive -> "$LIVE_REGION_LABEL: $LIVE_REGION_ASSERTIVE_LABEL"
      Polite -> "$LIVE_REGION_LABEL: $LIVE_REGION_POLITE_LABEL"
      else -> null
    }

    val annotatedStringActions = config.getOrNull(SemanticsProperties.Text)?.flatMap { annotatedString ->
      val annotations = annotatedString.getLinkAnnotations(start = 0, end = annotatedString.text.length)

      if (annotations.isNotEmpty()) {
        annotations.map {
          val prefix = if (it.item is LinkAnnotation.Url) {
            URL_ACTION_LABEL
          } else {
            CLICK_ACTION_LABEL
          }

          "$prefix: ${annotatedString.substring(it.start until it.end)}"
        }
      } else {
        emptyList()
      }
    }?.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val customActions = config.getOrNull(SemanticsActions.CustomActions)?.joinToString(", ") { action ->
      "$CUSTOM_ACTION_LABEL: ${action.label}"
    }

    val isInList = parent?.config?.getOrNull(SemanticsProperties.CollectionInfo)?.let { IN_LIST_LABEL }

    return constructTextList(
      stateDescription,
      selected,
      toggleableState,
      mainAccessibilityText,
      role,
      editable,
      disabled,
      onClickLabel,
      heading,
      errorLabel,
      progressBarRangeInfoLabel,
      setProgress,
      liveRegionMode,
      annotatedStringActions,
      customActions,
      isInList
    )
  }

  private fun View.accessibilityText(): String? {
    val nodeInfo = createAccessibilityNodeInfo()
    onInitializeAccessibilityNodeInfo(nodeInfo)

    val parentView = parent?.let { it as? View }
    val isInList = if (parentView != null && parentView.isImportantForAccessibility) {
      val parentNodeInfo = createAccessibilityNodeInfo()
      parentView.onInitializeAccessibilityNodeInfo(parentNodeInfo)

      parentNodeInfo.collectionInfo?.let { IN_LIST_LABEL }
    } else {
      null
    }

    val stateDescription = if (SdkLevel.isAtLeastR()) stateDescription?.toString() else null
    val selected = if (isSelected) SELECTED_LABEL else null
    val toggleableState = if (this is Checkable) {
      buildString {
        append("$TOGGLEABLE_LABEL: ")
        append(if (isChecked) CHECKED_LABEL else UNCHECKED_LABEL)
      }
    } else {
      null
    }
    val mainAccessibilityText = iterableTextForAccessibility?.toString() ?: contentDescription?.toString()
    val editable = if (nodeInfo.isEditable) EDITABLE_LABEL else null
    val disabled = if (!isEnabled) DISABLED_LABEL else null
    val heading = if (SdkLevel.isAtLeastR() && isAccessibilityHeading) HEADING_LABEL else null
    val liveRegionMode = when (accessibilityLiveRegion) {
      View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE -> "$LIVE_REGION_LABEL: $LIVE_REGION_ASSERTIVE_LABEL"
      View.ACCESSIBILITY_LIVE_REGION_POLITE -> "$LIVE_REGION_LABEL: $LIVE_REGION_POLITE_LABEL"
      else -> null
    }
    val customActions = computeCustomActions()

    return constructTextList(
      stateDescription,
      selected,
      toggleableState,
      mainAccessibilityText,
      editable,
      disabled,
      heading,
      liveRegionMode,
      customActions,
      isInList
    )
  }

  private fun View.computeCustomActions(): String? {
    if (!SdkLevel.isAtLeastR()) return null
    val nodeInfo = createAccessibilityNodeInfo()
    accessibilityDelegate?.onInitializeAccessibilityNodeInfo(this, nodeInfo)
    return nodeInfo.actionList
      .filter { it.id > 0 && it.label != null }
      .takeIf { it.isNotEmpty() }
      ?.joinToString(", ") {
        "$CUSTOM_ACTION_LABEL: ${it.label}"
      }
  }

  private fun constructTextList(vararg text: String?): String? {
    val textList = listOfNotNull(*text)
    return if (textList.isNotEmpty()) {
      // Escape newline characters to simplify accessibility text.
      textList.joinToString(", ").replaceLineBreaks()
    } else {
      null
    }
  }

  private fun String.replaceLineBreaks() =
    replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  internal companion object {
    private const val ON_CLICK_LABEL = "<on-click>"
    private const val DISABLED_LABEL = "<disabled>"
    private const val TOGGLEABLE_LABEL = "<toggleable>"
    private const val SELECTED_LABEL = "<selected>"
    private const val UNSELECTED_LABEL = "<unselected>"
    private const val HEADING_LABEL = "<heading>"
    private const val CHECKED_LABEL = "checked"
    private const val UNCHECKED_LABEL = "not checked"
    private const val INDETERMINATE_LABEL = "indeterminate"
    private const val PROGRESS_LABEL = "<progress>"
    private const val SET_PROGRESS_LABEL = "<set-progress>"
    private const val ADJUSTABLE_LABEL = "<adjustable>"
    private const val URL_ACTION_LABEL = "<url-action>"
    private const val CLICK_ACTION_LABEL = "<click-action>"
    private const val CUSTOM_ACTION_LABEL = "<custom-action>"
    private const val LIVE_REGION_LABEL = "<live-region>"
    private const val LIVE_REGION_ASSERTIVE_LABEL = "assertive"
    private const val LIVE_REGION_POLITE_LABEL = "polite"
    private const val EDITABLE_LABEL = "<editable>"
    private const val IN_LIST_LABEL = "<in-list>"

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

private fun View.findRootView(): View {
  var parent = parent
  while (parent != null) {
    if (parent is ComposeViewAdapter) {
      return parent
    }
    parent = parent.parent
  }
  throw IllegalArgumentException("View hierarchy does not contain a ComposeViewAdapter")
}
