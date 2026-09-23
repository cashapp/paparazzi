package app.cash.paparazzi.plugin.test

import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LayoutlibVersionTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun renders() {
    paparazzi.snapshot(TextView(paparazzi.context).apply { text = "Hello, layoutlib" })
  }
}
