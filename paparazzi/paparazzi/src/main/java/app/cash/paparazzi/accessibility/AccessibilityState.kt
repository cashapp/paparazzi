/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Shared representation of the accessibility elements in a Compose view.
 * A bridge between the ComposeA11yExtension and A11ySnapshotHandler.
 */
data class AccessibilityState(
  val width: Int,
  val height: Int,
  val elements: List<Element>
) {
  data class Element(
    val id: String,
    val displayBounds: Rect,
    val touchBounds: Rect? = null,
    val text: List<String>? = null,
    val contentDescription: List<String>? = null,
    val stateDescription: String? = null,
    val onClickLabel: String? = null,
    val role: String? = null,
    val disabled: Boolean = false,
    val heading: Boolean = false,
    val customActions: List<CustomAction>? = null,
    val progress: Progress? = null
  ) {
    fun renderString() =
      text?.joinToString(", ") ?: contentDescription?.joinToString(", ") ?: stateDescription ?: onClickLabel ?: role
  }

  data class CustomAction(val label: String)

  data class Progress(
    val current: Float,
    val range: ClosedRange<Float>,
    val steps: Int,
    val hasAction: Boolean
  ) {
    override fun toString(): String {
      return "$current [$range] ${if (hasAction) "Action" else ""}"
    }
  }
}

fun View.accessibilityState(): AccessibilityState {
  val elements = buildList {
    processAccessibleChildren {
      add(it)
    }
  }
  return AccessibilityState(
    width,
    height,
    elements
  )
}

private fun View.processAccessibleChildren(
  processElement: (AccessibilityState.Element) -> Unit
) {
  if (isImportantForAccessibility && !iterableTextForAccessibility.isNullOrBlank()) {
    val bounds = Rect()
    getBoundsOnScreen(bounds)

    processElement(
      AccessibilityState.Element(
        id = "${this::class.simpleName}($iterableTextForAccessibility)",
        displayBounds = bounds,
        contentDescription = listOf(iterableTextForAccessibility!!.toString())
      )
    )
  }

  if (this is AbstractComposeView) {
    // ComposeView creates a child view `AndroidComposeView` for view root for test.
    val viewRoot = getChildAt(0) as? ViewRootForTest
    viewRoot?.semanticsOwner?.rootSemanticsNode?.processAccessibleChildren(this, processElement)
  }

  if (this is ViewGroup) {
    (0 until childCount).forEach {
      getChildAt(it).processAccessibleChildren(processElement)
    }
  }
}

private fun SemanticsNode.processAccessibleChildren(
  rootView: View,
  processElement: (AccessibilityState.Element) -> Unit
) {
  val id = id
  val contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
  val stateDescription = config.getOrNull(SemanticsProperties.StateDescription)
  val onClickLabel = config.getOrNull(SemanticsActions.OnClick)?.label
  val role = config.getOrNull(SemanticsProperties.Role)?.toString()
  val disabled = config.getOrNull(SemanticsProperties.Disabled) != null
  val heading = config.getOrNull(SemanticsProperties.Heading) != null
  val customActions = config.getOrNull(SemanticsActions.CustomActions)
  val text = config.getOrNull(SemanticsProperties.Text)
  val progress = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
  val hasProgressAction = config.getOrNull(SemanticsActions.SetProgress) != null

  if (contentDescription != null || stateDescription != null || onClickLabel != null || role != null || progress != null || text != null) {
    val position = boundsInWindow.toAndroidRect()
    val touchBounds = touchBoundsInRoot.toAndroidRect()
    processElement(
      AccessibilityState.Element(
        id.toString(),
        position,
        if (touchBounds != position) touchBounds else null,
        text?.map { it.toString() },
        contentDescription,
        stateDescription,
        onClickLabel,
        role,
        disabled,
        heading,
        customActions?.map { AccessibilityState.CustomAction(label = it.label) },
        progress?.let {
          AccessibilityState.Progress(it.current, it.range, it.steps, hasProgressAction)
        }
      )
    )
  }

  children.forEach {
    it.processAccessibleChildren(rootView, processElement)
  }
}
