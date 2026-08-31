package app.cash.paparazzi.sample

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SeparateSourceSetTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun compose() {
    paparazzi.snapshot { HelloPaparazzi() }
  }
}
