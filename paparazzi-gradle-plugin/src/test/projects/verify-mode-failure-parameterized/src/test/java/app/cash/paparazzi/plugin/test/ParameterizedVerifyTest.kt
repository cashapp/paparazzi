package app.cash.paparazzi.plugin.test

import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Text is nondeterministic so a verify run always differs from what was recorded. */
@RunWith(Parameterized::class)
class ParameterizedVerifyTest(private val label: String) {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun verify() {
    paparazzi.snapshot(
      TextView(paparazzi.context).apply {
        text = "$label ${System.nanoTime()}"
        textSize = 24f
      }
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): List<Array<Any>> = listOf(arrayOf<Any>("alpha"))
  }
}
