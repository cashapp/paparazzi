package app.cash.paparazzi.plugin.test

import android.graphics.Color
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ScreenshotSourceSetTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun record() {
    val view = TextView(paparazzi.context).apply {
      setBackgroundColor(Color.WHITE)
      setTextColor(Color.BLACK)
      text = "screenshotTest source set"
    }
    paparazzi.snapshot(view)
  }
}
