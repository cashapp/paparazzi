package app.cash.paparazzi.plugin.test

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class AccessibilityInsetsTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL.copy(screenWidth = DeviceConfig.PIXEL.screenWidth * 2, softButtons = false),
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun windowInsets() = paparazzi.snapshot(MixedView(paparazzi.context))
}
