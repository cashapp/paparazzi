package app.cash.paparazzi.plugin.test

import android.view.Gravity
import android.widget.TextView
import app.cash.paparazzi.Paparazzi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import runner.PaparazziExtension

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaparazziJupiterTest {

  @RegisterExtension
  val paparazziExtension = PaparazziExtension(Paparazzi())

  @Test
  fun `verify paparazzi jupiter snapshot`() {
    val textView = paparazziExtension.api.inflate<TextView>(android.R.layout.simple_list_item_1)
    textView.apply {
      text = "Paparazzi Jupiter test"
      textSize = 24f
      gravity = Gravity.CENTER
    }

    paparazziExtension.api.snapshot(textView)
  }
}
