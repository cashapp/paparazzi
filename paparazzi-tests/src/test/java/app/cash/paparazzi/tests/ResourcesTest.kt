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
package app.cash.paparazzi.tests

import android.graphics.Color
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.Toolbar
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ResourcesTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun dimensionFromXml() {
    val linearLayout = paparazzi.inflate<LinearLayout>(R.layout.resources_test_dimensions_from_xml)
    paparazzi.snapshot(linearLayout)
  }

  @Test
  fun dimensionFromObtainStyledAttributes() {
    val typedArray = paparazzi.context.obtainStyledAttributes(
      null,
      intArrayOf(android.R.attr.actionBarSize),
    )
    val toolbarSize = typedArray.getDimensionPixelSize(0, -1)
    typedArray.recycle()

    val linearLayout = LinearLayout(paparazzi.context)
    val toolbar = Toolbar(paparazzi.context).apply {
      setBackgroundColor(Color.BLUE)
      linearLayout.addView(this, LayoutParams(MATCH_PARENT, toolbarSize))
    }
    paparazzi.snapshot(linearLayout)
  }

  @Test
  fun dimensionFromResolveAttribute() {
    val toolbarSize = TypedValue().let { tv ->
      paparazzi.context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
      TypedValue.complexToDimensionPixelSize(tv.data, paparazzi.context.resources.displayMetrics)
    }

    val linearLayout = LinearLayout(paparazzi.context)
    val toolbar = Toolbar(paparazzi.context).apply {
      setBackgroundColor(Color.GREEN)
      linearLayout.addView(this, LayoutParams(MATCH_PARENT, toolbarSize))
    }
    paparazzi.snapshot(linearLayout)
  }
}
