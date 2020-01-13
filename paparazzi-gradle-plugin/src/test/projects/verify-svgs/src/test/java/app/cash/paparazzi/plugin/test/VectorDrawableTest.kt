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

import android.view.Gravity.CENTER
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout.LayoutParams
import androidx.appcompat.widget.AppCompatImageView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class VectorDrawableTest {
  @get:Rule
  var paparazzi = Paparazzi()

  @Test
  fun vectorDrawable() {
    val imageView = AppCompatImageView(paparazzi.context).apply {
      layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
          .apply {
            gravity = CENTER
            height = 400
            width = 400
          }
      setImageResource(R.drawable.arrow_up)
    }
    paparazzi.snapshot(imageView, "arrow up")
  }
}
