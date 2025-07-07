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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

  @Test
  fun compose() {
    paparazzi.snapshot {
      MaterialTheme(
        typography = Typography(
          FontFamily(
            Font(R.font.cashmarket_medium),
          )
        ),
      ) {
        Column(
          modifier = Modifier
            .background(Color.White)
            .fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "Normal (theming)",
          )
          Text(
            text = "Normal (default)",
            style = LocalTextStyle.current.copy(
              fontFamily = FontFamily(
                Font(R.font.cashmarket_medium)
              )
            )
          )
          Text(
            text = "Normal (styled)",
            style = LocalTextStyle.current.copy(
              fontStyle = FontStyle.Normal
            )
          )
          Text(
            text = "Normal (explicit)",
            style = LocalTextStyle.current.copy(
              fontFamily = FontFamily(
                Font(R.font.cashmarket_medium_normal, style = FontStyle.Normal)
              )
            )
          )
          Text(
            text = "Italics (styled)",
            style = LocalTextStyle.current.copy(
              fontStyle = FontStyle.Italic
            )
          )
          Text(
            text = "Italics (explicit)",
            style = LocalTextStyle.current.copy(
              fontFamily = FontFamily(
                Font(R.font.cashmarket_medium_italic, style = FontStyle.Italic)
              )
            )
          )
        }
      }
    }
  }

  private fun createTextView(context: Context) =
    AppCompatTextView(context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f)
      gravity = CENTER
      textSize = 32f
    }
}
