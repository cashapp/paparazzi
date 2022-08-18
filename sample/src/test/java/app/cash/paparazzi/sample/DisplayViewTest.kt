package app.cash.paparazzi.sample

import androidx.constraintlayout.widget.ConstraintLayout
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class DisplayViewTest {

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun pixel3() {
    val launch = paparazzi.inflate<ConstraintLayout>(R.layout.display)
    paparazzi.snapshot(launch)
  }
}
