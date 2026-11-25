package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class FilteredOutPaparazziTest {
  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun omittedByUnitTestIncludeFilter() {
    throw AssertionError("Paparazzi test omitted by the unit test include filter ran")
  }
}
