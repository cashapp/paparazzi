package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class WearGifTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.GALAXY_WATCH4_CLASSIC_LARGE
  )

  @Test
  fun gif() {
    paparazzi.gif(paparazzi.inflate(R.layout.layout))
  }
}
