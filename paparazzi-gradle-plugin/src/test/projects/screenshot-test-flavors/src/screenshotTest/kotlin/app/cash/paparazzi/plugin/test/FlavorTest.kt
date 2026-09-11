package app.cash.paparazzi.plugin.test

import android.graphics.Color
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class FlavorTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun render() {
    val view = TextView(paparazzi.context).apply {
      setBackgroundColor(Color.WHITE)
      setTextColor(Color.BLACK)
      text = "flavored"
    }
    paparazzi.snapshot(view)
  }
}
