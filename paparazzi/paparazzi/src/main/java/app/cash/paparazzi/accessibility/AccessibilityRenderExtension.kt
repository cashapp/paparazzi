/*
 * Copyright (C) 2021 Square, Inc.
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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.RenderExtension
import com.android.internal.view.OneShotPreDrawListener

class AccessibilityRenderExtension : RenderExtension {
  override fun renderView(
    contentView: View
  ): View {
    return LinearLayout(contentView.context).apply {
      orientation = LinearLayout.HORIZONTAL
      weightSum = 2f
      layoutParams = ViewGroup.LayoutParams(
        MATCH_PARENT,
        MATCH_PARENT
      )

      val overlay = AccessibilityOverlayView(context).apply {
        addView(
          contentView,
          FrameLayout.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT
          )
        )
      }

      val contentLayoutParams = contentView.layoutParams ?: generateLayoutParams(null)
      addView(
        overlay,
        LinearLayout.LayoutParams(
          contentLayoutParams.width,
          contentLayoutParams.height,
          1f
        )
      )

      val accessibilityOverlayDetailsView = AccessibilityOverlayDetailsView(context)
      addView(
        accessibilityOverlayDetailsView,
        LinearLayout.LayoutParams(
          MATCH_PARENT,
          MATCH_PARENT,
          1f
        )
      )

      OneShotPreDrawListener.add(this) {
        val elements = buildList {
          processAccessibleChildren { add(it) }
        }

        accessibilityOverlayDetailsView.addElements(elements)
        overlay.addElements(elements)
      }
    }
  }

  private fun View.processAccessibleChildren(
    processElement: (AccessibilityElement) -> Unit
  ) {
    if (isImportantForAccessibility && !iterableTextForAccessibility.isNullOrBlank()) {
      val bounds = Rect()
      getBoundsOnScreen(bounds)

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
      val viewRoot = getChildAt(0) as? ViewRootForTest
      println("Processing ComposeView: ${viewRoot?.semanticsOwner?.rootSemanticsNode?.children?.size}")
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
    processElement: (AccessibilityElement) -> Unit
  ) {
    fun androidx.compose.ui.geometry.Rect.toAndroidRect(): Rect {
      return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    val id = id
    val contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
    val text = config.getOrNull(SemanticsProperties.Text)
    val stateDescription = config.getOrNull(SemanticsProperties.StateDescription)
    val onClickLabel = config.getOrNull(SemanticsActions.OnClick)?.label
    val role = config.getOrNull(SemanticsProperties.Role)

    // TODO: Add input from [AccessibilityRenderExtension] to determine what generates the accessibility text output.
    val accessibilityText = text?.joinToString(", ")
      ?: contentDescription?.joinToString(", ")
      ?: stateDescription
      ?: onClickLabel
      ?: role?.toString()

    if (accessibilityText != null) {
      val displayBounds = boundsInWindow.toAndroidRect()
      processElement(
        AccessibilityElement(
          id = id.toString(),
          displayBounds = displayBounds,
          contentDescription = accessibilityText
        )
      )
    }

    children.forEach {
      it.processAccessibleChildren(rootView, processElement)
    }
  }
}
