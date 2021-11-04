package app.cash.paparazzi.sample

import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
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
    val deviceConfig: DeviceConfig,
  ) {
    NEXUS_4(deviceConfig = DeviceConfig.NEXUS_4),
    NEXUS_5(deviceConfig = DeviceConfig.NEXUS_5),
    NEXUS_5_LAND(deviceConfig = DeviceConfig.NEXUS_5_LAND),
  }

  enum class Theme(val themeName: String) {
    LIGHT("android:Theme.Material.Light"),
    LIGHT_NO_ACTION_BAR("android:Theme.Material.Light.NoActionBar")
  }

  @get:Rule
  val paparazzi = Paparazzi(deviceConfig = config.deviceConfig)

  @Test
  fun simple() {
    val launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(launch)
  }

  @Test
  fun simpleWithTheme(@TestParameter theme: Theme) {
    val launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(launch, theme = theme.themeName)
  }
}
