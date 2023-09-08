package app.cash.paparazzi.sample

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class MapsTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun default() {
    paparazzi.snapshot {
      PaparazziMap()
    }
  }
}


