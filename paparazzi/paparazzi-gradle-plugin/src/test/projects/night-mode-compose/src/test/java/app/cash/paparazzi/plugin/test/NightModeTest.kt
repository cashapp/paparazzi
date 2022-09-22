package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class NightModeTest(
  @TestParameter nightMode: NightMode
) {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(nightMode = nightMode)
  )

  @Test
  fun compose() {
    paparazzi.snapshot { LightDark() }
  }
}
