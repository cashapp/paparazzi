package app.cash.paparazzi.sample

import android.widget.LinearLayout
import android.widget.TextView
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
    val deviceConfig: DeviceConfig
  ) {
    NEXUS_4(deviceConfig = DeviceConfig.NEXUS_4),
    NEXUS_5(deviceConfig = DeviceConfig.NEXUS_5),
    NEXUS_5_LAND(deviceConfig = DeviceConfig.NEXUS_5_LAND),
  }

  enum class Theme(val themeName: String) {
    LIGHT("android:Theme.Material.Light"),
    LIGHT_NO_ACTION_BAR("android:Theme.Material.Light.NoActionBar")
  }

  object AmountProvider : TestParameter.TestParameterValuesProvider {
    override fun provideValues(): List<String> = listOf("\$1.00", "\$2.00", "\$5.00", "\$10.00")
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
    paparazzi.unsafeUpdateConfig(theme = theme.themeName)
    val launch = paparazzi.inflate<LinearLayout>(R.layout.launch)
    paparazzi.snapshot(launch)
  }

  @Test
  fun amountProviderTest(@TestParameter(valuesProvider = AmountProvider::class) amount: String) {
    val keypad = paparazzi.inflate<LinearLayout>(R.layout.keypad)
    val amountView = keypad.findViewById<TextView>(R.id.amount)
    amountView.text = amount
    paparazzi.snapshot(keypad)
  }
}
