package app.cash.paparazzi.plugin.test

import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** JUnit 4, via the Vintage wrapper. Text is nondeterministic so verify always differs. */
class MixedRuleTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verify() {
    paparazzi.snapshot(
      TextView(paparazzi.context).apply {
        text = "rule ${System.nanoTime()}"
        textSize = 24f
      }
    )
  }
}
