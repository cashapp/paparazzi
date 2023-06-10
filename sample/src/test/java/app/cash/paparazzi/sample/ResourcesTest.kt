package app.cash.paparazzi.sample

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ResourcesTest {
  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

  @Test
  fun legacy() {
    paparazzi.snapshot(ResourcesDemoView(paparazzi.context))
  }

  @Test
  fun compose() {
    paparazzi.snapshot { ResourcesDemo() }
  }
}
