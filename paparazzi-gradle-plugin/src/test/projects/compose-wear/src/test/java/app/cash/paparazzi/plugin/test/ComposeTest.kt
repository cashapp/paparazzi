package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ComposeTest {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.WEAR_OS_SMALL_ROUND,
    theme = "android:ThemeOverlay.Material.Dark"
  )

  @Test
  fun compose() {
    paparazzi.snapshot {
      HelloPaparazzi()
    }
  }
}
