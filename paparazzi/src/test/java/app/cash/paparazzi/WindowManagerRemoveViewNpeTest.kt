/*
 * Copyright (C) 2025 Square, Inc.
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
package app.cash.paparazzi

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.WindowManagerImpl
import android.widget.TextView
import org.junit.Rule
import org.junit.Test

/**
 * Regression test for https://github.com/cashapp/paparazzi/issues/2373.
 *
 * layoutlib 16.2.1's [WindowManagerImpl.removeView] does not null-check the result of
 * [ViewGroup.buildOrderedChildList] before calling `size()` on it. When a window (e.g. a
 * Compose Dialog) is dismissed and its view is removed, leaving exactly one child in the
 * window's root view, `buildOrderedChildList()` returns null and `removeView` throws a
 * [NullPointerException].
 *
 * This test reproduces that exact state without needing Compose: after a view is rendered
 * (so the render root is attached and views have a [ViewRootImpl]), it adds two views to the
 * window manager and removes the last one. [WindowManagerImplRemoveViewAdvice] must suppress
 * the resulting NPE for the test to pass.
 */
class WindowManagerRemoveViewNpeTest {
  @get:Rule
  val testRule = PaparazziTestRule()

  val paparazzi
    get() = testRule.paparazzi

  @Test
  fun removeViewDoesNotCrashWhenOneChildRemains() {
    // Render once so the render root is attached; views added to the window manager then
    // have a ViewRootImpl, which is required for the buggy code path in removeView to run.
    paparazzi.snapshot(TextView(paparazzi.context))

    val windowManager = paparazzi.context.getSystemService(Context.WINDOW_SERVICE) as WindowManagerImpl

    val first = TextView(paparazzi.context)
    val last = TextView(paparazzi.context)
    val layoutParams = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT
    )

    windowManager.addView(first, layoutParams)
    windowManager.addView(last, layoutParams)

    // Removing the last added view leaves exactly one child in the window root view, which
    // makes ViewGroup.buildOrderedChildList() return null in the following removeView
    // reentrant path. Without the WindowManagerImplRemoveViewAdvice workaround this throws:
    //   NullPointerException: Cannot invoke "java.util.ArrayList.size()" because
    //   "childrenList" is null
    windowManager.removeView(last)

    // Clean up the remaining window.
    windowManager.removeView(first)
  }
}
