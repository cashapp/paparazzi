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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import org.junit.Rule
import org.junit.Test

class LayoutParamsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun linearLayoutLayoutParams() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
    }

    view.addView(TextView(paparazzi.context).apply {
      setBackgroundColor(Color.BLUE)
      text = "LinearLayout.LayoutParams"
      layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = 100
      }
    })
    view.addView(TextView(paparazzi.context).apply {
      setBackgroundColor(Color.RED)
      text = "LinearLayout.LayoutParams"
      layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = 100
      }
    })

    paparazzi.snapshot(view)
  }

  @Test
  fun linearLayoutMarginLayoutParams() {
    val view = LinearLayout(paparazzi.context).apply {
      orientation = LinearLayout.VERTICAL
    }

    view.addView(TextView(paparazzi.context).apply {
      setBackgroundColor(Color.BLUE)
      text = "LinearLayout.LayoutParams"
      layoutParams = MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = 100
      }
    })
    view.addView(TextView(paparazzi.context).apply {
      setBackgroundColor(Color.RED)
      text = "LinearLayout.LayoutParams"
      layoutParams = MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = 100
      }
    })

    paparazzi.snapshot(view)
  }
}

