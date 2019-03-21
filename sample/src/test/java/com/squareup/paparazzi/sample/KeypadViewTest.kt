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
package com.squareup.paparazzi.sample

import android.widget.LinearLayout
import android.widget.TextView
import com.squareup.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class KeypadViewTest {
  @get:Rule
  var paparazzi = Paparazzi("com.squareup.paparazzi.sample")

  @Test
  fun testViews() {
    val keypad = paparazzi.inflate<LinearLayout>(R.layout.keypad)
    val amount = keypad.findViewById<TextView>(R.id.amount)
    val amount123 = keypad.findViewById<TextView>(R.id.amount123)
    val amount456 = keypad.findViewById<TextView>(R.id.amount456)
    val amount789 = keypad.findViewById<TextView>(R.id.amount789)
    val amount0 = keypad.findViewById<TextView>(R.id.amount0)

    amount.text = "$0"
    paparazzi.snapshot(keypad, "zero dollars")

    amount.text = "$5.00"
    paparazzi.snapshot(keypad, "five bucks")

    keypad.setBackgroundResource(R.color.keypadDarkGrey)
    val darkGrey = paparazzi.resources.getColor(R.color.keypadDarkGrey)
    keypad.setBackgroundColor(darkGrey)
    amount.text = "$1.00"
    paparazzi.snapshot(keypad, "grey")

    keypad.setBackgroundResource(R.color.keypadDarkGrey)
    keypad.setBackgroundColor(paparazzi.resources.getColor(R.color.bolt))
    amount.setTextColor(darkGrey)
    amount123.setTextColor(darkGrey)
    amount456.setTextColor(darkGrey)
    amount789.setTextColor(darkGrey)
    amount0.setTextColor(darkGrey)
    amount.text = ".01 BTC"
    paparazzi.snapshot(keypad, "bolt")
  }
}
