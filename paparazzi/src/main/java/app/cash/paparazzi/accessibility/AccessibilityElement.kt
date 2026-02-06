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
import android.view.View
import android.widget.Checkable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode.Companion.Assertive
import androidx.compose.ui.semantics.LiveRegionMode.Companion.Polite
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.LinkAnnotation

internal data class AccessibilityElement(
  val id: String,
  val displayBounds: Rect,
  val stateDescription: String? = null,
  val selected: String? = null,
  val toggleableState: String? = null,
  val progress: String? = null,
  val setProgress: String? = null,
  val mainAccessibilityText: String? = null,
  val role: String? = null,
  val editable: String? = null,
  val disabled: String? = null,
  val onClickLabel: String? = null,
  val heading: String? = null,
  val errorLabel: String? = null,
  val liveRegionMode: String? = null,
  val annotatedStringActions: String? = null,
  val customActions: String? = null,
  val isInList: String? = null
) {
  val legendText: String
    get() {
      val textList = listOfNotNull(
        stateDescription,
        selected,
        toggleableState,
        progress,
        setProgress,
        mainAccessibilityText,
        role,
        editable,
        disabled,
        onClickLabel,
        heading,
        errorLabel,
        liveRegionMode,
        annotatedStringActions,
        customActions,
        isInList
      )
      return if (textList.isNotEmpty()) {
        textList.joinToString(", ").replaceLineBreaks()
      } else {
        ""
      }
    }

  val color = RenderSettings.getColor(id)

  companion object {
    fun fromView(view: View, displayBounds: Rect): AccessibilityElement? {
      val element = view.toViewElement(displayBounds) ?: return null
      return element.copy(id = "${view::class.simpleName}(${element.legendText})")
    }

    fun fromSemanticsNode(
      node: SemanticsNode,
      displayBounds: Rect,
      unmergedNodes: List<SemanticsNode>?
    ): AccessibilityElement? {
      val mergedAccessibilityText = if (node.config.isMergingSemanticsOfDescendants) {
        val unmergedNode = unmergedNodes?.firstOrNull { it.id == node.id }
        unmergedNode?.findAllUnmergedNodes()
          ?.mapNotNull { it.toSemanticsLegendText() }
          ?.joinToString(", ")
          ?.ifEmpty { null }
          ?.takeIf { it != IN_LIST_LABEL }
      } else {
        null
      }

      if (mergedAccessibilityText != null) {
        return AccessibilityElement(
          // SemanticsNode.id is backed by AtomicInteger and is not guaranteed consistent across runs.
          id = mergedAccessibilityText,
          displayBounds = displayBounds,
          mainAccessibilityText = mergedAccessibilityText
        )
      }

      val element = node.toSemanticsElement(displayBounds) ?: return null
      return element.copy(
        // SemanticsNode.id is backed by AtomicInteger and is not guaranteed consistent across runs.
        id = element.legendText
      )
    }

    private fun View.toViewElement(displayBounds: Rect): AccessibilityElement? {
      val nodeInfo = createAccessibilityNodeInfo()
      onInitializeAccessibilityNodeInfo(nodeInfo)

      val parentView = parent as? View
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
      val hasNonListLabelText = listOfNotNull(
        stateDescription,
        selected,
        toggleableState,
        mainAccessibilityText,
        editable,
        disabled,
        heading,
        liveRegionMode,
        customActions
      ).isNotEmpty()
      val effectiveIsInList = if (hasNonListLabelText) isInList else null

      val element = AccessibilityElement(
        id = "",
        displayBounds = displayBounds,
        stateDescription = stateDescription,
        selected = selected,
        toggleableState = toggleableState,
        mainAccessibilityText = mainAccessibilityText,
        editable = editable,
        disabled = disabled,
        heading = heading,
        liveRegionMode = liveRegionMode,
        customActions = customActions,
        isInList = effectiveIsInList
      )

      return element.takeIf { it.legendText.isNotEmpty() }
    }

    private fun SemanticsNode.toSemanticsElement(displayBounds: Rect): AccessibilityElement? {
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
        // The selected state is only read by TalkBack if the state description is not set.
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
      val disabled = if (config.getOrNull(SemanticsProperties.Disabled) != null) DISABLED_LABEL else null
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
      val progress = when (progressBarRangeInfo) {
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

      val element = AccessibilityElement(
        id = "",
        displayBounds = displayBounds,
        stateDescription = stateDescription,
        selected = selected,
        toggleableState = toggleableState,
        progress = progress,
        setProgress = setProgress,
        mainAccessibilityText = mainAccessibilityText,
        role = role,
        editable = editable,
        disabled = disabled,
        onClickLabel = onClickLabel,
        heading = heading,
        errorLabel = errorLabel,
        liveRegionMode = liveRegionMode,
        annotatedStringActions = annotatedStringActions,
        customActions = customActions,
        isInList = isInList
      )

      return element.takeIf { it.legendText.isNotEmpty() }
    }

    private fun SemanticsNode.toSemanticsLegendText(): String? = toSemanticsElement(Rect())?.legendText

    private fun SemanticsNode.findAllUnmergedNodes(): List<SemanticsNode> {
      if (config.isClearingSemantics) {
        // Semantics information is already set on parent semantic node where `clearAndSetSemantics` is called.
        // No need to iterate through children.
        return listOf(this)
      }

      return buildList {
        addAll(
          children
            .filter { !it.config.isMergingSemanticsOfDescendants }
            .flatMap { it.findAllUnmergedNodes() }
        )
        add(this@findAllUnmergedNodes)
      }
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
  }
}

private fun String.replaceLineBreaks() =
  replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
