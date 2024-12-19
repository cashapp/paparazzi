package app.cash.paparazzi.plugin.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test

class ScaledA11yTest {
  @get:Rule
  val paparazzi = Paparazzi(
    theme = "Theme.AppCompat.Light.NoActionBar",
    deviceConfig = DeviceConfig.PIXEL.copy(fontScale = 2.0f),
    renderExtensions = setOf(AccessibilityRenderExtension())
  )

  @Test
  fun legendDoesNotScale() {
    paparazzi.snapshot {
      Column(Modifier.background(Color.LightGray)) {
        Text("Some text that will appear scaled in the UI, but not scaled in the legend")
      }
    }
  }
}
