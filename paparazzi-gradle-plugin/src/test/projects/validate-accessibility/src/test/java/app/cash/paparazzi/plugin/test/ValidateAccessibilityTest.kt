package app.cash.paparazzi.plugin.test

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ValidateAccessibilityTest {
  @get:Rule
  val paparazzi = Paparazzi(validateAccessibility = true)

  @Test
  fun validateTextContrast() {
    val textViewBad = TextView(paparazzi.context).apply {
      text = "Low Contrast"
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.WHITE)
    }

    val textViewGood = TextView(paparazzi.context).apply {
      text = "High Contrast"
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.BLACK)
    }

    val view = LinearLayout(paparazzi.context).apply {
      addView(textViewBad)
      addView(textViewGood)
    }

    paparazzi.snapshot(view)
  }
}
