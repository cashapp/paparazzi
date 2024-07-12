/*
 * Copyright (C) 2019 Square, Inc.
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
import android.graphics.Typeface
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class CustomFontsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun inCode() {
    val context = paparazzi.context

    val root = LinearLayout(context).apply {
      orientation = VERTICAL
      gravity = CENTER

      addView(
        createTextView(context).apply {
          typeface = ResourcesCompat.getFont(context, R.font.cashmarket_medium)
          text = "Normal (default)"
        }
      )
      addView(
        createTextView(context).apply {
          setTypeface(ResourcesCompat.getFont(context, R.font.cashmarket_medium), Typeface.NORMAL)
          text = "Normal (styled)"
        }
      )
      addView(
        createTextView(context).apply {
          typeface = ResourcesCompat.getFont(context, R.font.cashmarket_medium_normal)
          text = "Normal (explicit)"
        }
      )
      addView(
        createTextView(context).apply {
          setTypeface(ResourcesCompat.getFont(context, R.font.cashmarket_medium), Typeface.ITALIC)
          text = "Italics (styled)"
        }
      )
      addView(
        createTextView(context).apply {
          typeface = ResourcesCompat.getFont(context, R.font.cashmarket_medium_italic)
          text = "Italics (explicit)"
        }
      )
    }

    paparazzi.snapshot(root)
  }

  @Test
  fun inXml() {
    val view = paparazzi.inflate<View>(R.layout.textviews)
    paparazzi.snapshot(view)
  }

  @Test
  fun singleLine() {
    val text = object : TextView(paparazzi.context) {
      init {
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1f)
        textSize = 32f
        typeface = ResourcesCompat.getFont(context, R.font.cashmarket_medium)
        text = "Single line sample"
        isSingleLine = true
        gravity = CENTER
      }
    }
    paparazzi.snapshot(text)
  }

  private fun createTextView(context: Context) =
    AppCompatTextView(context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f)
      gravity = CENTER
      textSize = 32f
    }
}
