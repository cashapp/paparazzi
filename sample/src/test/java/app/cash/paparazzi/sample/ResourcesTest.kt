package app.cash.paparazzi.sample

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ResourcesTest(
  @TestParameter locale: Locale
) {
  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5.copy(locale = locale.tag)
  )

  @Test
  fun legacy() {
    paparazzi.snapshot(ResourcesDemoView(paparazzi.context))
  }

  @Test
  fun compose() {
    paparazzi.snapshot { ResourcesDemo() }
  }

  enum class Locale(val tag: String?) {
    Default(null), AR("ar")
  }
}
