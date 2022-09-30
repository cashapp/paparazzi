/*
 * Copyright (C) 2022 Block, Inc.
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

import android.graphics.Color
import android.widget.Button
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test

/**
 * Confirm the `viewOnly` attribute has the desired effects.
 */
class ViewOnlyTest {
  @get:Rule
  val ruleTester = RuleTester()

  @Test
  fun viewSmallerThanDeviceResolution() {
    for ((name, paparazzi) in paparazzis()) {
      ruleTester.test(paparazzi) {
        val view = Button(paparazzi.context).apply {
          text = "hello"
          setTextColor(Color.GREEN)
        }

        paparazzi.snapshot(view, name)
      }
    }
  }

  /** Confirm we don't word-wrap strings on a device width that doesn't exist. */
  @Test
  fun viewLargerThanDeviceResolution() {
    for ((name, paparazzi) in paparazzis()) {
      ruleTester.test(paparazzi) {
        val view = Button(paparazzi.context).apply {
          text = "hello ".repeat(25)
          setTextColor(Color.GREEN)
        }

        paparazzi.snapshot(view, name)
      }
    }
  }

  /** Returns objects configured with different rending modes and viewOnly flags. */
  private fun paparazzis(): Map<String, Paparazzi> {
    return buildMap {
      for (viewOnly in listOf(true, false)) {
        for (renderingMode in SessionParams.RenderingMode.values()) {
          val paparazzi = Paparazzi(
            renderingMode = renderingMode,
            viewOnly = viewOnly
          )
          val suffix = if (viewOnly) "-viewOnly" else ""
          put("$renderingMode$suffix", paparazzi)
        }
      }
    }
  }
}
