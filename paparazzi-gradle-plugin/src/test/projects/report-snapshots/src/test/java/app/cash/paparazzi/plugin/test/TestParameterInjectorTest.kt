package app.cash.paparazzi.plugin.test

import androidx.compose.material.Text
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation.LANDSCAPE
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class TestParameterInjectorTest(
  @TestParameter config: Config
) {
  enum class Config(
    val deviceConfig: DeviceConfig
  ) {
    NEXUS_4(deviceConfig = DeviceConfig.NEXUS_4),
    NEXUS_5(deviceConfig = DeviceConfig.NEXUS_5),
    NEXUS_5_LAND(deviceConfig = DeviceConfig.NEXUS_5.copy(orientation = LANDSCAPE))
  }

  @get:Rule
  val paparazzi = Paparazzi(maxPercentDifference = 0.0, deviceConfig = config.deviceConfig)

  @Test
  fun compose() {
    paparazzi.snapshot {
      Text("Hello, Paparazzi")
    }
  }
}
