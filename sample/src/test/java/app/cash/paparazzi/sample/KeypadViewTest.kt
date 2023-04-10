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
package app.cash.paparazzi.sample

import android.animation.ObjectAnimator
import android.view.View
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.sample.databinding.KeypadBinding
import org.junit.Rule
import org.junit.Test

class KeypadViewTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun testViews() {
    val binding = KeypadBinding.inflate(paparazzi.layoutInflater)

    with(binding) {
      amount.text = "$0"
      paparazzi.snapshot(root, "zero dollars")

      amount.text = "$5.00"
      paparazzi.snapshot(root, "five bucks")

      root.setBackgroundResource(R.color.keypadDarkGrey)
      val darkGrey = paparazzi.context.getColor(R.color.keypadDarkGrey)
      root.setBackgroundColor(darkGrey)
      amount.text = "$1.00"
      paparazzi.snapshot(root, "grey")

      root.setBackgroundResource(R.color.keypadDarkGrey)
      root.setBackgroundColor(paparazzi.context.getColor(R.color.bolt))
      amount.setTextColor(darkGrey)
      amount123.setTextColor(darkGrey)
      amount456.setTextColor(darkGrey)
      amount789.setTextColor(darkGrey)
      amount0.setTextColor(darkGrey)
      amount.text = ".01 BTC"

      paparazzi.snapshot(root, "bolt")

      val rotation = ObjectAnimator.ofFloat(amount, View.ROTATION, 0.0f, 360.0f).apply {
        duration = 500
        startDelay = 500
      }
      rotation.start()

      // Uncomment once snapshot verification supports videos
      paparazzi.gif(root, "spin", start = 500, end = 1500, fps = 30)
    }
  }
}
