package app.cash.paparazzi.sample1060.dynamicfeature

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Test

import org.junit.Assert.*
import org.junit.Rule

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class HelloWorldTest {

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun helloWorld() {
    paparazzi.snapshot {
      HelloWorld()
    }
  }
}
