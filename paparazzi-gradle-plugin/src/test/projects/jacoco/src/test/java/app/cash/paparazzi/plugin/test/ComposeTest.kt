package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun compose() {
    assertEquals("present", System.getProperty("app.cash.paparazzi.test.firstArgumentProvider"))
    assertEquals("present", System.getProperty("app.cash.paparazzi.test.secondArgumentProvider"))
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }
}
