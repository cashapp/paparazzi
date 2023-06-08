package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class LocaleQualifierTest(
  @TestParameter locale: Locale
) {
  enum class Locale(val tag: String?) {
    Default(null), GB("en-rGB")
  }

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(
      locale = locale.tag
    )
  )

  @Test
  fun locale() {
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
  }
}
