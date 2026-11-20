package app.cash.paparazzi.plugin.test

import android.view.View
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IsolatedPaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun snapshot() {
    assertEquals("present", System.getProperty("app.cash.paparazzi.test.argumentProvider"))
    paparazzi.snapshot(View(paparazzi.context))
  }

  @Test
  fun excludedByUnitTestFilter() {
    throw AssertionError("Test excluded by the unit test filter ran during Paparazzi task")
  }
}
