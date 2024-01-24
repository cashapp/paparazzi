package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class VisualTransformationTest(
  @TestParameter val configuration: TransformationConfiguration
) {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    theme = "android:Theme.Material.Light.NoActionBar"
  )

  @Test
  fun test() {
    paparazzi.snapshot(name = configuration.testName) {
      CustomBasicText(value = configuration.amount)
    }
  }
}

enum class TransformationConfiguration(
  val amount: String,
  val testName: String
) {
  DECIMAL(
    amount = "10",
    testName = "decimal"
  ),
  FRACTIONAL(
    amount = "10.00",
    testName = "fractional"
  )
}
