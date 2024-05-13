package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class DeviceResolutionQualifierTest(
  @TestParameter useDeviceResolutionFlag: OriginalDeviceResolutionFlag
) {
  enum class OriginalDeviceResolutionFlag(val useDeviceResolution: Boolean) {
    Default(false),
    UseDeviceRes(true)
  }

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5,
    useDeviceResolution = useDeviceResolutionFlag.useDeviceResolution
  )

  @Test
  fun deviceResolution() {
    paparazzi.snapshot(paparazzi.inflate(R.layout.launch))
  }
}
