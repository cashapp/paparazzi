package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SimpleTest {
  @get:Rule
  val paparazzi = Paparazzi(maxPercentDifference = 0.0)

  @Test
  fun compose() {
    paparazzi.snapshot {
      Text("Hello Paparazzi!")
    }
  }
}
