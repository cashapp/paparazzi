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

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.RenderExtension

/**
 * Paparazzi Render Extension that collects Accessibility information for Compose
 * hierarchies.
 *
 * Currently captures Content Description, State Description, On Click, Role, Disabled,
 * Heading, Custom Actions, Text, and Progress.  These are saved as AccessibilityState.Element in
 * the [accessibilityState] list.
 */
class ComposeA11yExtension : RenderExtension {
  lateinit var accessibilityState: AccessibilityState

  private lateinit var rootForTest: RootForTest

  init {
    ViewRootForTest.onViewCreatedCallback = { viewRoot ->
      viewRoot.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(p0: View) {
          // Grab the AndroidComposeView using the same hook that createComposeRule uses
          // Note: It isn't usefully populated at this point.
          rootForTest = p0 as RootForTest
        }

        override fun onViewDetachedFromWindow(p0: View) {
        }
      })
    }
  }

  private fun processAccessibleChildren(
    p0: SemanticsNode,
    fn: (AccessibilityState.Element) -> Unit
  ) {
    val contentDescription = p0.config.getOrNull(SemanticsProperties.ContentDescription)
    val stateDescription = p0.config.getOrNull(SemanticsProperties.StateDescription)
    val onClickLabel = p0.config.getOrNull(SemanticsActions.OnClick)?.label
    val role = p0.config.getOrNull(SemanticsProperties.Role)?.toString()
    val disabled = p0.config.getOrNull(SemanticsProperties.Disabled) != null
    val heading = p0.config.getOrNull(SemanticsProperties.Heading) != null
    val customActions = p0.config.getOrNull(SemanticsActions.CustomActions)
    val text = p0.config.getOrNull(SemanticsProperties.Text)
    val progress = p0.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
    val hasProgressAction = p0.config.getOrNull(SemanticsActions.SetProgress) != null

    if (contentDescription != null || stateDescription != null || onClickLabel != null || role != null || progress != null || text != null) {
      val position = p0.boundsInRoot.toAndroidRect()
      val touchBounds = p0.touchBoundsInRoot.toAndroidRect()
      fn(
        AccessibilityState.Element(
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

    p0.children.forEach {
      processAccessibleChildren(it, fn)
    }
  }

  override fun renderView(contentView: View): View {
    val composeView = (contentView as ViewGroup).getChildAt(0) as ComposeView

    // Capture the accessibility elements during the drawing phase after
    // measurement and layout has occurred
    composeView.viewTreeObserver.addOnPreDrawListener {
      extractAccessibilityState()
      true
    }

    return contentView
  }

  private fun extractAccessibilityState() {
    val rootSemanticsNode = rootForTest.semanticsOwner.rootSemanticsNode
    val elements = buildList {
      processAccessibleChildren(rootSemanticsNode) {
        add(it)
      }
    }
    accessibilityState = AccessibilityState(
      rootSemanticsNode.size.width,
      rootSemanticsNode.size.height,
      elements
    )
  }
}
