/*
 * Copyright (C) 2023 Square, Inc.
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

import android.animation.ObjectAnimator
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class KeypadViewTest {
  @get:Rule
  val paparazzi = Paparazzi(showSystemUi = true)

  @Test
  fun testViews() {
    val keypad = paparazzi.inflate<LinearLayout>(R.layout.keypad)
    val amount = keypad.findViewById<TextView>(R.id.amount)

    val rotation = ObjectAnimator.ofFloat(amount, View.ROTATION, 0.0f, 360.0f).apply {
      duration = 500
      startDelay = 500
    }
    rotation.start()

    paparazzi.gif(keypad, "spin", start = 500, end = 1500, fps = 30)
  }
}
