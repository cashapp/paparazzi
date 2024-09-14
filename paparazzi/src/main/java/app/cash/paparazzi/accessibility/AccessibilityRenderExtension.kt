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
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.view.WindowManagerImpl
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.core.content.getSystemService
import app.cash.paparazzi.RenderExtension
import com.android.internal.view.OneShotPreDrawListener

public class AccessibilityRenderExtension : RenderExtension {
  override fun renderView(
    contentView: View
  ): View {
    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

      val overlay = AccessibilityOverlayView(context).apply {
        addView(contentView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
      }

      val contentLayoutParams = contentView.layoutParams ?: generateLayoutParams(null)
      addView(overlay, LinearLayout.LayoutParams(contentLayoutParams.width, contentLayoutParams.height, 1f))

      val overlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(overlayDetailsView, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f))

      OneShotPreDrawListener.add(this) {
        // Window Manager needed to access accessibility elements for views that draw to other windows
        val windowManager = context.getSystemService<WindowManager>()
        val windowManagerRootView = (windowManager as WindowManagerImpl).currentRootView

        val elements = buildList {
          windowManagerRootView?.processAccessibleChildren { add(it) }
          processAccessibleChildren { add(it) }
        }

        overlayDetailsView.addElements(elements)
        overlay.addElements(elements)
      }
    }
  }

  private fun View.processAccessibleChildren(
    processElement: (AccessibilityElement) -> Unit
  ) {
    if (isImportantForAccessibility && !iterableTextForAccessibility.isNullOrBlank() && visibility == VISIBLE) {
      val bounds = Rect().also(::getBoundsOnScreen)

      processElement(
        AccessibilityElement(
          id = "${this::class.simpleName}($iterableTextForAccessibility)",
          displayBounds = bounds,
          contentDescription = iterableTextForAccessibility!!.toString()
        )
      )
    }

    if (this is AbstractComposeView) {
      // ComposeView creates a child view `AndroidComposeView` for view root for test.
      val viewRoot = getChildAt(0) as ViewRootForTest
      val unmergedNodes = viewRoot.semanticsOwner.getAllSemanticsNodes(false)
      viewRoot.semanticsOwner.rootSemanticsNode.processAccessibleChildren(processElement, unmergedNodes)
    }

    if (this is ViewGroup) {
      (0 until childCount).forEach {
        getChildAt(it).processAccessibleChildren(processElement)
      }
    }
  }

  private fun SemanticsNode.processAccessibleChildren(
    processElement: (AccessibilityElement) -> Unit,
    unmergedNodes: List<SemanticsNode>?
  ) {
    val accessibilityText = if (config.isMergingSemanticsOfDescendants) {
      val unmergedNode = unmergedNodes?.filter { it.id == id }
      unmergedNode?.first()?.let { node ->
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

    children.forEach {
      it.processAccessibleChildren(processElement, unmergedNodes)
    }
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
  val stateDescription = config.getOrNull(SemanticsProperties.StateDescription)
  val selected = if (stateDescription != null) {
    // The selected state is only read by TalkBack if the state description is not set
    null
  } else {
    config.getOrNull(SemanticsProperties.Selected)?.let { if (it) "<selected>" else "<unselected>" }
  }
  val mainAccessibilityText =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
      ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(", ")
  val role = config.getOrNull(SemanticsProperties.Role)?.toString()
  val disabled = if (config.getOrNull(SemanticsProperties.Disabled) != null) "<disabled>" else null
  val onClickLabel = if (disabled != null) {
    null
  } else {
    config.getOrNull(SemanticsActions.OnClick)?.label?.let { "<on-click>: $it" }
  }
  val heading = if (config.getOrNull(SemanticsProperties.Heading) != null) "<heading>" else null
  val toggleableState = config.getOrNull(SemanticsProperties.ToggleableState)?.let {
    buildString {
      append("<toggleable>: ")
      append(
        when (it) {
          ToggleableState.On -> "checked"
          ToggleableState.Off -> "not checked"
          ToggleableState.Indeterminate -> "indeterminate"
        }
      )
    }
  }

  val textList = listOfNotNull(
    stateDescription,
    selected,
    toggleableState,
    mainAccessibilityText,
    role,
    disabled,
    onClickLabel,
    heading
  )
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
