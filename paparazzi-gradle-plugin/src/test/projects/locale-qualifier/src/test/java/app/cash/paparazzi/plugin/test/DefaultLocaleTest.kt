package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class DefaultLocaleTest(@TestParameter val locale: Locale) {

  enum class Locale(val tag: String?) {
    DEFAULT(null),
    FR("fr-rFR"),
    GB("en-rGB")
  }

  @Before
  fun setup() {
    System.setProperty("app.cash.paparazzi.defaultLocale", locale.tag.orEmpty())
    paparazzi.unsafeUpdateConfig(
      deviceConfig = DeviceConfig.NEXUS_5.copy(
        locale = System.getProperty("app.cash.paparazzi.defaultLocale")?.takeIf { it.isNotEmpty() }
      )
    )
  }

  @After
  fun teardown() {
    System.clearProperty("app.cash.paparazzi.defaultLocale")
  }

  @get:Rule
  val paparazzi = Paparazzi()

  @Test
  fun `verify system property sets default locale`() {
    paparazzi.snapshot(view = paparazzi.inflate(R.layout.title_color))
  }
}
