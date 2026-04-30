package app.cash.paparazzi.plugin.test

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FailingRobolectricTest {
  @Test
  fun regularUnitTestMustNotRunDuringPaparazziTask() {
    throw AssertionError("Robolectric test ran during Paparazzi task")
  }
}
