package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class LayoutDirectionTest(
  @TestParameter localeAndDirection: LocaleAndDirection
) {
  enum class LocaleAndDirection(
    val tag: String?,
    val direction: LayoutDirection
  ) {
    DefaultRtl(
      tag = null,
      direction = LayoutDirection.RTL
    ),
    AR(
      tag = "ar",
      direction = LayoutDirection.LTR
    )
  }

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.NEXUS_5.copy(
      layoutDirection = localeAndDirection.direction,
      locale = localeAndDirection.tag
    ),
    supportsRtl = true
  )

  @Test
  fun layoutDirection() {
    paparazzi.snapshot(paparazzi.inflate(R.layout.title_color))
  }
}
