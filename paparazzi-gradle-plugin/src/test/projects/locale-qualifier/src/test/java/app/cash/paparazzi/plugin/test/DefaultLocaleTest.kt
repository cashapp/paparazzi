package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.Paparazzi
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class DefaultLocaleTest {

  // Work-around to force system property before Paparazzi loads config.
  // Not a pattern to follow or copy.
  companion object {
    @JvmStatic
    @BeforeClass
    fun setup() {
      System.setProperty("app.cash.paparazzi.defaultLocale", "fr-rFR")
    }
  }

  @get:Rule
  val paparazzi = Paparazzi()

  @After
  fun tearDown() {
    System.clearProperty("app.cash.paparazzi.defaultLocale")
  }

  @Test
  fun `verify system property sets default locale`() {
    paparazzi.snapshot(
      view = paparazzi.inflate(R.layout.title_color)
    )
  }
}
