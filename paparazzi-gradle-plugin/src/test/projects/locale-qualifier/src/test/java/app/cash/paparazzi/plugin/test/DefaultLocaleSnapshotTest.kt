package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
internal class DefaultLocaleSnapshotTest(
  @TestParameter private val defaultLocale: SystemPropLocale
) {

  internal enum class SystemPropLocale(val tag: String?) {
    Default(null),
    EN_GB("en-rGB"),
    FR_FR("fr-rFR")
  }

  init {
    System.setProperty("app.cash.paparazzi.localeDefault", defaultLocale.tag.orEmpty())
  }

  @get:Rule
  val paparazzi = Paparazzi()

  @After
  fun tearDown() {
    System.clearProperty("app.cash.paparazzi.localeDefault")
  }

  @Test
  fun `GIVEN system prop WHEN snapshot THEN use provided locale`() {
    paparazzi.snapshot(
      view = paparazzi.inflate(R.layout.title_color)
    )
  }
}
