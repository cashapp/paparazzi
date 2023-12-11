/*
 * Copyright (C) 2020 Square, Inc.
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
package app.cash.paparazzi.plugin.test

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class TextAppearanceTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verify() {
    val context = paparazzi.context
    val view = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundResource(android.R.color.white)

      val textStyle = R.style.TextAppearance_Title
      addView(
        createTextView(context).apply {
          text = "Hello, Text Appearance!"
          setTextAppearance(textStyle)
        }
      )
      addView(
        createTextView(ContextThemeWrapper(context, textStyle)).apply {
          text = "Hello, Style!"
        }
      )
    }
    paparazzi.snapshot(view)
  }

  private fun createTextView(context: Context) =
    TextView(context, null, 0).apply {
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
}
