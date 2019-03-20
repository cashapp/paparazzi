package com.squareup.cash.screenshot.android

import android.widget.Button
import com.squareup.cash.screenshot.R
import com.squareup.cash.screenshot.jvm.Paparazzi
import org.junit.Rule
import org.junit.Test

class BoostViewTest {
  @get:Rule
  var paparazzi = Paparazzi()

  @Test
  fun testViews() {
    val button = paparazzi.inflate<Button>(R.layout.button)

    button.text = "Fuck yeeaaa"

    paparazzi.snapshot()
  }
}
